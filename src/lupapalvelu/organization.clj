(ns lupapalvelu.organization
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf fatal]]
            [clojure.string :as s]
            [monger.operators :refer :all]
            [lupapalvelu.core :refer [ok fail fail!]]
            [lupapalvelu.action :refer [defquery defcommand]]
            [lupapalvelu.xml.krysp.reader :as krysp]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.attachment :as attachments]
            [lupapalvelu.operations :as operations]))

;;
;; local api
;;

(defn get-organization [id]
  (and id (mongo/select-one :organizations {:_id id})))

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
  (let [organization (mongo/by-id :organizations organization-id)
        krysp-config (get-in organization [:krysp (keyword permit-type)])]
    (when-not (s/blank? (:url krysp-config))
      (select-keys krysp-config [:url :version])))))

(defn- municipalities-with-organization []
  (let [id-and-scopes (mongo/select :organizations {} {:scope 1})]
    (distinct
      (for [{id :id scopes :scope} id-and-scopes
            {:keys [municipality]} scopes] municipality))))

(defn- find-user-organizations [user]
  (mongo/select :organizations {:_id {$in (:organizations user)}}))

(defn- find-user-municipalities [user]
  (distinct (reduce into [] (map :municipalities (find-user-organizations user)))))

(defn- organization-attachments
  "Returns a map where key is permit type, value is a list of attachment types for the permit type"
  [{scope :scope}]
  (reduce #(assoc %1 %2 (attachments/get-attachment-types-by-permit-type %2)) {} (map (comp keyword :permitType) scope)))

(defn- organization-operations
  "Returns a map where key is permit type, value is a list of operations for the permit type"
  [{scope :scope :as organization}]
  (reduce
    #(assoc %1 %2 (let [operation-names (keys (filter (fn [[_ op]] (= %2 (:permit-type op))) operations/operations))
                        empty-operation-attachments (zipmap operation-names (repeat []))
                        saved-operation-attachments (select-keys (:operations-attachments organization) operation-names)]
                    (merge empty-operation-attachments saved-operation-attachments))) {}
    (map :permitType scope)))

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
   :roles [:authorityAdmin]
   :verified true}
  [{{:keys [organizations] :as user} :user}]
  (let [orgs (find-user-organizations user)
        organization (first orgs)
        ops (organization-operations organization)]
    (ok :organization (assoc organization :operations-attachments ops)
        :attachmentTypes (organization-attachments organization))))

(defcommand update-organization
  {:description "Update organization details."
   :parameters [organizationId inforequestEnabled applicationEnabled openInforequestEnabled openInforequestEmail]
   :roles [:admin]
   :verified true}
  [_]
  (mongo/update-by-id :organizations organizationId {$set {"inforequest-enabled" inforequestEnabled
                                                           "new-application-enabled" applicationEnabled
                                                           "open-inforequest" openInforequestEnabled
                                                           "open-inforequest-email" openInforequestEmail}})
  (ok))

(defcommand add-organization-link
  {:description "Adds link to organization."
   :parameters [url nameFi nameSv]
   :roles [:authorityAdmin]
   :verified true}
  [{{:keys [organizations]} :user}]
  (let [organization (first organizations)]
    (mongo/update-by-id :organizations organization {$push {:links {:name {:fi nameFi :sv nameSv} :url url}}})
    (ok)))

(defcommand update-organization-link
  {:description "Updates organization link."
   :parameters [url nameFi nameSv index]
   :roles [:authorityAdmin]
   :verified true}
  [{{:keys [organizations]} :user}]
  (let [organization (first organizations)]
    (mongo/update-by-id :organizations organization {$set {(str "links." index) {:name {:fi nameFi :sv nameSv} :url url}}})
    (ok)))

(defcommand remove-organization-link
  {:description "Removes organization link."
   :parameters [nameFi nameSv url]
   :roles [:authorityAdmin]
   :verified true}
  [{{:keys [organizations]} :user}]
  (let [organization (first organizations)]
    (mongo/update-by-id :organizations organization {$pull {:links {:name {:fi nameFi :sv nameSv} :url url}}})
    (ok)))

