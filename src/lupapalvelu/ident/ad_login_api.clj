(ns lupapalvelu.ident.ad-login-api
  "AD login endpoints for the default login flow.
  OAuth AD flow endpoints are part of the OAuth API.
  Also has the commands for updating the ad-logins collection (configuration for the AD login)"
  (:require [clojure.set :as set]
            [lupapalvelu.action :as action :refer [defcommand defquery non-blank-parameters]]
            [lupapalvelu.http.noir.util :as noir-util]
            [lupapalvelu.ident.ad-login :as ad-login]
            [lupapalvelu.ident.ad-login-util :as ad-util]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.system.util-components :as components]
            [monger.operators :refer :all]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as resp]
            [ring.util.response :refer :all]
            [sade.core :refer [fail ok]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]))

;; Routes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; OAuth login page
(defpage [:get "/api/saml/metadata/:domain"] {domain :domain}
  (let [{:keys [app-name sp-cert]} ad-util/ad-config
        system                     (noir-util/get-system)]
    (resp/status 200
                 (resp/xml
                   (ad-util/metadata
                     app-name
                     (ad-util/make-acs-uri (components/system->host system) domain)
                     (ad-util/parse-certificate sp-cert)
                     true)))))

;; Send the SAML request to the IdP
(defpage [:get "/api/saml/ad-login/:domain"] {domain :domain}
  (let [system                       (noir-util/get-system)
        {:keys [error] :as redirect} (ad-login/saml-request-redirect system domain)]
    (if error
      (do
        (log/error "AD login SAML request failed:" error)
        (ad-login/login-redirect))
      redirect)))

;; Receive the SAML response from the IdP
(defpage [:post "/api/saml/ad-login/:domain"] {domain :domain}
  (let [request (request/ring-request)]
    (ad-login/process-relay-state (assoc (ad-login/process-saml-response domain request)
                                         :domain domain
                                         :request request))))

;; Actions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn role-mapping-validator [{{:keys [role-map]} :data}]
  (when role-map
    (let [org-roles (-> (set/union roles/oauth-roles-without-admin
                                   roles/authority-managed-org-authz-roles)
                        (disj :digitization-project-user))]
      (cond
        (empty? role-map)
        (fail :error.empty-role-map)

        (not-every? org-roles (keys role-map))
        (fail :error.illegal-role)))))

(defn check-ad-login-enabled
  "Pre-check for checking if any of the ad-logins are enabled for the given organization"
  [{{:keys [organizationId]} :data}]
  (when organizationId
    (when-not (mongo/any? :ad-logins {:enabled          true
                                      :organizations.id organizationId})
      (fail :error.ad-login-not-enabled))))

(defquery ad-logins
  {:user-roles #{:admin}}
  [_]
  (ok :ad-logins (into [] (mongo/select :ad-logins))))

(defquery ad-login-enabled
  {:parameters  [organizationId]
   :pre-checks  [check-ad-login-enabled]
   :permissions [{:required [:organization/admin]}]}
  [_]
  (ok))

(defquery ad-login-role-mapping
  {:parameters  [organizationId]
   :pre-checks  [check-ad-login-enabled]
   :permissions [{:required [:organization/admin]}]}
  [_]
  (let [organization        (org/get-organization organizationId)
        role-mapping        (->> (mongo/select-one :ad-logins {:organizations.id organizationId} [:organizations])
                                 :organizations
                                 (util/find-by-id organizationId)
                                 :role-mapping)
        allowed-roles       (-> organization
                                org/allowed-roles-in-organization
                                set
                                (disj :digitization-project-user))
        allowed-oauth-roles (org/allowed-oauth-roles-in-organization organization)
        all-roles           (disj roles/authority-managed-org-authz-roles :digitization-project-user)
        all-oauth-roles     roles/oauth-roles-without-admin]
    (ok :role-mapping        (dissoc role-mapping :digitization-project-user)
        :allowed-roles       allowed-roles
        :all-roles           all-roles
        :allowed-oauth-roles allowed-oauth-roles
        :all-oauth-roles     all-oauth-roles)))

(defcommand create-ad-login
  {:parameters  []
   :description "Creates an empty ad-login config for the Lupapiste admin to edit further."
   :user-roles  #{:admin}}
  [_]
  (let [dummy-ad-login {:id              (mongo/create-id)
                        :enabled         false
                        :trusted-domains []
                        :organizations   []
                        :idp-uri         ""
                        :idp-certs       [""]}]
    (mongo/insert :ad-logins dummy-ad-login)
    (ok :data dummy-ad-login)))

(defcommand remove-ad-login
  {:parameters       [ad-login-id]
   :input-validators [(partial non-blank-parameters [:ad-login-id])]
   :user-roles       #{:admin}}
   [_]
   (mongo/remove-by-id :ad-logins ad-login-id)
   (ok))

(defcommand update-ad-login-settings
  {:parameters       [ad-login-id data]
   :input-validators [{:ad-login-id sc/Str
                       :data        (st/select-keys ad-login/ADLogin
                                                    [:enabled :forced :trusted-domains
                                                     :idp-uri :idp-certs :idp-cert-url :organizations])}
                      (partial non-blank-parameters [:ad-login-id])]
   :user-roles       #{:admin}}
  [_]
  (mongo/update-by-id :ad-logins
                      ad-login-id
                      {$set (update data :idp-certs #(mapv ad-util/parse-certificate %))})
  (ok))

(defcommand update-ad-login-role-mapping
  {:description      "Updates active directory role mapping"
   :parameters       [organizationId role-map]
   :pre-checks       [role-mapping-validator
                      check-ad-login-enabled]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :role-map       {sc/Keyword sc/Str}}
                      (partial action/non-empty-map-parameters [:role-map])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (let [ad-login         (mongo/select-one :ad-logins {:organizations.id organizationId} [:organizations])
        updated-role-map (merge (->> (:organizations ad-login)
                                     (util/find-by-id organizationId)
                                     :role-mapping)
                                (ss/trimwalk role-map))
        changes          (reduce (fn [acc [k v]]
                                   (update acc (if (ss/blank-as-nil v) $set $unset)
                                           assoc
                                           (util/kw-path :organizations.$.role-mapping k)
                                           v))
                                 {}
                                 updated-role-map)]
    (mongo/update-by-query :ad-logins
                           {:_id           (:id ad-login)
                            :organizations {$elemMatch {:id organizationId}}}
                           changes)
    (ok)))
