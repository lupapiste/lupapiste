(ns lupapalvelu.organization-api
  (:require [camel-snake-kebab.core :as csk]
            [clojure.set :as set]
            [clojure.string :as s]
            [clojure.walk :refer [keywordize-keys]]
            [lupapalvelu.action :refer [defquery defcommand defraw non-blank-parameters vector-parameters vector-parameters-with-at-least-n-non-blank-items boolean-parameters number-parameters email-validator validate-url validate-optional-url map-parameters-with-required-keys string-parameters partial-localization-parameters localization-parameters supported-localization-parameters parameters-matching-schema coordinate-parameters] :as action]
            [lupapalvelu.attachment.stamps :as stamps]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.schema-util :as pate-schema]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [lupapalvelu.wfs :as wfs]
            [lupapalvelu.xml.validator :as krysp-xml]
            [me.raynes.fs :as fs]
            [monger.operators :refer :all]
            [noir.core :refer [defpage]]
            [noir.response :as resp]
            [sade.core :refer [ok fail fail! now unauthorized]]
            [sade.municipality :as muni]
            [sade.shared-schemas :as sssc]
            [sade.schemas :as ssc]
            [sade.schema-utils :as ssu]
            [sade.strings :as ss]
            [sade.util :refer [fn->>] :as util]
            [sade.validators :as v]
            [schema.core :as sc]
            [slingshot.slingshot :refer [try+]]
            [swiss.arrows :refer :all]
            [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf fatal]]))
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
                               (when (-> org :krysp (get (-> scope :permitType keyword)) :url s/blank? not)
                                 municipality))))}))

(defn- organization-attachments
  "Returns a map where key is permit type, value is a list of attachment types for the permit type"
  [{scope :scope}]
  (let [permit-types (->> scope (map :permitType) distinct (map keyword))]
    (->> (select-keys operations/operation-names-by-permit-type permit-types)
         (map (fn [[permit-type operations]] (->> (map att-type/get-attachment-types-for-operation operations)
                                                  (map att-type/->grouped-array)
                                                  (zipmap operations))))
         (zipmap permit-types))))

