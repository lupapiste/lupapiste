(ns lupapalvelu.backing-system.krysp.http
  (:require [clj-http.cookies :as cookies]
            [lupapalvelu.cookie :as lupa-cookies]
            [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.integrations.message-queue :as mq]
            [lupapalvelu.integrations.messages :as imessages]
            [lupapalvelu.integrations.pubsub :as lip]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [monger.operators :refer :all]
            [mount.core :refer [defstate]]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schema-utils :as ssu]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [taoensso.timbre :refer [infof debug errorf warn]]
            [schema.core :as s])
  (:import (com.mongodb WriteConcern)
           (javax.jms Session)
           [java.lang AutoCloseable]
           [java.net UnknownHostException]
           [clojure.lang ExceptionInfo]))

(defn- with-krysp-defaults [options]
  (merge
    {:content-type "text/xml;charset=UTF-8"}
    options
    (env/in-dev
      {:cookie-store
       (doto (cookies/cookie-store)
         (cookies/add-cookie (lupa-cookies/->lupa-cookie "test_db_name" mongo/*db-name*)))})))

(sc/defn ^:always-validate wrap-authentication [options http-conf :- org/KryspHttpConf]
  (if-not (:auth-type http-conf)
    options
    (let [creds (org/get-credentials http-conf)]
      (case (:auth-type http-conf)
        "basic" (assoc options :basic-auth creds)
        "x-header" (-> options
                       (update :headers assoc "x-username" (first creds))
                       (update :headers assoc "x-password" (second creds)))))))

(sc/defn ^:always-validate create-headers [headers :- (ssu/get org/KryspHttpConf :headers)]
  (when (seq headers)
    (zipmap
      (map (comp ss/lower-case :key) headers)
      (map :value headers))))

(sc/defn ^:always-validate create-url [type :- org/EndpointTypesEnum http-conf :- org/KryspHttpConf]
  (let [path (get-in http-conf [:path type])]
    (assert path (str "No path defined for endpoint " type))
    (-> (ss/join "/" [(:url http-conf) path])
        (ss/strip-trailing-slashes))))

(sc/defn ^:always-validate POST
  "HTTP POST given XML string to endpoint defined in http-conf."
  ([type :- org/EndpointTypesEnum xml :- sc/Str http-conf :- org/KryspHttpConf]
    (POST type xml http-conf nil))
  ([type :- org/EndpointTypesEnum
    xml :- sc/Str
    http-conf :- org/KryspHttpConf
    options :- (sc/maybe {sc/Keyword sc/Any})]
    (http/post
      (create-url type http-conf)
      (-> options
          (assoc :body xml)
          (with-krysp-defaults)
          (update :headers merge (create-headers (:headers http-conf)))
          (wrap-authentication http-conf)))))

(def kuntagml-queue "lupapiste.kuntagml.http")

(when (= (env/value :integration-message-queue) "jms")
  (defn create-kuntagml-message-handler [^Session session]
    (fn [payload]
      (let [{:keys [message-id url xml http-conf options]} payload]
        (logging/with-logging-context {:userId (:user-id options) :applicationId (:application-id options)}
          ; If we would handle duplicates in client code, in theory we could use Session/DUPS_OK_ACKNOWLEDGE mode
          ; to reduce newtork roundtrips to broker. See message-queue-intro.md.
          (try
            (http/post
              url
              (-> (with-krysp-defaults {:body xml})
                  (update :headers merge (create-headers (:headers http-conf)))
                  (wrap-authentication http-conf)))
            (imessages/update-message message-id {$set {:acknowledged (now) :status "done"}} WriteConcern/UNACKNOWLEDGED)
            (jms/commit session)
            (infof "KuntaGML (id: %s) consumed and acknowledged from queue successfully" message-id)
            (catch Exception e ; this is most likely a slingshot exception from clj-http
              (errorf "Error when sending consumed KuntaGML message (%s) to %s: %s. Message rollback initiated." message-id url (.getMessage e))
              (jms/rollback session)))))))

  (defstate ^AutoCloseable kuntagml-jms-session
    :start (jms/create-transacted-session (jms/get-default-connection))
    :stop (.close kuntagml-jms-session))

  (defstate ^AutoCloseable kuntagml-consumer
    :start (jms/listen
             kuntagml-jms-session
             (jms/queue kuntagml-jms-session kuntagml-queue)
             (jms/message-listener (jms/nippy-callbacker (create-kuntagml-message-handler kuntagml-jms-session))))
    :stop (.close kuntagml-consumer))
  )

(when (= (env/value :integration-message-queue) "pubsub")
  (defn- handle-pubsub-message
    [{:keys [message-id url xml http-conf options]}]
    ;; Possible test database must be bound for the Mongo query to work
    (mongo/with-db (or (:db-name options) mongo/*db-name*)
      (logging/with-logging-context {:userId (:user-id options) :applicationId (:application-id options)}
        (try
          (http/post
            url
            (-> (with-krysp-defaults {:body xml})
                (update :headers merge (create-headers (:headers http-conf)))
                (wrap-authentication http-conf)))
          (imessages/update-message message-id {$set {:acknowledged (now) :status "done"}} WriteConcern/UNACKNOWLEDGED)
          (infof "KuntaGML (id: %s) consumed and acknowledged from queue successfully" message-id)
          true
          (catch UnknownHostException _
            (errorf "Unknown host: %s" url)
            true)
          (catch ExceptionInfo ex
            (errorf ex "Error when sending consumed KuntaGML message (%s) to %s." message-id url)
            (let [ed (ex-data ex)]
              (if (= (:status ed) 404)
                ;; ACK
                true
                false)))
          (catch Exception e
            (errorf e "Error when sending consumed KuntaGML message (%s) to %s. NACKing." message-id url)
            false)))))

  (defstate kuntagml-pubsub-consumer
    :start (lip/subscribe kuntagml-queue handle-pubsub-message))
  )

(def opts-schema {:user-id                  sc/Str
                  :application-id           ssc/ApplicationId
                  (s/optional-key :db-name) sc/Str})

(sc/defn ^:always-validate build-mq-message
  [id :- ssc/ObjectIdStr
   type :- org/EndpointTypesEnum
   xml :- sc/Str
   http-conf :- org/KryspHttpConf
   options :- opts-schema]
  {:message-id id
   :url        (create-url type http-conf)
   :xml        xml
   :http-conf  http-conf
   :options    options})

(sc/defn ^:always-validate send-xml
  [application user type :- org/EndpointTypesEnum xml :- sc/Str http-conf :- org/KryspHttpConf]
  (let [message-id (mongo/create-id)
        mq?        (env/feature? :integration-message-queue)]
    (imessages/save (util/strip-nils
                      {:id           message-id
                       :direction    "out"
                       :messageType  (str "KuntaGML " (name type))
                       :partner      (:partner http-conf)
                       :data         xml ; TODO where should we put these KuntaGML payloads ?!
                       :transferType "http"
                       :format       "xml"
                       :created      (now)
                       :status       (if mq? "queued" "processing")
                       :initiator    (select-keys user [:id :username])
                       :application  (select-keys application [:id :organization])}))
    (if mq?
      (mq/publish kuntagml-queue
                  (build-mq-message message-id type xml http-conf {:user-id        (:id user)
                                                                   :application-id (:id application)
                                                                   :db-name        mongo/*db-name*}))
      (POST type xml http-conf))
    (infof "KuntaGML (id: %s, type: %s) for partner %s sent via %s successfully"
           message-id
           (name type)
           (:partner http-conf)
           (if mq? "MQ" "HTTP"))
    (when-not mq?
      (imessages/update-message message-id {$set {:acknowledged (now) :status "done"}}))))
