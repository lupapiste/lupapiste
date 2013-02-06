(ns lupapalvelu.domain
  (:use [monger.operators]
        [lupapalvelu.log])
  (:require [lupapalvelu.mongo :as mongo]))

(defn application-query-for [user]
  (case (keyword (:role user))
    :applicant {:auth.id (:id user)
                :state {$ne "canceled"}}
    :authority {:municipality (:municipality user)
                $and [{:state {$ne "draft"}} {:state {$ne "canceled"}}]}
    :admin     {:state {$ne "canceled"}}
    (do
      (warn "invalid role to get applications")
      {:_id "-1"} ))) ; should not yield any results

(defn get-application-as [application-id user]
  (when user (mongo/select-one :applications {$and [{:_id application-id} (application-query-for user)]})))

(defn role-in-application [user-id {roles :roles}]
  (some (fn [[role {id :id}]]
          (if (= id user-id) role))
        roles))

(defn get-document-by-id
  "returns first document from application with the document-id"
  [{documents :documents} document-id]
  (first (filter #(= document-id (:id %)) documents)))

(defn get-document-by-name
  "returns first document from application by name"
  [{documents :documents} name]
  (first (filter #(= name (get-in % [:schema :info :name])) documents)))

(defn is-invited [{invites :invites} user-id]
  (or (some #(= user-id (-> % :user :id)) invites) false))
