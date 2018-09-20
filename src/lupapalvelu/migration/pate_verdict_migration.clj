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
            [lupapalvelu.data-skeleton :as ds]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.pdf :as pdf]
            [lupapalvelu.pate.legacy-schemas :as legacy-schemas]
            [lupapalvelu.pate.schemas :as schemas :refer [PateLegacyVerdict]]
            [lupapalvelu.pate.schema-helper :as schema-helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.verdict :as verdict]
            [lupapalvelu.pate.verdict-common :as vc]))

(defn- wrap
  "Wraps accessor function with metadata/wrap"
  [accessor]
  {::wrap accessor})

(def- wrap? (comp boolean ::wrap))

(defn wrap-metadata [x timestamp]
  (metadata/wrap "Verdict draft Pate migration" timestamp x))

(defn- post-process [timestamp]
  (fn [x]
    (if (wrap? x)
      (wrap-metadata (::wrap x) timestamp)
      x)))

;;
;; Helpers for accessing relevant data from current verdicts
;;

(defn- get-in-verdict [path & [default]]
  (fn [{:keys [verdict]}]
    (if-let [result (get-in verdict path)]
      result
      default)))

(defn- get-in-poytakirja [key & [default]]
  (get-in-verdict (conj [:paatokset 0 :poytakirjat 0] key) default))

(defn- get-in-paivamaarat [key]
  (get-in-verdict (conj [:paatokset 0 :paivamaarat] key)))

(defn- filter-tasks-of-verdict [p]
  (fn [{:keys [application verdict]}]
    (->> application :tasks
         (filter #(and (= (:id verdict)
                          (-> % :source :id))
                       (p %))))))
(defn- task-name? [tn]
  (fn [task]
    (util/=as-kw (-> task :schema-info :name)
                 tn)))

(defn- get-in-context [path]
  (fn [{:keys [context]}]
    (get-in context path)))

(defn- context [{:keys [context]}]
  context)

(defn- verdict-category [{:keys [application verdict]}]
  (if (or (not-empty (:signatures verdict))
          (:sopimus verdict)
          (= (:permitSubtype application) "sijoitussopimus"))
    "migration-contract"
    (if-let [category (schema-util/application->category application)]
     (name category)
     "migration-verdict")))

(defn- verdict-template [context]
  {:inclusions (-> (verdict-category context)
                   verdict/legacy-verdict-inclusions)})

(defn- verdict-code-is-free-text? [category]
  (-> category
      legacy-schemas/legacy-verdict-schema
      :dictionary :verdict-code
      (contains? :text)))

(defn- verdict-code-for-category
  "If verdict code should be free text, attempt to localize the
  numeric 'status', falling back to 'code', which can be
  eg. 'hyv\00e4ksytty'. If it should be numeric, use 'status' as string."
  [context category]
  (if (verdict-code-is-free-text? category)
    (i18n/try-localize (fn [& args]
                         ((get-in-poytakirja :code) context))
                       "fi"
                       ["verdict" "status" (str ((get-in-poytakirja :status) context))])
    (str ((get-in-poytakirja :status) context))))

(defn verdict-code [context]
  (verdict-code-for-category context
                             (verdict-category context)))

(defn- get-when [p getter-fn]
  (fn [context]
    (when (p context)
      (getter-fn context))))

(defn- verdict-published? [{:keys [verdict]}]
  (not (:draft verdict)))

