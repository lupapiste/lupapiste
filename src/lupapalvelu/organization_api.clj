(ns lupapalvelu.organization-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf fatal]]
            [clojure.core.memoize :as memo]
            [clojure.set :as set]
            [clojure.string :as s]
            [clojure.walk :refer [keywordize-keys]]
            [schema.core :as sc]
            [monger.operators :refer :all]
            [noir.core :refer [defpage]]
            [noir.response :as resp]
            [noir.request :as request]
            [camel-snake-kebab.core :as csk]
            [me.raynes.fs :as fs]
            [slingshot.slingshot :refer [try+]]
            [sade.core :refer [ok fail fail! now unauthorized]]
            [sade.env :as env]
            [sade.municipality :as muni]
            [sade.property :as p]
            [sade.strings :as ss]
            [sade.util :refer [fn->>] :as util]
            [sade.validators :as v]
            [lupapalvelu.action :refer [defquery defcommand defraw non-blank-parameters vector-parameters boolean-parameters number-parameters email-validator] :as action]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.states :as states]
            [lupapalvelu.wfs :as wfs]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as o]
            [lupapalvelu.logging :as logging]))
;;
;; local api
;;

(defn- municipalities-with-organization []
  (let [organizations (o/get-organizations {} [:scope :krysp])]
    {:all (distinct
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
             operation-names (keys (filter
                                     (fn [[name op]]
                                       (and
                                         (= %2 (:permit-type op))
                                         (selected-operations name)))
                                     operations/operations))]
         (if operation-names (assoc %1 %2 operation-names) %1))
       %1)
    {}
    (map :permitType scope)))

;; Validators
(defn validate-optional-url [param command]
  (let [url (ss/trim (get-in command [:data param]))]
    (when-not (ss/blank? url)
      (util/validate-url url))))

;;
;; Actions
;;

(defquery organization-by-user
  {:description "Lists organization details."
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [organization (o/get-organization (user/authority-admins-organization-id user))
        ops-with-attachments (organization-operations-with-attachments organization)
        selected-operations-with-permit-type (selected-operations-with-permit-types organization)
        allowed-roles (o/allowed-roles-in-organization organization)]
    (ok :organization (-> organization
                        (assoc :operationsAttachments ops-with-attachments
                               :selectedOperations selected-operations-with-permit-type
                               :allowedRoles allowed-roles)
                        (dissoc :operations-attachments :selected-operations))
        :attachmentTypes (organization-attachments organization))))

