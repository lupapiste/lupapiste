(ns lupapalvelu.pate.tasks
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.tasks :as tasks]
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
      ((or rt :ei-tiedossa) helper/review-type-map)
      (rt helper/ya-review-type-map))))

(defn- new-review-task [{:keys [category] :as verdict}
                        ts review-name review-type buildings]
  (let [ya? (util/includes-as-kw? [:ya :contract] category)]
    (new-task verdict ts
              (if ya?
                "task-katselmus-ya"
                "task-katselmus")
              review-name
              (merge {:katselmuksenLaji   (resolve-review-type verdict
                                                               review-type)
                      :vaadittuLupaehtona true}
                     (when-not ya?
                       {:rakennus (tasks/rakennus-data-from-buildings
                                   {} buildings)})))))

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
