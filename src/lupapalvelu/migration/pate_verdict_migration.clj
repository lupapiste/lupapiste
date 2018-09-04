(ns lupapalvelu.migration.pate-verdict-migration
  (:require [clojure.walk :refer [postwalk prewalk walk]]
            [monger.operators :refer :all]
            [sade.core :refer [def-]]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.pdf :as pdf]
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

(defn- id-map-from [collection-key element-skeleton]
  {::id-map-from {::collection collection-key
                      ::element element-skeleton}})

(defn- build-id-map? [x]
  (boolean (::id-map-from x)))


(defn- array-from [collection-key element-skeleton]
  {::array-from {::collection collection-key
                  ::element element-skeleton}})

(defn- build-array? [x]
  (boolean (::array-from x)))

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

(defn- context [_ _ context]
  context)

(defn- verdict-category [application _ _]
  (schema-util/application->category application))

(defn- verdict-template [app _ _]
  {:inclusions (-> (verdict-category app nil nil)
                   verdict/legacy-verdict-inclusions)})

(defn- get-when [p getter-fn]
  (fn [& args]
    (when (apply p args)
      (apply getter-fn args))))

(defn- verdict-published? [_ verdict _]
  (not (:draft verdict)))

(defn- get-archive-data [_ verdict _]
  {:verdict-date nil
   :verdict-giver ((get-in-poytakirja :paatoksentekija) nil verdict nil)
   :lainvoimainen ((get-in-paivamaarat :lainvoimainen) nil verdict nil)})

(defn- targets-verdict? [attachment verdict]
  (= (:id verdict)
     (-> attachment :target :id)))

;; See lupapalvelu.pate.verdict/verdict-attachment-items
(defn- attachment-summaries [application verdict _]
  (->> (:attachments application)
       (filter #(some-> % :latestVersion :fileId ss/not-blank?))
       (filter #(targets-verdict? % verdict))
       (mapv (fn [{:keys [type id]}]
               {:type-group (:type-group type)
                :type-id    (:type-id type)
                :id         id}))
       (group-by #(select-keys % [:type-group
                                  :type-id]))
       (map (fn [[k v]]
              (assoc k :amount (count v))))))

(def- signature-name (comp (partial apply str)
                           (juxt (get-in-context [:user :firstName])
                                 (constantly " ")
                                 (get-in-context [:user :lastName]))))

(defn- published-attachment-id [application verdict _]
  (->> (:attachments application)
       (util/find-first #(and (util/=as-kw (-> % :type :type-id)
                                           :paatos)
                              (targets-verdict? % verdict)))
       :id))

(def- accessor-functions
  "Contains functions for accessing relevant Pate verdict data from
  current verdict drafts. These return the raw values but are
  subsequently to be wrapped with relevant metadata."
  {:anto              (get-in-paivamaarat :anto)
   :archive           (get-when verdict-published? get-archive-data)
   :attachment-summaries attachment-summaries
   :category          verdict-category
   :condition-name    (get-in-context [:taskname])
   :conditions        (filter-tasks-of-verdict (task-name? :task-lupamaarays))
   :context           context
   :foreman-role      (get-in-context [:taskname])
   :foremen           (filter-tasks-of-verdict (task-name? :task-vaadittu-tyonjohtaja))
   :handler           (get-in-poytakirja :paatoksentekija)
   :id                (get-in-verdict [:id])
   :kuntalupatunnus   (get-in-verdict [:kuntalupatunnus])
   :lainvoimainen     (get-in-paivamaarat :lainvoimainen)
   :modified          (get-in-verdict [:timestamp])
   :published         (get-when verdict-published? (get-in-paivamaarat :anto))
   :published-attachment-id (get-when verdict-published? published-attachment-id)
   :review-name       (get-in-context [:taskname])
   :review-type       (get-in-context [:data :katselmuksenLaji :value])
   :reviews           (filter-tasks-of-verdict (task-name? :task-katselmus))
   :signature-date    (get-in-context [:created])
   :signature-name    signature-name
   :signature-user-id (get-in-context [:user :id])
   :signatures        (get-in-verdict [:signatures])
   :template          verdict-template
   :verdict-code      (comp str (get-in-poytakirja :status))
   :verdict-section   (get-in-poytakirja :pykala)
   :verdict-text      (get-in-poytakirja :paatos)})

