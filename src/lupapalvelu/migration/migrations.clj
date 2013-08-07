(ns lupapalvelu.migration.migrations
  (:require [lupapalvelu.migration.core :refer [defmigration]]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]))

(defmigration add-default-permit-type
  {:apply-when (pos? (mongo/count :applications {:permitType {$exists false}}))}
  (mongo/update :applications {:permitType {$exists false}} {$set {:permitType "R"}} :multi true))  

(defmigration add-scope-to-organizations
  {:apply-when (pos? (mongo/count :organizations {:scope {$exists false}}))}
  (let [without-scope (mongo/select :organizations {:scope {$exists false}})]
    (doseq [{:keys [id municipalities]} without-scope]
      (let [scopes (map (fn [municipality] {:municipality municipality :permitType "R"}) municipalities)]
        (mongo/update-by-id :organizations id {$set {:scope scopes}})))
    {:fixed-organizations   (count without-scope)
     :organizations-total   (mongo/count :organizations)}))
