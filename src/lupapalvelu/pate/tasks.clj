(ns lupapalvelu.pate.tasks
  (:require [clojure.set :as set]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.tasks :as tasks]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer :all]))

(defn- new-task [verdict ts schema-name taskname & [taskdata]]
  (tasks/new-task schema-name
                  taskname
                  (cond-> taskdata
                    (= schema-name "task-lupamaarays") (assoc :maarays taskname))
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
   (->> (some-> data dict vals)
        (remove empty?) ; TT-18524
        (map #(new-task verdict ts "task-lupamaarays" (:condition %)))))
  ([verdict ts] (conditions->tasks verdict ts :conditions)))

(defn- foremen->tasks
  ([{data :data :as verdict} ts dict]
   (when (included? verdict dict)
     (map #(new-task verdict ts "task-vaadittu-tyonjohtaja"
                     (i18n/localize (:language data) :pate-r.foremen %)
                     {:kuntaRoolikoodi (helper/foreman-role %)})
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
  (map #(new-task verdict ts "task-vaadittu-tyonjohtaja"
                  (i18n/localize (:language data) :osapuoli.tyonjohtaja.kuntaRoolikoodi (:role %))
                  {:kuntaRoolikoodi (:role %)})
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

;; ------------------------------------------
;; Task order
;; ------------------------------------------

(defn- group-tasks [tasks]
  (reduce (fn [acc {:keys [id data taskname schema-info source]}]
            (let [t (util/assoc-when {:id       id
                                      :taskname (ss/lower-case taskname)}
                                     :source-review (when (= (:type source) "task")
                                                      (:id source))
                                     :kuntaroolikoodi (ss/lower-case (get-in data [:kuntaRoolikoodi :value])))]
              (update acc
                      (if (:source-review t)
                        :subreviews
                        (case (:name schema-info)
                          ("task-katselmus" "task-katselmus-ya"
                           "task-katselmus-backend")  :reviews
                          "task-vaadittu-tyonjohtaja" :foremen
                          "task-lupamaarays"          :plans))
                      concat
                      [t])))
          {}
          (sort-by :created tasks)))

(defn- combine-subreviews [review-ids subreviews]
  (loop [[x & xs :as review-ids] review-ids
         grouped                 (group-by :source-review subreviews)
         result                  []]
    (if (nil? x)
      ;; We add the orphan subreviews although those should not exist.
      (concat result
              (some->> (vals grouped)
                       (apply concat)
                       (map :id)))
      (if-let [subreviews (get grouped x)]
        (recur (concat (map :id subreviews) xs) ; Since subreview can also be source
               (dissoc grouped x)
               (concat result [x]))
        (recur xs grouped (concat result [x]))))))

(defn- order-tasks
  "The ordering defined in verdict `:references` is taken into account."
  [tasks {:keys [foreman review plan] :as orders}]
  (let [ordered-ids           (fn [order k]
                                (when order
                                  (some->> (get tasks k)
                                           (sort-by #(get order (:taskname %)))
                                           (map :id))))
        ordered-foreman-roles (fn [order]
                                (when order
                                  (some->> (:foremen tasks)
                                           (sort-by #(get order (:kuntaroolikoodi %)))
                                           (map :kuntaroolikoodi)
                                           distinct)))]
    (-> (util/assoc-when-pred {}
                              not-empty
                              :foremen (ordered-foreman-roles foreman)
                              :plans (ordered-ids plan :plans)
                              :reviews (ordered-ids review :reviews))
        (update :reviews combine-subreviews (:subreviews tasks)))))

(defn- update-order
  "`order-map` has integer values starting from zero. If a k in `ks` is not in the map it is added (with
  largest value). Returns `order-map`."
  [order-map & ks]
  (reduce (fn [acc k]
            (cond-> acc
              (nil? (get acc k)) (assoc k (count acc))))
          order-map
          (flatten ks)))

(defn- application-task-order
  "The order is determined by the order of the tasks in mongo."
  [{:keys [foremen reviews plans] :as tasks}]
  (order-tasks tasks
               {:foreman (update-order {} (map :kuntaroolikoodi foremen))
                :review  (update-order {} (map :taskname reviews))
                :plan    (update-order {} (map :taskname plans))}))

(defn- pate-foreman-order [tasks {:keys [references] :as verdict}]
  (let [foreman-order (some-<> references
                               :foremen
                               (zipmap (range))
                               (util/map-keys keyword <>)
                               (set/rename-keys helper/foreman-roles)
                               (util/map-keys ss/lower-case <>))]
    (->> (:foremen tasks)
         (map :kuntaroolikoodi)
         (update-order foreman-order))))

(defn- pate-plan-order [tasks {:keys [references data] :as verdict}]
  (let [lang       (keyword (:language data :fi))
        plan-order (->> references
                        :plans
                        (map (comp ss/lower-case lang))
                        (update-order {}))
        conditions (some->> data :conditions vals
                            (map (comp ss/lower-case :condition))
                            not-empty)]
    (->> (:plans tasks)
         (map :taskname)
         (concat conditions)
         (update-order plan-order))))

(defn- pate-review-order [tasks {:keys [references data] :as verdict}]
  (let [lang         (keyword (:language data :fi))
        review-order (->> references
                          :reviews
                          (map (comp ss/lower-case lang))
                          (update-order {}))]
    (->> (:reviews tasks)
         (map :taskname)
         (update-order review-order))))

(defn- pate-task-order
  "The ordering defined in verdict `:references` is taken into account."
  [tasks {:keys [references data] :as verdict}]
  (order-tasks tasks {:foreman (pate-foreman-order tasks verdict)
                      :review  (pate-review-order tasks verdict)
                      :plan    (pate-plan-order tasks verdict)}))

(defn task-order
  "Returns map of task types and sorting orders. If Pate `verdict` has the ordering information, it is
  used. The ordering depends on the task and verdict types. Whereas `:plans` and `:reviews` are list
  of ids, `:foremen` is a list of foreman codes (kuntaroolikoodi)."
  [application verdict]
  (let [tasks (group-tasks (:tasks application))]
    (cond
      (empty? tasks)            nil
      (or (nil? verdict)
          (vc/legacy? verdict)) (application-task-order tasks)
      :else                     (pate-task-order tasks verdict))))
