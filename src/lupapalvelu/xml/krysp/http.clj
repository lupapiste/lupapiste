(ns lupapalvelu.xml.krysp.http
  (:require [taoensso.timbre :refer [infof]]
            [clj-http.cookies :as cookies]
            [monger.operators :refer :all]
            [clojure.tools.reader.edn :as edn]
            [sade.http :as http]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.schema-utils :as ssu]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [lupapalvelu.cookie :as lupa-cookies]
            [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.integrations.messages :as imessages]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]))


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
      (ss/replace #"/+$" "")))

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

(defn message-handler
  [payload]
  (let [{:keys [url xml http-conf]} (edn/read-string payload)]
    (http/post
      url
      (-> (with-krysp-defaults {:body xml})
          (update :headers merge (create-headers (:headers http-conf)))
          (wrap-authentication http-conf)))))

#_(def kuntagml-consumer
  (jms/register-consumer
    "application.kuntagml.http"
    message-handler))

(sc/defn ^:always-validate send-xml-jms
  [type :- (apply sc/enum org/endpoint-types) xml :- sc/Str http-conf :- org/KryspHttpConf]
  (let [url (create-url type http-conf)]
    (jms/send-jms-message
      "application.kuntagml.http"
      (prn-str {:url url :xml xml :http-conf http-conf}))))

(sc/defn ^:always-validate send-xml
  [application user type :- (apply sc/enum org/endpoint-types) xml :- sc/Str http-conf :- org/KryspHttpConf]
  (let [message-id (mongo/create-id)]
    (imessages/save (util/strip-nils
                      {:id message-id :direction "out" :messageType (str "KuntaGML " (name type))
                       :partner             (:partner http-conf) :data xml ; TODO where should we put these KuntaGML payloads ?!
                       :transferType        "http" :format "xml" :created (now)
                       :status              "processing" :initator (select-keys user [:id :username])
                       :application         (select-keys application [:id :organization])}))
    (if (env/feature? :jms)
      (send-xml-jms type xml http-conf)
      (POST type xml http-conf))
    (infof "KuntaGML (type: %s) sent via HTTP successfully to partner %s" (name type) (:partner http-conf))
    (imessages/update-message message-id {$set {:acknowledged (now) :status "done"}})))
