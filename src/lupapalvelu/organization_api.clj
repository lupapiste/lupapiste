(ns lupapalvelu.organization-api
  (:require [camel-snake-kebab.core :as csk]
            [clojure.set :as set]
            [lupapalvelu.action
             :refer [defquery defcommand defraw non-blank-parameters
                     vector-parameters boolean-parameters number-parameters
                     email-validator validate-url validate-optional-url
                     map-parameters-with-required-keys partial-localization-parameters
                     localization-parameters supported-localization-parameters
                     parameters-matching-schema coordinate-parameters]
             :as action]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.stamps :as stamps]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.schema-util :as pate-schema]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.shapefile :as shp]
            [lupapalvelu.states :as states]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.user :as usr]
            [lupapalvelu.wfs :as wfs]
            [lupapalvelu.xml.validator :as krysp-xml]
            [monger.operators :refer :all]
            [noir.response :as resp]
            [sade.core :refer [ok fail fail! unauthorized]]
            [sade.municipality :as muni]
            [sade.schema-utils :as ssu]
            [sade.schemas :as ssc]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :refer [fn->>] :as util]
            [sade.validators :as v]
            [schema-tools.core :as schema-tools]
            [schema.core :refer [defschema] :as sc]
            [swiss.arrows :refer :all]
            [taoensso.timbre :refer [error]]))
;;
;; local api
;;

(defn- municipalities-with-organization []
  (let [organizations (org/get-organizations {} [:scope :krysp])]
    {:all          (distinct
                     (for [{scopes :scope} organizations
                           {municipality :municipality} scopes]
                       municipality))
     :with-backend (remove nil?
                           (distinct
                             (for [{scopes :scope :as org} organizations
                                   {municipality :municipality :as scope} scopes]
                               (when (-> org :krysp (get (-> scope :permitType keyword)) :url ss/blank? not)
                                 municipality))))}))

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

