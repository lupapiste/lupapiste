(ns lupapalvelu.migration.task-duplicate-ids-fix
  (:require [lupapalvelu.migration.migration-data :as migration-data]
            [clojure.java.io :as io]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]))

(defn update-attachment-task-ref
  [task-id->new-id-mapping attachment]
  (->> [:source :target]
       (reduce
         (fn [att k]
           (if-let [new-id (task-id->new-id-mapping (get-in att [k :id]))]
             (assoc-in att [k :id] new-id)
             att))
         attachment)))


(defn tasks-by-id []
  (->> (with-open [id-csv (io/reader (migration-data/duplicate-task-ids-csv))]
         (into [] ; realize lazy line-seq
               (for [csv-line (line-seq id-csv)
                     :let [[lp-id task-id] (ss/split csv-line #",")]]
                 [lp-id task-id])))
       (group-by first)
       (util/map-values #(map second %))))
