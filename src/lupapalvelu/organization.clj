
(ns lupapalvelu.organization
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf fatal]]
            [clojure.string :as s]
            [monger.operators :refer :all]
            [lupapalvelu.core :refer [ok fail fail!]]
            [lupapalvelu.action :refer [defquery defcommand non-blank-parameters vector-parameters boolean-parameters]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.xml.krysp.reader :as krysp]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.attachment :as attachments]
            [lupapalvelu.operations :as operations]))

;;
;; local api
;;

(defn get-organizations
  ([]
    (get-organizations {} {}))
  ([query]
    (get-organizations query {}))
  ([query projection]
    (mongo/select :organizations query projection)))

(defn get-organization [id]
  {:pre [(not (s/blank? id))]}
  (mongo/by-id :organizations id))

(defn update-organization [id changes]
  {:pre [(not (s/blank? id))]}
  (mongo/update-by-id :organizations id changes))

(defn get-organization-attachments-for-operation [organization operation]
  (-> organization :operations-attachments ((-> operation :name keyword))))

(defn municipality-by-propertyId [id]
  (when (and (>= (count id) 3) (not (s/blank? id)))
    (subs id 0 3)))

(defn get-krysp-wfs
  "Returns a map containing :url and :version information for municipality's KRYSP WFS"
  ([{:keys [organization permitType] :as application}]
    (get-krysp-wfs organization permitType))
  ([organization-id permit-type]
  (let [organization (get-organization organization-id)
        krysp-config (get-in organization [:krysp (keyword permit-type)])]
    (when-not (s/blank? (:url krysp-config))
      (select-keys krysp-config [:url :version])))))

(defn- municipalities-with-organization []
  (let [organizations (get-organizations {} {:scope 1})]
    (distinct
      (for [{scopes :scope} organizations
            {municipality :municipality} scopes]
        municipality))))

(defn- organization-attachments
  "Returns a map where key is permit type, value is a list of attachment types for the permit type"
  [{scope :scope}]
  (reduce #(assoc %1 %2 (attachments/get-attachment-types-by-permit-type %2)) {} (map (comp keyword :permitType) scope)))

(defn- organization-operations-with-attachments
  "Returns a map where key is permit type, value is a list of operations for the permit type"
  [{scope :scope :as organization}]
  (reduce
    #(if-not (get-in %1 [%2])
       (assoc %1 %2 (let [operation-names (keys (filter (fn [[_ op]] (= %2 (:permit-type op))) operations/operations))
                          empty-operation-attachments (zipmap operation-names (repeat []))
                          saved-operation-attachments (select-keys (:operations-attachments organization) operation-names)]
                      (merge empty-operation-attachments saved-operation-attachments)))
       %1)
    {}
    (map :permitType scope)))

(defn- selected-operations-with-permit-types
  "Returns a map where key is permit type, value is a list of operations for the permit type"
  [{scope :scope selected-ops :selected-operations :as organization}]
  (reduce
    #(if-not (get-in %1 [%2])
       (let [selected-operations (set (map keyword selected-ops))
             operation-names (keys (filter
                                     (fn [[name op]]
                                       (and
                                         (= %2 (:permit-type op))
                                         (or (empty? selected-operations) (selected-operations name))))
                                     operations/operations))]
         (if operation-names (assoc %1 %2 operation-names) %1))
       %1)
    {}
    (map :permitType scope)))