(defn- operations-attachements-by-operation [organization operations]
  (->> (map #(get-in organization [:operations-attachments %] []) operations)
       (zipmap operations)))

(defn- organization-operations-with-attachments
  "Returns a map of maps where key is permit type, value is a map operation names to list of attachment types"
  [{scope :scope :as organization}]
  (let [selected-ops (->> organization :selected-operations (map keyword) set)
        permit-types (->> scope (map :permitType) distinct (map keyword))]
    (zipmap permit-types (map (fn->> (operations/operation-names-by-permit-type)
                                     (filter selected-ops)
                                     (operations-attachements-by-operation organization))
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


;;
;; Actions
;;

(defquery organization-by-user
  {:description "Lists organization details."
   :permissions [{:required [:organization/admin]}]}
  [{user :user}]
  (let [organization                         (org/get-organization (usr/authority-admins-organization-id user))
        ops-with-attachments                 (organization-operations-with-attachments organization)
        selected-operations-with-permit-type (selected-operations-with-permit-types organization)
        allowed-roles                        (org/allowed-roles-in-organization organization)]
    (ok :organization (-> organization
                        (assoc :operationsAttachments ops-with-attachments
                               :selectedOperations selected-operations-with-permit-type
                               :allowedRoles allowed-roles)
                        (dissoc :operations-attachments :selected-operations)
                        (update-in [:map-layers :server] select-keys [:url :username])
                        (update-in [:suti :server] select-keys [:url :username])
                        (decode-state-change-conf))
        :attachmentTypes (organization-attachments organization))))

(defquery organization-attachment-types
  {:description "Combined list of attachment types for every organization scope."
   :permissions [{:required [:organization/admin]}]}
  [{:keys [user user-organizations]}]
  (-<>> (usr/authority-admins-organization-id user)
        (util/find-by-id <> user-organizations)
        :scope
        (map (util/fn->> :permitType
                         keyword
                         att-type/get-all-attachment-types-for-permit-type))
        flatten
        (map #(select-keys % [:type-group :type-id]))
        distinct
        (ok :attachmentTypes)))

(defquery organization-name-by-user
  {:description "Lists organization names for all languages."
   :permissions [{:required [:organization/admin]}]}
  [{user :user}]
  (ok (-> (usr/authority-admins-organization-id user)
          org/get-organization
          (select-keys [:id :name]))))

(defquery user-organizations-for-permit-type
  {:parameters       [permitType]
   :user-roles       #{:authority}
   :input-validators [permit/permit-type-validator]}
  [{user :user}]
  (ok :organizations (org/get-organizations {:_id   {$in (usr/organization-ids-by-roles user #{:authority})}
                                             :scope {$elemMatch {:permitType permitType}}})))

(defquery user-organizations-for-archiving-project
  {:user-roles #{:authority}}
  [{user :user}]
  (ok :organizations (org/get-organizations {:_id {$in (usr/organization-ids-by-roles user #{:archivist :digitizer})}})))

(defn- check-bulletins-enabled [{user-orgs :user-organizations {permit-type :permitType municipality :municipality} :data}]
  (when-not (org/bulletins-enabled? (first user-orgs) permit-type municipality)
    (fail :error.bulletins-not-enebled-for-scope)))

(defquery user-organization-bulletin-settings
  {:permissions [{:required [:organization/admin]}]
   :pre-checks  [check-bulletins-enabled]}
  [{user :user user-orgs :user-organizations}]
  (let [user-org (first user-orgs)
        scopes   (->> user-org :scope
                      (filter (comp :enabled :bulletins))
                      (map #(select-keys % [:permitType :municipality :bulletins])))
        texts    (->> user-org :local-bulletins-page-settings :texts)]
    (ok :bulletin-scopes scopes
        :local-bulletins-page-texts texts)))

(defn- bulletin-scope-settings-validator
  [{{:keys [notificationEmail descriptionsFromBackendSystem]} :data}]
  (when (and notificationEmail (not (v/valid-email? notificationEmail)))
    (fail! :error.email))
  (when (and descriptionsFromBackendSystem (not (boolean? descriptionsFromBackendSystem)))
    (fail! :error.invalid-value)))

(defcommand update-organization-bulletin-scope
  {:permissions         [{:required [:organization/admin]}]
   :parameters          [permitType municipality]
   :optional-parameters [notificationEmail descriptionsFromBackendSystem]
   :input-validators    [permit/permit-type-validator
                         bulletin-scope-settings-validator]
   :pre-checks          [check-bulletins-enabled]}
  [{user :user data :data}]
  (let [updates (merge (when (util/not-empty-or-nil? notificationEmail)
                         {:scope.$.bulletins.notification-email notificationEmail})
                       (when (contains? data :descriptionsFromBackendSystem)
                         {:scope.$.bulletins.descriptions-from-backend-system descriptionsFromBackendSystem}))]
    (when updates
      (mongo/update-by-query :organizations
        {:scope {$elemMatch {:permitType permitType :municipality municipality}}} {$set updates}))
    (ok)))

(defcommand remove-organization-local-bulletins-caption
  {:permissions      [{:required [:organization/admin]}]
   :parameters       [lang index]
   :input-validators [(partial action/supported-lang :lang)
                      (partial action/positive-integer-parameters [:index])]
   :pre-checks       [check-bulletins-enabled]}
  [{[user-org & _] :user-organizations}]
  (if (integer? index)
    (let [{{texts :texts} :local-bulletins-page-settings org-id :id} user-org
          caption (get-in texts [(keyword lang) :caption])
          caption (util/drop-nth index caption)
          updates {$set {(util/kw-path [:local-bulletins-page-settings.texts (keyword lang) :caption]) caption}}]
      (org/update-organization org-id updates)
      (ok :removed true))
    (ok :removed false)))

(defcommand upsert-organization-local-bulletins-text
  {:permissions         [{:required [:organization/admin]}]
   :parameters          [lang key value]
   :optional-parameters [index]
   :input-validators    [(partial action/supported-lang :lang)
                         (partial non-blank-parameters [:key])]
   :pre-checks          [check-bulletins-enabled]}
  [{[user-org & _] :user-organizations}]
  (let [{{texts :texts} :local-bulletins-page-settings org-id :id} user-org
        path    (remove nil?
                        [(keyword lang) (keyword key) (when (integer? index) index)])
        valid?  (nil? (sc/check org/LocalBulletinsPageTexts (assoc-in texts path value)))
        updates {$set {(util/kw-path (cons :local-bulletins-page-settings.texts path)) value}}]
    (when valid?
      (org/update-organization org-id updates))
    (ok :valid valid?)))

(defcommand update-organization
  {:description "Update organization details."
   :parameters [permitType municipality
                inforequestEnabled applicationEnabled openInforequestEnabled openInforequestEmail
                opening pateEnabled]
   :optional-parameters [bulletinsEnabled bulletinsUrl]
   :input-validators [permit/permit-type-validator
                      (fn [{{:keys [permitType pateEnabled]} :data}]
                        (if (true? pateEnabled)
                          (when-not (true? (-> (pate-schema/permit-type->categories permitType)
                                               first
                                               pate-schema/pate-category?))
                            (fail :error.pate-not-supported-for-scope))))]
   :user-roles #{:admin}}
  [_]
  (mongo/update-by-query :organizations
      {:scope {$elemMatch {:permitType permitType :municipality municipality}}}
      {$set (merge {:scope.$.inforequest-enabled inforequestEnabled
                    :scope.$.new-application-enabled applicationEnabled
                    :scope.$.open-inforequest openInforequestEnabled
                    :scope.$.open-inforequest-email openInforequestEmail
                    :scope.$.opening (when (number? opening) opening)
                    :scope.$.pate-enabled pateEnabled}
                   (when-not (nil? bulletinsEnabled)
                     {:scope.$.bulletins.enabled bulletinsEnabled
                      :scope.$.bulletins.url     (or bulletinsUrl "")}))})
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
  (mongo/insert :organizations {:_id           org-id
                                :name          {:fi name :sv name :en name}
                                :scope         (map (partial org/new-scope municipality) permit-types)
                                :handler-roles [(org/create-handler-role)]
                                :stamps        [(assoc stamps/default-stamp-data :id (mongo/create-id))]
                                :docstore-info org/default-docstore-info})
  (ok))

(defn- validate-map-with-optional-url-values [param command]
  (let [urls (map ss/trim (vals (get-in command [:data param])))]
    (some #(when-not (ss/blank? %)
             (validate-url %))
          urls)))

(defcommand add-organization-link
  {:description      "Adds link to organization."
   :parameters       [url name]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial map-parameters-with-required-keys
                               [:url :name] i18n/supported-langs)
                      (partial validate-map-with-optional-url-values :url)]}
  [{user :user created :created}]
  (org/add-organization-link (usr/authority-admins-organization-id user)
                             name url created)
  (ok))

(defcommand update-organization-link
  {:description      "Updates organization link."
   :parameters       [url name index]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial map-parameters-with-required-keys
                               [:url :name] i18n/supported-langs)
                      (partial validate-map-with-optional-url-values :url)
                      (partial number-parameters [:index])]}
  [{user :user created :created}]
  (org/update-organization-link (usr/authority-admins-organization-id user)
                                index name url created)
  (ok))

(defcommand remove-organization-link
  {:description      "Removes organization link."
   :parameters       [url name]
   :input-validators [(partial map-parameters-with-required-keys
                               [:url :name] i18n/supported-langs)
                      (partial validate-map-with-optional-url-values :url)]
   :permissions      [{:required [:organization/admin]}]}
  [{user :user}]
  (org/remove-organization-link (usr/authority-admins-organization-id user)
                                name url)
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
  (->> (org/autogin-ip-mongo-changes ips)
       (org/update-organization org-id))
  (ok))

(defcommand update-adlogin-settings
  {:parameters       [org-id enabled trusted-domains idp-uri idp-cert]
   :input-validators [(partial non-blank-parameters [:org-id])]
   :user-roles       #{:admin}}
  [_]
  (org/set-adlogin-settings org-id enabled trusted-domains idp-uri idp-cert)
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

(defquery municipalities
  {:description "Returns a list of all municipality IDs. For admin use."
   :user-roles  #{:admin}}
  (ok :municipalities muni/municipality-codes))

(defquery all-operations-for-organization
  {:description      "Returns operations that match the permit types of the organization whose id is given as parameter"
   :parameters       [organizationId]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial non-blank-parameters [:organizationId])]}
  (when-let [org (org/get-organization organizationId)]
    (ok :operations (operations/organization-operations org))))

(defquery selected-operations-for-municipality
  {:description      "Returns selected operations of all the organizations who have a scope with the given municipality.
                 If a \"permitType\" parameter is given, returns selected operations for only that organization (the municipality + permitType combination)."
   :parameters       [:municipality]
   :user-roles       #{:applicant :authority}
   :input-validators [(partial non-blank-parameters [:municipality])]}
  [{{:keys [municipality permitType]} :data}]
  (when-let [organizations (org/resolve-organizations municipality permitType)]
    (ok :operations (operations/selected-operations-for-organizations organizations))))

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
  {:parameters       [operations]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial vector-parameters [:operations])
                      (fn [{{:keys [operations]} :data}]
                        (when-not (every? (->> operations/operations keys (map name) set) operations)
                          (fail :error.unknown-operation)))]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:selected-operations operations}})
  (ok))

(defcommand organization-operations-attachments
  {:parameters       [operation attachments]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial non-blank-parameters [:operation])
                      (partial vector-parameters [:attachments])
                      (fn [{{:keys [operation attachments]} :data, user :user}]
                        (let [organization        (org/get-organization (usr/authority-admins-organization-id user))
                              selected-operations (set (:selected-operations organization))
                              allowed-types       (att-type/get-attachment-types-for-operation operation)
                              attachment-types    (map (fn [[group id]] {:type-group group :type-id id}) attachments)]
                          (cond
                            (not (selected-operations operation)) (do
                                                                    (error "Unknown operation: " (logging/sanitize 100 operation))
                                                                    (fail :error.unknown-operation))
                            (not-every? (partial att-type/contains? allowed-types) attachment-types) (fail :error.unknown-attachment-type))))]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {(str "operations-attachments." operation) attachments}})
  (ok))

(defcommand set-organization-app-required-fields-filling-obligatory
  {:parameters       [enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:app-required-fields-filling-obligatory enabled}})
  (ok))

(defcommand set-automatic-ok-for-attachments
  {:parameters       [enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:automatic-ok-for-attachments-enabled enabled}})
  (ok))

(defcommand set-organization-assignments
  {:parameters       [enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:assignments-enabled enabled}})
  (ok))

(defcommand set-organization-inspection-summaries
  {:parameters       [enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:inspection-summaries-enabled enabled}})
  (ok))

(defcommand set-organization-extended-construction-waste-report
  {:parameters       [enabled]
   :permissions      [{:required [:organization/admin]}]
   :pre-checks       [(org/permit-type-validator :R)]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:extended-construction-waste-report-enabled enabled}})
  (ok))

