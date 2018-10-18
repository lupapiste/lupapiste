(ns lupapalvelu.ident.ad-login
  (:require [taoensso.timbre :refer [debug infof warn error errorf]]
            [clj-uuid :as uuid]
            [noir.core :refer [defpage]]
            [noir.response :as resp]
            [noir.request :as request]
            [lupapalvelu.ident.ad-login-util :as ad-util]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$set]]
            [ring.util.response :refer :all]
            [sade.core :refer [def-]]
            [sade.env :as env]
            [sade.session :as ssess]
            [sade.util :as util]
            [saml20-clj.sp :as saml-sp]
            [saml20-clj.routes :as saml-routes]
            [saml20-clj.shared :as saml-shared]))

(def ad-config
  {:sp-cert (env/value :sso :cert)
   :private-key (env/value :sso :privatekey)
   :acs-uri (format "%s/api/saml/ad-login/%s" (env/value :host) "609-R")
   :app-name "Lupapiste"})

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
                 (usr/update-user-by-email email updated-user-data)
                 updated-user-data)
               (usr/create-new-user {:role "admin"} user-data))
        response (ssess/merge-to-session
                   req
                   (resp/redirect (format "%s/app/fi/authority" (env/value :host)))
                   {:user (usr/session-summary user)})]
    response))

(defpage [:get "/api/saml/metadata"] []
  (let [{:keys [app-name sp-cert acs-uri]} ad-config]
    (resp/status 200 (saml-sp/metadata app-name acs-uri (ad-util/parse-certificate sp-cert)))))

(defpage [:get "/api/saml/ad-login/:org-id"] {org-id :org-id}
  (let [{:keys [enabled idp-uri]} (-> org-id org/get-organization :ad-login)
        {:keys [sp-cert private-key]} ad-config
        acs-uri (format "%s/api/saml/ad-login/%s" (env/value :host) org-id)
        saml-req-factory! (saml-sp/create-request-factory
                            #(str "_" (uuid/v1))
                            (constantly 0) ; :saml-id-timeouts
                            (ad-util/make-saml-signer sp-cert private-key)
                            idp-uri
                            saml-routes/saml-format
                            "Lupapiste"
                            acs-uri)
        saml-request (saml-req-factory!)
        relay-state (saml-routes/create-hmac-relay-state (:secret-key-spec (saml-sp/generate-mutables)) "no-op")
        full-querystring (str idp-uri "?" (saml-shared/uri-query-str {:SAMLRequest (saml-shared/str->deflate->base64 saml-request) :RelayState relay-state}))]
    (if enabled
      (do
        (infof "Sent the following SAML-request: %s" saml-request)
        (infof "Complete request string: %s" full-querystring)
        (saml-sp/get-idp-redirect idp-uri saml-request relay-state))
      (do
        (errorf "Organization %s does not have valid AD-login settings or has AD-login disabled" org-id)
        (resp/redirect (format "%s/app/fi/welcome#!/login" (env/value :host)))))))

(defpage [:post "/api/saml/ad-login/:org-id"] {org-id :org-id}
  (let [idp-cert (some-> org-id org/get-organization :ad-login :idp-cert ad-util/parse-certificate)
        req (request/ring-request)
        decrypter (ad-util/make-saml-decrypter (:private-key ad-config))
        xml-response (saml-shared/base64->inflate->str (get-in req [:params :SAMLResponse])) ; The raw XML string
        saml-resp (saml-sp/xml-string->saml-resp xml-response) ; An OpenSAML object
        saml-info (saml-sp/saml-resp->assertions saml-resp decrypter) ; The response as a Clojure map
        parsed-saml-info (ad-util/parse-saml-info saml-info) ; The response as a "normal" Clojure map
        _ (infof "Received XML response for organization %s: %s" org-id xml-response)
        _ (infof "SAML response for organization %s: %s" org-id saml-info)
        _ (infof "Parsed SAML response for organization %s: %s" org-id parsed-saml-info)
        valid-signature? (if-not idp-cert
                           false
                           (try
                             (saml-sp/validate-saml-response-signature saml-resp idp-cert)
                             (catch java.security.cert.CertificateException e
                               (do
                                 (error (.getMessage e))
                                 false))))
        _ (infof "SAML message signature was %s" (if valid-signature? "valid" "invalid"))
        {:keys [Group emailaddress givenname name surname]} (get-in parsed-saml-info [:assertions :attrs])
        _ (infof "firstName: %s, lastName: %s, groups: %s, email: %s" givenname surname Group emailaddress)
        ad-role-map (-> org-id org/get-organization :ad-login :role-mapping)
        _ (infof "AD-role map: %s" ad-role-map)
        authz (ad-util/resolve-roles ad-role-map Group)
        _ (infof "Resolved authz: %s" authz)]
    (cond
      (false? (:success? parsed-saml-info)) (do
                                              (error "Login was not valid")
                                              (resp/redirect (format "%s/app/fi/welcome#!/login" (env/value :host))))
      (and valid-signature? (seq authz)) (validated-login req org-id givenname surname emailaddress authz)
      valid-signature? (do
                         (error "User does not have organization authorization")
                         (resp/redirect (format "%s/app/fi/welcome#!/login" (env/value :host))))
      :else (do
              (error "SAML validation failed")
              (resp/status 403 (resp/content-type "text/plain" "Validation of SAML response failed"))))))