(defn- assoc-context [element context]
  (postwalk (fn [x]
              (if (accessor-key? x)
                (assoc x ::context context)
                x))
            element))

(defn- build-id-map
  "Builds a collection skeleton dynamically based on the data present in the
   `application` and `verdict`."
  [application verdict {{collection ::collection
                         element    ::element} ::id-map-from}]
  (->> ((get accessor-functions collection) application verdict nil)
       (group-by :id)
       (util/map-values (comp (partial assoc-context element) first))
       not-empty))

(defn- build-array
  [application verdict {{collection ::collection
                         element    ::element} ::array-from}]
  (->> ((get accessor-functions collection) application verdict nil)
       (mapv (partial assoc-context element))))

(defn- fetch-with-accessor
  "Given the `application` under migration, the source `verdict` and
  current `timestamp`, returns a function for accessing desired data
  from the `application` and `verdict`. Used with `prewalk`."
  [application verdict]
  (fn [x]
    (cond (accessor-key? x)
          ((get accessor-functions (::access x)) application verdict (::context x))

          (build-id-map? x)
          (build-id-map application verdict x)

          (build-array? x)
          (build-array application verdict x)

          :else x)))

(defn- post-process [timestamp]
  (fn [x]
    (if (wrap? x)
      (metadata/wrap "Verdict draft Pate migration" timestamp (::wrap x))
      x)))
;;
;; Verdict migration
;;

(def verdict-migration-skeleton
  "This map describes the shape of the migrated verdict. When building the
   migrated verdict, `(access :x)` will be replaced by calling the accessor
   function found under the key :x in the accessor function map. See `accessors`."
  {:id        (access :id)
   :modified  (access :modified)
   :category  (access :category)
   :published {:published     (access :published)
               ;; :tags          (access :published-tags)
               :attachment-id (access :published-attachment-id)}
   :data {:handler         (wrap (access :handler))
          :kuntalupatunnus (wrap (access :kuntalupatunnus))
          :verdict-section (wrap (access :verdict-section))
          :verdict-code    (wrap (access :verdict-code))
          :verdict-text    (wrap (access :verdict-text))
          :anto            (wrap (access :anto))
          :lainvoimainen   (wrap (access :lainvoimainen))
          :reviews         (id-map-from :reviews
                                        {:name (wrap (access :review-name))
                                         :type (wrap (access :review-type))})
          :foremen         (id-map-from :foremen
                                        {:role (wrap (access :foreman-role))})
          :conditions      (id-map-from :conditions
                                        {:name (wrap (access :condition-name))})
          :attachments     (array-from :attachment-summaries
                                       (access :context))}
   :signatures (array-from :signatures
                           {:date (access :signature-date)
                            :name (access :signature-name)
                            :user-id (access :signature-user-id)})
   :template (access :template)
   :archive (access :archive)
   :legacy? true})

(defn- add-tags [application verdict]
  (if (-> verdict :published :published)
    (assoc-in verdict [:published :tags]
              (pr-str (pdf/verdict-tags application
                                        (metadata/unwrap-all verdict))))
    verdict))

(defn ->pate-legacy-verdict [application verdict timestamp]
  (->> (prewalk (fetch-with-accessor application
                                     verdict)
                verdict-migration-skeleton)
       (postwalk (post-process timestamp))
       (add-tags application)
       util/strip-nils
       util/strip-empty-collections))

(defn- draft-verdict-ids [application]
  (->> application
       :verdicts
       (filter :draft)
       (mapv :id)))

;;
;; Application migration
;;
(def migration-query
  ;; The best indication of a verdict having been created in Lupapiste
  ;; is the existence of :draft. Since we migrate all verdicts created
  ;; in Lupapiste, we are only interested in the existence of the
  ;; field, not the value.
  {:verdicts.draft {$exists true}})

(defn migration-updates [application timestamp]
  (merge {$unset {:verdicts ""}
          $set {:pate-verdicts (mapv #(->pate-legacy-verdict application
                                                             %
                                                             timestamp)
                                     (:verdicts application))}
          $pull {:tasks {:source.id {$in (draft-verdict-ids application)}}}}))