(defcommand set-organization-multiple-operations-support
  {:parameters       [enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:multiple-operations-supported enabled}})
  (ok))

(defcommand set-organization-remove-handlers-from-reverted-draft
  {:parameters        [enabled]
   :permissions       [{:required [:organization/admin]}]
   :input-validators  [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:remove-handlers-from-reverted-draft enabled}})
  (ok))

(defcommand set-organization-validate-verdict-given-date
  {:parameters       [enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:validate-verdict-given-date enabled}})
  (ok))

(defcommand set-organization-review-fetch-enabled
  {:parameters       [enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:automatic-review-fetch-enabled enabled}})
  (ok))

(defcommand set-only-use-inspection-from-backend
  {:parameters       [enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:only-use-inspection-from-backend enabled}})
  (ok))

(defcommand set-organization-use-attachment-links-integration
  {:parameters       [enabled]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:use-attachment-links-integration enabled}})
  (ok))

(defcommand set-organization-calendars-enabled
  {:parameters       [enabled organizationId]
   :user-roles       #{:admin}
   :input-validators [(partial non-blank-parameters [:organizationId])
                      (partial boolean-parameters [:enabled])]
   :feature          :ajanvaraus}
  [{user :user}]
  (org/update-organization organizationId {$set {:calendars-enabled enabled}})
  (ok))

