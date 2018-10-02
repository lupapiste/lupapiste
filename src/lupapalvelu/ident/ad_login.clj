(ns lupapalvelu.ident.ad-login
  (:require [taoensso.timbre :refer [trace debug info warn error fatal]]
            [noir.core :refer [defpage]]
            [noir.response :as response]
            [noir.request :as request]
            [lupapalvelu.organization :as org]
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

(defn validated-login [req org-id firstName lastName email orgAuthz]
  (let [user-data {:firstName firstName
                   :lastName  lastName
                   :role      "authority"
                   :email     email
                   :username  email
                   :enabled   true
                   :orgAuthz  {(keyword org-id) orgAuthz}}        ;; validointi ja virheiden hallinta?
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

(def ad-config
  (let [c (env/get-config)
        {:keys [keystore key-password]} (:ssl c)
        key-alias "jetty"]
    {:app-name "Lupapiste"
     :base-uri (:host c)
     :keystore-file keystore
     :keystore-password key-password
     :key-alias key-alias
     :secret-key-spec (saml-shared/new-secret-key-spec)
     :xml-signer (saml-sp/make-saml-signer keystore
                                           key-password
                                           key-alias
                                           :algorithm :sha256)
     :token-timeout 5 ; This is in minutes
     :sp-cert (saml-shared/get-certificate-b64 keystore key-password key-alias)
     :decrypter (saml-sp/make-saml-decrypter keystore key-password key-alias)}))

(defpage [:get "/api/saml/ad-login/:org-id"] {org-id :org-id}
  (let [{:keys [enabled idp-cert idp-uri trusted-domains]} (-> org-id org/get-organization :ad-login)
        {:keys [app-name xml-signer mutables]} ad-config
        acs-uri (str (:host (env/get-config)) "/api/saml/ad-login/" org-id)
        saml-req-factory! (saml-sp/create-request-factory
                            (constantly 0) ; :saml-id-timeouts
                            (constantly 0) ; :saml-last-id
                            xml-signer
                            idp-uri
                            saml-routes/saml-format
                            app-name
                            acs-uri)
        saml-request (saml-req-factory!)
        relay-state-token (saml-routes/create-hmac-relay-state (:secret-key-spec ad-config) "target")]
    (when enabled
      (do
        (org/add-token-to-org org-id relay-state-token)
        (saml-sp/get-idp-redirect idp-uri saml-request relay-state-token)))))

(defpage [:post "/api/saml/ad-login/:org-id"] {org-id :org-id}
  (let [idp-cert (-> org-id org/get-organization :ad-login :idp-cert parse-certificate)
        req (request/ring-request)
        _ (org/purge-time-out-tokens! org-id (:token-timeout ad-config)) ; Accept only tokens that are not older than the amount specified in ad-config
        xml-response (saml-shared/base64->inflate->str (get-in req [:params :SAMLResponse]))
        saml-resp (saml-sp/xml-string->saml-resp xml-response)
        relay-state-token (get-in req [:params :RelayState])
        tokens (->> (org/get-sent-tokens org-id) (map :token) set)
        [valid-relay-state? _] (saml-routes/valid-hmac-relay-state? (:secret-key-spec ad-config) relay-state-token)
        token-found? (contains? tokens relay-state-token)
        valid-signature? (if-not idp-cert
                           false
                           (try
                             (saml-sp/validate-saml-response-signature saml-resp idp-cert)
                             (catch java.security.cert.CertificateException e
                               (do
                                 (error (.getMessage e))
                                 false))))
        _ (info (str "RelayState parameter was "
                     (if token-found? "found" "not found!") " and "
                     (if valid-relay-state? "valid" "invalid")))
        _ (info (str "SAML message signature was " (if valid-signature? "valid" "invalid")))
        valid? (and token-found? valid-signature? valid-relay-state?)
        _ (info (str "SAML login credentials " (if valid? "valid!" "invalid")))
        saml-info (when valid-signature? (saml-sp/saml-resp->assertions saml-resp (:decrypter ad-config)))
        parsed-saml-info (parse-saml-info saml-info)
        {:keys [email firstName lastName groups]} (get-in parsed-saml-info [:assertions :attrs])
        ad-role-map (-> org-id (org/get-organization) :ad-login :role-mapping)
        _ (org/remove-used-token! org-id relay-state-token)
        authz (resolve-roles ad-role-map groups)]
    (cond
      (and valid? (seq authz)) (validated-login req org-id firstName lastName email authz)
      valid-signature? (do
                         (error "User does not have organization authorization")
                         (response/redirect (format "%s/app/fi/welcome#!/login" (:host (env/get-config)))))
      :else (do
              (error "SAML validation failed")
              (response/status 403 (response/content-type "text/plain" "Validation of SAML response failed"))))))
