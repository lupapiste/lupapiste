(ns lupapalvelu.ident.ad-login
  (:require [lupapalvelu.http.csrf :as csrf]
            [lupapalvelu.http.noir.util :as noir-util]
            [lupapalvelu.ident.ad-login-util :as ad-util]
            [lupapalvelu.ident.xml-signer :as xml-signer]
            [lupapalvelu.logging :refer [error errorf info infof]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$set $unset]]
            [net.cgrand.enlive-html :as enlive]
            [noir.response :as resp]
            [sade.common-reader :as cr]
            [sade.core :refer [fail]]
            [sade.schemas :as ssc]
            [sade.session :as ssess]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]
            [saml20-clj.routes :as saml-routes]
            [saml20-clj.shared :as saml-shared]
            [saml20-clj.sp :as saml-sp]
            [schema.core :as sc]
            [taoensso.nippy :as nippy]))

(set! *warn-on-reflection* true)

;; OpenSAML needs to be bootstrapped when loading the namespace. `saml-sp/create-request-factory`
;; does this under the hood, but we need to do this explicitly here as well so that
;; we won't run into problems if the return route (with POST) is served by an another app than
;; the GET route. Having state here is inelegant and sucky, but this seems to be how
;; OpenSAML does things.
(org.opensaml.DefaultBootstrap/bootstrap)

(sc/defschema ADLogin
  {:id                              ssc/NonBlankStr
   :enabled                         sc/Bool
   :idp-certs                       [sc/Str]
   :idp-uri                         sc/Str
   :trusted-domains                 [ssc/NonBlankStr]
   (sc/optional-key :forced)        sc/Bool
   (sc/optional-key :idp-cert-url)  sc/Str ; Technically the URL for the Federation Metadata which contains the cert
   (sc/optional-key :organizations) [{:id           ssc/NonBlankStr
                                      :role-mapping (sc/maybe {sc/Keyword sc/Str})}]})

(defn get-certs-from-metadata-xml-string
  [xml-string]
  (some-> xml-string
          ss/blank-as-nil
          xml/parse
          cr/strip-xml-namespaces
          (enlive/select [:IDPSSODescriptor
                          [:KeyDescriptor (enlive/attr= :use "signing")]
                          :KeyInfo
                          :X509Data
                          :X509Certificate
                          first])))