(defcommand set-organization-boolean-path
  {:parameters       [path value organizationId]
   :description      "Set boolean value to given path in organization settgins. Path must be string with dot denoted path. Eg 'foo.bar.baz'."
   :user-roles       #{:admin}
   :input-validators [(partial non-blank-parameters [:organizationId :path])
                      (partial boolean-parameters [:value])]}
  [{user :user}]
  (when-let [kw-path (-> path (ss/split #"\.") (util/kw-path))]
    (org/update-organization organizationId {$set {kw-path value}}))
  (ok))

(defcommand set-organization-scope-pate-value
  {:parameters       [permitType municipality value]
   :description      "Set boolean value for pate-enabled in organization scope level."
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
                         {:scope {$elemMatch {:permitType permitType :municipality municipality}}}  {$set {:scope.$.pate-enabled value}})
  (ok))

(defcommand set-organization-boolean-attribute
  {:parameters       [enabled organizationId attribute]
   :user-roles       #{:admin}
   :input-validators [(partial non-blank-parameters [:organizationId :attribute])
                      (partial boolean-parameters [:enabled])]}
  [_]
  (when-let [org (->> (org/get-organization organizationId)
                      org/parse-organization)]
    (if (->> (assoc org (keyword attribute) enabled)
             (sc/check org/Organization))
      (fail :error.illegal-key)
      (do (org/update-organization organizationId {$set {attribute enabled}})
          (ok)))))

(defcommand set-organization-permanent-archive-start-date
  {:parameters       [date]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial number-parameters [:date])]
   :pre-checks       [(fn [{:keys [user]}]
                        (when-not (org/some-organization-has-archive-enabled? [(usr/authority-admins-organization-id user)])
                          unauthorized))]}
  [{user :user}]
  (let [org-id (usr/authority-admins-organization-id user)
        {:keys [earliest-allowed-archiving-date]} (org/get-organization org-id)]
    (if (>= date earliest-allowed-archiving-date)
      (do (org/update-organization org-id {$set {:permanent-archive-in-use-since date}})
          (ok))
      (fail :error.invalid-date))))

(defcommand set-default-digitalization-location
  {:parameters       [x y]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial coordinate-parameters :x :y)]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:default-digitalization-location.x x
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
  {:parameters       [emails]
   :description      "When application is submitted and the applicant wishes that the organization hears neighbours,
                 send notification to these email addresses"
   :permissions      [{:required [:organization/admin]}]
   :input-validators email-list-validators}
  [{user :user}]
  (let [addresses       (when-not (ss/blank? emails) (split-emails emails))
        organization-id (usr/authority-admins-organization-id user)]
    (org/update-organization organization-id {$set {:notifications.neighbor-order-emails addresses}})
    (ok)))