(defquery organizations
  {:roles       [:admin]
   :authenticated true
   :verified true}
  [{user :user}]
  (ok :organizations (mongo/select :organizations {})))

(defquery organization-names
  {:authenticated true
   :verified true}
  [{user :user}]
  (ok :organizations (mongo/select :organizations {} {:name 1})))

(defquery "municipalities-with-organization"
  {:verified true}
  [_]
  (ok :municipalities (municipalities-with-organization)))

(defquery operations-for-municipality
  {:parameters [municipality]
   :authenticated true
   :verified true}
  [_]
  (ok :operations (operations/municipality-operations municipality)))

(defn resolve-organization [municipality permit-type]
  (when-let [organizations (mongo/select :organizations {:scope {$elemMatch {:municipality municipality :permitType permit-type}}})]
    (when (> (count organizations) 1)
      (errorf "*** multiple organizations in scope of - municipality=%s, permit-type=%s -> %s" municipality permit-type (count organizations)))
    (first organizations)))

(defquery organization-by-id
  {:parameters [organizationId]
   :roles [:admin]
   :verified true}
  [_]
  (mongo/select-one :organizations {:_id organizationId}))

(defquery organization-details
  {:parameters [municipality operation lang]
   :verified true}
  [_]
  (let [permit-type (:permit-type ((keyword operation) operations/operations))]
    (let [result (mongo/select-one
                      :organizations
                      {:scope {$elemMatch {:municipality municipality :permitType permit-type}}}
                      {"name" 1
                       "links" 1
                       "operations-attachments" 1
                       "inforequest-enabled" 1
                       "new-application-enabled" 1})]
      (when-not result (fail! :error.unknown-organization :municipality municipality :permitType permit-type))
      (let [inforequests-enabled (:inforequest-enabled result)
            new-applications-enabled (:new-application-enabled result)
            name-map (:name result)
            ;; if name of the organization is not set in current language, then use the name that is set for it
            org-name (if ((keyword lang) name-map)
                       ((keyword lang) name-map)
                       (first (vals name-map)))]
        (ok
          :inforequests-disabled (not (:inforequest-enabled result))
          :new-applications-disabled (not (:new-application-enabled result))
          :links (:links result)
          :attachmentsForOp (-> result :operations-attachments ((keyword operation))))))))

(defcommand organization-operations-attachments
  {:parameters [operation attachments]
   :roles [:authorityAdmin]}
  [{user :user}]
  ; FIXME: validate operation and attachments
  (let [organizations (:organizations user)
        organization  (first organizations)]
    (mongo/update-by-id :organizations organization {$set {(str "operations-attachments." operation) attachments}})
    (ok)))

(defquery krysp-config
  {:roles [:authorityAdmin]
   :verified true}
  [{{:keys [organizations]} :user}]
  (let [organization-id (first organizations)]
    (if-let [organization (mongo/by-id :organizations organization-id {:krysp 1, :scope 1})]
      (let [empty-confs (zipmap (map (comp keyword :permitType) (:scope organization)) (repeat {}))]
        (ok :krysp (merge empty-confs (:krysp organization))))
      (fail :error.unknown-organization))))

(defcommand set-krysp-endpoint
  {:parameters [url permitType version]
   :roles      [:authorityAdmin]
   :input-validators [(fn [{{permit-type :permitType} :data}]
                        (when-not (contains? (permit/permit-types) permit-type)
                          (warn "invalid permit type" permit-type)
                          (fail :error.missing-parameters :parameters [:permitType])))]
   :verified   true}
  [{{:keys [organizations] :as user} :user}]
  (let [organization (first organizations)]
    (if (or (s/blank? url) (krysp/wfs-is-alive? url))
      (mongo/update-by-id :organizations organization {$set {(str "krysp." permitType ".url") url
                                                             (str "krysp." permitType ".version") version}})
      (fail :auth-admin.legacyNotResponding))))

;;
;; Helpers
;;

(defn with-organization [id function]
  (if-let [organization (get-organization id)]
    (function organization)
    (do
      (debugf "organization '%s' not found with id." id)
      (fail :error.organization-not-found))))
