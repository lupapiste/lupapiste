(ns lupapalvelu.organization-api
  (:require [camel-snake-kebab.core :as csk]
            [clojure.set :as set]
            [lupapalvelu.action :refer [boolean-parameters coordinate-parameters defcommand defquery defraw
                                        email-validator map-parameters-with-required-keys non-blank-parameters
                                        number-parameters parameters-matching-schema partial-localization-parameters
                                        supported-localization-parameters validate-optional-url validate-url
                                        vector-parameters] :as action]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.stamps :as stamps]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.backing-system.krysp.kuntagml-yht-version :as yht]
            [lupapalvelu.backing-system.security :as bs-sec]
            [lupapalvelu.docstore :as docstore]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.invoices.util :as invoices-util]
            [lupapalvelu.logging :as log :refer [error]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.schema-util :as pate-schema]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.publish-dates :as pd]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.sftp.client :as sftp]
            [lupapalvelu.shapefile :as shp]
            [lupapalvelu.states :as states]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.user :as usr]
            [lupapalvelu.wfs :as wfs]
            [lupapalvelu.xml.validator :as krysp-xml]
            [monger.operators :refer :all]
            [sade.core :refer [fail fail! ok unauthorized]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.municipality :as muni]
            [sade.schemas :as ssc]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :refer [fn->>] :as util]
            [sade.validators :as v]
            [schema-tools.core :as schema-tools]
            [schema.core :refer [defschema] :as sc]
            [swiss.arrows :refer :all])
  (:import [java.net URI]))

(set! *warn-on-reflection* true)

;;
;; local api
;;

(defn- municipalities-with-organization []
  (let [organizations (org/get-organizations {} [:scope :krysp])]
    {:all          (->> (for [{scopes :scope}              organizations
                              {municipality :municipality} scopes]
                          municipality)
                        distinct
                        (into []))
     :with-backend (->> (for [{scopes :scope :as org}                organizations
                              {municipality :municipality :as scope} scopes]
                          (when (-> org :krysp (get (-> scope :permitType keyword)) :url ss/blank? not)
                            municipality))
                        distinct
                        (remove nil?)
                        (into []))}))

(defn- operations-attachments-by-operation [organization operations]
  (->> (map #(get-in organization [:operations-attachments %] []) operations)
       (zipmap operations)))

(defn- organization-operations-with-attachments
  "Returns a map of maps where key is permit type, value is a map operation names to list of attachment types"
  [{scope :scope :as organization}]
  (let [selected-ops (->> organization :selected-operations (map keyword) set)
        permit-types (->> scope (map :permitType) distinct (map keyword))]
    (zipmap permit-types (map (fn->> (operations/operation-names-by-permit-type)
                                     (filter selected-ops)
                                     (operations-attachments-by-operation organization))
                              permit-types))))

(defn- selected-operations-with-permit-types
  "Returns a map where key is permit type, value is a list of operations for the permit type"
  [{scope :scope selected-ops :selected-operations}]
  (reduce
    #(if-not (get-in %1 [%2])
       (let [selected-operations (set (map keyword selected-ops))
             operation-names     (keys (filter
                                         (fn [[name op]]
                                           (and
                                             (= %2 (:permit-type op))
                                             (selected-operations name)))
                                         operations/operations))]
         (if operation-names (assoc %1 %2 operation-names) %1))
       %1)
    {}
    (map :permitType scope)))

(defn- automatic-emails-enabled-user-org
  "Pre-checker that fails if automatic emails are not enabled in any of the user organizations"
  [command]
  (when-not (some true? (mapv :automatic-emails-enabled (:user-organizations command)))
    (fail :error.automatic-emails-disabled)))

