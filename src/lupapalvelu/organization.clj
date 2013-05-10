(ns lupapalvelu.organization
  (:use [monger.operators]
        [lupapalvelu.core]
        [clojure.tools.logging])
  (:require [clojure.string :as s]
            [lupapalvelu.xml.krysp.reader :as krysp]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.attachment :as attachments]
            [lupapalvelu.operations :as operations]))

(defn find-user-organizations [user]
  (mongo/select :organizations {:_id {$in (:organizations user)}}))

(defn find-user-municipalities [user]
  (distinct (reduce into [] (map #(:municipalities %) (find-user-organizations user)))))

(defquery "organization-by-user"
  {:description "Lists all organization users by organization."
   :roles [:authorityAdmin]
   :verified true}
  [{user :user {:keys [organizations]} :user}]
  (let [orgs (find-user-organizations user)
        organization (first orgs)
        ops (merge (zipmap (keys operations/operations) (repeat [])) (:operations-attachments organization))]
    (ok :organization (assoc organization :operations-attachments ops)
        :attachmentTypes (partition 2 (attachments/organization-attachments organization)))))

(defcommand "organization-link-add"
  {:description "Adds link to organization."
   :parameters [:url :nameFi :nameSv]
   :roles [:authorityAdmin]
   :verified true}
  [{{:keys [organizations]} :user {:keys [url nameFi nameSv]} :data}]
  (let [organization (first organizations)]
    (mongo/update :organizations {:_id organization} {$push {:links {:name {:fi nameFi :sv nameSv} :url url}}})
    (ok)))

(defcommand "organization-link-update"
  {:description "Updates organization link."
   :parameters [:url :nameFi :nameSv :index]
   :roles [:authorityAdmin]
   :verified true}
  [{{:keys [organizations]} :user {url :url nameFi :nameFi nameSv :nameSv i :index} :data}]
  (let [organization (first organizations)]
    (mongo/update :organizations {:_id organization} {$set {(str "links." i) {:name {:fi nameFi :sv nameSv} :url url}}})
    (ok)))

(defcommand "organization-link-rm"
  {:description "Removes organization link."
   :parameters [:nameFi :nameSv :url]
   :roles [:authorityAdmin]
   :verified true}
  [{{:keys [organizations]} :user {nameFi :nameFi nameSv :nameSv url :url} :data}]
  (let [organization (first organizations)]
    (mongo/update :organizations {:_id organization} {$pull {:links {:name {:fi nameFi :sv nameSv} :url url}}})
    (ok)))

(defquery "organizations"
  {:authenticated true
   :verified true}
  [{user :user}]
  (ok :organizations (mongo/select :organizations {} {:name 1})))

(defquery "municipalities-for-new-application"
  {:authenticated true
   :verified true}
  [{user :user}]
  (ok :municipalities
     (map (fn [id] {:id id :operations (operations/municipality-operations id)})
          (->> (mongo/select :organizations {} {"municipalities" 1}) (mapcat :municipalities) (distinct)))))

(defquery "organization"
  {:parameters [:organizationId] :verified true}
  [{{organizationId :organizationId} :data}]
  (if-let [result (mongo/select-one :organizations {:_id organizationId} {"links" 1})]
    (ok :links (:links result)
        :operations (operations/municipality-operations organizationId)
        :attachments (attachments/organization-attachments organizationId))
    (fail :unknown-organization)))

; Returns the organization based on municipality and operation.
; For example in municipality 753 with operation of type R the organization is 753-R
(defn resolve-organization [municipality operation]
  (mongo/select-one :organizations {:municipalities municipality}))

; return the organization by municipality (eg. 753) and operation type (eg. 'R'), resulting to eg. organization 753-R
; TODO: operation does not have permitModule
(defquery "get-organization-details"
  {:parameters [:municipality] :verified true}
  [{{municipality :municipality operation :operation} :data}]
  (if-let [result (mongo/select-one :organizations {:municipalities municipality} {"links" 1})]
    (ok :links (:links result))
    (fail :unknown-organization)))

(defcommand "organization-operations-attachments"
  {:parameters [:operation :attachments]
   :roles [:authorityAdmin]}
  [{{operation :operation attachments :attachments} :data user :user}]
  ; FIXME: validate operation and attachments
  (let [organizations (:organizations user)
        organization  (first organizations)]
    (mongo/update :organizations {:_id organization} {$set {(str "operations-attachments." operation) attachments}})
    (ok)))

(defquery "legacy-system"
  {:roles [:authorityAdmin] :verified true}
  [{{:keys [organizations]} :user}]
  (let [organization (first organizations)]
    (if-let [result (mongo/select-one :organizations {:_id organization} {"legacy" 1})]
      (ok :legacy (:legacy result))
      (fail :unknown-organization))))

(defcommand "set-legacy-system"
  {:parameters [:legacy]
   :roles      [:authorityAdmin]
   :verified   true}
  [{{:keys [legacy]} :data {:keys [organizations] :as user} :user}]
  (let [organization (first organizations)]
    (if (or (s/blank? legacy) (krysp/legacy-is-alive? legacy))
      (do
        (mongo/update :organizations {:_id organization} {$set {:legacy legacy}})
        (ok))
      (fail :legacy_is_dead))))

;;
;; local api
;;

(defn get-organization [id]
  (and id (mongo/select-one :organizations {:_id id})))

(defn municipality-by-propertyId [id]
  (when (and (>= (count id) 3) (not (s/blank? id)))
    (subs id 0 3)))

(defn get-legacy [organization-id]
  (let [organization (mongo/select-one :organizations {:_id organization-id})
        legacy       (:legacy organization)]
    (when-not (s/blank? legacy) legacy)))

;;
;; Helpers
;;

(defn with-organization [id function]
  (if-let [organization (get-organization id)]
    (function organization)
    (do
      (debugf "organization '%s' not found with id." id)
      (fail :error.organization-not-found))))