(defcommand set-organization-submit-notification-email
  {:parameters       [emails]
   :description      "When application is submitted, send notification to these email addresses"
   :permissions      [{:required [:organization/admin]}]
   :input-validators email-list-validators}
  [{user :user}]
  (let [addresses       (when-not (ss/blank? emails) (split-emails emails))
        organization-id (usr/authority-admins-organization-id user)]
    (org/update-organization organization-id {$set {:notifications.submit-notification-emails addresses}})
    (ok)))

(defcommand set-organization-inforequest-notification-email
  {:parameters       [emails]
   :description      "When inforequest is received to organization, send notification to these email addresses"
   :permissions      [{:required [:organization/admin]}]
   :input-validators email-list-validators}
  [{user :user}]
  (let [addresses       (when-not (ss/blank? emails) (split-emails emails))
        organization-id (usr/authority-admins-organization-id user)]
    (org/update-organization organization-id {$set {:notifications.inforequest-notification-emails addresses}})
    (ok)))

(defcommand set-organization-funding-enabled-notification-email
  {:parameters       [emails]
   :description      "When ARA funding is enabled to application, send notification to these email addresses"
   :permissions      [{:required [:organization/admin]}]
   :input-validators email-list-validators}
  [{user :user}]
  (let [addresses       (when-not (ss/blank? emails) (split-emails emails))
        organization-id (usr/authority-admins-organization-id user)]
    (org/update-organization organization-id {$set {:notifications.funding-notification-emails addresses}})
    (ok)))

(defcommand set-organization-default-reservation-location
  {:parameters       [location]
   :description      "When reservation is made, use this location as default value"
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial action/string-parameters [:location])]
   :feature          :ajanvaraus}
  [{user :user}]
  (let [organization-id (usr/authority-admins-organization-id user)]
    (org/update-organization organization-id {$set {:reservations.default-location location}})
    (ok)))

(defcommand set-organization-state-change-endpoint
  {:parameters [url headers authType]
   :optional-parameters [basicCreds]
   :description "Set REST endpoint configurations for organization state change messages"
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial action/string-parameters [:url])]}
  [{user :user}]
  (let [organization-id (usr/authority-admins-organization-id user)]
    (org/set-state-change-endpoint organization-id (ss/trim url) headers authType basicCreds)))

