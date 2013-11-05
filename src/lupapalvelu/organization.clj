(ns lupapalvelu.organization
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf fatal]]
            [clojure.string :as s]
            [monger.operators :refer :all]
            [lupapalvelu.core :refer [ok fail fail!]]
            [lupapalvelu.action :refer [defquery defcommand]]
            [lupapalvelu.xml.krysp.reader :as krysp]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
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

(defn get-legacy [organization-id]
  (let [organization (mongo/select-one :organizations {:_id organization-id})
        legacy       (:legacy organization)]
    (when-not (s/blank? legacy) legacy)))

(defn municipalities-with-organization []
  (let [id-and-scopes (mongo/select :organizations {} {:scope 1})]
    (distinct
      (for [{id :id scopes :scope} id-and-scopes
            {:keys [municipality]} scopes] municipality))))

(defn find-user-organizations [user]
  (mongo/select :organizations {:_id {$in (:organizations user)}}))

(defn find-user-municipalities [user]
  (distinct (reduce into [] (map #(:municipalities %) (find-user-organizations user)))))

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
        ops (merge (zipmap (keys operations/operations) (repeat [])) (:operations-attachments organization))]
    (ok :organization (assoc organization :operations-attachments ops)
        :attachmentTypes (partition 2 (attachments/organization-attachments organization)))))

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
            name-map (-> result :name)
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

(defquery legacy-system
  {:roles [:authorityAdmin]
   :verified true}
  [{{:keys [organizations]} :user}]
  (let [organization (first organizations)]
    (if-let [result (mongo/select-one :organizations {:_id organization} {"legacy" 1})]
      (ok :legacy (:legacy result))
      (fail :error.unknown-organization))))

(defcommand set-legacy-system
  {:parameters [legacy]
   :roles      [:authorityAdmin]
   :verified   true}
  [{{:keys [organizations] :as user} :user}]
  (let [organization (first organizations)]
    (if (or (s/blank? legacy) (krysp/legacy-is-alive? legacy))
      (mongo/update-by-id :organizations organization {$set {:legacy legacy}})
      (fail :error.legacy_is_dead))))

;;
;; Helpers
;;

(defn with-organization [id function]
  (if-let [organization (get-organization id)]
    (function organization)
    (do
      (debugf "organization '%s' not found with id." id)
      (fail :error.organization-not-found))))
