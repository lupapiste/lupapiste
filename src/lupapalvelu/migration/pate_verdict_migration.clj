(ns lupapalvelu.migration.pate-verdict-migration
  (:require [clojure.edn :as edn]
            [clojure.set :refer [map-invert rename-keys]]
            [clojure.walk :refer [postwalk prewalk walk]]
            [monger.operators :refer :all]
            [taoensso.timbre :refer [infof warnf]]
            [schema.core :as sc]
            [sade.core :refer [def-]]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.pdf :as pdf]
            [lupapalvelu.pate.legacy-schemas :as legacy-schemas]
            [lupapalvelu.pate.schemas :as schemas :refer [PateLegacyVerdict]]
            [lupapalvelu.pate.schema-helper :as schema-helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.verdict :as verdict]
            [lupapalvelu.pate.verdict-common :as vc]))

;;
;; Helpers for operating on the verdict skeleton
;;

(defn- access [accessor-key]
  {::access accessor-key})

(def- accessor-key? (comp boolean ::access))

(defn- wrap
  "Wraps accessor function with metadata/wrap"
  [accessor]
  {::wrap accessor})

(def- wrap? (comp boolean ::wrap))

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

(defn- assoc-context [element context]
  (postwalk (fn [x]
              (if (accessor-key? x)
                (assoc x ::context context)
                x))
            element))

(defn- build-id-map
  "Builds a collection skeleton dynamically based on the data present in the
   `application` and `verdict`."
  [application verdict accessor-functions
   {{collection ::collection
     element    ::element} ::id-map-from}]
  (->> ((get accessor-functions collection) application verdict nil)
       (group-by :id)
       (util/map-values (comp (partial assoc-context element) first))
       not-empty))

(defn- build-array
  [application verdict accessor-functions
   {{collection ::collection
     element    ::element} ::array-from}]
  (->> ((get accessor-functions collection) application verdict nil)
       (mapv (partial assoc-context element))))

(defn- fetch-with-accessor
  "Given the `application` under migration, the source `verdict` and
  current `timestamp`, returns a function for accessing desired data
  from the `application` and `verdict`. Used with `prewalk`."
  [application verdict accessor-functions]
  (fn [x]
    (cond (accessor-key? x)
          (if-let [accessor-fn (get accessor-functions (::access x))]
            (accessor-fn application verdict (::context x))
            (throw (ex-info "Missing accessor" x)))

          (build-id-map? x)
          (build-id-map application verdict accessor-functions x)

          (build-array? x)
          (build-array application verdict accessor-functions x)

          :else x)))

(defn- post-process [timestamp]
  (fn [x]
    (if (wrap? x)
      (metadata/wrap "Verdict draft Pate migration" timestamp (::wrap x))
      x)))

;;
;; Helpers for accessing relevant data from current verdicts
;;

(defn- get-in-verdict [path & [default]]
  (fn [_ verdict _]
    (if-let [result (get-in verdict path)]
      result
      default)))

(defn- get-in-poytakirja [key & [default]]
  (get-in-verdict (conj [:paatokset 0 :poytakirjat 0] key) default))

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

(defn- verdict-category [application verdict _]
  (if (or (:sopimus verdict)
          (= (:permitSubtype application) "sijoitussopimus"))
    "migration-contract"
    (if-let [category (schema-util/application->category application)]
     (name category)
     "migration-verdict")))

(defn- verdict-template [app verdict _]
  {:inclusions (-> (verdict-category app verdict nil)
                   verdict/legacy-verdict-inclusions)})

(defn- get-when [p getter-fn]
  (fn [& args]
    (when (apply p args)
      (apply getter-fn args))))

(defn- verdict-published? [_ verdict _]
  (not (:draft verdict)))

(def contract-category? #{"contract" "migration-contract"})

(defn- contract? [app verdict _]
  (contract-category? (verdict-category app verdict nil)))

(defn- timestamp
  "If timestamp is 0, return nil"
  [val]
  (if (and (integer? val) (zero? val))
    nil
    val))

(defn- get-archive-data [_ verdict _]
  {:verdict-giver ((get-in-poytakirja :paatoksentekija "") nil verdict nil)
   :anto          (timestamp ((get-in-paivamaarat :anto) nil verdict nil))
   :lainvoimainen (timestamp ((get-in-paivamaarat :lainvoimainen) nil verdict nil))})

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

(def- verdict-state
  (comp #(if %
           "published"
           "draft")
        verdict-published?))