(defquery krysp-config
  {:permissions [{:required [:organization/admin]}]}
  [{user :user}]
  (let [organization-id (usr/authority-admins-organization-id user)]
    (if-let [organization (org/get-organization organization-id)]
      (let [permit-types (->> (:scope organization)
                              (map (comp keyword :permitType))
                              (filter #(get krysp-xml/supported-krysp-versions-by-permit-type %)))
            krysp-keys   (conj (vec permit-types) :osoitteet)
            empty-confs  (zipmap krysp-keys (repeat {}))]
        (ok :krysp (merge empty-confs (:krysp organization))))
      (fail :error.unknown-organization))))

(defcommand set-krysp-endpoint
  {:parameters       [url username password permitType version]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(fn [{{permit-type :permitType} :data}]
                        (when-not (or
                                    (= "osoitteet" permit-type)
                                    (get krysp-xml/supported-krysp-versions-by-permit-type (keyword permit-type)))
                          (fail :error.missing-parameters :parameters [:permitType])))
                      (partial validate-optional-url :url)
                      (partial action/string-parameters [:url :username :password :permitType :version])]}
  [{data :data user :user}]
  (let [url             (-> data :url ss/trim)
        organization-id (usr/authority-admins-organization-id user)
        krysp-config    (org/get-krysp-wfs {:_id organization-id} permitType)
        password        (if (s/blank? password) (second (:credentials krysp-config)) password)]
    (if (or (s/blank? url) (wfs/wfs-is-alive? url username password))
      (org/set-krysp-endpoint organization-id url username password permitType version krysp-config)
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
                         (fn [{:keys [data]}]
                           (when (and (ss/not-blank? (:path data))
                                      (sc/check (ssu/get org/KryspHttpConf :path) (:path data)))
                             (fail :error.illegal-value:schema-validation :data :path)))
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
  [{data :data user :user}]
  (let [url     (-> data :url ss/trim)
        updates (->> (when username
                       (org/encode-credentials username password))
                     (merge {:url url} (select-keys data [:headers :auth-type]))
                     (util/strip-nils)
                     org/krysp-http-conf-validator
                     (map (fn [[k v]] [(str "krysp." permitType ".http." (name k)) v]))
                     (into {}))]
    (mongo/update-by-id :organizations organization {$set updates})))

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
  {:parameters       [kopiolaitosEmail kopiolaitosOrdererAddress kopiolaitosOrdererPhone kopiolaitosOrdererEmail]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(fn [{{email-str :kopiolaitosEmail} :data :as command}]
                        (let [emails (util/separate-emails email-str)]
                          ;; action/email-validator returns nil if email was valid
                          (when (some #(email-validator :email {:data {:email %}}) emails)
                            (fail :error.set-kopiolaitos-info.invalid-email))))]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user)
                           {$set {:kopiolaitos-email           kopiolaitosEmail
                                  :kopiolaitos-orderer-address kopiolaitosOrdererAddress
                                  :kopiolaitos-orderer-phone   kopiolaitosOrdererPhone
                                  :kopiolaitos-orderer-email   kopiolaitosOrdererEmail}})
  (ok))

(defquery kopiolaitos-config
  {:permissions [{:required [:organization/admin]}]}
  [{user :user}]
  (let [organization-id (usr/authority-admins-organization-id user)]
    (if-let [organization (org/get-organization organization-id)]
      (ok
        :kopiolaitos-email (:kopiolaitos-email organization)
        :kopiolaitos-orderer-address (:kopiolaitos-orderer-address organization)
        :kopiolaitos-orderer-phone (:kopiolaitos-orderer-phone organization)
        :kopiolaitos-orderer-email (:kopiolaitos-orderer-email organization))
      (fail :error.unknown-organization))))

(defquery get-organization-names
  {:description "Returns an organization id -> name map. (Used by TOJ.)"
   :user-roles  #{:anonymous}}
  [_]
  (ok :names (into {} (for [{:keys [id name]} (org/get-organizations {} {:name 1})]
                        [id name]))))

(defquery vendor-backend-redirect-config
  {:permissions [{:required [:organization/admin]}]}
  [{user :user}]
  (let [organization-id (usr/authority-admins-organization-id user)]
    (if-let [organization (org/get-organization organization-id)]
      (ok (:vendor-backend-redirect organization))
      (fail :error.unknown-organization))))

(defcommand save-vendor-backend-redirect-config
  {:parameters       [key val]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(fn [{{key :key} :data}]
                        (when-not (contains? #{:vendorBackendUrlForBackendId :vendorBackendUrlForLpId} (keyword key))
                          (fail :error.illegal-key)))
                      (partial validate-optional-url :val)]}
  [{user :user}]
  (let [key    (csk/->kebab-case key)
        org-id (usr/authority-admins-organization-id user)]
    (org/update-organization org-id {$set {(str "vendor-backend-redirect." key) (ss/trim val)}})))

(defcommand update-organization-name
  {:description      "Updates organization name for different languages. 'name' should be a map with lang-id as key and name as value."
   :parameters       [org-id name]
   :permissions [{:required [:organization/update-name]}]
   :pre-checks       [(fn [{{org-id :org-id} :data user :user}]
                        (when-not (or (usr/admin? user)
                                      (= org-id (usr/authority-admins-organization-id user)))
                          (fail :error.unauthorized)))]
   :input-validators [(partial partial-localization-parameters [:name])
                      (fn [{{name :name} :data}]
                        (when (some ss/blank? (vals name))
                          (fail :error.empty-organization-name)))]}
  [_]
  (->> (util/map-keys (fn->> clojure.core/name (str "name.")) name)
       (hash-map $set)
       (org/update-organization org-id)))

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
  {:parameters       [tags]
   :input-validators [(partial action/vector-parameter-of :tags map?)]
   :permissions      [{:required [:organization/admin]}]}
  [{user :user}]
  (let [org-id            (usr/authority-admins-organization-id user)
        old-tag-ids       (set (map :id (:tags (org/get-organization org-id))))
        new-tag-ids       (set (map :id tags))
        removed-ids       (set/difference old-tag-ids new-tag-ids)
        tags-with-ids     (org/create-tag-ids tags)
        validation-errors (seq (remove nil? (map (partial sc/check org/Tag) tags-with-ids)))]
    (when validation-errors (fail! :error.missing-parameters))

    (when (seq removed-ids)
      (mongo/update-by-query :applications {:tags {$in removed-ids} :organization org-id} {$pull {:tags {$in removed-ids}}}))
    (org/update-organization org-id {$set {:tags tags-with-ids}})))

(defquery remove-tag-ok
  {:parameters       [tagId]
   :input-validators [(partial non-blank-parameters [:tagId])]
   :permissions      [{:required [:organization/admin]}]}
  [{user :user}]
  (let [org-id (usr/authority-admins-organization-id user)]
    (when-let [tag-applications (seq (mongo/select
                                       :applications
                                       {:tags tagId :organization org-id}
                                       [:_id]))]
      (fail :warning.tags.removing-from-applications :applications tag-applications))))

(defquery get-organization-tags
  {:permissions [{:required [:organization/tags]}]}
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
  [{{:keys [orgAuthz] :as user} :user}]
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
  {:permissions [{:required [:organization/admin]}]}
  [{user :user {[{:keys [tempfile filename size]}] :files created :created} :data :as action}]
  (let [org-id       (usr/authority-admins-organization-id user)
        filename     (mime/sanitize-filename filename)
        content-type (mime/mime-type filename)
        file-info    {:filename     filename
                      :contentType  content-type
                      :size         size
                      :organization org-id
                      :created      created}
        tmpdir       (fs/temp-dir "area")]
    (try+
      (when-not (= (:contentType file-info) "application/zip")
        (fail! :error.illegal-shapefile))
      (let [areas (org/parse-shapefile-to-organization-areas org-id tempfile tmpdir)]
        (->> (assoc file-info :areas areas :ok true)
             (resp/json)
             (resp/content-type "application/json")
             (resp/status 200)))
      (catch [:sade.core/type :sade.core/fail] {:keys [text] :as all}
        (error "Failed to parse shapefile" text)
        (->> {:ok false :text text}
             (resp/json)
             (resp/status 200)))
      (catch Throwable t
        (error "Failed to parse shapefile" t)
        (->> {:ok false :text (.getMessage t)}
             (resp/json)
             (resp/status 200)))
      (finally
        (when tmpdir
          (fs/delete-dir tmpdir))))))

(defcommand pseudo-organization-area
  {:description "Pseudo command for differentiating authority admin
  impersonation regarding organization-area."
   :permissions [{:required [:organization/admin]}]}
  [_])


(defquery get-map-layers-data
  {:description "Organization server and layer details."
   :permissions [{:required [:organization/admin]}]}
  [{user :user}]
  (let [org-id (usr/authority-admins-organization-id user)
        {:keys [server layers]} (org/organization-map-layers-data org-id)]
    (ok :server (select-keys server [:url :username]), :layers layers)))

(defcommand update-map-server-details
  {:parameters       [url username password]
   :input-validators [(partial validate-optional-url :url)]
   :permissions      [{:required [:organization/admin]}]}
  [{user :user}]
  (org/update-organization-map-server (usr/authority-admins-organization-id user)
                                      (ss/trim url) username password)
  (ok))

(defcommand update-user-layers
  {:parameters       [layers]
   :input-validators [(partial action/vector-parameter-of :layers map?)]
   :permissions      [{:required [:organization/admin]}]}
  [{user :user}]
  (let [selected-layers   (remove (comp ss/blank? :id) layers)
        validation-errors (remove nil? (map (partial sc/check org/Layer) selected-layers))]
    (if (zero? (count validation-errors))
      (do
        (org/update-organization (usr/authority-admins-organization-id user)
                                 {$set {:map-layers.layers selected-layers}})
        (ok))
      (fail :error.missing-parameters))))

(defcommand update-suti-server-details
  {:parameters       [url username password]
   :input-validators [(partial validate-optional-url :url)]
   :permissions      [{:required [:organization/admin]}]}
  [{user :user}]
  (org/update-organization-suti-server (usr/authority-admins-organization-id user)
                                       (ss/trim url) username password)
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
   :parameters       [flag]
   :input-validators [(partial action/boolean-parameters [:flag])]
   :permissions      [{:required [:organization/admin]}]}
  [{user :user}]
  (org/toggle-group-enabled (usr/authority-admins-organization-id user) :section flag))

