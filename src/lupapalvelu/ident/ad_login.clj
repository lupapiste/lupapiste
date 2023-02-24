(ns lupapalvelu.ident.ad-login
  (:require [clj-uuid :as uuid]
            [lupapalvelu.ident.ad-login-util :as ad-util]
            [lupapalvelu.ident.xml-signer :as xml-signer]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$set $unset]]
            [noir.response :as resp]
            [ring.util.response :refer :all]
            [sade.env :as env]
            [sade.session :as ssess]
            [sade.strings :as ss]
            [sade.util :as util]
            [saml20-clj.routes :as saml-routes]
            [saml20-clj.shared :as saml-shared]
            [saml20-clj.sp :as saml-sp]
            [taoensso.nippy :as nippy]
            [taoensso.timbre :refer [infof info error errorf]]))

;; OpenSAML needs to be bootstrapped when loading the namespace. `saml-sp/create-request-factory`
;; does this under the hood, but we need to do this explicitly here as well so that
;; we won't run into problems if the return route (with POST) is served by an another app than
;; the GET route. Having state here is inelegant and sucky, but this seems to be how
;; OpenSAML does things.
(org.opensaml.DefaultBootstrap/bootstrap)

(defn- process-authz
  "Combine `authz` resolved from SAML message and the user orgAuthz."
  [authz user-authz]
  (-> (merge user-authz authz)
      util/strip-empty-collections
      not-empty))

(defn- resolve-user!
  "Takes the user creds received from SAML, updates the user info in the DB if necessary.
  If the user doesn't exist already, it's created. Returns the updated/created user that
  is enabled and either an applicant or an authority.

  User role (applicant/authority) reflects whether the user has orgAuthz or not. However,
  company users are never promoted to authority, but (for legacy reasons) they retain
  their authority status when appropriate."
  [firstName lastName email orgAuthz]
  (let [email        (ss/canonize-email email) ;; Just in case
        user         (usr/get-user-by-email email)
        authority?   (usr/authority? user)
        applicant?   (usr/applicant? user)
        id-source?   (ss/not-blank? (:personIdSource user))
        company?     (some-> user :company :id)
        orgAuthz     (when-not (and company? applicant?)
                       (process-authz orgAuthz (:orgAuthz user)))
        user-data    (->> {:firstName      firstName
                           :lastName       lastName
                           :enabled        true
                           :email          email
                           :username       email
                           :role           (if orgAuthz "authority" "applicant")
                           :orgAuthz       orgAuthz
                           :personIdSource (when-not (or orgAuthz id-source? company?)
                                             "AD")}
                          ss/trimwalk
                          util/strip-nils)
        user-updates (cond->  {$set user-data}
                       (nil? orgAuthz) (assoc $unset {:orgAuthz true}))
        new-user     (when-not user
                       (usr/create-new-user {:role :admin}
                                            {:email email :role :dummy}))]
    (when (or (and (:enabled user) (or authority? applicant?))
              new-user)
      (when (:ok (usr/update-user-by-email email user-updates))
        (merge (dissoc user :orgAuthz)
               new-user
               user-data)))))

(def login-redirect (resp/redirect (format "%s/login/fi"
                                           (env/value :host))))

(defn ad-login-uri
  "Returns the AD login URI if `email` (or rather its domain part) belongs to any active AD
  domains. Otherwise nil."
  [email]
  (let [domain (ad-util/email-domain email)]
    (when (seq (org/get-organizations-by-ad-domain domain))
      (str (env/value :host) "/api/saml/ad-login/" domain))))

(defn ad-login?
  "True if the email (or rather its domain part) belongs to any active AD domains."
  [email]
  (some-> (ad-util/email-domain email)
          org/get-organizations-by-ad-domain
          not-empty))

