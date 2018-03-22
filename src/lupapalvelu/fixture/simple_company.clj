(ns lupapalvelu.fixture.simple-company
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture.core :refer [deffixture]]
            [lupapalvelu.fixture.minimal :as minimal]))

(def usernames #{"admin" "teppo" "sonja" "pena" "kaino@solita.fi" "erkki@example.com" "mikko@example.com" "sven@example.com"})

; No need to do company invites in Robot tests
(defn users-to-company [company-id users all-users]
  (letfn [(update-user-company [user]
            (if-not (get users (:username user))
              user
              (assoc user :company {:id company-id :role "user"})))]
    (map update-user-company all-users)))

(def users (->> minimal/users
                (filter (comp usernames :username))
                (users-to-company "solita" #{"sven@example.com"})))

(def organizations (filter (comp (set (mapcat (comp keys :orgAuthz) users)) keyword :id) minimal/organizations))

(def companies minimal/companies)

(deffixture "simple-company" {}
  (mongo/clear!)
  (mongo/insert-batch :users users)
  (mongo/insert-batch :companies companies)
  (mongo/insert-batch :organizations organizations))