(defn- get-review-officer-values
  "Returns a set of the review officers' values under the given key"
  [organizationId key]
  (->> organizationId org/get-organization :reviewOfficers (map #(get % key)) set))

(defn- review-officer-exists
  "Pre-check that fails if the edited review officer doesn't exist.
  Is only checked when actually editing the officer, and not when determining auth for the command"
  [{{personId :personId organizationId :organizationId} :data}]
  (when personId
    (when-let [officer-ids (get-review-officer-values organizationId :id)]
      (when-not (contains? officer-ids personId)
        (fail :error.unknown-id)))))

(defn- unique-review-officer-code
  "Pre-check that fails if the given code is a duplicate of one already in the database"
  [{{code :code organizationId :organizationId} :data}]
  (when code
    (when-let [officer-codes (get-review-officer-values organizationId :code)]
      (when (contains? officer-codes code)
        (fail :error.duplicate-code)))))

;;
;; Pre-checks
;;

(defn organization-operation
  "Pre-check that fails if the operation (operationId) is not selected
  in the organization (organizationId)."
  [{:keys [data]}]
  (let [{:keys [organizationId operationId]} data]
    (when (and organizationId operationId)
      (when-not (some-> (org/get-organization organizationId {:selected-operations 1})
                        :selected-operations
                        (util/includes-as-kw? operationId))
        (fail :error.operation-not-found)))))

;;
;; Actions
;;

(defquery organization-by-user
  {:description "Lists organization details."
   :parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [_]
  (let [organization                         (org/get-organization organizationId)
        ops-with-attachments                 (organization-operations-with-attachments organization)
        selected-operations-with-permit-type (selected-operations-with-permit-types organization)
        allowed-roles                        (vec (org/allowed-roles-in-organization organization))
        allowed-oauth-roles                  (vec (org/allowed-oauth-roles-in-organization organization))]
    (ok :organization (-> organization
                          (assoc :operationsAttachments ops-with-attachments
                                 :selectedOperations selected-operations-with-permit-type
                                 :allowedRoles allowed-roles
                                 :allowedOAuthRoles allowed-oauth-roles)
                          (dissoc :operations-attachments :selected-operations :operations-attachments-settings)
                          (update-in [:map-layers :server] select-keys [:url :username])
                          (update-in [:suti :server] select-keys [:url :username])
                          (util/safe-update-in [:invoicing-config :credentials] select-keys [:user]))
        :operation-attachment-settings (att-type/organization->organization-attachment-settings organization :types-layout :tuple-vector))))

(defquery organization-attachment-types
  {:description "Combined list of attachment types for every organization scope."
   :parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (-<>> organizationId
        (util/find-by-id <> user-organizations)
        (att-type/organization->organization-attachment-settings <>)
        att-type/get-all-allowed-attachment-types
        (map #(select-keys % [:type-group :type-id]))
        distinct
        vec
        (ok :attachmentTypes)))

(defquery organization-name-by-user
  {:description "authorityAdmin organization name for all languages."
   :parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (ok (select-keys (util/find-by-id organizationId user-organizations) [:id :name])))

(defquery usage-purposes
  {:description "Lupapiste usage purposes e.g. [{:type \"authority\"}, {:type \"authority-admin\", :orgId \"753-R\"}]
                for user based on orgAuthz. Used by frontend role selector."
   :user-roles  roles/all-user-roles}
  [{:keys [user]}]
  (if (:role user)                                          ; prevent NullPointerException in applicationpage-for
    (let [applicationpage (usr/applicationpage-for user)]
      (ok :usagePurposes (into (if (= applicationpage "authority-admin")
                                 []
                                 [{:type applicationpage}])
                               (for [[org-id authz] (:orgAuthz user)
                                     auth authz
                                     :when (= auth :authorityAdmin)]
                                 {:type "authority-admin", :orgId (name org-id)}))))
    (ok :usagePurposes [])))

(defquery user-organizations-for-permit-type
  {:parameters       [permitType]
   :user-roles       #{:authority}
   :input-validators [permit/permit-type-validator]}
  [{user :user}]
  (ok :organizations (org/get-organizations {:_id   {$in (usr/organization-ids-by-roles user #{:authority})}
                                             :scope {$elemMatch {:permitType permitType}}})))

(defquery get-organization-suomifi-attachments
  {:description      "Query which attachments are selected to be delivered along Suomi.fi-messages"
   :parameters       [organizationId]
   :input-validators [(partial action/non-blank-parameters [:organizationId])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (let [{:keys [neighbors verdict]} (:suomifi-messages (mongo/by-id :organizations organizationId {:suomifi-messages 1}))]
    (ok :neighbors (:attachments neighbors) :verdict (:attachments verdict))))

(defquery user-organizations-for-archiving-project
  {:user-roles #{:authority}}
  [{user :user}]
  (ok :organizations
      (->> (org/get-organizations {:digitizer-tools-enabled   true
                                   :deactivated               {$ne true}
                                   :permanent-archive-enabled true
                                   :_id                       {$in (usr/organization-ids-by-roles user #{:archivist :digitizer})}}
                                  [:name :scope])
           (into []))))

(defn- check-bulletins-enabled-for-auth-admin
  [{user-orgs :user-organizations {:keys [permitType municipality organizationId]} :data}]
  (when (or permitType municipality organizationId)
    (let [organization (cond
                         organizationId
                         (util/find-by-id organizationId user-orgs)

                         (and permitType municipality)
                         (util/find-first (fn [{:keys [scope]}]
                                            (util/find-by-keys {:municipality municipality
                                                                :permitType   permitType}
                                                               scope))
                                          user-orgs))]
      ;; The timestamp is nil so the authority admin can configure bulletins before they are live
      (when-not (org/bulletins-enabled? organization permitType municipality nil)
        (fail :error.bulletins-not-enabled-for-scope)))))

(defquery user-organization-bulletin-settings
  {:parameters  [organizationId]
   :permissions [{:required [:organization/admin]}]
   :pre-checks  [check-bulletins-enabled-for-auth-admin]}
  [{user-orgs :user-organizations}]
  (let [user-org (util/find-by-id organizationId user-orgs)
        scopes   (->> user-org :scope
                      (filter (comp :enabled :bulletins))
                      (mapv #(select-keys % [:permitType :municipality :bulletins])))]
    (ok :bulletin-scopes scopes
        :local-bulletins-page-texts (or (some-> user-org :local-bulletins-page-settings :texts)
                                        org/default-bulletin-texts)
        :local-kuulutus-page-texts (or (some-> user-org :local-kuulutus-page-settings :texts)
                                       org/default-bulletin-texts))))

(defn- bulletin-scope-settings-validator
  [{{:keys [notificationEmail descriptionsFromBackendSystem appeal]} :data}]
  (cond
    (some-> notificationEmail ss/blank-as-nil
            ss/canonize-email v/valid-email? not)
    (fail :error.email)

    (some-> descriptionsFromBackendSystem boolean? not)
    (fail :error.invalid-value)

    (some-> appeal boolean? not)
    (fail :error.invalid-value)))

(defcommand update-organization-bulletin-scope
  {:parameters          [organizationId permitType municipality]
   :optional-parameters [notificationEmail descriptionsFromBackendSystem appeal]
   :input-validators    [permit/permit-type-validator
                         org/organization-exists
                         bulletin-scope-settings-validator]
   :permissions         [{:required [:organization/admin]}]
   :pre-checks          [check-bulletins-enabled-for-auth-admin]}
  [{:keys [data]}]
  (when-let [updates (some-> {:scope.$.bulletins.appeal                           appeal
                              :scope.$.bulletins.notification-email               (ss/canonize-email notificationEmail)
                              :scope.$.bulletins.descriptions-from-backend-system descriptionsFromBackendSystem}
                             util/strip-nils
                             ss/trimwalk
                             not-empty)]
    (mongo/update-by-query :organizations
                           {:_id   organizationId
                            :scope {$elemMatch {:permitType   permitType
                                                :municipality municipality}}}
                           {$set updates}))
  (ok))

(defcommand remove-organization-local-bulletins-caption
  {:parameters          [organizationId lang index]
   :optional-parameters [isKuulutus]
   :input-validators    [(partial action/supported-lang :lang)
                         (partial action/positive-integer-parameters [:index])]
   :permissions         [{:required [:organization/admin]}]
   :pre-checks          [check-bulletins-enabled-for-auth-admin]}
  [{user-orgs :user-organizations}]
  (if (integer? index)
    (let [settings-key                  (if isKuulutus
                                          :local-kuulutus-page-settings
                                          :local-bulletins-page-settings)
          {{texts :texts} settings-key} (util/find-by-id organizationId user-orgs)
          caption                       (get-in texts [(keyword lang) :caption])
          caption                       (util/drop-nth index caption)
          updates                       {$set {(util/kw-path [settings-key :texts (keyword lang) :caption]) caption}}]
      (org/update-organization organizationId updates)
      (ok :removed true))
    (ok :removed false)))

(def local-bulletins-page-texts-checker (sc/checker org/LocalBulletinsPageTexts))

(defcommand upsert-organization-local-bulletins-text
  {:parameters          [organizationId lang key value]
   :optional-parameters [index isKuulutus]
   :input-validators    [(partial action/supported-lang :lang)
                         (partial non-blank-parameters [:key])]
   :permissions         [{:required [:organization/admin]}]
   :pre-checks          [check-bulletins-enabled-for-auth-admin]}
  [{user-orgs :user-organizations}]
  (let [settings-key                  (if isKuulutus
                                        :local-kuulutus-page-settings
                                        :local-bulletins-page-settings)
        {{texts :texts} settings-key} (util/find-by-id organizationId user-orgs)
        path                          (->> [(keyword lang) (keyword key) (when (integer? index) index)]
                                           (remove nil?))
        texts                         (-> (util/deep-merge org/default-bulletin-texts texts)
                                          (assoc-in path value))
        valid?                        (-> texts
                                          local-bulletins-page-texts-checker
                                          nil?)
        updates                       {$set {(util/kw-path settings-key :texts) texts}}]
    (when valid?
      (org/update-organization organizationId updates))
    (ok :valid valid?)))

(defcommand reset-bulletin-text-settings
  {:parameters          [organizationId]
   :optional-parameters [isKuulutus]
   :input-validators    [(partial non-blank-parameters [:organizationId])]
   :user-roles          #{:admin}}
  [command]
  (let [settings-key (if isKuulutus
                       :local-kuulutus-page-settings
                       :local-bulletins-page-settings)
        org          (org/get-organization organizationId [settings-key])
        skeleton     {"texts" org/default-bulletin-texts}]
    (if-not (settings-key org)
      (org/update-organization organizationId {$set {settings-key skeleton}})
      (fail :error.page-settings-already-exists))))

(defschema OrgUpdateParams
  {:permitType                               (apply sc/enum (keys (permit/permit-types)))
   :municipality                             sc/Str
   (sc/optional-key :inforequestEnabled)     sc/Bool
   (sc/optional-key :applicationEnabled)     sc/Bool
   (sc/optional-key :openInforequestEnabled) sc/Bool
   (sc/optional-key :openInforequestEmail)   (sc/maybe sc/Str)
   (sc/optional-key :opening)                (sc/maybe ssc/Timestamp)
   (sc/optional-key :pateEnabled)            sc/Bool
   (sc/optional-key :pateSftp)               sc/Bool
   (sc/optional-key :invoicingEnabled)       sc/Bool
   (sc/optional-key :bulletinsEnabled)       sc/Bool
   (sc/optional-key :bulletinsEnabledAt)     (sc/maybe ssc/Timestamp)
   (sc/optional-key :bulletinsRedirectNames) [{:lang (sc/enum "sv" "fi") :name sc/Str}]
   (sc/optional-key :bulletinsEmail)         sc/Str
   (sc/optional-key :bulletinsDescriptions)  sc/Bool})

(defcommand update-organization
  {:description      "Update organization details."
   :input-validators [OrgUpdateParams
                      permit/permit-type-validator
                      (fn [{{:keys [permitType pateEnabled]} :data}]
                        (when (and (true? pateEnabled)
                                   (not (pate-schema/pate-scope? permitType)))
                          (fail :error.pate-not-supported-for-scope)))
                      (fn [{{:keys [bulletinsRedirectNames]} :data}]
                        (when-not (every? #(->> % :name (re-matches #"[a-z]*")) bulletinsRedirectNames)
                          (fail :error.invalid-bulletin-redirect-name)))]
   :user-roles       #{:admin}}
  [{{:keys [permitType municipality] :as data} :data}]
  (let [removed-on-nil? #{:bulletins.enabled-at}
        remove-prop?    (fn [prop value]
                          (and (nil? value)
                               (removed-on-nil? prop)))
        param->prop      {:inforequestEnabled     :inforequest-enabled
                          :applicationEnabled     :new-application-enabled
                          :openInforequestEnabled :open-inforequest
                          :openInforequestEmail   :open-inforequest-email
                          :opening                :opening
                          :pateEnabled            :pate.enabled
                          :pateSftp               :pate.sftp
                          :invoicingEnabled       :invoicing-enabled
                          :bulletinsEnabled       :bulletins.enabled
                          :bulletinsEnabledAt     :bulletins.enabled-at
                          :bulletinsRedirectNames :bulletins.redirect-names
                          :bulletinsEmail         :bulletins.notification-email
                          :bulletinsDescriptions  :bulletins.descriptions-from-backend-system}]
    (some->> (keys param->prop)
             (filter #(contains? data %))
             seq
             (reduce (fn [acc param]
                       (let [value (param data)
                             prop  (param->prop param)]
                         (assoc-in acc
                                   [(if (remove-prop? prop value) $unset $set)
                                    (util/kw-path :scope.$ prop)]
                                   value)))
                     {})
             (mongo/update-by-query :organizations {:scope {$elemMatch (util/mmap permitType municipality)}})))
  (ok))

(defn- duplicate-scope-validator [municipality & permit-types]
  (when-let [duplicate-scopes (org/get-duplicate-scopes municipality (vec permit-types))]
    (fail :error.organization.duplicate-scope :organization duplicate-scopes)))

(defcommand add-scope
  {:description      "Admin can add new scopes for organization"
   :parameters       [organization permitType municipality
                      inforequestEnabled applicationEnabled openInforequestEnabled openInforequestEmail
                      opening]
   :pre-checks       [(fn [{{:keys [municipality permitType]} :data}]
                        (duplicate-scope-validator municipality permitType))]
   :input-validators [permit/permit-type-validator
                      (fn [{{:keys [municipality]} :data}]
                        (when-not (contains? muni/municipality-codes municipality)
                          (fail :error.invalid-municipality)))]
   :user-roles       #{:admin}}
  [_]
  (org/update-organization
    organization
    {$push {:scope
            (org/new-scope municipality
                           permitType
                           :inforequest-enabled inforequestEnabled
                           :new-application-enabled applicationEnabled
                           :open-inforequest openInforequestEnabled
                           :open-inforequest-email openInforequestEmail
                           :opening opening)}})
  (ok))

(defn- permit-types-validator [{{:keys [permit-types]} :data}]
  (when (some (comp not permit/valid-permit-type?) permit-types)
    (fail :error.invalid-permit-type)))

(defn- org-id-not-exist [{{org-id :org-id} :data}]
  (when (and org-id (pos? (mongo/count :organizations {:_id org-id})))
    (fail :error.organization-already-exists)))

(defcommand create-organization
  {:parameters       [org-id municipality name permit-types]
   :pre-checks       [org-id-not-exist
                      (fn [{{:keys [municipality permit-types]} :data}]
                        (apply duplicate-scope-validator municipality permit-types))]
   :input-validators [(partial action/vector-parameters [:permit-types])
                      permit-types-validator
                      (partial action/non-blank-parameters [:org-id :municipality :name])
                      (partial action/numeric-parameters [:municipality])]
   :user-roles       #{:admin}}
  [_]
  (let [org-model {:id            org-id
                   :name          {:fi name :sv name :en name}
                   :scope         (map (partial org/new-scope municipality) permit-types)
                   :handler-roles [(org/create-handler-role)]
                   :stamps        [(assoc stamps/default-stamp-data :id (mongo/create-id))]
                   :docstore-info org/default-docstore-info}]
    (org/valid-organization! org-model)
    (mongo/insert :organizations (set/rename-keys org-model {:id :_id}))
    (ok)))

(defn- validate-map-with-optional-url-values [param command]
  (let [urls (map ss/trim (vals (get-in command [:data param])))]
    (some #(when-not (ss/blank? %)
             (validate-url %))
          urls)))

(defcommand add-organization-link
  {:description      "Adds link to organization."
   :parameters       [organizationId url name]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial map-parameters-with-required-keys
                               [:url :name] i18n/supported-langs)
                      (partial validate-map-with-optional-url-values :url)]}
  [{:keys [created]}]
  (org/add-organization-link organizationId name (util/map-values ss/trim url) created)
  (ok))

(defcommand update-organization-link
  {:description      "Updates organization link."
   :parameters       [organizationId url name index]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial map-parameters-with-required-keys
                               [:url :name] i18n/supported-langs)
                      (partial validate-map-with-optional-url-values :url)
                      (partial number-parameters [:index])]}
  [{:keys [created]}]
  (org/update-organization-link organizationId index name (util/map-values ss/trim url) created)
  (ok))

(defcommand remove-organization-link
  {:description      "Removes organization link."
   :parameters       [organizationId url name]
   :input-validators [(partial map-parameters-with-required-keys
                               [:url :name] i18n/supported-langs)
                      (partial validate-map-with-optional-url-values :url)]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (org/remove-organization-link organizationId name url)
  (ok))

(defquery organizations
  {:user-roles #{:admin}}
  [_]
  (ok :organizations (into [] (org/get-organizations {} org/admin-projection))))

(defquery asiointi-api-organizations
  {:description "Find organizations which support AsiointiAPI"
   :feature     :asiointi-api
   :user-roles  #{:admin}}
  [_]
  (->> (org/get-organizations
         {:scope.permitType {$in permit/asiointi-api-permit-types}
          :deactivated      {$ne true}}
         [:id :name])
       (map (juxt :id :name))
       (into {})
       (ok :organizations)))

(defquery allowed-autologin-ips-for-organization
  {:parameters       [org-id]
   :input-validators [(partial non-blank-parameters [:org-id])]
   :user-roles       #{:admin}}
  [_]
  (ok :ips (org/get-autologin-ips-for-organization org-id)))

(defcommand update-allowed-autologin-ips
  {:parameters       [org-id ips]
   :input-validators [(partial non-blank-parameters [:org-id])
                      (comp org/valid-ip-addresses :ips :data)]
   :user-roles       #{:admin}}
  [_]
  (->> (org/autologin-ip-mongo-changes ips)
       (org/update-organization org-id))
  (ok))

(defquery organization-by-id
  {:parameters       [organizationId]
   :input-validators [(partial non-blank-parameters [:organizationId])]
   :user-roles       #{:admin}}
  [_]
  (ok :data (org/get-organization organizationId org/admin-projection)))

(defquery permit-types
  {:user-roles #{:admin}}
  [_]
  (ok :permitTypes (->> (permit/permit-types)
                        keys
                        ;; remove pseudo permit types
                        (remove #(= % "TJ-V3"))
                        (into []))))

(defquery municipalities-with-organization
  {:description "Returns a list of municipality IDs that are affiliated with Lupapiste."
   :user-roles  #{:applicant :authority :admin}}
  [_]
  (let [munis (municipalities-with-organization)]
    (ok
      :municipalities (:all munis)
      :municipalitiesWithBackendInUse (:with-backend munis))))

(defquery get-organizations-review-officers
  {:description      "Returns the list of review officers for the given organization."
   :optional-parameters [organizationId]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (if (nil? organizationId)
    (ok :data [])
    (org/fetch-organization-review-officers organizationId)))

(defquery municipalities
  {:description "Returns a list of all municipality IDs. For admin use."
   :user-roles  #{:admin}}
  (ok :municipalities muni/municipality-codes))

(defquery all-operations-for-organization
  {:description      "Returns operations that match the permit types of the organization whose id is given as parameter"
   :parameters       [organizationId]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial non-blank-parameters [:organizationId])]}
  [{:keys [user-organizations]}]
  (ok :operations (operations/organization-operations (util/find-by-id organizationId user-organizations))))

(defquery selected-operations-for-municipality
  {:description      "Returns selected operations of all the organizations who have a scope with the given municipality.
                      If a \"permitType\" parameter is given, returns selected operations for only that organization
                      (the municipality + permitType combination).
                      Returns also operation infos for the selected operations. Currently (2022-09) the info contains only permit-type."
   :parameters       [:municipality]
   :user-roles       #{:applicant :authority}
   :input-validators [(partial non-blank-parameters [:municipality])]}
  [{{:keys [municipality permitType]} :data}]
  (when-let [organizations (org/resolve-organizations municipality permitType)]
    (let [filter-fn (partial operations/published-operation? (date/now))]
      (ok :operations (operations/selected-operations-for-organizations organizations filter-fn)
          :operation-infos (operations/get-selected-operation-infos organizations filter-fn)))))

(defquery addable-operations
  {:description "Returns operations addable for the application whose id is given as parameter.
                 Note that the application's submitted date determines which operations are available to add."
   :parameters  [:id]
   :permissions [{:context  {:application {:state states/pre-sent-application-states}}
                  :required [:application/read]}
                 {:context  {:application {:facta-imported true?}}
                  :required [:application/read]}]}
  [{{:keys [organization permitType submitted pre-ryhti-2025-link-permit]} :application}]
  (when-let [org (org/get-organization organization)]
    (let [publish-timestamp   (if pre-ryhti-2025-link-permit
                                (date/timestamp pd/ryhti-2025-publish-date)
                                (or submitted (date/now)))
          filter-fn           (partial operations/published-operation? publish-timestamp)
          selected-operations (->> (:selected-operations org)
                                   (filter filter-fn)
                                   (map keyword)
                                   set)]
      (ok :operations (operations/addable-operations selected-operations permitType)))))

(defquery organization-details
  {:description      "Resolves organization based on municipality and selected operation."
   :parameters       [municipality operation]
   :input-validators [(partial non-blank-parameters [:municipality :operation])]
   :user-roles       #{:applicant :authority}}
  [_]
  (let [permit-type (:permit-type ((keyword operation) operations/operations))]
    (if-let [organization (org/resolve-organization municipality permit-type)]
      (let [scope (org/resolve-organization-scope municipality permit-type organization)]
        (ok
          :inforequests-disabled (not (:inforequest-enabled scope))
          :new-applications-disabled (not (:new-application-enabled scope))
          :links (:links organization)
          :attachmentsForOp (-> organization :operations-attachments ((keyword operation)))))
      (fail :municipalityNotSupported))))

(defcommand set-organization-selected-operations
  {:parameters       [organizationId operations]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial vector-parameters [:operations])
                      (fn [{{:keys [operations]} :data}]
                        (when-not (every? (->> operations/operations keys (map name) set) operations)
                          (fail :error.unknown-operation)))]}
  [_]
  (org/update-organization organizationId {$set {:selected-operations operations}})
  (ok))

; TODO: rename command
; This command updates default attachament. Because now (2022-07-01) it's also possible to update also allowed
; attachment commmand name has become ambiguous and probably you guess wrong what this command does.
(defcommand organization-operations-attachments
            {:parameters       [organizationId operation attachments]
             :permissions      [{:required [:organization/admin]}]
             :input-validators [(partial non-blank-parameters [:operation])
                                (partial vector-parameters [:attachments])]
             :pre-checks       [(fn [{{:keys [organizationId operation attachments]} :data}]
                                  (when (and organizationId operation)
                                    (let [organization (org/get-organization organizationId)
                                          att-settings (att-type/organization->organization-attachment-settings organization)
                                          selected-operations (set (:selected-operations organization))
                                          allowed-types (att-type/get-organizations-attachment-types-for-operation
                                                          att-settings operation :operation-baseline-only? true)
                                          attachment-types (map (fn [[group id]] {:type-group group :type-id id}) attachments)]
                                      (cond
                                        (not (selected-operations operation))
                                        (do (error "Unknown operation: " (log/sanitize 100 operation))
                                            (fail :error.unknown-operation))

                                        (not-every? (partial att-type/contains? allowed-types) attachment-types)
                                        (fail :error.unknown-attachment-type)))))]}
  [_]
  (org/update-organization organizationId {$set {(str "operations-attachments." operation) attachments}})
  (ok))

(defcommand set-organization-operations-allowed-attachments
            {:parameters       [organizationId operation attachments mode]
             :permissions      [{:required [:organization/admin]}]
             :input-validators [(partial non-blank-parameters [:operation])
                                (partial non-blank-parameters [:mode])
                                (partial vector-parameters [:attachments])]
             :pre-checks       [(fn [{user-organizations                                  :user-organizations
                                      {:keys [organizationId operation attachments mode]} :data}]
                                  (when (and organizationId operation mode)
                                    (let [organization (util/find-by-id organizationId user-organizations)
                                          att-settings (att-type/organization->organization-attachment-settings organization)
                                          operation-permit-type (operations/get-operation-metadata operation
                                                                                                   :permit-type)
                                          allowed-types (att-type/get-organizations-attachment-types-for-operation
                                                          att-settings operation :operation-baseline-only? true)
                                          attachment-types (map (fn [[group id]] {:type-group group :type-id id}) attachments)]
                                      (cond
                                        (not (util/find-by-key :permitType operation-permit-type
                                                               (:scope organization)))
                                        (do (error "Unknown operation: " (log/sanitize 100 operation))
                                            (fail :error.unknown-operation))

                                        (nil? (#{:inherit :set} (keyword mode)))
                                        (fail :error.unknown-attachment-mode)

                                        (and (= :inherit (keyword mode)) (seq attachments))
                                        (fail :error.attachment-mode-was-inherit-but-attachments-were-found)

                                        (not-every? (partial att-type/contains? allowed-types) attachment-types)
                                        (fail :error.unknown-attachment-type)))))]
             }
            [_]
            (let [settings {:mode mode
                            :types (att-type/attachment-tuple-vector->map attachments)}
                  mongo-update-path (str "operations-attachment-settings.operation-nodes." operation ".allowed-attachments")]
              (org/update-organization organizationId {$set {mongo-update-path settings}}))
            (ok))

(defn toggle-organization-flag
  "Flag should be a mongo-compatible keyword and can include a multi-step path, e.g. :docstore-info.docStoreInUse"
  [command flag-kw]
  (let [{:keys [organizationId enabled]} (:data command)
        flag-path                        (util/split-kw-path flag-kw)]
    (when (sc/check (schema-tools/get-in org/Organization flag-path) enabled)
      (fail! :error.illegal-key))
    (org/update-organization organizationId {$set {flag-kw enabled}})
    (ok)))

(defcommand set-organization-app-required-fields-filling-obligatory
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :app-required-fields-filling-obligatory))

(defcommand set-organization-plan-info-disabled
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :plan-info-disabled))

(defcommand set-automatic-ok-for-attachments
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :automatic-ok-for-attachments-enabled))

(defn- automatic-construction-started-supported
  "An option for automatic state transfer to `constructionStarted` is supported for an organization if
  contains Pate-enabled scope for a permit type with a suitable statemachine."
  [{:keys [user-organizations data]}]
  (when-not (->> user-organizations
                 (util/find-by-id (:organizationId data))
                 :scope
                 (some (fn [{:keys [pate permitType]}]
                         (and (util/not=as-kw permitType permit/YA)
                              (:enabled pate)
                              (-> (permit/get-state-graph permitType)
                                  util/keyset
                                  (contains? :constructionStarted))))))
    (fail :error.no-pate-construction-started-scope)))

(defquery show-automatic-construction-started
  {:description "Pseudo query for the construction started toggle visiblity. Separate query is needed, in
  order to make the toggle visible also during impersonation."
   :permissions [{:required [:organization/admin]}]
   :pre-checks  [automatic-construction-started-supported]}
  [_]
  (ok))

(defcommand set-automatic-construction-started
  {:description      "If Pate is enabled, will the first review done in Lupapiste automatically transfer the
  application to `constructionStarted` state."
   :parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]
   :pre-checks       [automatic-construction-started-supported]}
  [command]
  (toggle-organization-flag command :automatic-construction-started))

(defcommand set-organization-assignments
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :assignments-enabled))

(defcommand set-organization-inspection-summaries
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :inspection-summaries-enabled))

(defcommand toggle-organization-ram
  {:parameters       [organizationId disabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:disabled])]}
  [{user :user}]
  (org/update-organization organizationId {$set {:ram.disabled disabled}})
  (ok))

(defcommand toggle-organization-suomifi-messages
  {:parameters       [organizationId enabled section] ;; section = "verdict" or "neighbors"
   :permissions      [{:required [:organization/admin]}]
   :input-validators [{:organizationId sc/Str
                       :enabled        sc/Bool
                       :section        (sc/enum "verdict" "neighbors")}]}
  [_]
  (org/update-organization organizationId {$set {(util/kw-path :suomifi-messages section :enabled) enabled}})
  (ok))

(defquery get-organizations-suomifi-messages-enabled
  {:parameters        [organizationId section]
   :input-validators  [(partial action/non-blank-parameters [:organizationId])
                       (partial action/non-blank-parameters [:section])]
   :permissions       [{:required [:organization/suomifi]}]}
  [_]
  (ok :enabled (org/suomifi-messages-enabled? organizationId section)))

(defcommand update-suomifi-settings
  {:description      "Admin can set the organization's PalveluTunnus and ViranomaisTunnus here"
   :parameters       [org-id authority-id service-id]
   :input-validators [{:org-id         sc/Str
                       :authority-id   sc/Str
                       :service-id     sc/Str}]
   :user-roles       #{:admin}}
  (org/update-organization org-id {$set {:suomifi-messages.authority-id authority-id
                                         :suomifi-messages.service-id   service-id}})
  (ok))

(defcommand set-organization-ram-message
  {:parameters       [organizationId message]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial map-parameters-with-required-keys
                               [:message] i18n/supported-langs)]}
  [{user :user}]
  (org/update-organization organizationId {$set {:ram.message message}})
  (ok))

(defcommand set-organization-suomifi-message
  {:parameters       [organizationId message section] ;; section = "verdict" or "neighbors"
   :permissions      [{:required [:organization/admin]}]
   :input-validators [{:organizationId sc/Str
                       :message        sc/Str
                       :section        (sc/enum "verdict" "neighbors")}]}
  [_]
  (org/update-organization organizationId {$set {(util/kw-path :suomifi-messages section :message) message}})
  (ok))

(defcommand set-organization-suomifi-attachments
  {:parameters       [organizationId attachments section] ;; section = "verdict" or "neighbors"
   :permissions      [{:required [:organization/admin]}]
   :input-validators [{:organizationId sc/Str
                       :enabled        sc/Bool
                       :section        (sc/enum "verdict" "neighbors")}]}
  [_]
  (org/update-organization organizationId {$set {(util/kw-path :suomifi-messages section :attachments) attachments}})
  (ok))

(defcommand set-organization-extended-construction-waste-report
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :pre-checks       [(org/permit-type-validator :R)]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :extended-construction-waste-report-enabled))

(defcommand set-organization-multiple-operations-support
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :multiple-operations-supported))

(defcommand set-organization-no-comment-neighbor-attachment-enabled
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :no-comment-neighbor-attachment-enabled))

(defcommand set-organization-force-kuntagml-226-for-pre-2025
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :force-kuntagml-226-for-pre-2025))

(defcommand set-organization-handling-time
  {:parameters        [organizationId days]
   :description       "Sets the desired application handling time in days, where 0 days = disabled)"
   :permissions       [{:required [:organization/admin]}]
   :input-validators  [(partial number-parameters [:days])]}
  [_]
  (when (< days 0)
    (fail! :error.invalid-value))
  (org/update-organization organizationId (if (> days 0)
                                            {$set {:handling-time.enabled true
                                                   :handling-time.days    days}}
                                            {$set {:handling-time.enabled false}}))
  (ok))

(defcommand set-organization-remove-handlers-from-reverted-draft
  {:parameters        [organizationId enabled]
   :permissions       [{:required [:organization/admin]}]
   :input-validators  [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :remove-handlers-from-reverted-draft))

(defcommand set-organization-remove-handlers-from-converted-application
  {:parameters        [organizationId enabled]
   :permissions       [{:required [:organization/admin]}]
   :input-validators  [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :remove-handlers-from-converted-application))

(defcommand set-organization-foreman-termination-request-enabled
  {:parameters        [organizationId enabled]
   :permissions       [{:required [:organization/admin]}]
   :input-validators  [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :foreman-termination-request-enabled))

(defcommand set-organization-rectification-verdicts-enabled
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :rectification-verdicts-enabled))

(defcommand set-organization-validate-verdict-given-date
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :validate-verdict-given-date))

(defcommand set-organization-validate-verdict-bulletin-date
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :validate-verdict-bulletin-date))

(defcommand set-organization-review-fetch-enabled
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :automatic-review-fetch-enabled))

(defcommand set-only-use-inspection-from-backend
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :only-use-inspection-from-backend))

(defcommand set-organization-use-attachment-links-integration
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :use-attachment-links-integration))

(defcommand set-organization-boolean-path
  {:parameters       [path value organizationId]
   :description      "Set boolean value to given path in organization settgins. Path must be string with dot denoted path. Eg 'foo.bar.baz'."
   :user-roles       #{:admin}
   :input-validators [(partial non-blank-parameters [:organizationId :path])
                      (partial boolean-parameters [:value])]}
  [_]
  (when-let [kw-path (-> path (ss/split #"\.") (util/kw-path))]
    (org/update-organization organizationId {$set {kw-path value}}))
  (ok))

(defcommand set-organization-scope-pate-value ; FIXME only used in tests, could be removed
  {:parameters       [permitType municipality value]
   :description      "Set boolean value for pate.enabled in organization scope level."
   :user-roles       #{:admin}
   :input-validators [(partial non-blank-parameters [:permitType :municipality])
                      (partial boolean-parameters [:value])
                      (fn [{{:keys [permitType]} :data}]
                        (when-not (pate-schema/pate-scope? permitType)
                          (fail :error.pate-not-supported-for-scope)))]}
  [_]
  (mongo/update-by-query :organizations
                         {:scope {$elemMatch {:permitType permitType :municipality municipality}}}  {$set {:scope.$.pate.enabled value}})
  (ok))

(defcommand set-organization-boolean-attribute
  {:parameters       [enabled organizationId attribute]
   :user-roles       #{:admin}
   :input-validators [(partial non-blank-parameters [:organizationId :attribute])
                      (partial boolean-parameters [:enabled])]
   :pre-checks       [org/organization-exists]}
  [command]
  (toggle-organization-flag command (keyword attribute)))

(defcommand set-organization-review-officers-list-enabled
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :review-officers-list-enabled))

(defcommand set-organization-login-required-for-previews
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :docstore-info.loginRequiredForPreviews))

(defn- has-archiving-enabled
  [{{:keys [organizationId]} :data}]
  (when organizationId
    (when-not (org/some-organization-has-archive-enabled? [organizationId])
      unauthorized)))

(defn- has-earliest-allowed-archiving-date-set
  [{{:keys [organizationId]} :data}]
  (when-not (some-> organizationId
                    (org/get-organization [:earliest-allowed-archiving-date])
                    :earliest-allowed-archiving-date)
    (fail :error.earliestArchivingDate-missing)))

(defcommand set-organization-permanent-archive-start-date
  {:parameters       [organizationId date]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial number-parameters [:date])]
   :pre-checks       [has-archiving-enabled
                      has-earliest-allowed-archiving-date-set]}
  [{:keys [user-organizations]}]
  (let [{:keys [earliest-allowed-archiving-date]} (util/find-by-id organizationId user-organizations)]
    (if (>= date earliest-allowed-archiving-date)
      (do (org/update-organization organizationId {$set {:permanent-archive-in-use-since date}})
          (ok))
      (fail :error.invalid-date))))

(defcommand set-default-digitalization-location
  {:parameters       [organizationId x y]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial coordinate-parameters :x :y)]}
  [_]
  (org/update-organization organizationId {$set {:default-digitalization-location.x x
                                                 :default-digitalization-location.y y}})
  (ok))

(defcommand set-organization-earliest-allowed-archiving-date
  {:parameters       [organizationId date]
   :user-roles       #{:admin}
   :input-validators [(partial number-parameters [:date])]}
  [_]
  (when-let [{:keys [permanent-archive-in-use-since]} (org/get-organization organizationId)]
    (when-not (neg? date)
      (org/update-organization organizationId
                               {$set (cond-> {:earliest-allowed-archiving-date date}

                                             (and permanent-archive-in-use-since (< permanent-archive-in-use-since date))
                                             (assoc :permanent-archive-in-use-since date))})
      (ok))))

(defcommand set-organization-ryhti-data-2026-start-ts
  {:description      "Sets (or clears) a timestamp after which organization start collecting ryhti data"
   :parameters       [organizationId ryhti-data-2026-start-ts]
   :user-roles       #{:admin}
   :input-validators [{:organizationId           org/OrgId
                       :ryhti-data-2026-start-ts (sc/maybe ssc/Timestamp)}]}
  [command]
  (mongo/update-by-id :organizations organizationId {(if (int? ryhti-data-2026-start-ts) $set $unset)
                                                     {:ryhti-data-2026-start-ts ryhti-data-2026-start-ts}})
  (ok))

(defcommand set-organization-tiff-archiving-enabled
  {:description      "Enable (or disable) archiving of TIFF files as-is for organization"
   :parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [{:organizationId org/OrgId
                       :enabled        sc/Bool}]}
  [command]
  (toggle-organization-flag command :tiff-archiving-enabled))

(defcommand set-organization-jpeg-archiving-enabled
  {:description      "Enable (or disable) archiving of JPEG files as-is for organization"
   :parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [{:organizationId org/OrgId
                       :enabled        sc/Bool}]}
  [command]
  (toggle-organization-flag command :jpeg-archiving-enabled))

(defquery get-organization-automatic-email-templates
  {:description       "Retrieves the list of automatic email templates the organization has.
                       The templates here refer to the ones the authority admin can edit.
                       Returns an empty list if there are none."
   :parameters        [organizationId]
   :permissions       [{:required [:organization/admin]}]
   :input-validators  [(partial non-blank-parameters [:organizationId])]
   :pre-checks        [automatic-emails-enabled-user-org]}
  [_]
  (let [data (org/get-organization organizationId [:automatic-email-templates])]
    (ok :templates (if data (:automatic-email-templates data) []))))

(defcommand add-organization-automatic-email-template
  {:description       "Creates an empty new template to the given organization's automatic email template list.
                       The templates here refer to the ones the authority admin can edit.
                       Creates a new list if one does not exist on the organization already."
   :parameters        [organizationId]
   :permissions       [{:required [:organization/admin]}]
   :input-validators  [(partial non-blank-parameters [:organizationId])]
   :pre-checks        [automatic-emails-enabled-user-org]}
  [_]
  (org/update-organization organizationId
                           {$push {:automatic-email-templates {:id         (mongo/create-id)
                                                               :title      ""
                                                               :contents   ""
                                                               :operations []
                                                               :states     []
                                                               :parties    []}}})
  (ok))

(defcommand save-organization-automatic-email-template-field
  {:description       "Sets the given field on the given automatic email template.
                       The templates here refer to the ones the authority admin can edit.
                       Note that this only saves a single field."
   :parameters        [organizationId emailId field value]
   :permissions       [{:required [:organization/admin]}]
   :input-validators  [(partial non-blank-parameters [:organizationId :emailId :field])]
   :pre-checks        [automatic-emails-enabled-user-org]}
  [_]
  (org/update-organization organizationId
                           {:automatic-email-templates {$elemMatch {:id emailId}}}
                           {$set {(str "automatic-email-templates.$." field) value}})
  (ok))

(defcommand remove-organization-automatic-email-template
  {:description       "Removes the given automatic email template.
                       The templates here refer to the ones the authority admin can edit."
   :parameters        [organizationId emailId]
   :permissions       [{:required [:organization/admin]}]
   :input-validators  [(partial non-blank-parameters [:organizationId :emailId])]
   :pre-checks        [automatic-emails-enabled-user-org]}
  [_]
  (org/update-organization organizationId
                           {$pull {:automatic-email-templates {:id emailId}}}))

(defquery available-reporting-systems
  {:user-roles #{:admin}}
  (ok :reporting-systems org/reporting-systems))

(defcommand set-organization-reporting-system
  {:parameters       [org-id reporting-system]
   :user-roles       #{:admin}
   :input-validators [{:org-id           ssc/NonBlankStr
                       :reporting-system (apply sc/enum (map name (conj org/reporting-systems :not-in-use)))}]
   :pre-checks       [org/organization-exists]}
  (let [reporting-system (keyword reporting-system)
        operation        (cond
                           (org/reporting-systems reporting-system) $set
                           (= reporting-system :not-in-use) $unset)]
    (org/update-organization org-id {operation {:reporting-system reporting-system}})
    (ok)))

(defquery get-organization-application-states
  {:description         "Returns a list of valid application states and their translations."
   :parameters          [organizationId] ;In future versions use organization type to get different state graphs
   :optional-parameters [lang]
   :permissions         [{:required [:organization/admin]}]
   :input-validators    [(partial non-blank-parameters [:organizationId])]
   :pre-checks          [automatic-emails-enabled-user-org]}
  [query]
  (ok :states (->> states/full-application-state-graph keys set
                   ;; Made a set of states; next make them into autocomplete options
                   (mapv #(hash-map :value % :text (i18n/localize (if lang lang (:lang query)) %)))
                   (sort-by :text))))

(defn split-emails
  "Splits and canonizes emails string"
  [emails]
  (map ss/canonize-email (ss/split emails #"[\s,;]+")))

(def email-checker (sc/checker ssc/Email))

(def email-list-validators [(partial action/string-parameters [:emails])
                            (fn [{{emails :emails} :data}]
                              (let [splitted (split-emails emails)]
                                (when (and (not (ss/blank? emails)) (some email-checker splitted))
                                  (fail :error.email))))])

(defcommand set-organization-neighbor-order-email
  {:parameters       [emails organizationId]
   :description      "When application is submitted and the applicant wishes that the organization hears neighbours,
                      send notification to these email addresses"
   :permissions      [{:required [:organization/admin]}]
   :input-validators email-list-validators}
  [_]
  (let [addresses       (when-not (ss/blank? emails) (split-emails emails))]
    (org/update-organization organizationId {$set {:notifications.neighbor-order-emails addresses}})
    (ok)))

(defcommand set-organization-submit-notification-email
  {:parameters       [emails organizationId]
   :description      "When application is submitted, send notification to these email addresses"
   :permissions      [{:required [:organization/admin]}]
   :input-validators email-list-validators}
  [_]
  (let [addresses       (when-not (ss/blank? emails) (split-emails emails))]
    (org/update-organization organizationId {$set {:notifications.submit-notification-emails addresses}})
    (ok)))

(defcommand set-organization-inforequest-notification-email
  {:parameters       [emails organizationId]
   :description      "When inforequest is received to organization, send notification to these email addresses"
   :permissions      [{:required [:organization/admin]}]
   :input-validators email-list-validators}
  [_]
  (let [addresses       (when-not (ss/blank? emails) (split-emails emails))]
    (org/update-organization organizationId {$set {:notifications.inforequest-notification-emails addresses}})
    (ok)))

(defcommand set-organization-funding-enabled-notification-email
  {:parameters       [organizationId emails]
   :description      "When Varke funding is enabled to application, send notification to these email addresses"
   :permissions      [{:required [:organization/admin]}]
   :input-validators email-list-validators}
  [_]
  (let [addresses (when-not (ss/blank? emails) (split-emails emails))]
    (org/update-organization organizationId {$set {:notifications.funding-notification-emails addresses}})
    (ok)))

(defquery krysp-config
  {:parameters  [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (if-let [organization (util/find-by-id organizationId user-organizations)]
    (let [krysp-versions (krysp-xml/supported-krysp-versions-by-permit-type)
          permit-types   (->> (:scope organization)
                              (map (comp keyword :permitType))
                              (filter #(get krysp-versions %)))
          krysp-keys     (conj (vec permit-types) :osoitteet)
          empty-confs    (zipmap krysp-keys (repeat {}))]
      (ok :krysp (merge empty-confs (:krysp organization))
          :krysp-versions krysp-versions))
    (fail :error.unknown-organization)))

(defcommand set-krysp-endpoint
  {:parameters       [organizationId url username password permitType version]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(fn [{{permit-type :permitType} :data}]
                        (when-not (or
                                    (= "osoitteet" permit-type)
                                    (get (krysp-xml/supported-krysp-versions-by-permit-type) (keyword permit-type)))
                          (fail :error.missing-parameters :parameters [:permitType])))
                      (partial validate-optional-url :url)
                      (partial action/string-parameters [:url :username :password :permitType :version])]}
  [{data :data}]
  (let [url             (-> data :url ss/trim)
        krysp-config    (org/get-krysp-wfs {:_id organizationId} permitType)
        password        (if (ss/blank? password) (second (:credentials krysp-config)) password)]
    (if (or (ss/blank? url) (wfs/wfs-is-alive? url username password))
      (if (org/krysp-version-ok? version permitType organizationId)
        (do (org/set-krysp-endpoint organizationId url username password permitType version krysp-config)
            (ok))
        (fail :error.invalid-version-id))
      (fail :auth-admin.legacyNotResponding))))

(defcommand set-kopiolaitos-info
  {:parameters       [organizationId
                      kopiolaitosEmail kopiolaitosOrdererAddress kopiolaitosOrdererPhone kopiolaitosOrdererEmail]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(fn [{{email-str :kopiolaitosEmail} :data}]
                        (let [emails (util/separate-emails email-str)]
                          ;; action/email-validator returns nil if email was valid
                          (when (some #(email-validator :email {:data {:email %}}) emails)
                            (fail :error.set-kopiolaitos-info.invalid-email))))]}
  [_]
  (org/update-organization organizationId
                           {$set {:kopiolaitos-email           kopiolaitosEmail
                                  :kopiolaitos-orderer-address kopiolaitosOrdererAddress
                                  :kopiolaitos-orderer-phone   kopiolaitosOrdererPhone
                                  :kopiolaitos-orderer-email   kopiolaitosOrdererEmail}})
  (ok))

(defquery kopiolaitos-config
  {:parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (let [organization (util/find-by-id organizationId user-organizations)]
    (ok :kopiolaitos-email (:kopiolaitos-email organization)
        :kopiolaitos-orderer-address (:kopiolaitos-orderer-address organization)
        :kopiolaitos-orderer-phone (:kopiolaitos-orderer-phone organization)
        :kopiolaitos-orderer-email (:kopiolaitos-orderer-email organization))))

(defquery get-organization-names
  {:description "Returns an organization id -> name map. (Used by TOJ.)"
   :user-roles  #{:anonymous}}
  [_]
  (ok :names (into {} (for [{:keys [id name]} (org/get-organizations {} {:name 1})]
                        [id name]))))

(defquery organization-names-by-user
  {:description "User organization names for all languages."
   :user-roles  roles/all-user-roles}
  [{{:keys [orgAuthz]} :user}]
  (let [org-ids (map (comp name key) orgAuthz)
        orgs (if (seq org-ids)
               (org/get-organizations {:_id {$in org-ids}} {:name 1})
               [])]
    (ok :names (into {} (map (juxt :id :name)) orgs))))

(defquery vendor-backend-redirect-config
  {:parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (ok (:vendor-backend-redirect (util/find-by-id organizationId user-organizations))))

(defcommand save-vendor-backend-redirect-config
  {:parameters       [organizationId key val]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(fn [{{key :key} :data}]
                        (when-not (contains? #{:vendorBackendUrlForBackendId :vendorBackendUrlForLpId} (keyword key))
                          (fail :error.illegal-key)))
                      (partial validate-optional-url :val)]}
  [_]
  (let [key (csk/->kebab-case key)]
    (org/update-organization organizationId {$set {(str "vendor-backend-redirect." key) (ss/trim val)}}))
  (ok))

(defcommand update-organization-name
  {:description      "Updates organization name for different languages. 'name' should be a map with lang-id as key
                     and name as value."
   :parameters       [organizationId name]
   :permissions      [{:required [:organization/update-name]}]
   :pre-checks       [(fn [{{:keys [organizationId]} :data user :user}]
                        (when organizationId
                          (when-not (or (usr/admin? user)
                                        (usr/authority-admin-in? organizationId user))
                            (fail :error.unauthorized))))]
   :input-validators [(partial partial-localization-parameters [:name])
                      (fn [{{name :name} :data}]
                        (when (some ss/blank? (vals name))
                          (fail :error.empty-organization-name)))]}
  [_]
  (->> (util/map-keys (fn->> clojure.core/name (str "name.")) name)
       (hash-map $set)
       (org/update-organization organizationId))
  (ok))

(defcommand pseudo-update-organization-name
  {:description "Pseudo command for differentiating impersonation."
   :permissions [{:required [:organization/update-name]}]}
  [_]
  (ok))

(defcommand set-organization-permit-type-krysp-version
  {:description         "Stores organization's krysp version for permit type"
   :parameters          [org-id permitType]
   :optional-parameters [version]
   :user-roles          #{:admin}
   :input-validators    [{:org-id                    org/OrgId
                          :permitType                permit/PermitType
                          (sc/optional-key :version) yht/KryspVersionString}]}
  (org/update-organization org-id
                           (if version
                             {$set {(util/kw-path :krysp permitType :version) version}}
                             {$unset {(util/kw-path :krysp permitType :version) 1}}))
  (ok))

(defquery organization-krysp-settings
  {:description      "Returns krysp settings for organization"
   :parameters       [org-id]
   :user-roles       #{:admin}
   :input-validators [{:org-id org/OrgId}]}
  (let [org (org/get-organization org-id [:krysp :dmcity-kuntagml-schemas-enabled])]
    (ok :data (->> org
                   :krysp
                   (util/map-values #(select-keys % [:url :version])))
        :dmcity-kuntagml-schemas-enabled (:dmcity-kuntagml-schemas-enabled org)
        :krysp-versions (krysp-xml/supported-krysp-versions-by-permit-type))))


(def tag-checker (sc/checker org/Tag))

(defcommand save-organization-tags
  {:parameters       [organizationId tags]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :tags           [{:label ssc/NonBlankStr
                                         :id    (sc/maybe ssc/ObjectIdStr)}]}
                      (partial action/vector-parameters [:tags])]
   :permissions      [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (let [tags              (ss/trimwalk tags)
        old-tag-ids       (set (map :id (:tags (util/find-by-id organizationId user-organizations))))
        new-tag-ids       (set  (map :id tags))
        removed-ids       (set/difference old-tag-ids new-tag-ids)
        tags-with-ids     (org/create-tag-ids tags)
        validation-errors (seq (remove nil? (map tag-checker tags-with-ids)))]
    (when validation-errors (fail! :error.missing-parameters))

    (when (seq removed-ids)
      (mongo/update-by-query :applications {:tags {$in removed-ids} :organization organizationId}
                             {$pull {:tags {$in removed-ids}}}))
    (org/update-organization organizationId {$set {:tags tags-with-ids}})
    (ok)))

(defquery remove-tag-ok
  {:parameters       [organizationId tagId]
   :input-validators [(partial non-blank-parameters [:tagId])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (if-let [tag-applications (seq (mongo/select
                                     :applications
                                     {:tags tagId :organization organizationId}
                                     [:_id]))]
    (fail :warning.tags.removing-from-applications :applications tag-applications)
    (ok)))

(defquery get-organization-tags
  {:optional-parameters [:organizationId]
   :permissions [{:required [:organization/tags]}]}
  [{{:keys [orgAuthz]} :user}]
  (if (seq orgAuthz)
    (let [organization-tags (mongo/select
                              :organizations
                              {:_id {$in (keys orgAuthz)} :tags {$exists true}}
                              [:tags :name])
          result            (map (juxt :id #(select-keys % [:tags :name])) organization-tags)]
      (ok :tags (into {} result)))
    (ok :tags {})))

(defquery application-organization-tags
  {:description      "Organization tags for the application. For statement
  givers, only the organization statement givers are authorized."
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :contexts         [org/organization-statement-giver-context]
   :permissions      [{:required [:application/organization-tags]}]}
  [{organization :organization}]
  (ok :tags (:tags @organization)))

(defquery get-organization-areas
  {:user-authz-roles #{:statementGiver}
   :user-roles       #{:authority}}
  [{{:keys [orgAuthz]} :user}]
  (if (seq orgAuthz)
    (let [organization-areas (mongo/select
                               :organizations
                               {:_id {$in (keys orgAuthz)} :areas-wgs84 {$exists true}}
                               [:areas-wgs84 :name])
          organization-areas (map #(clojure.set/rename-keys % {:areas-wgs84 :areas}) organization-areas)
          result             (map (juxt :id #(select-keys % [:areas :name])) organization-areas)]
      (ok :areas (into {} result)))
    (ok :areas {})))

(defraw organization-area
  {:parameters  [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [{{[{:keys [tempfile]}] :files} :data lang :lang :as command}]
  (let [file-info (assoc (shp/command->fileinfo command)
                         :organization organizationId)]
    (shp/parse-and-respond {:file-info  file-info
                            :tempfile   tempfile
                            :lang       lang
                            :org-id     organizationId
                            :respond-fn (fn [{:keys [areas] :as parsed-areas}]
                                          (org/update-organization organizationId {$set parsed-areas})
                                          (assoc file-info :areas areas :ok true))})))

(defcommand pseudo-organization-area
  {:description "Pseudo command for differentiating authority admin
  impersonation regarding organization-area."
   :parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [_]
  (ok))


(defquery get-map-layers-data
  {:description "Organization server and layer details."
   :parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [_]
  (let [{:keys [server layers]} (org/organization-map-layers-data organizationId)]
    (ok :server (select-keys server [:url :username]), :layers layers)))

(defcommand update-map-server-details
  {:parameters       [organizationId url username password]
   :input-validators [(partial validate-optional-url :url)]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (org/update-organization-map-server organizationId (ss/trim url) username password)
  (ok))

(def layer-checker (sc/checker org/Layer))

(defcommand update-user-layers
  {:parameters       [organizationId layers]
   :input-validators [(partial action/vector-parameter-of :layers map?)]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (let [selected-layers   (remove (comp ss/blank? :id) layers)
        validation-errors (remove nil? (map layer-checker selected-layers))]
    (if (zero? (count validation-errors))
      (do
        (org/update-organization organizationId {$set {:map-layers.layers selected-layers}})
        (ok))
      (fail :error.missing-parameters))))

(defcommand update-suti-server-details
  {:parameters       [url organizationId username password]
   :input-validators [(partial validate-optional-url :url)]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (org/update-organization-suti-server organizationId  (ss/trim url) username password)
  (ok))

(defcommand section-toggle-enabled
  {:description      "Enable/disable section requirement for fetched
  verdicts support."
   :parameters       [organizationId flag]
   :input-validators [(partial action/boolean-parameters [:flag])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (org/toggle-group-enabled organizationId :section flag)
  (ok))

(defcommand section-toggle-operation
  {:description      "Toggles operation either requiring section or not."
   :parameters       [organizationId operationId flag]
   :input-validators [(partial action/non-blank-parameters [:operationId])
                      (partial action/boolean-parameters [:flag])]
   :permissions      [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (org/toggle-group-operation (util/find-by-id organizationId user-organizations)
                              :section (ss/trim operationId) flag)
  (ok))

(defn- validate-handler-role-in-organization
  "Pre-check that fails if roleId is defined but not found in handler-roles of authority admin's organization."
  [{{role-id :roleId :keys [organizationId]} :data user-orgs :user-organizations}]
  (when organizationId
    (when-let [org (util/find-by-id organizationId user-orgs)]
      (when (and role-id (not (util/find-by-id role-id (:handler-roles org))))
        (fail :error.unknown-handler)))))

(defn- validate-handler-role-not-general
  "Pre-check that fails if roleId is defined and found in handler-roles of authority admin's organization and is set as general."
  [{{role-id :roleId :keys [organizationId]} :data user-orgs :user-organizations}]
  (when organizationId
    (when-let [org (util/find-by-id organizationId user-orgs)]
      (when (and role-id (:general (util/find-by-id role-id (:handler-roles org))))
        (fail :error.illegal-handler-role)))))

(def handler-role-checker (sc/checker org/HandlerRole))

(defcommand upsert-handler-role
  {:description         "Create and modify organization handler role"
   :parameters          [name organizationId]
   :optional-parameters [roleId]
   :pre-checks          [validate-handler-role-in-organization]
   :input-validators    [(partial supported-localization-parameters [:name])]
   :permissions         [{:required [:organization/admin]}]}
  [{user-orgs :user-organizations}]
  (let [handler-role (org/create-handler-role roleId name)]
    (if (handler-role-checker handler-role)
      (fail :error.missing-parameters)
      (do (-> (util/find-by-id organizationId user-orgs)
              (org/upsert-handler-role! handler-role))
          (ok :id (:id handler-role))))))

(defcommand toggle-handler-role
  {:description      "Enable/disable organization handler role."
   :parameters       [organizationId roleId enabled]
   :pre-checks       [validate-handler-role-in-organization
                      validate-handler-role-not-general]
   :input-validators [(partial non-blank-parameters [:roleId])
                      (partial boolean-parameters [:enabled])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (org/toggle-handler-role! organizationId roleId enabled)
  (ok))

(defcommand upsert-organization-suomifi-message-attachments
  {:description      "Set and update attachment settings for Suomi.fi-messages"
   :parameters       [organizationId section attachments] ;; section = 'verdict' or 'neighbors'
   :input-validators [{:organizationId sc/Str
                       :section        (sc/enum "verdict" "neighbors")
                       :attachments    [org/AttachmentEntry]} ]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (mongo/update-by-query :organizations
                         {:_id organizationId}
                         {$set {(util/kw-path :suomifi-messages section :attachments) attachments}})

  (ok))

(defschema UpdateDocstoreInfo
  {:org-id                                    org/OrgId
   :docStoreInUse                             sc/Bool
   :docTerminalInUse                          sc/Bool
   :docDepartmentalInUse                      sc/Bool
   (sc/optional-key :pricing)                 { :price sssc/Nat
                                               :fee    sssc/Nat}
   (sc/optional-key :organizationDescription) (i18n/lenient-localization-schema sc/Str)})

(defcommand update-docstore-info
  {:description         "Updates organization's document store information"
   :parameters          [org-id docStoreInUse docTerminalInUse docDepartmentalInUse]
   :optional-parameters [pricing organizationDescription]
   :user-roles          #{:admin}
   :input-validators    [UpdateDocstoreInfo
                         (fn [{{:keys [docStoreInUse pricing]} :data}]
                           (when docStoreInUse
                             (if-let [{:keys [price fee]} pricing]
                               (when (<= price fee)
                                 (fail :error.bad-docstore-pricing))
                               (fail :error.missing-docstore-pricing))))]}
  [_]
  (mongo/update-by-id :organizations
                      org-id
                      {$set (merge {:docstore-info.docStoreInUse        docStoreInUse
                                    :docstore-info.docTerminalInUse     docTerminalInUse
                                    :docstore-info.docDepartmentalInUse docDepartmentalInUse}
                                   (when (and (or docStoreInUse
                                                  docTerminalInUse
                                                  docDepartmentalInUse)
                                              organizationDescription)
                                     {:docstore-info.organizationDescription organizationDescription})
                                   (when docStoreInUse
                                     {:docstore-info.documentPrice   (:price pricing)
                                      :docstore-info.organizationFee (:fee pricing)}))})
  (ok))

(defquery document-request-info
  {:description "Obtains the organization's document request info."
   :parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [_]
  (ok :documentRequest (docstore/document-request-info organizationId)))

(defcommand set-document-request-info
  {:description         "Updates organization's document request info. Document requests are made
  from document store."
   :parameters          [organizationId enabled instructions]
   :optional-parameters [email]
   :permissions         [{:required [:organization/admin]}]
   :input-validators    [(partial boolean-parameters [:enabled])
                         docstore/valid-instructions
                         (fn [command]
                           (when-let [email (some-> command :data :email
                                                    ss/canonize-email
                                                    ss/blank-as-nil)]
                             (when-not (v/valid-email? email)
                               (fail :error.email))))]}
  [_]
  (docstore/set-document-request-info organizationId
                                      enabled
                                      (ss/canonize-email email)
                                      instructions)
  (ok))

(def DocMode (sc/enum "terminal" "departmental"))

(defquery docterminal-attachment-types
  {:description "Returns the allowed docterminal and docdepartmental attachment types
                 in a structure that can be easily displayed in the client"
   :parameters  [docMode organizationId]
   :permissions [{:required [:organization/admin]}]
   :input-validators [(partial parameters-matching-schema [:docMode] DocMode)]}
  [_]
  (let [key (case docMode
              "departmental" :allowedDepartmentalAttachmentTypes
              :allowedTerminalAttachmentTypes)]
    (->> organizationId
         (docstore/allowed-docterminal-attachment-types key)
         (ok :attachment-types))))

(defcommand set-docterminal-attachment-type
  {:description      "Allows or disallows showing the given attachment type in
                      the archive document terminal application."
   :parameters       [organizationId attachmentType enabled]
   :pre-checks       [docstore/check-docterminal-enabled]
   :input-validators [(partial parameters-matching-schema [:attachmentType]
                               (sc/cond-pre (sc/enum "all")
                                            org/DocTerminalAttachmentType))
                      (partial boolean-parameters [:enabled])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (docstore/set-allowed-docterminal-attachment-type organizationId
                                                    attachmentType
                                                    enabled)
  (ok))

(defcommand set-docdepartmental-attachment-type
  {:description      "Allows or disallows showing the given attachment type in
                      the archive document departmental application."
   :parameters       [organizationId attachmentType enabled]
   :pre-checks       [docstore/check-docdepartmental-enabled]
   :input-validators [(partial parameters-matching-schema [:attachmentType]
                               (sc/cond-pre (sc/enum "all")
                                            org/DocTerminalAttachmentType))
                      (partial boolean-parameters [:enabled])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (docstore/set-allowed-docdepartmental-attachment-type organizationId
                                                        attachmentType
                                                        enabled)
  (ok))

(defquery docterminal-enabled
  {:pre-checks  [docstore/check-docterminal-enabled]
   :parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [_]
  (ok))

(defquery docdepartmental-enabled
  {:pre-checks  [docstore/check-docdepartmental-enabled]
   :parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [_]
  (ok))

(defquery docstore-enabled
  {:parameters [organizationId]
   :pre-checks  [docstore/check-docstore-enabled]
   :permissions [{:required [:organization/admin]}]}
  [_]
  (ok))

(defquery login-required-for-previews
  {:parameters  [organizationId]
   :pre-checks  [org/check-login-required-for-previews]
   :permissions [{:required [:organization/admin]}]}
  [_]
  (ok))

(defcommand toggle-default-attachments-mandatory-operation
  {:description      "Whether the default attachments are mandatory for an
  operation or not."
   :parameters       [organizationId operationId mandatory]
   :input-validators [(partial action/non-blank-parameters [:organizationId :operationId])
                      (partial action/boolean-parameters [:mandatory])]
   :permissions      [{:required [:organization/admin]}]
   :pre-checks       [organization-operation]}
  [{:keys [user-organizations]}]
  (let [already-mandatory? (boolean (some-> (util/find-by-id organizationId user-organizations)
                                            :default-attachments-mandatory
                                            (util/includes-as-kw? operationId)))]
    (when-not (= already-mandatory? mandatory)
      (org/update-organization organizationId
                               {(if mandatory $push $pull) {:default-attachments-mandatory operationId}}))
    (ok)))

(defcommand toggle-deactivation
  {:description      "When an organization is deactivated, everyone of its
  applications are marked readOnly. In addition, applications and
  inforequests are disabled for every scope. On activation, the
  applications are 'released' but scopes are left untouched."
   :parameters       [organizationId deactivated]
   :input-validators [(partial action/non-blank-parameters [:organizationId])
                      (partial action/boolean-parameters [:deactivated])]
   :pre-checks       [org/organization-exists]
   :user-roles       #{:admin}}
  [_]
  (if deactivated
    (org/deactivate-organization organizationId)
    (do (org/update-organization organizationId {$unset {:deactivated 1}})
        (org/toggle-applications-read-only organizationId false)))
  (ok))

(defcommand create-review-officer
  {:description         "Creates new review officer into the organization. The code
  cannot be changed once given, but the name can."
   :parameters          [organizationId name code]
   :optional-parameters [firstName lastName]
   :input-validators    [(partial action/non-blank-parameters [:name :code])]
   :pre-checks          [unique-review-officer-code]
   :permissions         [{:required [:organization/admin]}]}
  [_]
  (let [review-officer-id (mongo/create-id)
        officer-data (util/assoc-when {:id           review-officer-id
                                       :_atomic-map? true
                                       :name         (ss/trim name)
                                       :code         (ss/trim code)}
                                      :firstName (ss/trim-or-nil firstName)
                                      :lastName (ss/trim-or-nil lastName))]
    (org/update-organization
      organizationId
      {$push {:reviewOfficers officer-data}})
    (ok :id review-officer-id)))

(defcommand edit-review-officer
  {:description         "Updates a review officer's information.
  Editing the code or the id is prohibited."
   :parameters          [organizationId personId name]
   :optional-parameters [firstName lastName]
   :input-validators    [(partial action/non-blank-parameters [:personId :name])]
   :pre-checks          [review-officer-exists]
   :permissions         [{:required [:organization/admin]}]}
  [_]
  (let [updates (merge {:reviewOfficers.$.name (ss/trim name)}
                       (when firstName {:reviewOfficers.$.firstName (ss/trim firstName)})
                       (when lastName {:reviewOfficers.$.lastName (ss/trim lastName)}))]
    (org/update-organization
      organizationId
      {:reviewOfficers {$elemMatch {:id personId}}}
      {$set updates}))
  (ok :id personId))

(defcommand delete-review-officer
  {:description      "Delete a review officer from the organization."
   :parameters       [organizationId personId]
   :input-validators [(partial action/non-blank-parameters [:personId])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (org/update-organization organizationId {$pull {:reviewOfficers {:id personId}}})
  (ok))

(defquery organization-authorities
  {:description         "List of users (id, first name, last name, username, enabled) that are authorities in
  `organizationId`. The disabled users are omitted unless `includeDisabled` is true."
   :parameters          [organizationId]
   :optional-parameters [includeDisabled]
   :permissions         [{:required [:organization/authorities]}]
   :input-validators    [(partial non-blank-parameters [:organizationId])]
   :pre-checks          [(fn [{:keys [data]}]
                           (when-let [org-id (:organizationId data)]
                             (when-not (org/get-organization org-id [:id])
                               (fail :error.organization-not-found))))]}
  [_]
  (ok :authorities (vec (mongo/select :users
                                      (cond-> {(util/kw-path :orgAuthz organizationId) "authority"
                                               :role                                   "authority"
                                               :enabled                                true}
                                        includeDisabled (dissoc :enabled))
                                      [:id :firstName :lastName :enabled :username]))))

(defquery invoicing-config
  {:description "Returns organization's invoicing config"
   :parameters  [organizationId]
   :permissions [{:required [:organization/invoicing-config]}]}
  [{user :user}]
  (let [invoicing-config (cond-> (org/get-invoicing-config organizationId)
                           (not (usr/admin? user)) (dissoc :integration-url
                                                           :credentials
                                                           :local-sftp-user))]
    (ok (util/strip-nils {:invoicing-config  invoicing-config
                          :backend-id-config (:invoicing-backend-id-config
                                              (mongo/by-id :organizations organizationId
                                                           [:invoicing-backend-id-config]))}))))

(defcommand update-invoicing-config
  {:description      "Updates organization's invoicing config"
   :parameters       [org-id invoicing-config]
   :user-roles       #{:admin}
   :input-validators [{:org-id           ssc/NonBlankStr
                       :invoicing-config org/InvoicingConfig}]}
  [_]
  (let [invoicing-config              (ss/trimwalk invoicing-config)
        {:keys [local-sftp-user]}   (org/get-invoicing-config org-id)
        {:keys [username password]} (:credentials invoicing-config)
        new-creds                   (if (:ssh-authentication? invoicing-config)
                                      {:username username}
                                      (bs-sec/encode-credentials username password))
        new-config                  (-> invoicing-config
                                        (dissoc :credentials :local-sftp-user)
                                        (util/assoc-when :credentials new-creds
                                                         :local-sftp-user local-sftp-user))]
    (org/update-organization org-id {$set {:invoicing-config new-config}})
    (ok)))

(defcommand test-invoicing-sftp
  {:description      "Test SFTP connection to invoicing server"
   :user-roles       #{:admin}
   :parameters       [org-id]
   :input-validators [{:org-id org/OrgId}]
   :pre-checks       [(fn [{:keys [data]}]
                        (when-let [org-id (:org-id data)]
                          (if-let [{:keys [delivery-type]} (invoices-util/resolve-invoicing-config org-id)]
                            (when-not (contains? #{:sftp-ssh :sftp-password} delivery-type)
                              (fail :error.cannot-test-sftp))
                            (fail :error.organization-not-found))))]}
  [_]
  (let [{:keys [integration-url credentials ssh-authentication?]} (org/get-invoicing-config org-id)
        {:keys [host port]} (bean (URI. integration-url))]
    (ok (sftp/test-connection {:address host :auth (util/assoc-when credentials
                                                                    :private-key-path (when ssh-authentication?
                                                                                        (env/value :invoices :sftp :private-key-path))
                                                                    :port (when (pos? port) port))}))))

(defcommand update-review-pdf-configuration
  {:description      "Update review PDF configuration."
   :parameters       [organizationId]
   :input-validators [(assoc org/ReviewPdf
                             :organizationId sc/Str)]
   :permissions      [{:required [:organization/admin]}]}
  [{data :data}]
  (when-let [setters (some->> (dissoc data :organizationId)
                              (map (fn [[k v]]
                                     [(util/kw-path :review-pdf k) v]))
                              (into {})
                              not-empty)]
    (org/update-organization organizationId {$set setters}))
  (ok))


(defraw download-organization-attachments-export-file
  {:description      "Lets the authority admin download an exported attachments zip"
   :parameters       [organizationId fileId]
   :input-validators [(partial action/non-blank-parameters [:organizationId :fileId])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (if-let [organization (org/get-organization organizationId [:export-files])]
    (if-let [export-file (->> (:export-files organization)
                              (util/find-by-key :fileId (ss/trim fileId)))]
      (-> (storage/download-with-user-id "batchrun-user" (:fileId export-file))
          (att/output-attachment true))
      (fail! :error.file-not-found))
    (fail! :error.organization-not-found)))


(defquery user-is-pure-ymp-org-user
  {:description "Pseudo query that succeeds only when the user is authority only in YMP organizations."
   :user-roles  #{:authority}
   :pre-checks  [(fn [{:keys [user-organizations]}]
                   (when-not (org/is-pure-ymp-org-user? user-organizations) unauthorized))]}
  [_]
  (ok))
