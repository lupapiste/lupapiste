(ns lupapalvelu.pate.tasks
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.pate.shared :as shared]
            [sade.util :as util]))

(defn- new-task [verdict ts schema-name taskname & [taskdata]]
  (tasks/new-task schema-name
                  taskname
                  taskdata
                  {:created ts}
                  {:type "verdict"
                   :id   (:id verdict)}))

(defn- included? [verdict dict]
  (get-in verdict [:data (-> (name dict) (str "-included") keyword)]))

(defn reviews->tasks
  ([{{reviews :reviews} :references
     data               :data
     :as                verdict} ts buildings dict]
   (when (included? verdict dict)
     (->> reviews
          (filter #(util/includes-as-kw? (dict data) (:id %)))
          (map #(new-task verdict ts
                          "task-katselmus"
                          (get % (-> data :language keyword))
                          {:katselmuksenLaji   (shared/review-type-map
                                                (or (keyword (:type %))
                                                    :ei-tiedossa))
                           :vaadittuLupaehtona true
                           :rakennus           (tasks/rakennus-data-from-buildings
                                                {} buildings)
                           ;; data should be empty, as this is just placeholder task
                           :katselmus          {:tila         nil
                                                :pitaja       nil
                                                :pitoPvm      nil
                                                :lasnaolijat  ""
                                                :huomautukset {:kuvaus ""}
                                                :poikkeamat   ""}})))))
  ([verdict ts buildings] (reviews->tasks verdict ts buildings :reviews)))

(defn plans->tasks
  ([{{plans :plans} :references
     data           :data
     :as verdict} ts dict]
   (when (included? verdict dict)
     (->> plans
          (filter #(util/includes-as-kw? (dict data) (:id %)))
          (map #(new-task verdict ts
                          "task-lupamaarays"
                          (get % (-> data :language keyword)))))))
  ([verdict ts]
   (plans->tasks verdict ts :plans)))

(defn conditions->tasks
  ([{data :data :as verdict} ts dict]
   (map #(new-task verdict ts "task-lupamaarays" (:condition %))
        (some-> data dict vals)))
  ([verdict ts] (conditions->tasks verdict ts :conditions)))

(defn foremen->tasks
  ([{data :data :as verdict} ts dict]
   (when (included? verdict dict)
     (map #(new-task verdict ts
                     "task-vaadittu-tyonjohtaja"
                     (i18n/localize (:language data) :pate-r.foremen %))
          (dict data))))
  ([verdict ts] (foremen->tasks verdict ts :foremen)))
