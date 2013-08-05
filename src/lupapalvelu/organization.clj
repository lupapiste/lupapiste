(ns lupapalvelu.organization
  (:use [monger.operators]
        [lupapalvelu.core]
        [clojure.tools.logging])
  (:require [clojure.string :as s]
            [lupapalvelu.xml.krysp.reader :as krysp]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
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

(defquery "users-in-same-organizations"
  {:roles [:authority]}
  [{user :user}]
  (ok :users (map security/summary (mongo/select :users {:organizations {$in (:organizations user)}}))))

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

(defcommand "add-organization-link"
  {:description "Adds link to organization."
   :parameters [:url :nameFi :nameSv]
   :roles [:authorityAdmin]
   :verified true}
  [{{:keys [organizations]} :user {:keys [url nameFi nameSv]} :data}]
  (let [organization (first organizations)]
    (mongo/update :organizations {:_id organization} {$push {:links {:name {:fi nameFi :sv nameSv} :url url}}})
    (ok)))

(defcommand "update-organization-link"
  {:description "Updates organization link."
   :parameters [:url :nameFi :nameSv :index]
   :roles [:authorityAdmin]
   :verified true}
  [{{:keys [organizations]} :user {url :url nameFi :nameFi nameSv :nameSv i :index} :data}]
  (let [organization (first organizations)]
    (mongo/update :organizations {:_id organization} {$set {(str "links." i) {:name {:fi nameFi :sv nameSv} :url url}}})
    (ok)))

(defcommand "remove-organization-link"
  {:description "Removes organization link."
   :parameters [:nameFi :nameSv :url]
   :roles [:authorityAdmin]
   :verified true}
  [{{:keys [organizations]} :user {nameFi :nameFi nameSv :nameSv url :url} :data}]
  (let [organization (first organizations)]
    (mongo/update :organizations {:_id organization} {$pull {:links {:name {:fi nameFi :sv nameSv} :url url}}})
    (ok)))

(defquery "organization-names"
  {:authenticated true
   :verified true}
  [{user :user}]
  (ok :organizations (mongo/select :organizations {} {:name 1})))

(defquery "municipalities-with-organization"
  {} [_] (ok :municipalities (municipalities-with-organization)))

(defquery "operations-for-municipality"
  {:authenticated true}
  [{{:keys [municipality]} :data}]
  (ok :operations (operations/municipality-operations municipality)))

(defn resolve-organization [municipality permit-type]
  (when-let [organizations (mongo/select :organizations {$and [{:scope.municipality municipality} {:scope.permitType permit-type}]})]
    (when (> (count organizations) 1)
      (errorf "*** multiple organizations in scope of - municipality=%s, permit-type=%s -> %s" municipality permit-type))
    (first organizations)))

(defquery "organization-details"
  {:parameters [:municipality :operation] :verified true}
  [{{:keys [municipality operation]} :data}]
  (if-let [result (mongo/select-one :organizations {:municipalities municipality} {"links" 1 "operations-attachments" 1})]
    (ok :links (:links result)
        :attachmentsForOp (-> result :operations-attachments ((keyword operation))))
    (fail :unknown-organization)))

(defcommand "organization-operations-attachments"
  {:parameters [:operation :attachments]
   :roles [:authorityAdmin]}
  [{{:keys [operation attachments]} :data user :user}]
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
      (mongo/update :organizations {:_id organization} {$set {:legacy legacy}})
      (fail :legacy_is_dead))))

;;
;; Helpers
;;

(defn with-organization [id function]
  (if-let [organization (get-organization id)]
    (function organization)
    (do
      (debugf "organization '%s' not found with id." id)
      (fail :error.organization-not-found))))
