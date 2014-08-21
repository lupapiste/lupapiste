(ns lupapalvelu.company
  (:require [monger.operators :refer :all]
            [schema.core :as sc]
            [sade.util :refer [max-length max-length-string y?]]
            [lupapalvelu.core :refer [fail!]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.core :refer [now fail!]])
  (:import [java.util Date]))

;;
;; Company schema:
;;

(def Company {:id       #"^\w{24}$"
              :name     (max-length-string 64)
              :y        (sc/pred y? "Not valid Y number")
              :created  sc/Int
              (sc/optional-key :process-id) sc/Str})

(def company-updateable-keys [:name])

;;
;; API:
;;

(defn create-company
  "Create a new company. Returns the created company data. Throws if given company data is not valid."
  [company]
  (let [company (assoc company
                       :id      (mongo/create-id)
                       :created (now))]
    (sc/validate Company company)
    (mongo/insert :companies company)
    company))

(defn find-company
  "Returns company mathing the provided query, or nil"
  [q]
  (mongo/select-one :companies (mongo/with-_id q)))

(defn find-company!
  "Returns company mathing the provided query. Throws if not found."
  [q]
  (or (find-company q) (fail! :company.not-found)))

(defn find-company-by-id
  "Returns company by given ID, or nil"
  [id]
  (find-company {:id id}))

(defn update-company!
  "Update company. Throws if comoany is not found, or if updates would make company data invalid.
   Retuens the updated company data."
  [id updates]
  (let [q       {:id id}
        company (find-company! q)
        updated (merge company (select-keys updates company-updateable-keys))]
    (sc/validate Company updated)
    (mongo/update :companies q updated)
    updated))

(defn update-user!
  "Update company user."
  [user-id op value]
  (condp = op
    :admin  (mongo/update-by-id :users user-id {$set {:company.role (if value "admin" "user")}})
    :enabled (mongo/update-by-id :users user-id {$set {:enabled (if value true false)}})
    :delete (mongo/update-by-id :users user-id {$set {:enabled false :company nil}})
    (fail! :bad-request)))
