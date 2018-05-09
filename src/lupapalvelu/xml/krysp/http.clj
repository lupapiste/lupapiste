(ns lupapalvelu.xml.krysp.http
  (:require [taoensso.timbre :refer [infof debug errorf warn]]
            [clj-http.cookies :as cookies]
            [monger.operators :refer :all]
            [sade.http :as http]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.schema-utils :as ssu]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [lupapalvelu.cookie :as lupa-cookies]
            [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.integrations.messages :as imessages]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org])
  (:import (com.mongodb WriteConcern)
           (javax.jms Session)))

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

(sc/defn ^:always-validate create-url [type :- (apply sc/enum org/endpoint-types) http-conf :- org/KryspHttpConf]
  (-> (ss/join "/" [(:url http-conf) (get-in http-conf [:path type])])
      (ss/strip-leading-slashes)))

(sc/defn ^:always-validate POST
  "HTTP POST given XML string to endpoint defined in http-conf."
  ([type :- (apply sc/enum org/endpoint-types) xml :- sc/Str http-conf :- org/KryspHttpConf]
    (POST type xml http-conf nil))
  ([type :- (apply sc/enum org/endpoint-types)
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

(when (env/feature? :jms)
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
          (catch Exception e    ; this is most likely a slingshot exception from clj-http
            (errorf "Error when sending consumed KuntaGML message to %s: %s. Message rollback initiated." url (.getMessage e))
            (jms/rollback session)))))))

(def kuntagml-queue "lupapiste/kuntagml.http")

(def kuntagml-transacted-session (if-let [conn (jms/get-default-connection)]
                                   (-> conn
                                       (jms/create-transacted-session)
                                       (jms/register-session :consumer))
                                   (warn "No JMS connection available")))

(defonce kuntagml-consumer
  (when kuntagml-transacted-session
    (jms/create-nippy-consumer
      kuntagml-transacted-session
      kuntagml-queue
      (create-kuntagml-message-handler kuntagml-transacted-session))))

(def nippy-producer (when kuntagml-transacted-session   (jms/create-nippy-producer kuntagml-queue)))

(def opts-schema {:user-id sc/Str :application-id ssc/ApplicationId})

(sc/defn ^:always-validate send-xml-jms
  [id :- ssc/ObjectIdStr type :- (apply sc/enum org/endpoint-types) xml :- sc/Str http-conf :- org/KryspHttpConf options :- opts-schema]
  (let [url (create-url type http-conf)]
    (nippy-producer {:message-id id :url url :xml xml :http-conf http-conf :options options}))))

(sc/defn ^:always-validate send-xml
  [application user type :- (apply sc/enum org/endpoint-types) xml :- sc/Str http-conf :- org/KryspHttpConf]
  (let [message-id (mongo/create-id)
        jms?       (env/feature? :jms)]
    (imessages/save (util/strip-nils
                      {:id message-id :direction "out" :messageType (str "KuntaGML " (name type))
                       :partner             (:partner http-conf) :data xml ; TODO where should we put these KuntaGML payloads ?!
                       :transferType        "http" :format "xml" :created (now)
                       :status              (if jms? "queued" "processing") :initator (select-keys user [:id :username])
                       :application         (select-keys application [:id :organization])}))
    (if (and jms? kuntagml-transacted-session)
      (send-xml-jms message-id type xml http-conf {:user-id (:id user) :application-id (:id application)})
      (POST type xml http-conf))
    (infof "KuntaGML (id: %s, type: %s) for partner %s sent via %s successfully" message-id (name type) (:partner http-conf) (if jms? "JMS" "HTTP"))
    (when-not jms?
      (imessages/update-message message-id {$set {:acknowledged (now) :status "done"}}))))