(def ->pate-review-type
  (->> schema-helper/review-type-map
       map-invert
       (util/map-values name)))

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
               :attachment-id (access :published-attachment-id)}
   :state (wrap (access :state))
   :data {:handler         (wrap (access :handler))
          :kuntalupatunnus (wrap (access :kuntalupatunnus))
          :verdict-section (wrap (access :verdict-section))
          :verdict-code    (wrap (access :verdict-code))
          :verdict-text    (wrap (access :verdict-text))
          :contract-text   (wrap (access :contract-text))
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

(defn- accessor-functions [defaults]
  "Contains functions for accessing relevant Pate verdict data from
  current verdict drafts. These return the raw values but are
  subsequently to be wrapped with relevant metadata."
  {:anto              (comp timestamp (get-in-paivamaarat :anto))
   :archive           (get-when verdict-published? get-archive-data)
   :attachment-summaries attachment-summaries
   :category          verdict-category
   :condition-name    (get-in-context [:taskname])
   :conditions        (filter-tasks-of-verdict (task-name? :task-lupamaarays))
   :context           context
   :contract-text     (get-when contract? (get-in-poytakirja :paatos))
   :foreman-role      (get-in-context [:taskname])
   :foremen           (filter-tasks-of-verdict (task-name? :task-vaadittu-tyonjohtaja))
   :handler           (get-in-poytakirja :paatoksentekija)
   :id                (get-in-verdict [:id])
   :kuntalupatunnus   (get-in-verdict [:kuntalupatunnus])
   :lainvoimainen     (comp timestamp (get-in-paivamaarat :lainvoimainen))
   :modified          (comp timestamp (get-in-verdict [:timestamp] (:modified defaults)))
   :published         (comp timestamp (get-when verdict-published? (get-in-paivamaarat :anto)))
   :published-attachment-id (get-when verdict-published? published-attachment-id)
   :review-name       (get-in-context [:taskname])
   :review-type       (comp ->pate-review-type (get-in-context [:data :katselmuksenLaji :value]))
   :reviews           (filter-tasks-of-verdict (task-name? :task-katselmus))
   :signature-date    (comp timestamp (get-in-context [:created]))
   :signature-name    signature-name
   :signature-user-id (get-in-context [:user :id])
   :signatures        (get-in-verdict [:signatures])
   :state             verdict-state
   :template          verdict-template
   :verdict-code      (comp str (get-in-poytakirja :status))
   :verdict-section   (get-in-poytakirja :pykala)
   :verdict-text      (get-when (complement contract?) (get-in-poytakirja :paatos))})

(defn- defaults [timestamp]
  {:modified timestamp})

(defn- add-tags
  "For published verdicts, adds :tags, wich is a serialized hiccup
  string representing the verdict."
  [application verdict]
  {:post [(if (-> % :published :published) (edn/read-string (-> % :published :tags)) true)]}
  (try
    (if (-> verdict :published :published)
      (assoc-in verdict [:published :tags]
                (ss/serialize (pdf/verdict-tags application
                                                (metadata/unwrap-all verdict))))
      verdict)
    (catch Throwable e
      (throw (ex-info (str "Failed to build tags for application "
                           (:id application)
                           ", verdict "
                           (:id verdict))
                      {:verdict verdict}
                      e)))))

(defn- check-verdict
  "Checks whether the verdict has valid dictionary data and conforms
  to legacy verdict schema. Returns nil on success, otherwise
  validation errors."
  [verdict]
  (if-let [errors (schemas/validate-dictionary-data (legacy-schemas/legacy-verdict-schema (:category verdict))
                                                    (dissoc (:data (metadata/unwrap-all verdict))
                                                            :attachments
                                                            ;; TODO do we need to dissoc other fields?
                                                            ))]
    {:errors errors}
    (sc/check PateLegacyVerdict verdict)))


(defn- update-template [verdict app]
  (assoc verdict :template (verdict-template app verdict nil)))

(defn change-category [app verdict new-category]
  (add-tags app
            (-> verdict
                (assoc :category new-category)
                (update-template app))))

(defn ensure-valid-category [app verdict]
  (if-let [errors (check-verdict verdict)]
    (let [new-category (if (contract-category? (:category verdict)) "migration-contract" "migration-verdict")]
      (warnf "Verdict %s did not conform to category %s: %s" (:id verdict) (:category verdict) (str errors))
      (infof "Changing category of verdict %s to %s" (:id verdict) new-category)
      (change-category app verdict new-category))
    verdict))

(defn validate-verdict
  "Throws if the verdict does not validate according to check-verdict,
  otherwise returns the given verdict"
  [verdict]
  (if-let [errors (check-verdict verdict)]
    (throw (ex-info (str "Invalid dictionary data for verdict " (:id verdict))
                    errors))
    verdict))

(defn ->pate-legacy-verdict
  "Builds a Pate legacy verdict from the old one"
  [application verdict timestamp]
       ;; Build a new verdict by fetching the necessary data from the old one
  (->> (prewalk (fetch-with-accessor application
                                     verdict
                                     (accessor-functions (defaults timestamp)))
                verdict-migration-skeleton)
       ;; Add metadata to fields marked for wrapping in the verdict skeleton
       (postwalk (post-process timestamp))
       ;; Cleanup
       util/strip-nils
       util/strip-empty-collections
       ;; Add tags using the almost finished Pate verdict
       (add-tags application)
       ;; If the verdict doesn't validate for the intended category, use more
       ;; permissive migration-verdict or migration-contract
       (ensure-valid-category application)
       ;; Finally, validate the verdict again
       validate-verdict))

(defn- draft-verdict-ids
  "Return the ids of the draft verdicts in the application"
  [application]
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

(defn lupapiste-verdict?
  "Is the verdict created in Lupapiste, as opposed to having been
  fetched from a backing system"
  [verdict]
  (contains? verdict :draft))

(defn migration-updates
  "Return a map of mongo updates for a given application"
  [application timestamp]
  (let [lupapiste-verdicts (filter lupapiste-verdict?
                                   (:verdicts application))
        draft-ids (draft-verdict-ids application)]
    (merge {$set {:pate-verdicts (mapv #(->pate-legacy-verdict application
                                                               %
                                                               timestamp)
                                       lupapiste-verdicts)
                  :pre-pate-verdicts (:verdicts application)}
            $pull (merge (when (not-empty draft-ids)
                           {:tasks {:source.id {$in draft-ids}}})
                         {:verdicts {:id {$in (mapv :id lupapiste-verdicts)}}})})))
