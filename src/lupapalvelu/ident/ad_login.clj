(ns lupapalvelu.ident.ad-login
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [noir.core :refer [defpage]]
            [noir.response :as response]
            [noir.request :as request]
            [lupapalvelu.organization :refer [ad-login-data-by-domain get-organization]]
            [lupapalvelu.security :as security]
            [lupapalvelu.user-api :as user-api]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$set]]
            [ring.util.response :refer :all]
            [sade.core :refer [def-]]
            [sade.env :as env]
            [sade.session :as ssess]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as sxml]
            [schema.core :as sc]
            [saml20-clj.sp :as saml-sp]
            [saml20-clj.routes :as saml-routes]
            [saml20-clj.shared :as saml-shared]))

;; Headerit idp:sta
(def ad-header-translations
  {:ad-cn        :fullName
   :ad-firstname :firstName
   :ad-givenname :givenName
   :ad-sn        :lastName
   :ad-mail      :email
   :ad-address   :street
   :ad-zip       :zip
   :ad-city      :city
   :ad-role      :role
   :ad-org-authz :orgAuthz})

(defn- parse-certificate
  "Strip the -----BEGIN CERTIFICATE----- and -----END CERTIFICATE----- headers and newlines
  from certificate."
  [certstring]
  (ss/replace certstring #"[\n ]|(BEGIN|END) CERTIFICATE|-{5}" ""))

(defn make-ad-config [username]
  (let [c (env/get-config)
        ad-data (first (ad-login-data-by-domain username))
        {:keys [enabled idp-cert idp-uri]} (:ad-login ad-data)]
    (when ad-data
      {:app-name "Lupapiste"
       :base-uri (:host c)
       :idp-uri idp-uri ;"http://localhost:7000" ;; The dockerized, locally running mock-saml instance (https://github.com/lupapiste/mock-saml)
       :idp-cert (parse-certificate idp-cert) ;; This needs to live in Mongo's organizations collection
       :keystore-file (get-in c [:ssl :keystore])
       :keystore-password (get-in c [:ssl :key-password])
       :key-alias "jetty" ;; The normal Lupis certificate
       })))

(defn parse-saml-info
  "The saml-info map returned by saml20-clj comes in a wacky format, so its best to
  parse it into a more manageable form (without string keys or single-element lists etc)."
  [element]
  (cond
    (and (seq? element) (= (count element) 1)) (parse-saml-info (first element))
    (seq? element) (mapv parse-saml-info element)
    (map? element) (into {} (for [[k v] element] [(keyword k) (parse-saml-info v)]))
    :else element))

(def ad-config
  (let [c (env/get-config)
        {:keys [keystore key-password]} (:ssl c)
        key-alias "jetty"]
    (atom
      {:app-name "Lupapiste"
       :base-uri (:host c)
       :keystore-file keystore
       :keystore-password key-password
       :key-alias key-alias
       :mutables (assoc (saml-sp/generate-mutables)
                        :xml-signer (saml-sp/make-saml-signer keystore
                                                              key-password
                                                              key-alias
                                                              :algorithm :sha256))
       :sp-cert (saml-shared/get-certificate-b64 keystore key-password key-alias)
       :decrypter (saml-sp/make-saml-decrypter keystore key-password key-alias)
       :organizational-settings {}})))

(defn add-organization-data-to-config! [orgid]
  (if-let [ad-login (:ad-login (get-organization orgid))]
    (let [{:keys [enabled idp-cert idp-uri trusted-domains]} ad-login
          acs-uri (str (:host (env/get-config)) "/api/saml/ad-login/" orgid)]
      (swap! ad-config assoc-in [:organizational-settings (keyword orgid)] {:enabled enabled
                                                                            :idp-cert (parse-certificate idp-cert)
                                                                            :idp-uri idp-uri
                                                                            :trusted-domains trusted-domains
                                                                            :saml-req-factory! (saml-sp/create-request-factory
                                                                                                 (:mutables @ad-config)
                                                                                                 idp-uri
                                                                                                 saml-routes/saml-format
                                                                                                 "Lupapiste"
                                                                                                 acs-uri)}))))

(defpage [:get "/api/saml/ad-login/:orgid"] {orgid :orgid}
  (let [org-data (get-in (add-organization-data-to-config! orgid) [:organizational-settings (keyword orgid)])
        saml-request ((:saml-req-factory! org-data))
        hmac-relay-state (saml-routes/create-hmac-relay-state (:secret-key-spec (:mutables @ad-config)) "target")
        req (request/ring-request)]
    (saml-sp/get-idp-redirect (:idp-uri org-data)
                              saml-request
                              hmac-relay-state)))

(defpage [:post "/api/saml/ad-login/:orgid"] {orgid :orgid}
  (let [req (request/ring-request)
        xml-response (saml-shared/base64->inflate->str (get-in req [:params :SAMLResponse]))
        relay-state (get-in req [:params :RelayState])
        [valid-relay-state? continue-url] (saml-routes/valid-hmac-relay-state? (:secret-key-spec (:mutables @ad-config)) relay-state)
        saml-resp (saml-sp/xml-string->saml-resp xml-response)
        idp-cert (get-in @ad-config [:organizational-settings (keyword orgid) :idp-cert])
        valid-signature? (if idp-cert
                           (saml-sp/validate-saml-response-signature saml-resp idp-cert)
                           false)
        valid? (and valid-relay-state? valid-signature?)
        saml-info (when valid? (saml-sp/saml-resp->assertions saml-resp (:decrypter @ad-config)))
        parsed-saml-info (parse-saml-info saml-info)
        {:keys [email firstName lastName groups]} (get-in parsed-saml-info [:assertions :attrs])
        ;; This retrieves the orgid by user email (these need to be set in the database of course for this to work...)
        _ (info parsed-saml-info)
        _ (clojure.pprint/pprint parsed-saml-info)
        _ (info (str "SAML response validation " (if valid? "was successful" "failed")))
        ]
    (if valid?
      (let [orgAuthz (if (string? groups) #{groups} (set groups))
            user-data {:firstName firstName
                       :lastName  lastName
                       :role      "authority"
                       :email     email
                       :username  email
                       :enabled   true
                       :orgAuthz  {(keyword orgid) orgAuthz}}        ;; validointi ja virheiden hallinta?
            user (if-let [user-from-db (usr/get-user-by-email email)]
                   user-from-db
                   ;; The following form is temporarily commented out since the deep merge breaks orgAuthz field as it is now
                   #_(let [updated-user-data (util/deep-merge user-from-db user-data)]
                     (user-api/update-authority user-from-db email updated-user-data)
                     updated-user-data)
                   (usr/create-new-user {:role "admin"} user-data))
            response (ssess/merge-to-session
                       req
                       (response/redirect (format "%s/app/fi/authority" (:host (env/get-config))))
                       {:user (usr/session-summary user)})]
        response)
      (do
        (error "SAML validation failed")
        (response/status 403 (response/content-type "text/plain" "Validation of SAML response failed"))))))
