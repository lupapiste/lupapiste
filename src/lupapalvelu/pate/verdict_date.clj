(ns lupapalvelu.pate.verdict-date
  "Updates application verdictDate field. Separate namespace due to
  various dependencies."
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.verdict-interface :as vif]
            [monger.operators :refer :all]))

(defn update-verdict-date
  "Updates application verdictDate. Unsets the field if no date is
  found. Can be called as a command post function."
  ([application-id]
   (when-let [app (mongo/select-one :applications
                                    {:_id application-id}
                                    {:verdicts 1 :pate-verdicts 1})]
     (mongo/update-by-id :applications
                         application-id
                         (if-let [date (vif/latest-published-verdict-date app)]
                           {$set {:verdictDate date}}
                           {$unset {:verdictDate true}}))))
  ([command _]
   (update-verdict-date (some-> command :application :id))))
