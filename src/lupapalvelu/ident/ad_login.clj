(ns lupapalvelu.ident.ad-login
  (:require [taoensso.timbre :refer [debug infof warn error errorf]]
            [clj-uuid :as uuid]
            [noir.core :refer [defpage]]
            [noir.response :as resp]
            [noir.request :as request]
            [lupapalvelu.organization :as org]
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

(defn remove-namespaces-from-kws
  "Convert namespaced map keys like :http://schemas.microsoft.com/identity/claims/tenantid -> :tenantid"
  [m]
  (into {} (for [[k v] m]
             (let [newkey (-> k name (ss/split #"/") last keyword)]
               [newkey v]))))

(defn parse-saml-info
  "The saml-info map returned by saml20-clj comes in a wacky format, so its best to
  parse it into a more manageable form (without string keys or single-element lists etc)."
  [element]
  (cond
    (and (seq? element) (= (count element) 1)) (parse-saml-info (first element))
    (seq? element) (mapv parse-saml-info element)
    (map? element) (into {} (for [[k v] (remove-namespaces-from-kws element)] [(keyword k) (parse-saml-info v)]))
    :else element))

(defn resolve-roles
  "Takes a map of corresponding roles (key = role in Lupis, value is AD-group)
  and a seq of user roles from the SAML, returns a set of corresponding LP roles."
  [org-roles ad-params]
  (let [ad-roles-set (if (string? ad-params) #{ad-params} (set ad-params))
        orgAuthz     (for [[lp-role ad-role] org-roles]
                       (when (ad-roles-set ad-role)
                         (name lp-role)))]
    (->> orgAuthz (remove nil?) set)))

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

(def tila
  (atom {}))

(defn- testlogin []
  (let [req (saml-sp/get-idp-redirect (:idp-uri @tila)
                                      (:saml-request @tila)
                                      (:relay-state @tila))
        url (-> req
                :headers
                clojure.walk/keywordize-keys
                :Location)]
    (clojure.java.browse/browse-url url)))

(defpage [:get "/api/saml/ad-login/:org-id"] {org-id :org-id}
  (let [{:keys [enabled idp-uri]} (-> org-id org/get-organization :ad-login)
        acs-uri (format "%s/api/saml/ad-login/%s" (env/value :host) org-id)
        saml-req-factory! (saml-sp/create-request-factory
                            ; #(str (uuid/v4))
                            ; (:saml-last-id (saml-sp/generate-mutables))
                            uuid/v1
                            ; (constantly "d7591060-cae5-11e8-9157-5795e0ed89a3")
                            (constantly 0) ; :saml-id-timeouts
                            false
                            idp-uri
                            saml-routes/saml-format
                            "Lupapiste"
                            acs-uri)
        saml-request (saml-req-factory!)
        relay-state (saml-routes/create-hmac-relay-state (:secret-key-spec (saml-sp/generate-mutables)) "no-op")
        full-querystring (str idp-uri "?" (saml-shared/uri-query-str {:SAMLRequest (saml-shared/str->deflate->base64 saml-request) :RelayState relay-state}))
        _ (swap! tila assoc :saml-request saml-request :relay-state relay-state :full-querystring full-querystring :idp-uri idp-uri)]
    (if enabled
      (do
        (infof "Sent the following SAML-request: %s" saml-request)
        (infof "Complete request string: %s" full-querystring)
        (saml-sp/get-idp-redirect idp-uri saml-request relay-state))
      (do
        (errorf "Organization %s does not have valid AD-login settings or has disabled AD-login" org-id)
        (resp/redirect (format "%s/app/fi/welcome#!/login" (env/value :host)))))))


(defpage [:post "/api/saml/ad-login/:org-id"] {org-id :org-id}
  (let [idp-cert (-> org-id org/get-organization :ad-login :idp-cert parse-certificate)
        req (request/ring-request)
        xml-response (saml-shared/base64->inflate->str (get-in req [:params :SAMLResponse])) ; The raw XML string
        saml-resp (saml-sp/xml-string->saml-resp xml-response) ; An OpenSAML object
        saml-info (saml-sp/saml-resp->assertions saml-resp false) ; The response as a Clojure map
        parsed-saml-info (parse-saml-info saml-info) ; The response as a "normal" Clojure map
        _ (swap! tila assoc :saml-resp saml-resp
                 :saml-info saml-info
                 :parsed-saml-info parsed-saml-info
                 :xml-response xml-response)
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
        attrs (get-in parsed-saml-info [:assertions :attrs])
        {:keys [firstName lastName groups] :or {firstName "firstName" lastName "lastName" groups ["authority"]}} attrs
        email (:name attrs)
        _ (infof "firstName: %s, lastName: %s, groups: %s, email: %s" firstName, lastName groups email)
        ad-role-map (-> org-id (org/get-organization) :ad-login :role-mapping)
        _ (infof "AD-role map: %s" ad-role-map)
        authz (resolve-roles ad-role-map groups)
        _ (infof "Resolved authz: %s" authz)]
    (cond
      (and valid-signature? (seq authz)) (validated-login req org-id firstName lastName email authz)
      valid-signature? (do
                         (error "User does not have organization authorization")
                         (resp/redirect (format "%s/app/fi/welcome#!/login" (env/value :host))))
      :else (do
              (error "SAML validation failed")
              (resp/status 403 (resp/content-type "text/plain" "Validation of SAML response failed"))))))
