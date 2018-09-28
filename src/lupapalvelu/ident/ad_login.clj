(ns lupapalvelu.ident.ad-login
  (:require [taoensso.timbre :refer [trace debug info warn error fatal]]
            [noir.core :refer [defpage]]
            [noir.response :as response]
            [noir.request :as request]
            [lupapalvelu.organization :refer [ad-login-data-by-domain get-organization]]
            [lupapalvelu.user-api :as user-api]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$set]]
            [ring.util.response :refer :all]
            [sade.core :refer [def-]]
            [sade.env :as env]
            [sade.session :as ssess]
            [sade.strings :as ss]
            [sade.util :as util]
            [saml20-clj.sp :as saml-sp]
            [saml20-clj.routes :as saml-routes]
            [saml20-clj.shared :as saml-shared]))

(defn- parse-certificate
  "Strip the -----BEGIN CERTIFICATE----- and -----END CERTIFICATE----- headers and newlines
  from certificate."
  [certstring]
  (ss/replace certstring #"[\n ]|(BEGIN|END) CERTIFICATE|-{5}" ""))

(defn- parse-saml-info
  "The saml-info map returned by saml20-clj comes in a wacky format, so its best to
  parse it into a more manageable form (without string keys or single-element lists etc)."
  [element]
  (cond
    (and (seq? element) (= (count element) 1)) (parse-saml-info (first element))
    (seq? element) (mapv parse-saml-info element)
    (map? element) (into {} (for [[k v] element] [(keyword k) (parse-saml-info v)]))
    :else element))

(defn resolve-roles
  "Takes a seq of user roles from the SAML, returns a set of corresponding LP roles."
  [org-roles ad-params]
  (let [ad-roles-set (if (string? ad-params) #{ad-params} (set ad-params))
        orgAuthz     (for [[lp-role ad-role] org-roles]
                       (when (ad-roles-set ad-role)
                         (name lp-role)))]
    (->> orgAuthz (remove nil?) (set))))

(defn validated-login [req orgid firstName lastName email orgAuthz]
  (let [user-data {:firstName firstName
                   :lastName  lastName
                   :role      "authority"
                   :email     email
                   :username  email
                   :enabled   true
                   :orgAuthz  {(keyword orgid) orgAuthz}}        ;; validointi ja virheiden hallinta?
        user (if-let [user-from-db (usr/get-user-by-email email)]
               (let [updated-user-data (util/deep-merge user-from-db user-data)]
                 (user-api/update-authority user-from-db email updated-user-data)
                 updated-user-data)
               (usr/create-new-user {:role "admin"} user-data))
        response (ssess/merge-to-session
                   req
                   (response/redirect (format "%s/app/fi/authority" (:host (env/get-config))))
                   {:user (usr/session-summary user)})]
    response))

(defn make-ad-config [orgid]
  (let [c (env/get-config)
        {:keys [keystore key-password]} (:ssl c)
        key-alias "jetty"
        ad-login (-> orgid get-organization :ad-login)
        {:keys [enabled idp-cert idp-uri trusted-domains]} ad-login
        acs-uri (str (:host (env/get-config)) "/api/saml/ad-login/" orgid)
        mutables (assoc (saml-sp/generate-mutables)
                        :xml-signer (saml-sp/make-saml-signer keystore
                                                              key-password
                                                              key-alias
                                                              :algorithm :sha256))]
    {:app-name "Lupapiste"
     :base-uri (:host c)
     :keystore-file keystore
     :keystore-password key-password
     :key-alias key-alias
     :mutables mutables
     :sp-cert (saml-shared/get-certificate-b64 keystore key-password key-alias)
     :decrypter (saml-sp/make-saml-decrypter keystore key-password key-alias)
     :organizational-settings {:enabled enabled
                               :idp-cert (parse-certificate idp-cert)
                               :idp-uri idp-uri
                               :trusted-domains trusted-domains
                               :saml-req-factory! (saml-sp/create-request-factory
                                                    mutables
                                                    idp-uri
                                                    saml-routes/saml-format
                                                    "Lupapiste"
                                                    acs-uri)}}))

(defpage [:get "/api/saml/ad-login/:orgid"] {orgid :orgid}
  (let [ad-config (make-ad-config orgid)
        saml-req-factory! (get-in ad-config [:organizational-settings :saml-req-factory!])
        saml-request (saml-req-factory!)
        ; Even though we don't use state in our SAML login, a relay state token is created since 'saml-sp/get-idp-redirect' expects it.
        ; It's just not validated when receiving the response.
        hmac-relay-state (saml-routes/create-hmac-relay-state (:secret-key-spec (:mutables ad-config)) "target")]
      (saml-sp/get-idp-redirect (get-in ad-config [:organizational-settings :idp-uri])
                                saml-request
                                hmac-relay-state)))

(defpage [:post "/api/saml/ad-login/:orgid"] {orgid :orgid}
  (let [ad-config (make-ad-config orgid)
        req (request/ring-request)
        xml-response (saml-shared/base64->inflate->str (get-in req [:params :SAMLResponse]))
        saml-resp (saml-sp/xml-string->saml-resp xml-response)
        idp-cert (get-in ad-config [:organizational-settings :idp-cert])
        valid-signature? (if-not idp-cert
                           false
                           (try
                             (saml-sp/validate-saml-response-signature saml-resp idp-cert)
                             (catch java.security.cert.CertificateException e
                               (do
                                 (error (.getMessage e))
                                 false))))
        _ (info (str "SAML message signature was " (if valid-signature? "valid" "invalid")))
        saml-info (when valid-signature? (saml-sp/saml-resp->assertions saml-resp (:decrypter ad-config)))
        parsed-saml-info (parse-saml-info saml-info)
        {:keys [email firstName lastName groups]} (get-in parsed-saml-info [:assertions :attrs])
        _ (clojure.pprint/pprint parsed-saml-info)
        ad-role-map (-> orgid (get-organization) :ad-login :role-mapping)
        authz (resolve-roles ad-role-map groups)]
    (cond
      (and valid-signature? (seq authz)) (validated-login req orgid firstName lastName email authz)
      valid-signature? (do
                         (error "User does not have organization authorization")
                         (response/redirect (format "%s/app/fi/welcome#!/login" (:host (env/get-config)))))
      :else (do
              (error "SAML validation failed")
              (response/status 403 (response/content-type "text/plain" "Validation of SAML response failed"))))))