(defn update-certificates-from-url!
  "Update the given ad-login document's certificates from the Federation Metadata URL"
  [{:keys [id idp-cert-url]}]
  (when-not (ss/blank? idp-cert-url)
    (try
      (when-let [certs (some->> (ss/blank-as-nil idp-cert-url)
                                slurp
                                get-certs-from-metadata-xml-string
                                (remove ss/blank?)
                                (mapv ss/trim)
                                not-empty)]
        (mongo/update-by-id :ad-logins id {$set {:idp-certs certs}}))
      (catch Exception e
        (errorf "AD login certificate fetching from %s failed: %s"
                idp-cert-url
                (.getMessage e))))))

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
  their authority status when appropriate.

  Dummy users are promoted to applicants or authorities depending on the orgAuthz.

  Admin users should not be given orgAuthz or updated in any case"
  [firstName lastName email authz ad-login-org-ids]
  (let [email (ss/canonize-email email) ;; Just in case
        user  (usr/get-user-by-email email)]
    (if (usr/admin? user)
      user
      (let [authority?   (usr/authority? user)
            applicant?   (usr/applicant? user)
            id-source?   (ss/not-blank? (:personIdSource user))
            company?     (some-> user :company :id)
            oauth-roles  (seq (concat
                                ; Only update the oauth-roles for organizations that receive roles from AD.
                                (->> user
                                     :oauth-roles
                                     (remove #(ad-login-org-ids (:organization %))))
                                (-> authz :oauth-roles)))
            ad-orgAuthz  (:orgAuthz authz)
            orgAuthz     (when-not (and company? applicant?)
                           (process-authz ad-orgAuthz (:orgAuthz user)))
            user-data    (->> {:firstName      firstName
                               :lastName       lastName
                               :enabled        true
                               :email          email
                               :username       email
                               :role           (if orgAuthz "authority" "applicant")
                               :orgAuthz       orgAuthz
                               :oauth-roles    oauth-roles
                               :personIdSource (when-not (or orgAuthz id-source? company?)
                                                 "AD")}
                              ss/trimwalk
                              util/strip-nils)
            user-updates (cond-> {$set user-data}
                           (nil? orgAuthz)    (assoc-in [$unset :orgAuthz] true)
                           (nil? oauth-roles) (assoc-in [$unset :oauth-roles] true))
            new-user     (when-not user
                           (usr/create-new-user {:role :admin}
                                                {:email email :role :dummy}))]
        (when (or (and (:enabled user) (or authority? applicant?))
                  (usr/dummy? user)
                  new-user)
          (when (:ok (usr/update-user-by-email email user-updates))
            (merge (dissoc user :orgAuthz)
                   new-user
                   user-data)))))))

(defn login-redirect []
  (resp/redirect (format "%s/login/fi"
                         (noir-util/hostname))))

(defn domain-query [domain]
  (when-let [domain (some-> domain ss/canonize-email ss/blank-as-nil)]
    {:trusted-domains domain
     :enabled         true}))

(defn domain-enabled?
  "Returns true if an enabled ad-login entity"
  [domain]
  (some->> domain domain-query (mongo/any? :ad-logins)))

(defn get-ad-login-by-domain
  "Returns ad-login if it is enabled AND the :trusted-domains array contains the provided
  domain (e.g. 'pori.fi')."
  [domain]
  (some->> domain domain-query (mongo/select-one :ad-logins)))

(defn username->domain
  "Admin users are not allowed to bypass AD login by logging in with their username and password.
  For others this has been allowed and thus this returns nil if the username doesn't contain an email address"
  [username]
  (let [{:keys [email role]} (mongo/select-one :users {:username username} [:email :role])
        email (if (not= "admin" role)
                username
                email)]
    (ad-util/email-domain email)))

(defn ad-login-uri
  "Returns the AD login URI if the email related to the `username` (or rather its domain part)
  belongs to any active AD domains. Otherwise nil."
  [username]
  (let [domain (username->domain username)]
    (when-let [ad-login (get-ad-login-by-domain domain)]
      (let [user (usr/get-user-by-email username)]
        (when (or (:forced ad-login)
                  (:ad-login user))
          (str (noir-util/hostname) "/api/saml/ad-login/" domain))))))

(defn saml-request-redirect
  "Creates SAML request (redirect) for the IdP for the `domain`. Returns map with `:error`
  key if the request cannot be sent. `relay-state` is packaged into RelayState request
  param and ultimately passed to `process-relay-state` multimethod."
  ([system domain relay-state]
   {:pre [(map? system)]}
   (let [{:keys [idp-uri]} (get-ad-login-by-domain domain)]
     (if (ss/not-blank? idp-uri)
       (let [{:keys [sp-cert private-key nippy-opts
                     app-name]} ad-util/ad-config
             saml-req-factory! (saml-sp/create-request-factory
                                 #(str "_" (random-uuid))
                                 ;; :saml-id-timeouts, not relevant here but required by the library
                                 (constantly 0)
                                 (xml-signer/make-xml-signer sp-cert private-key)
                                 idp-uri
                                 saml-routes/saml-format
                                 app-name
                                 (ad-util/make-acs-uri (components/system->host system) domain))
             saml-request      (saml-req-factory!)
             _                 (info "Send SAMLRequest for" domain "to IdP" idp-uri
                                     "with RelayState" relay-state)
             relay-state       (nippy/freeze-to-string relay-state nippy-opts)]
         (saml-sp/get-idp-redirect idp-uri saml-request relay-state))
       {:error :error.ad-login.not-enabled})))
  ([system domain]
   (saml-request-redirect system domain {:target :login})))

(defn- get-assertion-groups
  "Different AD logins use different attributes for groups, like :Group for Pori and
  :groups for Azure AD. Here we check for any possible group attr and return their values,
  so we don't have to change the code each time an AD login needs to be configured with a
  different variation of the attribute :group(s).

  In theory there might be keys that contain the word 'group' and have values we
  do not want but in practice the chance of this happening is very small and furthermore
  the risk of those values then matching the configured role-mappings is basically zero."
  [assertion]
  (some->> (keys assertion)
           (filter #(re-matches #"(?i).*groups?.*" (name %))) ; case-insensitive
           (mapcat #(util/sequentialize (get assertion %)))))

(defn- assertions->user
  "Returns user that matches to the given `assertions`. If the assertions can be
  successfully resolved to a user, the user is either fetched from mongo or created."
  [ad-login assertions]
  ;; Concatenate groups from all assertions here in case there are multiple assertions
  ;; (in practice there seems to be only one assertion in the current use cases)
  (let [ad-login-org-ids (->> ad-login
                              :organizations
                              (keep :id)
                              set)
        groups           (mapcat get-assertion-groups assertions)
        emailaddress     (some :emailaddress assertions)
        givenname        (some :givenname assertions)
        surname          (some :surname assertions)
        email            (ss/canonize-email emailaddress)
        valid-email?     (when (ad-util/valid-email email)
                           (contains? (->> (:trusted-domains ad-login)
                                           (remove ss/blank?)
                                           set)
                                      (ad-util/email-domain email)))
        _                (infof "firstName: %s, lastName: %s, groups: %s, email: %s, assertion attrs: %s"
                                givenname surname (seq groups) email (->> assertions (mapcat keys) set))
        ;; Resolve authz. Received AD-groups are mapped the corresponding Lupis roles the
        ;; organization has/organizations have.  The result is formatted like: {:609-R
        ;; #{"commenter"} :609-YMP #{"commenter" "reader"}}
        authz            (ad-util/resolve-authz ad-login groups)]
    (when valid-email?
      (resolve-user! givenname surname email authz ad-login-org-ids))))

(defn process-saml-response
  "Parses the POST request and returns a map with either `:user` or `:error` key."
  [domain request]
  (let [{:keys [SAMLResponse
                RelayState]} (some-> request :params)
        log-error (fn [& [msg]]
                    (when msg
                      (error "%s: AD login failed %s" domain msg))
                    (error domain ": Bad SAML response:" SAMLResponse))]
    (if-let [ad-login (get-ad-login-by-domain domain)]
      (try
        (let [relay-state (some-> RelayState
                                  (nippy/thaw-from-string (:nippy-opts ad-util/ad-config)))
              idp-certs   (some-> ad-login :idp-certs)
              assertions  (some-> SAMLResponse
                                  saml-shared/base64->inflate->str
                                  saml-sp/xml-string->saml-resp
                                  (ad-util/parse-assertions idp-certs))]
          (or (some->> assertions
                       (assertions->user ad-login)
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
                    (noir-util/hostname)
                    (or (:language user) "fi")
                    (cond (usr/applicant? user) "applicant"
                          (usr/admin? user)     "admin"
                          :else                 "authority"))]
    (infof "Logging in user %s as %s" (:email user) (:role user))
    (-> (ssess/merge-to-session
          req
          (resp/redirect url)
          {:user (assoc (usr/session-summary user) :ad-login? true)}
          {:rotate-session? true})
        (update :cookies merge (csrf/new-csrf-cookie)))))

(defn ad-logged-in?
  "True if the command user has logged in via AD."
  [command]
  (boolean (some-> command :user :ad-login?)))

(defn- user-has-ad-login-enabled-in-db? [query-type target]
  (mongo/any? :users {query-type target :ad-login true}))

(defn- user-has-ad-enabled-or-forced?
  [ad-conf user-ad-login-enabled-fn]
  (boolean (and ad-conf
                (or (:forced ad-conf)
                    (user-ad-login-enabled-fn)))))

(defn user-ad-organization-ids
  "If the user should use AD, returns the corresponding organization id set. The set can be
  empty, if the AD is not configured to any specific organization. Returns nil, if the
  user should not use AD. `query-type` is either `:email` or `:username` (default).
  `target` is either email or username respectively."
  ([query-type target]
   (let [target (some->> target
                         ;; Works also for regular strings.
                         ss/canonize-email
                         ss/blank-as-nil)]
     (when-let [domain (when target
                         (case query-type
                           :email (ad-util/email-domain target)
                           :username (username->domain target)))]
       (let [ad-conf   (-> domain get-ad-login-by-domain)
             ad-login? (user-has-ad-enabled-or-forced?
                         ad-conf
                         (partial user-has-ad-login-enabled-in-db? query-type target))]
         (when ad-login?
           (set (keep :id (:organizations ad-conf))))))))
  ([username]
   (user-ad-organization-ids :username username)))

(defn user-should-use-ad?
  "Should the user with this username use AD to log in?"
  [username]
  (boolean (user-ad-organization-ids username)))

(defn fetch-enabled-ad-confs-for-organization [org-id]
  (when org-id
    (mongo/select :ad-logins
                  {:enabled          true
                   :organizations.id org-id}
                  [:forced :trusted-domains])))

(defn- ad-conf-for-domain [ad-confs domain]
  (util/find-first #(-> % :trusted-domains set (contains? domain)) ad-confs))

(defn- user-ad-conf [ad-confs {:keys [email]}]
  (some->> email
           ad-util/email-domain
           (ad-conf-for-domain ad-confs)))

(defn roles-maintained-in-ad?
  "The function checks whether user's roles are maintained in AD for those
  organizations whose AD configurations are included in the first argument."
  [org-ad-confs user]
  (user-has-ad-enabled-or-forced? (user-ad-conf org-ad-confs user) #(some-> user :ad-login)))

(defn roles-maintained-in-ad-pre-checker
  "Returns an error, if command data has email and organizationId parameters,
  and user's roles in corresponding organization are maintained in AD"
  [{{:keys [organizationId email]} :data}]
  (let [email (ss/canonize-email email)]
    (when (and organizationId
               email
               (user-has-ad-enabled-or-forced?
                 (user-ad-conf (fetch-enabled-ad-confs-for-organization organizationId) {:email email})
                 (partial user-has-ad-login-enabled-in-db? :email email)))
      (fail :error.user-roles-maintained-in-ad))))

(defmulti process-relay-state (util/fn-> :relay-state :target))

(defmethod process-relay-state :default
  [{:keys [domain request user error]}]
  (if user
    (log-user-in! request user)
    (do
      (errorf "AD login for domain %s failed: %s" domain error)
      (login-redirect))))