(defcommand section-toggle-operation
  {:description      "Toggles operation either requiring section or not."
   :parameters       [operationId flag]
   :input-validators [(partial action/non-blank-parameters [:operationId])
                      (partial action/boolean-parameters [:flag])]
   :permissions      [{:required [:organization/admin]}]}
  [{user :user}]
  (org/toggle-group-operation (usr/authority-admins-organization user)
                              :section
                              (ss/trim operationId)
                              flag))

(defn- validate-handler-role-in-organization
  "Pre-check that fails if roleId is defined but not found in handler-roles of authority admin's organization."
  [{{role-id :roleId} :data user :user user-orgs :user-organizations}]
  (when-let [org (-> (usr/authority-admins-organization-id user)
                     (util/find-by-id user-orgs))]
    (when (and role-id (not (util/find-by-id role-id (:handler-roles org))))
      (fail :error.unknown-handler))))

(defn- validate-handler-role-not-general
  "Pre-check that fails if roleId is defined and found in handler-roles of authority admin's organization and is set as general."
  [{{role-id :roleId} :data user :user user-orgs :user-organizations}]
  (when-let [org (-> (usr/authority-admins-organization-id user)
                     (util/find-by-id user-orgs))]
    (when (and role-id (:general (util/find-by-id role-id (:handler-roles org))))
      (fail :error.illegal-handler-role))))

(defcommand upsert-handler-role
  {:description         "Create and modify organization handler role"
   :parameters          [name]
   :optional-parameters [roleId]
   :pre-checks          [validate-handler-role-in-organization]
   :input-validators    [(partial supported-localization-parameters [:name])]
   :permissions         [{:required [:organization/admin]}]}
  [{user :user user-orgs :user-organizations}]
  (let [handler-role (org/create-handler-role roleId name)]
    (if (sc/check org/HandlerRole handler-role)
      (fail :error.missing-parameters)
      (do (-> (usr/authority-admins-organization-id user)
              (util/find-by-id user-orgs)
              (org/upsert-handler-role! handler-role))
          (ok :id (:id handler-role))))))

