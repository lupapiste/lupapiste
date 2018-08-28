(ns lupapalvelu.migration.pate-verdict-migration
  (:require [clojure.walk :refer [postwalk prewalk walk]]
            [sade.core :refer [def-]]
            [sade.util :as util]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.verdict :as verdict]))

;;
;; Post-walk related helpers
;;

;; The type exists only as an implemention detail for `accessor-key?`

(defn- access [accessor-key]
  {::access accessor-key})

(def- accessor-key? (comp boolean ::access))

(defn- wrap
  "Wraps accessor function with metadata/wrap"
  [accessor]
  {::wrap accessor})

(def- wrap? (comp boolean ::wrap))
;;
;; Helpers for obtaining collections from current verdicts
;;

(defn- from-collection [collection-key element-skeleton]
  {::from-collection {::collection collection-key
                      ::element element-skeleton}})

(defn- collection-key? [x]
  (boolean (::from-collection x)))

;;
;; Helpers for accessing relevant data from current verdicts
;;

(defn- get-in-verdict [path]
  (fn [_ verdict _]
    (get-in verdict path)))

(defn- get-in-poytakirja [key]
  (get-in-verdict (conj [:paatokset 0 :poytakirjat 0] key)))

(defn- get-in-paivamaarat [key]
  (get-in-verdict (conj [:paatokset 0 :paivamaarat] key)))

(defn- filter-tasks-of-verdict [p]
  (fn [app verdict _]
    (->> app :tasks
         (filter #(and (= (:id verdict)
                          (-> % :source :id))
                       (p %))))))
(defn- task-name? [tn]
  (fn [task]
    (util/=as-kw (-> task :schema-info :name)
                 tn)))

(defn- get-in-context [path]
  (fn [_ _ context]
    (get-in context path)))

(defn- verdict-category [application _ _]
  (schema-util/application->category application))

(defn- verdict-template [app _ _]
  {:inclusions (-> (verdict-category app nil nil)
                   verdict/legacy-verdict-inclusions)})

(def- accessor-functions
  "Contains functions for accessing relevant Pate verdict data from
  current verdict drafts. These return the raw values but are
  subsequently to be wrapped with relevant metadata."
  {:id       (get-in-verdict [:id])
   :modified (get-in-verdict [:timestamp])
   :user     (constantly "TODO")
   :category verdict-category
   :handler         (get-in-poytakirja :paatoksentekija)
   :kuntalupatunnus (get-in-verdict [:kuntalupatunnus])
   :verdict-section (get-in-poytakirja :pykala)
   :verdict-code    (comp str (get-in-poytakirja :status))
   :verdict-text    (get-in-poytakirja :paatos)
   :anto            (get-in-paivamaarat :anto)
   :lainvoimainen   (get-in-paivamaarat :lainvoimainen)
   :reviews         (filter-tasks-of-verdict (task-name? :task-katselmus))
   :review-name     (get-in-context [:taskname])
   :review-type     (get-in-context [:data :katselmuksenLaji :value])
   :foremen         (filter-tasks-of-verdict (task-name? :task-vaadittu-tyonjohtaja))
   :foreman-role    (get-in-context [:taskname])
   :conditions      (filter-tasks-of-verdict (task-name? :task-lupamaarays))
   :condition-name  (get-in-context [:taskname])
   :template        verdict-template})

(defn- assoc-context [element context]
  (postwalk (fn [x]
              (if (accessor-key? x)
                (assoc x ::context context)
                x))
            element))

(defn- build-collection
  "Builds a collection skeleton dynamically based on the data present in the
   `application` and `verdict`."
  [application verdict {{collection ::collection
                         element    ::element} ::from-collection}]
  (->> ((get accessor-functions collection) application verdict nil)
       (group-by :id)
       (util/map-values (comp (partial assoc-context element) first))
       not-empty))

(defn- fetch-with-accessor
  "Given the `application` under migration, the source `verdict` and
  current `timestamp`, returns a function for accessing desired data
  from the `application` and `verdict`. Used with `prewalk`."
  [application verdict]
  (fn [x]
    (cond (accessor-key? x)
          ((get accessor-functions (::access x)) application verdict (::context x))

          (collection-key? x)
          (build-collection application verdict x)

          :else x)))

(defn- post-process [timestamp]
  (fn [x]
    (if (wrap? x)
      (metadata/wrap "Verdict draft Pate migration" timestamp (::wrap x))
      x)))
;;
;; Core migration functionality
;;

(def verdict-migration-skeleton
  "This map describes the shape of the migrated verdict. When building the
   migrated verdict, `(access :x)` will be replaced by calling the accessor
   function found under the key :x in the accessor function map. See `accessors`."
  {:id       (access :id)
   :modified (access :modified)
   :user     (access :user)  ;; poisetaan
   :category (access :category)
   :data {:handler         (wrap (access :handler))
          :kuntalupatunnus (wrap (access :kuntalupatunnus))
          :verdict-section (wrap (access :verdict-section))
          :verdict-code    (wrap (access :verdict-code))
          :verdict-text    (wrap (access :verdict-text))
          :anto            (wrap (access :anto))
          :lainvoimainen   (wrap (access :lainvoimainen))
          :reviews         (from-collection :reviews
                                            {:name (wrap (access :review-name))
                                             :type (wrap (access :review-type))})
          :foremen         (from-collection :foremen
                                            {:role (wrap (access :foreman-role))})
          :conditions      (from-collection :conditions
                                            {:name (wrap (access :condition-name))})}
   :template (access :template)
   :legacy? true})

(defn ->pate-legacy-verdict [application verdict timestamp]
  (->> (prewalk (fetch-with-accessor application
                                     verdict)
                verdict-migration-skeleton)
       (postwalk (post-process timestamp))
       util/strip-nils))
