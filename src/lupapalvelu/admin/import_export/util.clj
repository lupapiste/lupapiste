(ns lupapalvelu.admin.import-export.util
  (:require [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$exists $ne]]))

(defn format-organizations
  [organizations]
  (->> organizations
       (keep (fn [{:keys [id name] :as org}]
               (if-not (and id name)
                 (log/errorf "Missing required field(s), not exporting" org)
                 org)))
       (map (juxt :id :name))
       (into {})))

(defn export-all-organizations!
  "Export all active organizations"
  []
  (->> (mongo/select :organizations {:deactivated {$ne true}
                                     :scope.0     {$exists true}} [:name])
       format-organizations))
