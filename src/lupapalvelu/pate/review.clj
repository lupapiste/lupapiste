(ns lupapalvelu.pate.review
  (:require [lupapalvelu.tasks :as tasks]
            [lupapalvelu.pate.shared :as pate-shared]
            [sade.shared-util :as util]))

(defn review->task [{{reviews :reviews} :references :as verdict} buildings ts pate-review-id]
  (when-some [pate-review (util/find-by-id pate-review-id reviews)]
    (let [source {:type "verdict" :id (:id verdict)}
          data {:katselmuksenLaji   (pate-shared/review-type-map (or (keyword (:type pate-review)) :ei-tiedossa))
                :vaadittuLupaehtona true
                :rakennus           (tasks/rakennus-data-from-buildings {} buildings)
                :katselmus          {:tila nil                ; data should be empty, as this is just placeholder task
                                     :pitaja nil
                                     :pitoPvm nil
                                     :lasnaolijat ""
                                     :huomautukset {:kuvaus ""}
                                     :poikkeamat ""}}
          review-name (get-in pate-review [:name (keyword (get-in verdict [:data :language] "fi"))])]
      (tasks/new-task "task-katselmus" review-name data {:created ts} source))))
