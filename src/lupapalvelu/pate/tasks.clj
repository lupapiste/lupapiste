(ns lupapalvelu.pate.tasks
  (:require [lupapalvelu.tasks :as tasks]
            [sade.shared-util :as util]))


(defn plan->task [{{plans :plans} :references :as verdict} ts plan-id]
  (when-some [pate-plan (util/find-by-id plan-id plans)]
    (let [source {:type "verdict" :id (:id verdict)}
          plan-name (get-in pate-plan [:name (keyword (get-in verdict [:data :language] "fi"))])]
      (tasks/new-task "task-lupamaarays" plan-name nil {:created ts} source))))

(defn condition->task [verdict ts condition]
  (let [source {:type "verdict" :id (:id verdict)}
        condition-title (:condition (second condition))]
    (tasks/new-task "task-lupamaarays" condition-title nil {:created ts} source)))
