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
   :acs-uri (format "%s/api/saml/ad-login/%s" (env/value :host) "pori.fi")
   :app-name "Lupapiste"})

(defn validated-login [req firstName lastName email orgAuthz]
  (let [user-data {:firstName firstName
                   :lastName  lastName
                   :role      "authority"
                   :email     email
                   :username  email
                   :enabled   true
                   :orgAuthz  orgAuthz}
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
    (resp/status 200 (ad-util/metadata app-name acs-uri (ad-util/parse-certificate sp-cert) true))))

(defpage [:get "/api/saml/ad-login/:domain"] {domain :domain}
  (let [{:keys [enabled idp-uri]} (-> domain org/get-organizations-by-ad-domain first :ad-login) ; This function returns a sequence
        {:keys [sp-cert private-key]} ad-config
        acs-uri (format "%s/api/saml/ad-login/%s" (env/value :host) domain)
        saml-req-factory! (saml-sp/create-request-factory
                            #(str "_" (uuid/v1))
                            (constantly 0) ; :saml-id-timeouts, not relevant here but required by the library
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
        (errorf "Domain %s does not have valid AD-login settings or has AD-login disabled" domain)
        (resp/redirect (format "%s/app/fi/welcome#!/login" (env/value :host)))))))

(defpage [:post "/api/saml/ad-login/:domain"] {domain :domain}
  (let [ad-settings (org/get-organizations-by-ad-domain domain) ; The result is a sequence of maps that contain keys :id and :ad-login
        idp-cert (some-> ad-settings first :ad-login :idp-cert)
        req (request/ring-request)
        decrypter (ad-util/make-saml-decrypter (:private-key ad-config))
        xml-response (saml-shared/base64->inflate->str (get-in req [:params :SAMLResponse])) ; The raw XML string
        saml-resp (saml-sp/xml-string->saml-resp xml-response) ; An OpenSAML object
        saml-info (saml-sp/saml-resp->assertions saml-resp decrypter) ; The response as a Clojure map
        parsed-saml-info (ad-util/parse-saml-info saml-info) ; The response as a "normal" Clojure map
        _ (infof "Received XML response for domain %s: %s" domain xml-response)
        _ (infof "SAML response for domain %s: %s" domain saml-info)
        _ (infof "Parsed SAML response for domain %s: %s" domain parsed-saml-info)
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
        ; The result is formatted like: {:609-R #{"commenter"} :609-YMP #("commenter" "reader")
        authz (into {} (for [org-setting ad-settings
                             :let [{:keys [id ad-login]} org-setting]
                             :when (true? (:enabled ad-login))]
                         [(keyword (:id org-setting)) (ad-util/resolve-roles (:role-mapping ad-login) Group)]))
        _ (infof "Resolved authz: %s" authz)]
    (cond
      (false? (:success? parsed-saml-info)) (do
                                              (error "Login was not valid")
                                              (resp/redirect (format "%s/app/fi/welcome#!/login" (env/value :host))))
      (and valid-signature? (seq authz)) (validated-login req givenname surname emailaddress authz)
      valid-signature? (do
                         (error "User does not have organization authorization")
                         (resp/redirect (format "%s/app/fi/welcome#!/login" (env/value :host))))
      :else (do
              (error "SAML validation failed")
              (resp/status 403 (resp/content-type "text/plain" "Validation of SAML response failed"))))))
