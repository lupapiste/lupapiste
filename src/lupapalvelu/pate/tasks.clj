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

(defn resolve-review-type [{:keys [category]} review-type]
  (let [rt (keyword review-type)]
    (if (util/=as-kw category :r)
      ((or rt :ei-tiedossa) shared/review-type-map)
      (rt shared/ya-review-type-map))))

(defn- new-review-task [verdict ts review-name review-type buildings]
  (new-task verdict ts
            "task-katselmus"
            review-name
            {:katselmuksenLaji   (resolve-review-type verdict review-type)
             :vaadittuLupaehtona true
             :rakennus           (tasks/rakennus-data-from-buildings
                                  {} buildings)
             ;; data should be empty, as this is just placeholder task
             :katselmus          {:tila         nil
                                  :pitaja       nil
                                  :pitoPvm      nil
                                  :lasnaolijat  ""
                                  :huomautukset {:kuvaus ""}
                                  :poikkeamat   ""}}))

(defn- included? [verdict dict]
  (get-in verdict [:data (-> (name dict) (str "-included") keyword)]))

(defn- reviews->tasks
  ([{{reviews :reviews} :references
     data               :data
     :as                verdict} ts buildings dict]
   (when (included? verdict dict)
     (->> reviews
          (filter #(util/includes-as-kw? (dict data) (:id %)))
          (map #(new-review-task verdict ts
                                 (get % (-> data :language keyword))
                                 (:type %)
                                 buildings)))))
  ([verdict ts buildings] (reviews->tasks verdict ts buildings :reviews)))

(defn- plans->tasks
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

(defn- conditions->tasks
  ([{data :data :as verdict} ts dict]
   (map #(new-task verdict ts "task-lupamaarays" (:condition %))
        (some-> data dict vals)))
  ([verdict ts] (conditions->tasks verdict ts :conditions)))

(defn- foremen->tasks
  ([{data :data :as verdict} ts dict]
   (when (included? verdict dict)
     (map #(new-task verdict ts
                     "task-vaadittu-tyonjohtaja"
                     (i18n/localize (:language data) :pate-r.foremen %))
          (dict data))))
  ([verdict ts] (foremen->tasks verdict ts :foremen)))

;; --------------------------
;; Legacy tasks
;; --------------------------

(defn- legacy-reviews->tasks [{data :data :as verdict} ts buildings]
  (map #(new-review-task verdict
                         ts
                         (:name %)
                         (:type %)
                         buildings)
       (some-> data :reviews vals)))

(defn- legacy-conditions->tasks [{data :data :as verdict} ts]
  (map #(new-task verdict ts "task-lupamaarays" (:name %))
       (some-> data :conditions vals)))

(defn- legacy-foremen->tasks [{data :data :as verdict} ts]
  (map #(new-task verdict ts "task-vaadittu-tyonjohtaja" (:role %))
       (some-> data :foremen vals)))

(defn pate-verdict->tasks
  "Generate Lupapiste tasks from Pate verdict."
  [verdict ts buildings]
  (->> (if (:legacy? verdict)
         [(legacy-reviews->tasks verdict ts buildings)
          (legacy-conditions->tasks verdict ts)
          (legacy-foremen->tasks verdict ts)]
         [(reviews->tasks verdict ts buildings)
          (plans->tasks verdict ts)
          (conditions->tasks verdict ts)
          (foremen->tasks verdict ts)])
       (apply concat)
       (remove nil?)))