(defn saml-request-redirect
  "Creates SAML request (redirect) for the IdP for the `domain`. Returns map with `:error`
  key if the request cannot be sent. `relay-state` is packaged into RelayState request
  param and ultimately passed to `process-relay-state` multimethod."
  ([domain relay-state]
   (let [{:keys [idp-uri]} (some-> domain org/get-organizations-by-ad-domain
                                   first :ad-login)]
     (if (ss/not-blank? idp-uri)
       (let [{:keys [sp-cert private-key
                     app-name]} ad-util/ad-config
             saml-req-factory!  (saml-sp/create-request-factory
                                  #(str "_" (uuid/v1))
                                  ;; :saml-id-timeouts, not relevant here but required by the library
                                  (constantly 0)
                                  (xml-signer/make-xml-signer sp-cert private-key)
                                  idp-uri
                                  saml-routes/saml-format
                                  app-name
                                  (ad-util/make-acs-uri domain))
             saml-request       (saml-req-factory!)
             _                  (info "Send SAMLRequest for" domain "to IdP" idp-uri
                                      "with RelayState" relay-state)
             relay-state        (nippy/freeze-to-string relay-state
                                                        (:nippy-opts ad-util/ad-config))]
         (saml-sp/get-idp-redirect idp-uri saml-request relay-state))
       {:error :error.ad-login.not-enabled})))
  ([domain]
   (saml-request-redirect domain {:target :login})))

(defn- assertions->user
  "Returns user that matches to the given `assertions`. If the assertions can be
  successfully resolved to a user, the user is either fetched from mongo or created."
  [ad-settings assertions]
  (when-let [{:keys [Group groups emailaddress givenname
                     surname]} assertions]
    (let [;; Apparently Pori AD uses "Group" while Helsinki Azure AD uses "groups"
          groups       (or Group groups)
          emailaddress (ss/canonize-email emailaddress)
          valid-email? (when (ad-util/valid-email emailaddress)
                         (contains? (->> ad-settings
                                         (mapcat (comp :trusted-domains :ad-login))
                                         (remove ss/blank?)
                                         set)
                                    (ad-util/email-domain emailaddress)))
          _            (infof "firstName: %s, lastName: %s, groups: %s, email: %s"
                              givenname surname groups emailaddress)
          ;; Resolve authz. Received AD-groups are mapped the corresponding Lupis roles the
          ;; organization has/organizations have.  The result is formatted like: {:609-R
          ;; #{"commenter"} :609-YMP #{"commenter" "reader"}}
          authz        (ad-util/resolve-authz ad-settings groups)]
      (when valid-email?
        (resolve-user! givenname surname emailaddress authz)))))

(defn process-saml-response
  "Parses the POST request and returns a map with either `:user` or `:error` key."
  [domain request]
  (let [{:keys [SAMLResponse
                RelayState]} (some-> request :params)
        log-error            (fn [& [msg]]
                               (when msg
                                 (error "%s: AD login failed %s" domain msg))
                               (error domain ": Bad SAML response:" SAMLResponse))]
    (if-let [ad-settings (org/get-organizations-by-ad-domain domain)]
     (try
       (let [relay-state (some-> RelayState
                                 (nippy/thaw-from-string (:nippy-opts ad-util/ad-config)))
             idp-cert    (some-> ad-settings first :ad-login :idp-cert)
             assertions  (some-> SAMLResponse
                                 saml-shared/base64->inflate->str
                                 saml-sp/xml-string->saml-resp
                                 (ad-util/parse-assertions idp-cert))]
         (or (some->> assertions
                      (assertions->user ad-settings)
                      (hash-map :user)
                      (merge {:relay-state relay-state}))
             (do
               (log-error)
               {:error :error.ad-login.login-failed})))
       (catch Exception e
         (log-error (.getMessage e))
         {:error :error.ad-login.saml-response-parsing-failed}))
     {:error :error.ad-login.unsupported-domain})))

(defn log-user-in!
  "Logs the user in and redirects him/her to the main page."
  [req user]
  (let [url (format "%s/app/%s/%s"
                    (env/value :host)
                    (or (:language user) "fi")
                    (if (usr/applicant? user) "applicant" "authority"))]
    (infof "Logging in user %s as %s" (:email user) (:role user))
    (ssess/merge-to-session
      req
      (resp/redirect url)
      {:user (usr/session-summary user)})))

(defmulti process-relay-state (util/fn-> :relay-state :target))

(defmethod process-relay-state :default
  [{:keys [domain request user error]}]
  (if user
    (log-user-in! request user)
    (do
      (errorf "AD login for domain %s failed: %s" domain error)
      login-redirect)))