(def contract-category? #{"contract" "migration-contract"})

(defn- contract? [context]
  (contract-category? (verdict-category context)))

(defn- timestamp
  "For unpublished verdicts, if timestamp is 0, return nil. If the
  verdict is published, we have to accept the 0 timestamp."
  [accessor-fn]
  (fn [context]
    (let [val (accessor-fn context)]
      (if (and (not (verdict-published? context))
               (integer? val)
               (zero? val))
        nil
        val))))

(defn- get-archive-data [context]
  {:verdict-giver ((get-in-poytakirja :paatoksentekija "") context)
   :anto          ((timestamp (get-in-paivamaarat :anto)) context)
   :lainvoimainen ((timestamp (get-in-paivamaarat :lainvoimainen)) context)})

(defn- targets-verdict? [attachment verdict]
  (or (= (:id verdict)
         (-> attachment :source :id))
      (= (:id verdict)
         (-> attachment :target :id))))

;; See lupapalvelu.pate.verdict/verdict-attachment-items
(defn- attachment-summaries [{:keys [application verdict]}]
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

(defn- published-attachment-id [{:keys [application verdict]}]
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
   migrated verdict, `(ds/access :x)` will be replaced by calling the accessor
   function found under the key :x in the accessor function map. See `accessors`."
  {:id        (ds/access :id)
   :modified  (ds/access :modified)
   :category  (ds/access :category)
   :published {:published     (ds/access :published)
               :attachment-id (ds/access :published-attachment-id)}
   :state (wrap (ds/access :state))
   :data {:handler         (wrap (ds/access :handler))
          :kuntalupatunnus (wrap (ds/access :kuntalupatunnus))
          :verdict-section (wrap (ds/access :verdict-section))
          :verdict-code    (wrap (ds/access :verdict-code))
          :verdict-text    (wrap (ds/access :verdict-text))
          :contract-text   (wrap (ds/access :contract-text))
          :anto            (wrap (ds/access :anto))
          :lainvoimainen   (wrap (ds/access :lainvoimainen))
          :reviews         (ds/id-map-from :reviews
                                           {:name (wrap (ds/access :review-name))
                                            :type (wrap (ds/access :review-type))})
          :foremen         (ds/id-map-from :foremen
                                           {:role (wrap (ds/access :foreman-role))})
          :conditions      (ds/id-map-from :conditions
                                           {:name (wrap (ds/access :condition-name))})
          :attachments     (ds/array-from :attachment-summaries
                                          (ds/access :context))}
   :signatures (ds/array-from :signatures
                              {:date (ds/access :signature-date)
                               :name (ds/access :signature-name)
                               :user-id (ds/access :signature-user-id)})
   :template (ds/access :template)
   :archive (ds/access :archive)
   :legacy? true})

(defn- accessor-functions [defaults]
  "Contains functions for accessing relevant Pate verdict data from
  current verdict drafts. These return the raw values but are
  subsequently to be wrapped with relevant metadata."
  {:anto              (timestamp (get-in-paivamaarat :anto))
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
   :lainvoimainen     (timestamp (get-in-paivamaarat :lainvoimainen))
   :modified          (timestamp (get-in-verdict [:timestamp] (:modified defaults)))
   :published         (timestamp (get-when verdict-published? (get-in-paivamaarat :anto)))
   :published-attachment-id (get-when verdict-published? published-attachment-id)
   :review-name       (get-in-context [:taskname])
   :review-type       (comp ->pate-review-type (get-in-context [:data :katselmuksenLaji :value]))
   :reviews           (filter-tasks-of-verdict (task-name? :task-katselmus))
   :signature-date    (timestamp (get-in-context [:created]))
   :signature-name    signature-name
   :signature-user-id (get-in-context [:user :id])
   :signatures        (get-in-verdict [:signatures])
   :state             verdict-state
   :template          verdict-template
   :verdict-code      verdict-code
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
  (assoc verdict :template (verdict-template {:application app
                                              :verdict verdict})))

(defn- update-verdict-code [verdict original-verdict new-category timestamp]
  (assoc-in verdict [:data :verdict-code ]
            (wrap-metadata (verdict-code-for-category {:verdict original-verdict}
                                                      new-category)
                           timestamp)))

(defn change-category [app verdict original-verdict new-category timestamp]
  (add-tags app
            (-> verdict
                (assoc :category new-category)
                (update-verdict-code original-verdict new-category timestamp)
                (update-template app))))

(defn ensure-valid-category [app original-verdict timestamp verdict]
  (if-let [errors (check-verdict verdict)]
    (let [new-category (if (contract-category? (:category verdict)) "migration-contract" "migration-verdict")]
      (warnf "Verdict %s did not conform to category %s: %s" (:id verdict) (:category verdict) (str errors))
      (infof "Changing category of verdict %s to %s" (:id verdict) new-category)
      (change-category app verdict original-verdict new-category timestamp))
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
  (->> (ds/build-with-skeleton verdict-migration-skeleton
                               {:application application
                                :verdict     verdict}
                               (accessor-functions (defaults timestamp)))
       ;; Add metadata to fields marked for wrapping in the verdict skeleton
       (postwalk (post-process timestamp))
       ;; Cleanup
       util/strip-nils
       util/strip-empty-collections
       ;; Add tags using the almost finished Pate verdict
       (add-tags application)
       ;; If the verdict doesn't validate for the intended category, use more
       ;; permissive migration-verdict or migration-contract
       (ensure-valid-category application verdict timestamp)
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
