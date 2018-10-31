(ns lupapalvelu.ident.ad-login
  (:require [taoensso.timbre :refer [debug info infof warn error errorf]]
            [clj-uuid :as uuid]
            [noir.core :refer [defpage]]
            [noir.response :as resp]
            [noir.request :as request]
            [lupapalvelu.ident.ad-login-util :as ad-util]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$set]]
            [ring.util.codec :refer [form-encode form-decode-str url-encode base64-encode]]
            [ring.util.response :refer :all]
            [sade.env :as env]
            [sade.session :as ssess]
            [sade.util :as util]
            [saml20-clj.sp :as saml-sp]
            [saml20-clj.routes :as saml-routes]
            [saml20-clj.shared :as saml-shared]))

(def ad-config
  {:app-name "Lupapiste"
   :sp-cert (env/value :sso :cert)
   :private-key (env/value :sso :privatekey)})

(defn update-or-create-user!
  "Takes the user creds received from SAML, updates the user info in the DB if necessary.
  If the user doesn't exist already, it's created. Returns the updated/created user."
  [firstName lastName email orgAuthz]
  (let [user-data {:firstName firstName
                   :lastName  lastName
                   :role      "authority"
                   :email     email
                   :username  email
                   :enabled   true
                   :orgAuthz  orgAuthz}]
    (if-let [user-from-db (usr/get-user-by-email email)]
      (let [updated-user-data (util/deep-merge user-from-db user-data)]
        (usr/update-user-by-email email updated-user-data)
        updated-user-data)
      (usr/create-new-user {:role "admin"} user-data))))

(defn log-user-in!
  "Logs the user in and redirects him/her to the main authority page."
  [req user]
  (ssess/merge-to-session
    req
    (resp/redirect (format "%s/app/fi/authority" (env/value :host)))
    {:user (usr/session-summary user)}))

(defpage [:get "/api/saml/metadata/:domain"] {domain :domain}
  (let [{:keys [app-name sp-cert]} ad-config]
    (resp/status 200
                 (resp/xml
                   (ad-util/metadata
                     app-name
                     (ad-util/make-acs-uri domain)
                     (ad-util/parse-certificate sp-cert)
                     true)))))

(defpage [:get "/api/saml/ad-login/:domain"] {domain :domain}
  (let [{:keys [enabled idp-uri]} (-> domain org/get-organizations-by-ad-domain first :ad-login) ; This function returns a sequence
        {:keys [sp-cert private-key]} ad-config
        saml-req-factory! (saml-sp/create-request-factory
                            #(str "_" (uuid/v1))
                            (constantly 0) ; :saml-id-timeouts, not relevant here but required by the library
                            (ad-util/make-saml-signer sp-cert private-key)
                            idp-uri
                            saml-routes/saml-format
                            "Lupapiste"
                            (ad-util/make-acs-uri domain))
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
  (let [req (request/ring-request)]
    (if-not (get-in req [:params :SAMLResponse])
      {:status 400
       :body "No SAML data found in request"}
      (try
        (let [ad-settings (org/get-organizations-by-ad-domain domain) ; The result is a sequence of maps that contain keys :id and :ad-login
              idp-cert (some-> ad-settings first :ad-login :idp-cert)
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
              _ (infof "SAML message signature was %s" (if valid-signature? "valid!" "invalid"))
              {:keys [Group emailaddress givenname name surname]} (get-in parsed-saml-info [:assertions :attrs])
              _ (infof "firstName: %s, lastName: %s, groups: %s, email: %s" givenname surname Group emailaddress)
              ; Resolve authz. Received AD-groups are mapped the corresponding Lupis roles the organization has/organizations have.
              ; The result is formatted like: {:609-R #{"commenter"} :609-YMP #("commenter" "reader")
              authz (ad-util/resolve-authz ad-settings Group)
              _ (infof "Resolved authz for user %s (domain: %s): %s" name domain authz)]
          (cond
            (false? (:success? parsed-saml-info)) (do
                                                    (error "Login was not valid")
                                                    (resp/redirect (format "%s/app/fi/welcome#!/login" (env/value :host))))
            (and valid-signature? (seq authz)) (->> (update-or-create-user! givenname surname emailaddress authz)
                                                    (log-user-in! req))
            valid-signature? (do
                               (error "User does not have organization authorization")
                               (resp/redirect (format "%s/app/fi/welcome#!/login" (env/value :host))))
            :else (do
                    (error "SAML validation failed")
                    (resp/status 403 (resp/content-type "text/plain" "Validation of SAML response failed")))))
        (catch Exception e
          (do
            (error (.getMessage e))
            {:status 400
             :body "Parsing SAML response failed"}))))))