(defquery user-organizations-for-permit-type
  {:parameters [permitType]
   :user-roles #{:authority}
   :input-validators [permit/permit-type-validator]}
  [{user :user}]
  (ok :organizations (o/get-organizations {:_id {$in (user/organization-ids-by-roles user #{:authority})}
                                           :scope {$elemMatch {:permitType permitType}}})))

(defcommand update-organization
  {:description "Update organization details."
   :parameters [permitType municipality
                inforequestEnabled applicationEnabled openInforequestEnabled openInforequestEmail
                opening]
   :input-validators [permit/permit-type-validator]
   :user-roles #{:admin}}
  [_]
  (mongo/update-by-query :organizations
      {:scope {$elemMatch {:permitType permitType :municipality municipality}}}
      {$set {:scope.$.inforequest-enabled inforequestEnabled
             :scope.$.new-application-enabled applicationEnabled
             :scope.$.open-inforequest openInforequestEnabled
             :scope.$.open-inforequest-email openInforequestEmail
             :scope.$.opening (when (number? opening) opening)}})
  (ok))

(defcommand add-scope
  {:description "Admin can add new scopes for organization"
   :parameters [organization permitType municipality
                inforequestEnabled applicationEnabled openInforequestEnabled openInforequestEmail
                opening]
   :input-validators [permit/permit-type-validator
                      (fn [{{:keys [municipality]} :data}]
                        (when-not (contains? muni/municipality-codes municipality)
                          (fail :error.invalid-municipality)))]
   :user-roles #{:admin}}
  (let [scope-count (mongo/count :organizations {:scope {$elemMatch {:permitType permitType :municipality municipality}}})]
    (if (zero? scope-count)
      (do
        (o/update-organization
          organization
          {$push {:scope
                  {:municipality            municipality
                   :permitType              permitType
                   :inforequest-enabled     inforequestEnabled
                   :new-application-enabled applicationEnabled
                   :open-inforequest        openInforequestEnabled
                   :open-inforequest-email  openInforequestEmail
                   :opening                 (when (number? opening) opening)}}})
        (ok))
      (fail :error.organization.duplicate-scope))))

(defcommand add-organization-link
  {:description "Adds link to organization."
   :parameters [url nameFi nameSv]
   :user-roles #{:authorityAdmin}
   :input-validators [(partial non-blank-parameters [:url :nameFi :nameSv])
                      (partial validate-optional-url :url)]}
  [{user :user}]
  (o/update-organization (user/authority-admins-organization-id user) {$push {:links {:name {:fi nameFi :sv nameSv} :url (ss/trim url)}}})
  (ok))

(defcommand update-organization-link
  {:description "Updates organization link."
   :parameters [url nameFi nameSv index]
   :user-roles #{:authorityAdmin}
   :input-validators [(partial non-blank-parameters [:url :nameFi :nameSv])
                      (partial validate-optional-url :url)
                      (partial number-parameters [:index])]}
  [{user :user}]
  (o/update-organization (user/authority-admins-organization-id user) {$set {(str "links." index) {:name {:fi nameFi :sv nameSv} :url (ss/trim url)}}})
  (ok))

(defcommand remove-organization-link
  {:description "Removes organization link."
   :parameters [url nameFi nameSv]
   :input-validators [(partial non-blank-parameters [:url :nameFi :nameSv])]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (o/update-organization (user/authority-admins-organization-id user) {$pull {:links {:name {:fi nameFi :sv nameSv} :url url}}})
  (ok))

(defquery organizations
  {:user-roles #{:admin}}
  [_]
  (ok :organizations (o/get-organizations)))

(defquery allowed-autologin-ips-for-organization
  {:parameters [org-id]
   :input-validators [(partial non-blank-parameters [:org-id])]
   :user-roles #{:admin}}
  [_]
  (ok :ips (o/get-autologin-ips-for-organization org-id)))

(defcommand update-allowed-autologin-ips
  {:parameters [org-id ips]
   :input-validators [(partial non-blank-parameters [:org-id])
                      (comp o/valid-ip-addresses :ips :data)]
   :user-roles #{:admin}}
  [_]
  (->> (o/autogin-ip-mongo-changes ips)
       (o/update-organization org-id))
  (ok))

(defquery organization-by-id
  {:parameters [organizationId]
   :input-validators [(partial non-blank-parameters [:organizationId])]
   :user-roles #{:admin}}
  [_]
  (ok :data (o/get-organization organizationId)))

(defquery permit-types
  {:user-roles #{:admin}}
  [_]
  (ok :permitTypes (keys (permit/permit-types))))

(defquery municipalities-with-organization
  {:description "Returns a list of municipality IDs that are affiliated with Lupapiste."
   :user-roles #{:applicant :authority :admin}}
  [_]
  (let [munis (municipalities-with-organization)]
    (ok
      :municipalities (:all munis)
      :municipalitiesWithBackendInUse (:with-backend munis))))

(defquery municipalities
  {:description "Returns a list of all municipality IDs. For admin use."
   :user-roles #{:admin}}
  (ok :municipalities muni/municipality-codes))

(defquery all-operations-for-organization
  {:description "Returns operations that match the permit types of the organization whose id is given as parameter"
   :parameters [organizationId]
   :user-roles #{:authorityAdmin}
   :input-validators [(partial non-blank-parameters [:organizationId])]}
  (when-let [org (o/get-organization organizationId)]
    (ok :operations (operations/organization-operations org))))

(defquery selected-operations-for-municipality
  {:description "Returns selected operations of all the organizations who have a scope with the given municipality.
                 If a \"permitType\" parameter is given, returns selected operations for only that organization (the municipality + permitType combination)."
   :parameters [:municipality]
   :user-roles #{:applicant :authority :authorityAdmin}
   :input-validators [(partial non-blank-parameters [:municipality])]}
  [{{:keys [municipality permitType]} :data}]
  (when-let [organizations (o/resolve-organizations municipality permitType)]
    (ok :operations (operations/selected-operations-for-organizations organizations))))

(defquery addable-operations
  {:description "returns operations addable for the application whose id is given as parameter"
   :parameters  [:id]
   :user-roles #{:applicant :authority}
   :states      states/pre-sent-application-states}
  [{{:keys [organization permitType]} :application}]
  (when-let [org (o/get-organization organization)]
    (let [selected-operations (map keyword (:selected-operations org))]
      (ok :operations (operations/addable-operations selected-operations permitType)))))

(defquery organization-details
  {:description "Resolves organization based on municipality and selected operation."
   :parameters [municipality operation]
   :input-validators [(partial non-blank-parameters [:municipality :operation])]
   :user-roles #{:applicant :authority}}
  [_]
  (let [permit-type (:permit-type ((keyword operation) operations/operations))]
    (if-let [organization (o/resolve-organization municipality permit-type)]
      (let [scope (o/resolve-organization-scope municipality permit-type organization)]
        (ok
          :inforequests-disabled (not (:inforequest-enabled scope))
          :new-applications-disabled (not (:new-application-enabled scope))
          :links (:links organization)
          :attachmentsForOp (-> organization :operations-attachments ((keyword operation)))))
      (fail :municipalityNotSupported))))

(defcommand set-organization-selected-operations
  {:parameters [operations]
   :user-roles #{:authorityAdmin}
   :input-validators  [(partial vector-parameters [:operations])
                       (fn [{{:keys [operations]} :data}]
                         (when-not (every? (->> operations/operations keys (map name) set) operations)
                           (fail :error.unknown-operation)))]}
  [{user :user}]
  (o/update-organization (user/authority-admins-organization-id user) {$set {:selected-operations operations}})
  (ok))

(defcommand organization-operations-attachments
  {:parameters [operation attachments]
   :user-roles #{:authorityAdmin}
   :input-validators [(partial non-blank-parameters [:operation])
                      (partial vector-parameters [:attachments])
                      (fn [{{:keys [operation attachments]} :data, user :user}]
                        (let [organization (o/get-organization (user/authority-admins-organization-id user))
                              selected-operations (set (:selected-operations organization))
                              allowed-types (att-type/get-attachment-types-for-operation operation)
                              attachment-types (map (fn [[group id]] {:type-group group :type-id id}) attachments)]
                          (cond
                            (not (selected-operations operation)) (do
                                                                    (error "Unknown operation: " (logging/sanitize 100 operation))
                                                                    (fail :error.unknown-operation))
                            (not-every? (partial att-type/contains? allowed-types) attachment-types) (fail :error.unknown-attachment-type))))]}
  [{user :user}]
  (o/update-organization (user/authority-admins-organization-id user) {$set {(str "operations-attachments." operation) attachments}})
  (ok))

(defcommand set-organization-app-required-fields-filling-obligatory
  {:parameters [enabled]
   :user-roles #{:authorityAdmin}
   :input-validators  [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (o/update-organization (user/authority-admins-organization-id user) {$set {:app-required-fields-filling-obligatory enabled}})
  (ok))

(defcommand set-organization-validate-verdict-given-date
  {:parameters [enabled]
   :user-roles #{:authorityAdmin}
   :input-validators  [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (o/update-organization (user/authority-admins-organization-id user) {$set {:validate-verdict-given-date enabled}})
  (ok))

(defcommand set-organization-calendars-enabled
  {:parameters [enabled organizationId]
   :user-roles #{:admin}
   :input-validators  [(partial non-blank-parameters [:organizationId])
                       (partial boolean-parameters [:enabled])]
   :feature :ajanvaraus}
  [{user :user}]
  (o/update-organization organizationId {$set {:calendars-enabled enabled}})
  (ok))

(defcommand set-organization-permanent-archive-enabled
  {:parameters [enabled organizationId]
   :user-roles #{:admin}
   :input-validators  [(partial non-blank-parameters [:organizationId])
                       (partial boolean-parameters [:enabled])]}
  [{user :user}]
  (o/update-organization organizationId {$set {:permanent-archive-enabled enabled}})
  (ok))

(defcommand set-organization-permanent-archive-start-date
  {:parameters [date]
   :user-roles #{:authorityAdmin}
   :input-validators  [(partial number-parameters [:date])]
   :pre-checks [(fn [{:keys [user]} _]
                  (when-not (o/some-organization-has-archive-enabled? [(user/authority-admins-organization-id user)])
                    unauthorized))]}
  [{user :user}]
  (when (pos? date)
    (o/update-organization (user/authority-admins-organization-id user) {$set {:permanent-archive-in-use-since date}})
    (ok)))

(defn split-emails [emails] (ss/split emails #"[\s,;]+"))

(def email-list-validators [(partial action/string-parameters [:emails])
                            (fn [{{emails :emails} :data}]
                              (let [splitted (split-emails emails)]
                                (when (and (not (ss/blank? emails)) (some (complement v/valid-email?) splitted))
                                  (fail :error.email))))])

(defcommand set-organization-neighbor-order-email
  {:parameters [emails]
   :description "When application is submitted and the applicant wishes that the organization hears neighbours,
                 send notification to these email addresses"
   :user-roles #{:authorityAdmin}
   :input-validators email-list-validators}
  [{user :user}]
  (let [addresses (when-not (ss/blank? emails) (split-emails emails))
        organization-id (user/authority-admins-organization-id user)]
    (o/update-organization organization-id {$set {:notifications.neighbor-order-emails addresses}})
    (ok)))

(defcommand set-organization-submit-notification-email
  {:parameters [emails]
   :description "When application is submitted, send notification to these email addresses"
   :user-roles #{:authorityAdmin}
   :input-validators email-list-validators}
  [{user :user}]
  (let [addresses (when-not (ss/blank? emails) (split-emails emails))
        organization-id (user/authority-admins-organization-id user)]
    (o/update-organization organization-id {$set {:notifications.submit-notification-emails addresses}})
    (ok)))

(defcommand set-organization-inforequest-notification-email
  {:parameters [emails]
   :description "When inforequest is received to organization, send notification to these email addresses"
   :user-roles #{:authorityAdmin}
   :input-validators email-list-validators}
  [{user :user}]
  (let [addresses (when-not (ss/blank? emails) (split-emails emails))
        organization-id (user/authority-admins-organization-id user)]
    (o/update-organization organization-id {$set {:notifications.inforequest-notification-emails addresses}})
    (ok)))

(defcommand set-organization-default-reservation-location
  {:parameters [location]
   :description "When reservation is made, use this location as default value"
   :user-roles #{:authorityAdmin}
   :input-validators [(partial action/string-parameters [:location])]
   :feature :ajanvaraus}
  [{user :user}]
  (let [organization-id (user/authority-admins-organization-id user)]
    (o/update-organization organization-id {$set {:reservations.default-location location}})
    (ok)))

(defquery krysp-config
  {:user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [organization-id (user/authority-admins-organization-id user)]
    (if-let [organization (o/get-organization organization-id)]
      (let [permit-types (mapv (comp keyword :permitType) (:scope organization))
            krysp-keys   (conj permit-types :osoitteet)
            empty-confs  (zipmap krysp-keys (repeat {}))]
        (ok :krysp (merge empty-confs (:krysp organization))))
      (fail :error.unknown-organization))))

(defcommand set-krysp-endpoint
  {:parameters [:url username password permitType version]
   :user-roles #{:authorityAdmin}
   :input-validators [(fn [{{permit-type :permitType} :data}]
                        (when-not (or
                                    (= "osoitteet" permit-type)
                                    (permit/valid-permit-type? permit-type))
                          (fail :error.missing-parameters :parameters [:permitType])))
                      (partial validate-optional-url :url)]}
  [{data :data user :user}]
  (let [url             (-> data :url ss/trim)
        organization-id (user/authority-admins-organization-id user)
        krysp-config    (o/get-krysp-wfs {:_id organization-id} permitType)
        password        (if (s/blank? password) (second (:credentials krysp-config)) password)]
    (if (or (s/blank? url) (wfs/wfs-is-alive? url username password))
      (o/set-krysp-endpoint organization-id url username password permitType version)
      (fail :auth-admin.legacyNotResponding))))

(defcommand set-kopiolaitos-info
  {:parameters [kopiolaitosEmail kopiolaitosOrdererAddress kopiolaitosOrdererPhone kopiolaitosOrdererEmail]
   :user-roles #{:authorityAdmin}
   :input-validators [(fn [{{email-str :kopiolaitosEmail} :data :as command}]
                        (let [emails (util/separate-emails email-str)]
                          ;; action/email-validator returns nil if email was valid
                          (when (some #(email-validator :email {:data {:email %}}) emails)
                            (fail :error.set-kopiolaitos-info.invalid-email))))]}
  [{user :user}]
  (o/update-organization (user/authority-admins-organization-id user)
    {$set {:kopiolaitos-email kopiolaitosEmail
           :kopiolaitos-orderer-address kopiolaitosOrdererAddress
           :kopiolaitos-orderer-phone kopiolaitosOrdererPhone
           :kopiolaitos-orderer-email kopiolaitosOrdererEmail}})
  (ok))

(defquery kopiolaitos-config
  {:user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [organization-id (user/authority-admins-organization-id user)]
    (if-let [organization (o/get-organization organization-id)]
      (ok
        :kopiolaitos-email (:kopiolaitos-email organization)
        :kopiolaitos-orderer-address (:kopiolaitos-orderer-address organization)
        :kopiolaitos-orderer-phone (:kopiolaitos-orderer-phone organization)
        :kopiolaitos-orderer-email (:kopiolaitos-orderer-email organization))
      (fail :error.unknown-organization))))

(defquery get-organization-names
  {:description "Returns an organization id -> name map. (Used by TOJ.)"
   :user-roles #{:anonymous}}
  [_]
  (ok :names (into {} (for [{:keys [id name]} (o/get-organizations {} {:name 1})]
                        [id name]))))

(defquery vendor-backend-redirect-config
  {:user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [organization-id (user/authority-admins-organization-id user)]
    (if-let [organization (o/get-organization organization-id)]
      (ok (:vendor-backend-redirect organization))
      (fail :error.unknown-organization))))

(defcommand save-vendor-backend-redirect-config
  {:parameters       [key val]
   :user-roles       #{:authorityAdmin}
   :input-validators [(fn [{{key :key} :data}]
                        (when-not (contains? #{:vendorBackendUrlForBackendId :vendorBackendUrlForLpId} (keyword key))
                          (fail :error.illegal-key)))
                      (partial validate-optional-url :val)]}
  [{user :user}]
  (let [key    (csk/->kebab-case key)
        org-id (user/authority-admins-organization-id user)]
    (o/update-organization org-id {$set {(str "vendor-backend-redirect." key) (ss/trim val)}})))

(defcommand save-organization-tags
  {:parameters [tags]
   :input-validators [(partial action/vector-parameter-of :tags map?)]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [org-id (user/authority-admins-organization-id user)
        old-tag-ids (set (map :id (:tags (o/get-organization org-id))))
        new-tag-ids (set (map :id tags))
        removed-ids (set/difference old-tag-ids new-tag-ids)
        tags-with-ids (o/create-tag-ids tags)
        validation-errors (seq (remove nil? (map (partial sc/check o/Tag) tags-with-ids)))]
    (when validation-errors (fail! :error.missing-parameters))

    (when (seq removed-ids)
      (mongo/update-by-query :applications {:tags {$in removed-ids} :organization org-id} {$pull {:tags {$in removed-ids}}}))
    (o/update-organization org-id {$set {:tags tags-with-ids}})))

(defquery remove-tag-ok
  {:parameters [tagId]
   :input-validators [(partial non-blank-parameters [:tagId])]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [org-id (user/authority-admins-organization-id user)]
    (when-let [tag-applications (seq (mongo/select
                                       :applications
                                       {:tags tagId :organization org-id}
                                       [:_id]))]
      (fail :warning.tags.removing-from-applications :applications tag-applications))))

(defquery get-organization-tags
  {:user-authz-roles #{:statementGiver}
   :org-authz-roles auth/reader-org-authz-roles
   :user-roles #{:authorityAdmin :authority}}
  [{{:keys [orgAuthz] :as user} :user}]
  (if (seq orgAuthz)
    (let [organization-tags (mongo/select
                                  :organizations
                                  {:_id {$in (keys orgAuthz)} :tags {$exists true}}
                                  [:tags :name])
          result (map (juxt :id #(select-keys % [:tags :name])) organization-tags)]
      (ok :tags (into {} result)))
    (ok :tags {})))

(defquery get-organization-areas
  {:user-authz-roles #{:statementGiver}
   :org-authz-roles  auth/reader-org-authz-roles
   :user-roles       #{:authorityAdmin :authority}}
  [{{:keys [orgAuthz] :as user} :user}]
  (if (seq orgAuthz)
    (let [organization-areas (mongo/select
                               :organizations
                               {:_id {$in (keys orgAuthz)} :areas-wgs84 {$exists true}}
                               [:areas-wgs84 :name])
          organization-areas (map #(clojure.set/rename-keys % {:areas-wgs84 :areas}) organization-areas)
          result (map (juxt :id #(select-keys % [:areas :name])) organization-areas)]
      (ok :areas (into {} result)))
    (ok :areas {})))

(defraw organization-area
  {:user-roles #{:authorityAdmin}}
  [{user :user {[{:keys [tempfile filename size]}] :files created :created} :data :as action}]
  (let [org-id (user/authority-admins-organization-id user)
        filename (mime/sanitize-filename filename)
        content-type (mime/mime-type filename)
        file-info {:file-name    filename
                   :content-type content-type
                   :size         size
                   :organization org-id
                   :created      created}
        tmpdir (fs/temp-dir "area")]
    (try+
      (let [areas (o/parse-shapefile-to-organization-areas org-id tempfile tmpdir file-info)]
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

(defquery get-map-layers-data
  {:description "Organization server and layer details."
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (ok (o/organization-map-layers-data (user/authority-admins-organization-id user))))

(defcommand update-map-server-details
  {:parameters [url username password]
   :input-validators [(partial validate-optional-url :url)]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (o/update-organization-map-server (user/authority-admins-organization-id user)
                                    (ss/trim url) username password)
  (ok))

(defcommand update-user-layers
  {:parameters [layers]
   :input-validators [(partial action/vector-parameter-of :layers map?)]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [selected-layers (remove (comp ss/blank? :id) layers)
        validation-errors (remove nil? (map (partial sc/check o/Layer) selected-layers))]
    (if (zero? (count validation-errors))
      (do
        (o/update-organization (user/authority-admins-organization-id user)
          {$set {:map-layers.layers selected-layers}})
        (ok))
      (fail :error.missing-parameters))))

(defcommand update-suti-server-details
  {:parameters [url username password]
   :input-validators [(partial validate-optional-url :url)]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (o/update-organization-suti-server (user/authority-admins-organization-id user)
                                     (ss/trim url) username password)
  (ok))

(defraw waste-ads-feed
  {:description "Simple RSS feed for construction waste information."
   :parameters [fmt]
   :optional-parameters [org lang]
   :input-validators [o/valid-feed-format o/valid-org o/valid-language]
   :user-roles #{:anonymous}}
  ((memo/ttl o/waste-ads :ttl/threshold 900000)             ; 15 min
    (ss/upper-case org)
    (-> fmt ss/lower-case keyword)
    (-> (or lang :fi) ss/lower-case keyword)))