(defn decode-state-change-conf [organization]
  (if-let [headers (get-in organization [:state-change-endpoint :header-parameters])]
    (assoc-in
      organization
      [:state-change-endpoint :header-parameters]
      (map
        (fn [header]
          (assoc
            header
            :value
            (org/decode-credentials (:value header) (get-in organization [:state-change-endpoint :crypto-iv-s]))))
        headers))
    organization))

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
        allowed-roles                        (org/allowed-roles-in-organization organization)]
    (ok :organization (-> organization
                          (assoc :operationsAttachments ops-with-attachments
                                 :selectedOperations selected-operations-with-permit-type
                                 :allowedRoles allowed-roles)
                          (dissoc :operations-attachments :selected-operations :operations-attachments-settings)
                          (update-in [:map-layers :server] select-keys [:url :username])
                          (update-in [:suti :server] select-keys [:url :username])
                          (util/safe-update-in [:invoicing-config :credentials] select-keys [:user])
                          (decode-state-change-conf))
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
  (ok :organizations (org/get-organizations {:_id {$in (usr/organization-ids-by-roles user #{:archivist :digitizer})}})))

(defn- check-bulletins-enabled
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
      (when-not (org/bulletins-enabled? organization permitType municipality)
        (fail :error.bulletins-not-enabled-for-scope)))))

(defquery user-organization-bulletin-settings
  {:parameters [organizationId]
   :permissions [{:required [:organization/admin]}]
   :pre-checks  [check-bulletins-enabled]}
  [{user-orgs :user-organizations}]
  (let [user-org (util/find-by-id organizationId user-orgs)
        scopes   (->> user-org :scope
                      (filter (comp :enabled :bulletins))
                      (map #(select-keys % [:permitType :municipality :bulletins])))
        texts    (->> user-org :local-bulletins-page-settings :texts)]
    (ok :bulletin-scopes scopes
        :local-bulletins-page-texts texts)))

(defn- bulletin-scope-settings-validator
  [{{:keys [notificationEmail descriptionsFromBackendSystem]} :data}]
  (when (and (seq notificationEmail) (not (v/valid-email? notificationEmail)))
    (fail! :error.email))
  (when (and descriptionsFromBackendSystem (not (boolean? descriptionsFromBackendSystem)))
    (fail! :error.invalid-value)))

(defcommand update-organization-bulletin-scope
  {:parameters          [organizationId permitType municipality]
   :optional-parameters [notificationEmail descriptionsFromBackendSystem]
   :input-validators    [permit/permit-type-validator
                         bulletin-scope-settings-validator]
   :permissions         [{:required [:organization/admin]}]
   :pre-checks          [check-bulletins-enabled]}
  [{:keys [data]}]
  (let [updates (merge (when (util/not-empty-or-nil? notificationEmail)
                         {:scope.$.bulletins.notification-email notificationEmail})
                       (when (contains? data :descriptionsFromBackendSystem)
                         {:scope.$.bulletins.descriptions-from-backend-system descriptionsFromBackendSystem}))]
    (when updates
      (mongo/update-by-query :organizations
        {:scope {$elemMatch {:permitType permitType :municipality municipality}}} {$set updates}))
    (ok)))

(defcommand remove-organization-local-bulletins-caption
  {:parameters       [organizationId lang index]
   :input-validators [(partial action/supported-lang :lang)
                      (partial action/positive-integer-parameters [:index])]
   :permissions      [{:required [:organization/admin]}]
   :pre-checks       [check-bulletins-enabled]}
  [{user-orgs :user-organizations}]
  (if (integer? index)
    (let [{{texts :texts} :local-bulletins-page-settings} (util/find-by-id organizationId user-orgs)
          caption (get-in texts [(keyword lang) :caption])
          caption (util/drop-nth index caption)
          updates {$set {(util/kw-path [:local-bulletins-page-settings.texts (keyword lang) :caption]) caption}}]
      (org/update-organization organizationId updates)
      (ok :removed true))
    (ok :removed false)))

(defcommand upsert-organization-local-bulletins-text
  {:parameters          [organizationId lang key value]
   :optional-parameters [index]
   :input-validators    [(partial action/supported-lang :lang)
                         (partial non-blank-parameters [:key])]
   :permissions         [{:required [:organization/admin]}]
   :pre-checks          [check-bulletins-enabled]}
  [{user-orgs :user-organizations}]
  (let [{{texts :texts} :local-bulletins-page-settings} (util/find-by-id organizationId user-orgs)
        path    (remove nil?
                        [(keyword lang) (keyword key) (when (integer? index) index)])
        valid?  (nil? (sc/check org/LocalBulletinsPageTexts (assoc-in texts path value)))
        updates {$set {(util/kw-path (cons :local-bulletins-page-settings.texts path)) value}}]
    (when valid?
      (org/update-organization organizationId updates))
    (ok :valid valid?)))

(defcommand reset-bulletin-text-settings
  {:parameters       [organizationId]
   :input-validators [(partial non-blank-parameters [:organizationId])]
   :user-roles       #{:admin}}
  [command]
  (let [org (org/get-organization organizationId [:local-bulletins-page-settings])
        skeleton {"texts"
                  {"fi" {"heading1" "",
                         "heading2" "",
                         "caption" [""]},
                   "sv" {"heading1" "",
                         "heading2" "",
                         "caption" [""]},
                   "en" {"heading1" "",
                         "heading2" "",
                         "caption" [""]}}}]
    (if-not (:local-bulletins-page-settings org)
      (org/update-organization organizationId {$set {:local-bulletins-page-settings skeleton}})
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
   (sc/optional-key :pateRobot)              sc/Bool
   (sc/optional-key :invoicingEnabled)       sc/Bool
   (sc/optional-key :bulletinsEnabled)       sc/Bool
   (sc/optional-key :bulletinsUrl)           sc/Str
   (sc/optional-key :bulletinsEmail)         sc/Str
   (sc/optional-key :bulletinsDescriptions)  sc/Bool})

(defcommand update-organization
  {:description      "Update organization details."
   :input-validators [OrgUpdateParams
                      permit/permit-type-validator
                      (fn [{{:keys [permitType pateEnabled]} :data}]
                        (if (true? pateEnabled)
                          (when-not (true? (-> (pate-schema/permit-type->categories permitType)
                                               first
                                               pate-schema/pate-category?))
                            (fail :error.pate-not-supported-for-scope))))]
   :user-roles       #{:admin}}
  [{data :data}]
  (let [{:keys [permitType
                municipality] } data
        param->prop             {:inforequestEnabled     :inforequest-enabled
                                 :applicationEnabled     :new-application-enabled
                                 :openInforequestEnabled :open-inforequest
                                 :openInforequestEmail   :open-inforequest-email
                                 :opening                :opening
                                 :pateEnabled            :pate.enabled
                                 :pateSftp               :pate.sftp
                                 :pateRobot              :pate.robot
                                 :invoicingEnabled       :invoicing-enabled
                                 :bulletinsEnabled       :bulletins.enabled
                                 :bulletinsUrl           :bulletins.url
                                 :bulletinsEmail         :bulletins.notification-email
                                 :bulletinsDescriptions  :bulletins.descriptions-from-backend-system}]
    (when-let [update (->> (keys param->prop)
                           (filter (partial contains? data))
                           (map (fn [param]
                                  [(util/kw-path :scope.$ (param param->prop))
                                   (param data)]))
                           (into {})
                           not-empty)]
      (mongo/update-by-query :organizations
                             {:scope {$elemMatch {:permitType permitType :municipality municipality}}}
                             {$set update})))
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
    (sc/validate org/Organization org-model)
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
  (ok :organizations (org/get-organizations {} org/admin-projection)))

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

(defcommand update-ad-login-settings
  {:parameters       [org-id enabled trusted-domains idp-uri idp-cert]
   :input-validators [(partial non-blank-parameters [:org-id])]
   :user-roles       #{:admin}}
  [_]
  (org/set-ad-login-settings org-id enabled trusted-domains idp-uri idp-cert)
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
  (ok :permitTypes (keys (permit/permit-types))))

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
    (ok :operations (operations/selected-operations-for-organizations organizations)
        :operation-infos (operations/get-selected-operation-infos organizations))))

(defquery addable-operations
  {:description "returns operations addable for the application whose id is given as parameter"
   :parameters  [:id]
   :user-roles  #{:applicant :authority}
   :states      states/pre-sent-application-states}
  [{{:keys [organization permitType]} :application}]
  (when-let [org (org/get-organization organization)]
    (let [selected-operations (map keyword (:selected-operations org))]
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
                                        (do (error "Unknown operation: " (logging/sanitize 100 operation))
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
                                        (do (error "Unknown operation: " (logging/sanitize 100 operation))
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

(defn- toggle-organization-flag [command flag]
  (let [{:keys [organizationId enabled]} (:data command)]
    (when (sc/check (schema-tools/get-in org/Organization [flag]) enabled)
      (fail! :error.illegal-key))
    (org/update-organization organizationId {$set {flag enabled}})
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
  [_])

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
  (org/update-organization organizationId {$set {:ram.disabled disabled}}))

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

(defcommand set-organization-validate-verdict-given-date
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :validate-verdict-given-date))

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

(defcommand set-organization-calendars-enabled
  {:parameters       [enabled organizationId]
   :user-roles       #{:admin}
   :input-validators [(partial non-blank-parameters [:organizationId])
                      (partial boolean-parameters [:enabled])]
   :feature          :ajanvaraus}
  [command]
  (toggle-organization-flag command :calendars-enabled))

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
                        (when-not (true? (-> (pate-schema/permit-type->categories permitType)
                                             first
                                             pate-schema/pate-category?))
                          (fail :error.pate-not-supported-for-scope)))]}
  [_]
  (mongo/update-by-query :organizations
                         {:scope {$elemMatch {:permitType permitType :municipality municipality}}}  {$set {:scope.$.pate.enabled value}})
  (ok))

(defn- organization-exists [{data :data}]
  (when-let [organizationId (:organizationId data)]
    (when-not (org/get-organization organizationId [:id])
      (fail :error.no-such-organization))))

(defcommand set-organization-boolean-attribute
  {:parameters       [enabled organizationId attribute]
   :user-roles       #{:admin}
   :input-validators [(partial non-blank-parameters [:organizationId :attribute])
                      (partial boolean-parameters [:enabled])]
   :pre-checks       [organization-exists]}
  [command]
  (toggle-organization-flag command (keyword attribute)))

(defcommand set-organization-review-officers-list-enabled
  {:parameters       [organizationId enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [command]
  (toggle-organization-flag command :review-officers-list-enabled))

(defcommand set-organization-permanent-archive-start-date
  {:parameters       [organizationId date]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial number-parameters [:date])]
   :pre-checks       [(fn [{{:keys [organizationId]} :data}]
                        (when organizationId
                          (when-not (org/some-organization-has-archive-enabled? [organizationId])
                            unauthorized)))]}
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

(def email-list-validators [(partial action/string-parameters [:emails])
                            (fn [{{emails :emails} :data}]
                              (let [splitted (split-emails emails)]
                                (when (and (not (ss/blank? emails)) (some (partial sc/check ssc/Email) splitted))
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
   :description      "When ARA funding is enabled to application, send notification to these email addresses"
   :permissions      [{:required [:organization/admin]}]
   :input-validators email-list-validators}
  [_]
  (let [addresses (when-not (ss/blank? emails) (split-emails emails))]
    (org/update-organization organizationId {$set {:notifications.funding-notification-emails addresses}})
    (ok)))

(defcommand set-organization-default-reservation-location
  {:parameters       [organizationId location]
   :description      "When reservation is made, use this location as default value"
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial action/string-parameters [:location])]
   :feature          :ajanvaraus}
  [_]
  (org/update-organization organizationId {$set {:reservations.default-location location}})
  (ok))

(defcommand set-organization-state-change-endpoint
  {:parameters [organizationId url headers authType]
   :optional-parameters [basicCreds]
   :description "Set REST endpoint configurations for organization state change messages"
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial action/string-parameters [:url])]}
  [_]
  (org/set-state-change-endpoint organizationId (ss/trim url) headers authType basicCreds))

(defquery krysp-config
  {:parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (if-let [organization (util/find-by-id organizationId user-organizations)]
    (let [permit-types (->> (:scope organization)
                            (map (comp keyword :permitType))
                            (filter #(get krysp-xml/supported-krysp-versions-by-permit-type %)))
          krysp-keys (conj (vec permit-types) :osoitteet)
          empty-confs (zipmap krysp-keys (repeat {}))]
      (ok :krysp (merge empty-confs (:krysp organization))))
    (fail :error.unknown-organization)))

(defcommand set-krysp-endpoint
  {:parameters       [organizationId url username password permitType version]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(fn [{{permit-type :permitType} :data}]
                        (when-not (or
                                    (= "osoitteet" permit-type)
                                    (get krysp-xml/supported-krysp-versions-by-permit-type (keyword permit-type)))
                          (fail :error.missing-parameters :parameters [:permitType])))
                      (partial validate-optional-url :url)
                      (partial action/string-parameters [:url :username :password :permitType :version])]}
  [{data :data}]
  (let [url             (-> data :url ss/trim)
        krysp-config    (org/get-krysp-wfs {:_id organizationId} permitType)
        password        (if (ss/blank? password) (second (:credentials krysp-config)) password)]
    (if (or (ss/blank? url) (wfs/wfs-is-alive? url username password))
      (org/set-krysp-endpoint organizationId url username password permitType version krysp-config)
      (fail :auth-admin.legacyNotResponding))))

(defcommand set-kuntagml-http-endpoint
  {:description         "Admin can configure KuntaGML sending as HTTP, instead of SFTP"
   :parameters          [url organization permitType]
   :optional-parameters [auth-type username password partner headers path]
   :user-roles          #{:admin}
   :input-validators    [(partial validate-optional-url :url)
                         (fn [{:keys [data]}]
                           (when (and (ss/not-blank? (:partner data))
                                      (sc/check (ssu/get org/KryspHttpConf :partner) (:partner data)))
                             (fail :error.illegal-value:schema-validation :data :partner)))
                         permit/permit-type-validator
                         (action/valid-db-key :permitType)
                         (fn [{:keys [data] :as command}]
                           (when (seq (:headers data))
                             (action/vector-parameters-with-map-items-with-required-keys
                               [:headers]
                               [:key :value]
                               command)))]
   :pre-checks          [(fn [{:keys [data]}]
                           (when-not (pos? (mongo/count :organizations {:_id (:organization data)}))
                             (fail :error.unknown-organization)))]}
  [{:keys [data]}]
  (let [url     (-> data :url ss/trim)
        updates (->> (when username
                       (org/encode-credentials username password))
                     (merge {:url url} (select-keys data [:headers :auth-type :partner :path]))
                     (util/strip-nils)
                     org/krysp-http-conf-validator
                     (map (fn [[k v]] [(str "krysp." permitType ".http." (name k)) v]))
                     (into {}))]
    (mongo/update-by-id :organizations organization {$set updates})))

(defquery matti-config
  {:description      "Convenience query for Matti/DMCity admin view."
   :parameters       [organizationId]
   :user-roles       #{:admin}
   :input-validators [(partial action/non-blank-parameters [:organizationId])]
   :pre-checks       [org/dmcity-backend]}
  [_]
  (let [data              (mongo/by-id :organizations
                                       organizationId
                                       {:krysp.R                  1
                                        :krysp.P                  1
                                        :state-change-msg-enabled 1})
        {:keys [url username password crypto-iv headers
                enabled]} (some-> data :krysp :R :http)]
    (ok :config {:url         url
                 :username    username
                 :password    (when (and crypto-iv password)
                                (org/decode-credentials password crypto-iv))
                 :vault       (:value (util/find-by-key :key "x-vault" headers))
                 :buildingUrl (get-in data [:krysp :R :buildings :url])
                 :enabled     {:R           enabled
                               :P           (get-in data [:krysp :P :http :enabled])
                               :stateChange (:state-change-msg-enabled data)}})))

(defcommand update-matti-config
  {:description      "Matti/DMCity specific HTTP integration and state change update mechanism."
   :parameters       [:organizationId :url :username :password
                      :vault :buildingUrl]
   :user-roles       #{:admin}
   :input-validators [ (partial action/non-blank-parameters
                                [:organizationId :url :username :password :vault :buildingUrl])]
   :pre-checks       [org/dmcity-backend]}
  [{data :data}]
  (org/update-matti-config data))

(defcommand toggle-matti-functionality
  {:description      "Matti/DMCity specific HTTP integration and state change (de)activation mechanism."
   :parameters       [:organizationId :function :enabled]
   :user-roles       #{:admin}
   :input-validators [{:organizationId org/OrgId
                       :function       (sc/enum "R" "P" "stateChange")
                       :enabled        sc/Bool}]
   :pre-checks       [org/dmcity-backend]}
  [{data :data}]
  (org/toggle-matti-functionality data))

(defcommand delete-kuntagml-http-endpoint
  {:description      "Remove HTTP config for permit-type"
   :parameters       [organization permitType]
   :user-roles       #{:admin}
   :input-validators [permit/permit-type-validator]
   :pre-checks       [(fn [{:keys [data]}]
                        (when-not (pos? (mongo/count :organizations {:_id (:organization data)}))
                          (fail :error.unknown-organization)))]}
  [_]
  (mongo/update-by-id :organizations organization {$unset {(str "krysp." permitType ".http") 1}}))

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
    (org/update-organization organizationId {$set {(str "vendor-backend-redirect." key) (ss/trim val)}})))

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
       (org/update-organization organizationId)))

(defcommand pseudo-update-organization-name
  {:description "Pseudo command for differentiating impersonation."
   :permissions [{:required [:organization/update-name]}]}
  [_])

(defquery available-backend-systems
  {:user-roles #{:admin}}
  (ok :backend-systems org/backend-systems))

(defcommand update-organization-backend-systems
  {:description      "Updates organization backend systems by permit type."
   :parameters       [org-id backend-systems]
   :user-roles       #{:admin}
   :input-validators [(partial action/non-empty-map-parameters [:backend-systems])]}
  [_]
  (->> (util/map-keys (fn->> name (format "krysp.%s.backend-system")) backend-systems)
       (reduce (fn [updates [path system]] (if (contains? org/backend-systems (keyword system))
                                             (assoc-in updates [$set path] system)
                                             (assoc-in updates [$unset path] "")))
               {})
       (org/update-organization org-id)))

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
        validation-errors (seq (remove nil? (map (partial sc/check org/Tag) tags-with-ids)))]
    (when validation-errors (fail! :error.missing-parameters))

    (when (seq removed-ids)
      (mongo/update-by-query :applications {:tags {$in removed-ids} :organization organizationId}
                             {$pull {:tags {$in removed-ids}}}))
    (org/update-organization organizationId {$set {:tags tags-with-ids}})))

(defquery remove-tag-ok
  {:parameters       [organizationId tagId]
   :input-validators [(partial non-blank-parameters [:tagId])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (when-let [tag-applications (seq (mongo/select
                                     :applications
                                     {:tags tagId :organization organizationId}
                                     [:_id]))]
    (fail :warning.tags.removing-from-applications :applications tag-applications)))

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
   :user-authz-roles #{:statementGiver}
   :user-roles       #{:authority :applicant}
   :pre-checks       [org/statement-giver-in-organization]}
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
  [_])


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

(defcommand update-user-layers
  {:parameters       [organizationId layers]
   :input-validators [(partial action/vector-parameter-of :layers map?)]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (let [selected-layers   (remove (comp ss/blank? :id) layers)
        validation-errors (remove nil? (map (partial sc/check org/Layer) selected-layers))]
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

(defraw waste-ads-feed
  {:description         "Simple RSS feed for construction waste information."
   :parameters          [fmt]
   :optional-parameters [org lang]
   :input-validators    [org/valid-feed-format org/valid-org i18n/valid-language]
   :user-roles          #{:anonymous}}
  (resp/status 404 "Not Found"))              ;; LPK-3787 New waste-ads coming

(defcommand section-toggle-enabled
  {:description      "Enable/disable section requirement for fetched
  verdicts support."
   :parameters       [organizationId flag]
   :input-validators [(partial action/boolean-parameters [:flag])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (org/toggle-group-enabled organizationId :section flag))

(defcommand section-toggle-operation
  {:description      "Toggles operation either requiring section or not."
   :parameters       [organizationId operationId flag]
   :input-validators [(partial action/non-blank-parameters [:operationId])
                      (partial action/boolean-parameters [:flag])]
   :permissions      [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (org/toggle-group-operation (util/find-by-id organizationId user-organizations)
                              :section (ss/trim operationId) flag))

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

(defcommand upsert-handler-role
  {:description         "Create and modify organization handler role"
   :parameters          [name organizationId]
   :optional-parameters [roleId]
   :pre-checks          [validate-handler-role-in-organization]
   :input-validators    [(partial supported-localization-parameters [:name])]
   :permissions         [{:required [:organization/admin]}]}
  [{user-orgs :user-organizations}]
  (let [handler-role (org/create-handler-role roleId name)]
    (if (sc/check org/HandlerRole handler-role)
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
  (org/toggle-handler-role! organizationId roleId enabled))

(defcommand upsert-organization-suomifi-message-attachments
  {:description         "Set and update attachment settings for Suomi.fi-messages"
   :parameters          [organizationId section attachments] ;; section = 'verdict' or 'neighbors'
   :input-validators    [{:organizationId sc/Str
                          :section        (sc/enum "verdict" "neighbors")
                          :attachments    [org/AttachmentEntry]} ]
   :permissions         [{:required [:organization/admin]}]}
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
  (ok :documentRequest (org/document-request-info organizationId)))

(defcommand set-document-request-info
  {:description         "Updates organization's document request info. Document requests are made
  from document store."
   :parameters          [organizationId enabled instructions]
   :optional-parameters [email]
   :permissions         [{:required [:organization/admin]}]
   :input-validators    [(partial boolean-parameters [:enabled])
                         (partial localization-parameters [:instructions])
                         (fn [command]
                           (when-let [email (some-> command :data :email
                                                    ss/canonize-email
                                                    ss/blank-as-nil)]
                             (when-not (v/valid-email? email)
                               (fail :error.email))))]}
  [_]
  (org/set-document-request-info organizationId
                                 enabled
                                 (ss/canonize-email email)
                                 (ss/trimwalk instructions))
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
         (org/allowed-docterminal-attachment-types key)
         (ok :attachment-types))))

(defcommand set-docterminal-attachment-type
  {:description      "Allows or disallows showing the given attachment type in
                      the archive document terminal application."
   :parameters       [organizationId attachmentType enabled]
   :pre-checks       [org/check-docterminal-enabled]
   :input-validators [(partial parameters-matching-schema [:attachmentType]
                               (sc/cond-pre (sc/enum "all")
                                            org/DocTerminalAttachmentType))
                      (partial boolean-parameters [:enabled])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (org/set-allowed-docterminal-attachment-type organizationId attachmentType enabled))

(defcommand set-docdepartmental-attachment-type
  {:description      "Allows or disallows showing the given attachment type in
                      the archive document departmental application."
   :parameters       [organizationId attachmentType enabled]
   :pre-checks       [org/check-docdepartmental-enabled]
   :input-validators [(partial parameters-matching-schema [:attachmentType]
                               (sc/cond-pre (sc/enum "all")
                                            org/DocTerminalAttachmentType))
                      (partial boolean-parameters [:enabled])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (org/set-allowed-docdepartmental-attachment-type organizationId attachmentType enabled))

(defquery docterminal-enabled
  {:pre-checks  [org/check-docterminal-enabled]
   :parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [_])

(defquery docdepartmental-enabled
  {:pre-checks  [org/check-docdepartmental-enabled]
   :parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [_])

(defquery docstore-enabled
  {:parameters [organizationId]
   :pre-checks  [org/check-docstore-enabled]
   :permissions [{:required [:organization/admin]}]}
  [_])

(defquery ad-login-enabled
  {:parameters [organizationId]
   :pre-checks [org/check-ad-login-enabled]
   :permissions [{:required [:organization/admin]}]}
  [_])

(defn role-mapping-validator [{{:keys [organizationId role-map]} :data}]
  (when organizationId
    (let [org-roles (-> organizationId
                        (org/get-organization)
                        (org/allowed-roles-in-organization)
                        set
                        (disj :digitization-project-user))]
      (cond
        (empty? role-map)
        (fail :error.empty-role-map)

        (not-every? org-roles (keys role-map))
        (fail :error.illegal-role)))))

(defcommand update-ad-login-role-mapping
  {:description      "Updates active directory role mapping"
   :parameters       [organizationId role-map]
   :pre-checks       [org/check-ad-login-enabled
                      role-mapping-validator]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :role-map       {sc/Keyword sc/Str}}]
   :permissions      [{:required [:organization/admin]}]}
  [{user :user}]
  (org/update-ad-login-role-mapping (ss/trimwalk role-map) user))

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
                               {(if mandatory $push $pull) {:default-attachments-mandatory operationId}}))))

(defcommand toggle-deactivation
  {:description      "When an organization is deactivated, everyone of its
  applications are marked readOnly. In addition, applications and
  inforequests are disabled for every scope. On activation, the
  applications are 'released' but scopes are left untouched."
   :parameters       [organizationId deactivated]
   :input-validators [(partial action/non-blank-parameters [:organizationId])
                      (partial action/boolean-parameters [:deactivated])]
   :pre-checks       [organization-exists]
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
   :input-validators    [(partial action/non-blank-parameters [:name :code])]
   :pre-checks          [unique-review-officer-code]
   :permissions         [{:required [:organization/admin]}]}
  [_]
  (let [review-officer-id (mongo/create-id)]
    (org/update-organization
      organizationId
      {$push {:reviewOfficers {:id          review-officer-id
                               :_atomic-map? true
                               :name        (ss/trim name)
                               :code        (ss/trim code)}}})
    (ok :id review-officer-id)))

(defcommand edit-review-officer
  {:description         "Updates a review officer's information.
  Editing the code or the id is prohibited."
   :parameters          [organizationId personId name]
   :input-validators    [(partial action/non-blank-parameters [:personId :name])]
   :pre-checks          [review-officer-exists]
   :permissions         [{:required [:organization/admin]}]}
  [_]
  (org/update-organization
    organizationId
    {:reviewOfficers {$elemMatch {:id personId}}}
    {$set {:reviewOfficers.$.name (ss/trim name)}})
  (ok :id personId))

(defcommand delete-review-officer
  {:description      "Delete a review officer from the organization."
   :parameters       [organizationId personId]
   :input-validators [(partial action/non-blank-parameters [:personId])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (org/update-organization organizationId {$pull {:reviewOfficers {:id personId}}}))

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
  (ok :authorities (mongo/select :users
                                 (cond-> {(util/kw-path :orgAuthz organizationId) "authority"
                                          :role                                   "authority"
                                          :enabled                                true}
                                   includeDisabled (dissoc :enabled))
                                 [:id :firstName :lastName :enabled :username])))

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
   :input-validators [{:org-id           sc/Str
                       :invoicing-config org/InvoicingConfig}]}
  [_]
  (let [{:keys [local-sftp-user]} (org/get-invoicing-config org-id)
        new-creds                 (->> invoicing-config
                                       :credentials
                                       ((juxt :username :password)) (apply org/encode-credentials))
        new-config                (-> invoicing-config
                                      (dissoc :credentials :local-sftp-user)
                                      (util/assoc-when :credentials new-creds
                                                       :local-sftp-user local-sftp-user))]
    (org/update-organization org-id {$set {:invoicing-config new-config}})
    (ok)))


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
    (org/update-organization organizationId {$set setters})))


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
  [_])
