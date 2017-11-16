(ns lupapalvelu.xml.krysp.http
  (:require [clj-http.cookies :as cookies]
            [monger.operators :refer :all]
            [sade.http :as http]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.schema-utils :as ssu]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [lupapalvelu.cookie :as lupa-cookies]
            [lupapalvelu.integrations.messages :as imessages]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]))


(defn- with-krysp-defaults [options]
  (merge
    {:content-type "text/xml;charset=UTF-8"}
    options
    (when env/dev-mode?
      {:cookie-store
       (doto (cookies/cookie-store)
         (cookies/add-cookie (lupa-cookies/->lupa-cookie "test_db_name" mongo/*db-name*)))})))

(sc/defn wrap-authentication [options http-conf :- org/KryspHttpConf]
  (if-not (:auth-type http-conf)
    options
    (let [creds (org/get-credentials http-conf)]
      (case (:auth-type http-conf)
        "basic" (assoc options :basic-auth creds)
        "x-header" (-> options
                       (update :headers assoc "x-username" (first creds))
                       (update :headers assoc "x-password" (second creds)))))))

(sc/defn create-headers [headers :- (ssu/get org/KryspHttpConf :headers)]
  (when (seq headers)
    (zipmap
      (map (comp ss/lower-case :key) headers)
      (map :value headers))))

(sc/defn POST
  "HTTP POST given XML string to endpoint defined in http-conf."
  ([xml :- sc/Str http-conf :- org/KryspHttpConf]
    (POST xml http-conf nil))
  ([xml :- sc/Str http-conf :- org/KryspHttpConf options :- {sc/Keyword sc/Any}]
    (http/post
      (:url http-conf)
      (-> options
          (assoc :body xml)
          (with-krysp-defaults)
          (update :headers merge (create-headers (:headers http-conf)))
          (wrap-authentication http-conf)))))

(sc/defn send-xml
  [application user type :- sc/Str xml :- sc/Str http-conf :- org/KryspHttpConf]
  (let [message-id (mongo/create-id)]
    (imessages/save (util/strip-nils
                      {:id message-id :direction "out" :messageType type
                       :partner             (:partner http-conf)
                       :transferType        "http" :format "xml" :created (now)
                       :status              "processing" :initator (select-keys user [:id :username])
                       :application         (select-keys application [:id :organization])}))
    (POST xml http-conf)
    (imessages/update-message message-id {$set {:acknowledged (now) :status "done"}})))