(defn loc-organization-name [organization]
  (let [default (get-in organization [:name :fi] (str "???ORG:" (:id organization) "???"))]
    (get-in organization [:name i18n/*lang*] default)))

(defn get-organization-name [organization]
  (loc-organization-name organization))

(defn resolve-organizations
  ([municipality]
    (resolve-organizations municipality nil))
  ([municipality permit-type]
    (get-organizations {:scope {$elemMatch (merge {:municipality municipality} (when permit-type {:permitType permit-type}))}})))

(defn resolve-organization [municipality permit-type]
  {:pre  [municipality (not (s/blank? permit-type))]}
  (when-let [organizations (resolve-organizations municipality permit-type)]
    (when (> (count organizations) 1)
      (errorf "*** multiple organizations in scope of - municipality=%s, permit-type=%s -> %s" municipality permit-type (count organizations)))
    (first organizations)))

(defn resolve-organization-scope [organization municipality permit-type]
  (first (filter #(and (= municipality (:municipality %)) (= permit-type (:permitType %))) (:scope organization))))

;;
;; Actions
;;

(defquery users-in-same-organizations
  {:roles [:authority]}
  [{user :user}]
  ;; TODO toimiiko jos jompi kumpi user on kahdessa organisaatiossa?
  (ok :users (map user/summary (mongo/select :users {:organizations {$in (:organizations user)}}))))

(defquery organization-by-user
  {:description "Lists all organization users by organization."
   :roles [:authorityAdmin]}
  [{{:keys [organizations]} :user}]
  (let [orgs (get-organizations {:_id {$in organizations}})
        organization (first orgs)
        ops-with-attachments (organization-operations-with-attachments organization)
        selected-operations-with-permit-type (selected-operations-with-permit-types organization)]
    (ok :organization (-> organization
                        (assoc :operationsAttachments ops-with-attachments
                               :selectedOperations selected-operations-with-permit-type)
                        (dissoc :operations-attachments :selected-operations))
        :attachmentTypes (organization-attachments organization))))

(defcommand update-organization
  {:description "Update organization details."
   :parameters [permitType municipality inforequestEnabled applicationEnabled openInforequestEnabled openInforequestEmail]
   :roles [:admin]}
  [_]
  (mongo/update-by-query :organizations
      {:scope {$elemMatch {:permitType permitType :municipality municipality}}}
      {$set {:scope.$.inforequest-enabled inforequestEnabled
             :scope.$.new-application-enabled applicationEnabled
             :scope.$.open-inforequest openInforequestEnabled
             :scope.$.open-inforequest-email openInforequestEmail}})
  (ok))

(defcommand add-organization-link
  {:description "Adds link to organization."
   :parameters [url nameFi nameSv]
   :roles [:authorityAdmin]}
  [{{:keys [organizations]} :user}]
  (update-organization (first organizations) {$push {:links {:name {:fi nameFi :sv nameSv} :url url}}})
  (ok))

(defcommand update-organization-link
  {:description "Updates organization link."
   :parameters [url nameFi nameSv index]
   :roles [:authorityAdmin]}
  [{{:keys [organizations]} :user}]
  (update-organization (first organizations) {$set {(str "links." index) {:name {:fi nameFi :sv nameSv} :url url}}})
  (ok))

(defcommand remove-organization-link
  {:description "Removes organization link."
   :parameters [nameFi nameSv url]
   :roles [:authorityAdmin]}
  [{{:keys [organizations]} :user}]
  (update-organization (first organizations) {$pull {:links {:name {:fi nameFi :sv nameSv} :url url}}})
  (ok))

(defquery organizations
  {:roles [:admin]}
  [_]
  (ok :organizations (get-organizations)))

(defquery organization-by-id
  {:parameters [organizationId]
   :roles [:admin]}
  [_]
  (get-organization organizationId))

(defquery "municipalities-with-organization"
  {:description "Returns a list of municipality IDs that are affiliated with Lupapiste."
   :roles [:applicant :authority]}
  [_]
  (ok :municipalities (municipalities-with-organization)))

(defquery all-operations-for-organization
  {:description "Returns operations that match the permit types of the organization whose id is given as parameter"
   :parameters [organizationId]
   :roles [:authorityAdmin]
   :input-validators [(partial non-blank-parameters [:organizationId])]}
  (when-let [org (get-organization organizationId)]
    (ok :operations (operations/organization-operations org))))

(defquery selected-operations-for-municipality
  {:description "Returns selected operations of all the organizations who have a scope with the given municipality.
                 If a \"permitType\" parameter is given, returns selected operations for only that organization (the municipality + permitType combination)."
   :parameters [:municipality]
   :roles [:applicant :authority :authorityAdmin]
   :input-validators [(partial non-blank-parameters [:municipality])]}
  [{{:keys [municipality permitType]} :data}]
  (when-let [organizations (resolve-organizations municipality permitType)]
    (ok :operations (operations/selected-operations-for-organizations organizations))))

(defquery addable-operations
  {:description "returns operations addable for the application whose id is given as parameter"
   :parameters  [:id]
   :roles       [:applicant :authority]
   :states      [:draft :open :submitted :complement-needed]}
  [{{:keys [organization permitType]} :application}]
  (when-let [org (get-organization organization)]
    (let [selected-operations (map keyword (:selected-operations org))]
      (ok :operations (operations/addable-operations selected-operations permitType)))))

(defquery organization-details
  {:description "Resolves organization based on municipality and selected operation."
   :parameters [municipality operation]
   :roles [:applicant :authority]}
  [_]
  (let [permit-type (:permit-type ((keyword operation) operations/operations))]
    (if-let [organization (resolve-organization municipality permit-type)]
      (let [scope (resolve-organization-scope organization municipality permit-type)]
        (ok
         :inforequests-disabled (not (:inforequest-enabled scope))
         :new-applications-disabled (not (:new-application-enabled scope))
         :links (:links organization)
         :attachmentsForOp (-> organization :operations-attachments ((keyword operation)))))

      (fail :municipalityNotSupported :municipality municipality :permitType permit-type))))

(defcommand set-organization-selected-operations
  {:parameters [operations]
   :roles [:authorityAdmin]
   :input-validators  [(partial non-blank-parameters [:operations])
                       (partial vector-parameters [:operations])]}
  [{{:keys [organizations]} :user}]
  (update-organization (first organizations) {$set {:selected-operations operations}})
  (ok))

(defcommand organization-operations-attachments
  {:parameters [operation attachments]
   :roles [:authorityAdmin]}
  [{{:keys [organizations]} :user}]
  ; FIXME: validate operation and attachments
  (update-organization (first organizations) {$set {(str "operations-attachments." operation) attachments}})
  (ok))

(defcommand set-organization-app-required-fields-filling-obligatory
  {:parameters [isObligatory]
   :roles [:authorityAdmin]
   :input-validators  [(partial non-blank-parameters [:isObligatory])
                       (partial boolean-parameters [:isObligatory])]}
  [{{:keys [organizations]} :user}]
  (update-organization (first organizations) {$set {:app-required-fields-filling-obligatory isObligatory}})
  (ok))

(defquery krysp-config
  {:roles [:authorityAdmin]}
  [{{:keys [organizations]} :user}]
  (let [organization-id (first organizations)]
    (if-let [organization (get-organization organization-id)]
      (let [empty-confs (zipmap (map (comp keyword :permitType) (:scope organization)) (repeat {}))]
        (ok :krysp (merge empty-confs (:krysp organization))))
      (fail :error.unknown-organization))))

(defcommand set-krysp-endpoint
  {:parameters [url permitType version]
   :roles      [:authorityAdmin]
   :input-validators [(fn [{{:keys [permitType]} :data}]
                        (when-not (contains? (permit/permit-types) permitType)
                          (warn "invalid permit type" permitType)
                          (fail :error.missing-parameters :parameters [:permitType])))]}
  [{{:keys [organizations] :as user} :user}]
  (if (or (s/blank? url) (krysp/wfs-is-alive? url))
    (mongo/update-by-id :organizations (first organizations) {$set {(str "krysp." permitType ".url") url
                                                                    (str "krysp." permitType ".version") version}})
    (fail :auth-admin.legacyNotResponding)))

;;
;; Helpers
;;

(defn with-organization [id function]
  (if-let [organization (get-organization id)]
    (function organization)
    (do
      (debugf "organization '%s' not found with id." id)
      (fail :error.organization-not-found))))