(defcommand toggle-handler-role
  {:description      "Enable/disable organization handler role."
   :parameters       [roleId enabled]
   :pre-checks       [validate-handler-role-in-organization
                      validate-handler-role-not-general]
   :input-validators [(partial non-blank-parameters [:roleId])
                      (partial boolean-parameters [:enabled])]
   :permissions      [{:required [:organization/admin]}]}
  [{user :user}]
  (-> (usr/authority-admins-organization-id user)
      (org/toggle-handler-role! roleId enabled)))

(defcommand upsert-assignment-trigger
  {:description         "Set and update automated assignment trigger"
   :parameters          [targets]
   :optional-parameters [triggerId description handler]
   :input-validators    [(partial vector-parameters-with-at-least-n-non-blank-items 1 [:targets])
                         (partial non-blank-parameters [:description])]
   :permissions         [{:required [:organization/admin]}]}
  [{user :user user-orgs :user-organizations}]
  (let [trigger      (org/create-trigger triggerId targets handler description)
        organization (util/find-by-id (usr/authority-admins-organization-id user) user-orgs)
        update?      (some? triggerId)]
    (if (sc/check org/AssignmentTrigger trigger)
      (fail :error.validator)
      (do (if update?
            (org/update-assignment-trigger organization trigger triggerId)
            (org/add-assignment-trigger organization trigger))
          (ok :trigger trigger)))))

(defcommand remove-assignment-trigger
  {:description      "Removes task trigger"
   :parameters       [triggerId]
   :input-validators [(partial non-blank-parameters [:triggerId])]
   :permissions      [{:required [:organization/admin]}]}
  [{user :user user-orgs :user-organizations}]
  (-> (usr/authority-admins-organization-id user)
      (util/find-by-id user-orgs)
      (org/remove-assignment-trigger triggerId)))

(defcommand update-docstore-info
  {:description      "Updates organization's document store information"
   :parameters       [org-id docStoreInUse docTerminalInUse documentPrice organizationDescription]
   :user-roles       #{:admin}
   :input-validators [(partial boolean-parameters [:docStoreInUse :docTerminalInUse])
                      (partial parameters-matching-schema [:documentPrice] sssc/Nat :error.illegal-number)
                      (partial localization-parameters [:organizationDescription])]}
  [_]
  (mongo/update-by-query :organizations
    {:_id org-id}
    {$set {:docstore-info.docStoreInUse           docStoreInUse
           :docstore-info.docTerminalInUse        docTerminalInUse
           :docstore-info.documentPrice           documentPrice
           :docstore-info.organizationDescription organizationDescription}})
  (ok))

(defquery document-request-info
  {:description "Obtains the organization's document request info."
   :permissions [{:required [:organization/admin]}]}
  [{user :user}]
  (->> user
       usr/authority-admins-organization-id
       org/document-request-info
       (ok :documentRequest)))

(defcommand set-document-request-info
  {:description      "Updates organization's document request info. Docucment requests are made from document store."
   :parameters       [enabled email instructions]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial boolean-parameters [:enabled])
                      (partial parameters-matching-schema [:email] ssc/OptionalEmail :error.email)
                      (partial localization-parameters [:instructions])]}
  [{user :user}]
  (-> user
      usr/authority-admins-organization-id
      (org/set-document-request-info enabled email instructions))
  (ok))

(defquery docterminal-attachment-types
  {:description "Returns the allowed docterminal attachment types in a structure
                 that can be easily displayed in the client"
   :permissions [{:required [:organization/admin]}]}
  [{user :user}]
  (->> user
       usr/authority-admins-organization-id
       org/allowed-docterminal-attachment-types
       (ok :attachment-types)))

(defcommand set-docterminal-attachment-type
  {:description      "Allows or disallows showing the given attachment type in
                 the archive document terminal application."
   :parameters     [attachmentType enabled]
   :pre-checks     [org/check-docterminal-enabled]
   :input-validators [(partial parameters-matching-schema [:attachmentType]
                               (sc/cond-pre (sc/enum "all")
                                            org/DocTerminalAttachmentType))
                      (partial boolean-parameters [:enabled])]
   :permissions      [{:required [:organization/admin]}]}
  [{user :user}]
  (-> user
      usr/authority-admins-organization-id
      (org/set-allowed-docterminal-attachment-type attachmentType enabled)))

(defquery docterminal-enabled
  {:pre-checks  [org/check-docterminal-enabled]
   :permissions [{:required [:organization/admin]}]}
  [_])

(defquery docstore-enabled
  {:pre-checks  [org/check-docstore-enabled]
   :permissions [{:required [:organization/admin]}]}
  [_])
