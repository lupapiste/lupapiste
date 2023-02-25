(ns lupapalvelu.pate.verdict-template
  (:require [clojure.set :as set]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as ops]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.schemas :as schemas]
            [lupapalvelu.pate.settings-schemas :as settings-schemas]
            [lupapalvelu.pate.verdict-template-schemas :as template-schemas]
            [monger.operators :refer :all]
            [plumbing.core :refer [defnk]]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(defn command->organization
  "User-organizations is not available for input-validators."
  [{:keys [user-organizations organization data]}]
  (if organization
    @organization
    (util/find-by-id (:organizationId data)
                     user-organizations)))

(defn command->options
  "Returns map with every data argument and also organization,
  timestamp, user, lang and wrapper. Also application, if
  available. The original command is stored just in case."
  [{:keys [created lang data application user] :as command}]
  (let [m (util/filter-map-by-val identity
                                  {:timestamp    created
                                   :wrapper      (metadata/wrapper command)
                                   :organization (command->organization command)
                                   :user         user
                                   :application  application
                                   :command      command})]
    (assert (empty? (util/intersection-as-kw (keys data)
                                             (keys m)))
            "Command->options key conflict.")
    ;; Lang does not cause conflict, because override is a reasonable
    ;; use case
    (merge data m {:lang (or (:lang data) lang)})))

(defn organization-categories [{scope :scope}]
  (->> scope
       (filter #(get-in % [:pate :enabled]))
       (map (comp schema-util/permit-type->categories :permitType))
       flatten
       set
       (set/intersection schema-util/pate-categories)))

(defn operation->category [operation]
  (or (schema-util/category-by-operation operation)
      (-> operation
          ops/permit-type-of-operation
          schema-util/permit-type->categories
          first)))

(defn error-response [{:keys [failure errors]}]
  (if failure
    (fail failure)
    (ok :errors errors)))

(defn changes-response
  "Encriches given response (changes and removals) with dictionary changes that need to be passed to
  the frontend. Typically, the frontend is already updated before the command call."
  [{:keys [changes removals] :as response} {:keys [op path value]}]
  (merge response
         (case op
           ;; New repeating grid row or :row-order change for a (non-repeating, top-level) grid.
           :add {:changes (concat changes [[path value]])}
           ;; Repeating grid row has been removed.
           :remove {:removals (concat removals [path])}
           ;; Repeating grid row has been reordered.
           :order {:changes (->> value
                                 (map (fn [[k v]]
                                        (let [[dict value] (first v)]
                                          [(concat path [k dict]) value])))
                                 (concat changes))}
           {})))

(declare settings)

(defn- sync-repeatings
  "Sync the template repeating (t-rep) according to settings repeating. Every language version
  of (plan/review) is copied. In addition, the manual order dicts are copied if present."
  [manual-dict s-rep t-rep]
  (letfn [(update-target [target source dict lang]
            (assoc target
                   (->> (map name [dict lang])
                        (ss/join "-")
                        keyword)
                   (get source lang (i18n/localize lang :pate.no-name))))
          (reduce-langs [target source dict]
            (reduce #(update-target %1 source dict %2)
                    target
                    helper/supported-languages))]
    (reduce-kv (fn [acc k v]
                 (cond-> (update acc k reduce-langs v :text)
                   manual-dict (assoc-in [k manual-dict] (manual-dict v))))
               (select-keys t-rep (keys s-rep))
               s-rep)))

(def template-settings-dependencies [:plans :reviews :handler-titles])

(defn verdict-template-settings-dependencies
  "Reviews and plans information from setting is merged into the
  corresponding template repeatings. Copying is done when the template
  dictionary has :reviews or :plans. Note that the copied information
  is not wrapped. Returns the updated template."
  [options {:keys [category draft] :as template}]
  (let [{dic :dictionary} (template-schemas/verdict-template-schema category)
        {s-data :draft}   (settings (assoc options :category category))]
    (reduce (fn [acc rep-dict]
              (let [repeating-data (sync-repeatings (get-in dic [rep-dict :sort-by :manual])
                                                    (rep-dict s-data)
                                                    (rep-dict draft))]
                (if (empty? repeating-data)
                  (util/dissoc-in acc [:draft rep-dict])
                  (assoc-in acc [:draft rep-dict] repeating-data))))
            template
            (util/intersection-as-kw template-settings-dependencies
                                     (keys dic)))))

(defn new-verdict-template
  ([{:keys [organizationId wrapper timestamp lang category draft name] :as options}]
   (let [data (verdict-template-settings-dependencies
               options
               {:id       (mongo/create-id)
                :draft    (or draft {})
                :name     (wrapper (or name
                                       (i18n/localize lang
                                                      (if (util/=as-kw category :contract)
                                                        :pate.contract.template
                                                        :pate-verdict-template))))
                :category category
                :modified timestamp
                :deleted  (wrapper false)})]
     (mongo/update-by-id :organizations
                         organizationId
                         {$push {:verdict-templates.templates
                                 (sc/validate schemas/PateSavedTemplate
                                              data)}})
     (metadata/unwrap-all data))))

(defnk verdict-template [organization template-id]
  (some->> organization :verdict-templates :templates
           (util/find-by-id template-id)
           metadata/unwrap-all))

(defn verdict-template-summary [{published :published :as template}]
  (assoc (select-keys template
                      [:id :name :modified :deleted :category])
         :published (:published published)))

(declare settings-filled? template-filled?)


(defnk verdict-template-response-data [organizationId :as options]
  (let [template (verdict-template options)]
    (assoc (verdict-template-summary template)
           :draft (:draft template)
           :filled (template-filled? {:template template}))))

(defn verdict-template-check
  "Returns prechecker for template-id parameters.
   Condition parameters:
     :editable    Template must be editable (not deleted)
     :published   Template must hav been published
     :blank       Template-id can be empty. Note: this does not
                  replace input-validator.
     :named       Template name cannot be empty
     :application Template must belong to the same category as the
                  application
     :filled      All the required fields (both in the template and
                  settings) must have been filled.

   Template's existence is always checked unless :blank matches."
  [& conditions]
  (let [{:keys [editable published blank
                named application filled]} (zipmap conditions
                                                   (repeat true))]
    (fn [{{template-id :template-id} :data :as command}]
      (when template-id
        (if (ss/blank? template-id)
          (when-not blank
            (fail :error.missing-parameters))
          (let [{:keys [organization]
                 :as   options} (command->options command)
                template        (verdict-template options)]
            (when-not template
              (fail! :error.verdict-template-not-found))
            (when (and editable (:deleted template))
              (fail! :error.verdict-template-deleted))
            (when (and published (not (:published (verdict-template-summary template))))
              (fail! :error.verdict-template-not-published))
            (when (and named (-> template :name ss/blank?))
              (fail! :error.verdict-template-name-missing))
            (when (and application
                       (util/not=as-kw (:category template)
                                       (-> command :application
                                           schema-util/application->category)))
              (fail! :error.invalid-category))
            (when (and filled (or (not (template-filled? {:template template}))
                                  (not (settings-filled? (assoc options
                                                                :category (:category template))))))
              (fail! :pate.required-fields))))))))

(defn- template-update [{:keys [organization template-id timestamp]} update]
  (mongo/update :organizations
                {:_id               (:id organization)
                 :verdict-templates.templates {$elemMatch {:id template-id}}}
                (cond-> (ss/trimwalk update)
                  timestamp (assoc-in [$set :verdict-templates.templates.$.modified]
                                      timestamp))))

(defnk verdict-template-update-and-open [organization wrapper template-id timestamp :as options]
  (let [{draft :draft :as data} (verdict-template-response-data options)
        {new-draft :draft :as updated} (verdict-template-settings-dependencies options data)
        updates                 (reduce (fn [acc dict]
                                          (let [new-dict-value (dict new-draft)]
                                            (if (not= (dict draft) new-dict-value)
                                              (assoc-in acc
                                                        [$set
                                                         (util/kw-path :verdict-templates.templates.$.draft
                                                                       dict)]
                                                        (wrapper new-dict-value))
                                              acc)))
                                        nil
                                        template-settings-dependencies)]
    (if updates
      (do
        (template-update options updates)
        (assoc updated :modified timestamp))
      data)))

(defnk settings [organization category]
  (some->> [:verdict-templates :settings (keyword category)]
           (get-in organization)
           metadata/unwrap-all))

(defn- pack-dependencies
  "Packs settings dependency (either :plans or :reviews). Only included items are packed. The
  selection status is packed as well. The result is a list with (at least) localisation (e.g., :fi)
  and :selected properties.

  If the packed repeating is sorted manually, the result is sorted accordingly, but the sort
  key (manual dict) is not included."
  [category settings dep-key template-data]
  (let [manual (get-in (settings-schemas/settings-schema category)
                       [:dictionary dep-key :sort-by :manual])
        t-data (->> template-data
                    dep-key
                    (filter (util/fn-> last :included))
                    (into {}))
        packed (map (fn [[k v]]
                      (assoc v
                             :selected (boolean (-> t-data
                                                    k
                                                    :selected))))
                    (select-keys (dep-key settings) (keys t-data)))]
    (if manual
      (->> packed (sort-by manual) (map #(dissoc % manual)))
      packed)))

(defn- pack-verdict-dates
  "Since the date calculation is cumulative we always store every delta _in the schema_ into
  kw-delta map. Including those that are not selected in the template. Empty deltas are
  zeros. For board-verdicts the appeal date (muutoksenhaku) is different.

  The `draft` can be template data, if the `custom-date-deltas` is true (then also `board-verdict?`
  is false)"
  [category draft board-verdict?]
  (let [{dic :dictionary} (settings-schemas/settings-schema category)]
    (cond-> (->> (helper/verdict-dates category)
                 (map (fn [k]
                        [k {:delta (-> draft k schemas/parse-int)
                            :unit (name (get-in dic [k :date-delta :unit] :days))}]))
                 (into {}))
      board-verdict? (assoc :muutoksenhaku {:delta (-> draft
                                                       :lautakunta-muutoksenhaku
                                                       schemas/parse-int)
                                            :unit  (name (get-in dic
                                                                 [:lautakunta-muutoksenhaku
                                                                  :date-delta :unit]))}))))

(defn- published-settings
  "The published settings only include lists without schema-ordained
  structure. Term list items contain the localisations. In other words,
  subsequent localisation changes do not affect already published
  verdict templates."
  [options category template-data]
  (let [draft          (:draft (settings (assoc options :category category)))
        board-verdict? (util/=as-kw (:giver template-data) :lautakunta)]
    (merge (select-keys draft (cond-> [:verdict-code :organization-name]
                                board-verdict? (conj :boardname)))
           {:date-deltas        (if (:custom-date-deltas template-data)
                                  (pack-verdict-dates category template-data false)
                                  (pack-verdict-dates category draft board-verdict?))
            :plans              (pack-dependencies category draft :plans template-data)
            :reviews            (pack-dependencies category draft :reviews template-data)
            :handler-titles     (pack-dependencies category draft :handler-titles template-data)})))

(defn save-draft-value
  "Error code on failure (see schemas for details)."
  [{:keys [wrapper path value] :as options}]
  (let [{:keys [category draft]} (verdict-template options)
        {:keys [path value op]
         :as processed} (schemas/validate-and-process-value
                         (template-schemas/verdict-template-schema category)
                         path
                         value
                         draft
                         {:settings (:draft (settings (assoc options
                                                             :category category)))})]
    (when op ;; Value could be nil
      (let [mongo-path (util/kw-path (cons :verdict-templates.templates.$.draft
                                           path))]
        (template-update options
                         (if (= op :remove)
                          {$unset {mongo-path 1}}
                          {$set {mongo-path (wrapper value)}}))))
    (assoc processed :category category)))

(defn- draft-for-publishing
  "Extracts template draft data for publishing. Keys with empty values
  are omitted. However, removed-sections do not affect the data
  selection, since the verdicts may handle removed-sections
  differently (e.g., foremen and reviews). Transforms :repeating in
  template draft from map of maps to sequence of maps."
  [{:keys [category draft]}]
  (letfn [(process-dictionary [dic data]
            (into {}
                  (for [[dict {:keys [repeating]}] dic
                        :let                       [v (get data dict)]
                        :when                      (if (coll? v)
                                (not-empty v)
                                (ss/not-blank? (str v)))]
                    [dict (if repeating
                            (map (partial process-dictionary repeating) (vals v))
                            v)])))]
    (-> (template-schemas/verdict-template-schema category)
        :dictionary
        (util/strip-matches :excluded?)
        (process-dictionary draft))))

(defn- template-inclusions
  "List if included top-level dicts. Dict is excluded if it
  belongs (only) to removed section and the section is not always
  included. The list is used when resolving the :template-dict
  references in verdicts."
  [{:keys [category draft]}]
  (let [{:keys [dictionary
                sections]} (template-schemas/verdict-template-schema category)
        dictionary         (util/strip-matches dictionary :excluded?)
        dict-secs          (schemas/dict-sections sections)
        always-included    (->> (filter :always-included? sections)
                                    (map :id)
                                    set)
        removed-sections   (set/difference (->> (:removed-sections draft)
                                                    (map (fn [[k v]]
                                                           (when v k)))
                                                    (remove nil?)
                                                    set)
                                               always-included)

        ]
    (->> dict-secs
         (reduce-kv (fn [acc dict sections]
                      (cond-> acc
                        (and (dict dictionary)
                          (or (empty? sections)
                                 (not-empty (set/difference sections
                                                            removed-sections))))
                        (conj dict)))
                    (util/difference-as-kw (keys dictionary)
                                           (keys dict-secs)
                                           [:removed-sections]))
         ;; Strings due to smoke tests (values are strings in mongo)
         (map name)
         sort)))

(defnk publish-verdict-template [organization wrapper template-id timestamp
                                 :as options]
  (let [{:keys [draft category]
         :as   template} (verdict-template options)
        settings         (sc/validate schemas/PatePublishedTemplateSettings
                                      (published-settings options
                                                          category
                                                          draft))]
    (template-update (dissoc options :timestamp)
                     {$set {:verdict-templates.templates.$.published
                            {:published  timestamp
                             :data       (apply dissoc (draft-for-publishing template) template-settings-dependencies)
                             :inclusions (template-inclusions template)
                             :settings   settings}}})))

(defnk set-name [name wrapper :as options]
  (template-update options
                   {$set {:verdict-templates.templates.$.name
                          (wrapper name)}}))

(defnk set-deleted [delete wrapper :as options]
  (template-update (dissoc options :timestamp)
                   {$set {:verdict-templates.templates.$.deleted
                          (wrapper delete)}}))

(defnk copy-verdict-template [lang wrapper :as options]
  (let [{name :name :as template} (verdict-template options)]
    (new-verdict-template (-> (select-keys template
                                           [:draft :category])
                              (update :draft (partial metadata/wrap-all wrapper) )
                              (merge options
                                     {:name (format "%s (%s)"
                                                    (metadata/unwrap name)
                                                    (i18n/localize lang
                                                                   :pate-copy-postfix))})))))

(defn- settings-key [category & extra]
  (->> [:verdict-templates.settings category extra]
       flatten
       (remove nil?)
       (map name)
       (ss/join ".")
       keyword))

(defn save-settings-value [{:keys [organization wrapper category timestamp path value]
                            :as   options}]
  (let [settings-key      (settings-key category)
        {:keys [path value op]
         :as   processed} (schemas/validate-and-process-value (settings-schemas/settings-schema category)
                                                              path
                                                              value
                                                              (:draft (settings options)))]
    (when op  ;; Value could be nil.
      (let [mongo-path (util/kw-path settings-key :draft path)]
        (mongo/update-by-id :organizations
                            (:id organization)
                            (assoc-in (case op
                                        :remove {$unset {mongo-path 1}}
                                        :order (->> value
                                                    (map (fn [[k v]]
                                                           (let [[dict value] (first v)]
                                                             [(util/kw-path mongo-path k dict) value])))
                                                    (into {})
                                                    (hash-map $set))
                                        {$set {mongo-path (wrapper value)}})
                                      [$set (util/kw-path settings-key :modified)]
                                      timestamp))))
    processed))

(defn settings-filled?
  "Settings are filled properly if every required field has been filled."
  [{ready :settings data :data category :category :as options}]
  (schemas/required-filled? (settings-schemas/settings-schema category)
                            (or data
                                (:draft (or ready
                                            (settings options))))))

(defn template-filled?
  "Template is filled when every required field has been filled."
  [{:keys [template template-id data category] :as options}]
  (let [{t-cat  :category
         t-data :draft} (or template
                            (when template-id
                              (verdict-template options)))
        category        (or category t-cat)]
    (schemas/required-filled? (template-schemas/verdict-template-schema category)
                              (or data t-data))))

;; Default operation verdict templates

(defn- published-available-templates [organization]
  (->> organization
       :verdict-templates
       :templates
       (remove :deleted)
       (filter :published)))

(defnk operation-verdict-templates [organization]
  (let [organization (metadata/unwrap-all organization)
        published (->> (published-available-templates organization)
                       (map :id)
                       set)]
    (->> organization
         :operation-verdict-templates
         (filter (fn [[_ v]] (contains? published v)))
         (into {}))))

(defn set-operation-verdict-template [organizationId operation template-id]
  (let [path (util/kw-path :operation-verdict-templates operation)]
    (org/update-organization organizationId
                             (if (ss/blank? template-id)
                               {$unset {path true}}
                               {$set {path template-id}}))))

(defn application-verdict-templates [{:keys [organization]}
                                     {:keys [primaryOperation]
                                      :as   application}]
  (let [{:keys [operation-verdict-templates
                verdict-templates]} organization
        app-category                (schema-util/application->category application)
        app-operation               (-> primaryOperation :name keyword)]
    (->> verdict-templates
         :templates
         metadata/unwrap-all
         (filter (fn [{:keys [deleted published category]}]
                   (and (not deleted)
                        published
                        (util/=as-kw category app-category))))
         (map (fn [{:keys [id name]}]
                {:id       id
                 :name     name
                 :default? (= id (get operation-verdict-templates
                                      app-operation))})))))

(defnk selectable-verdict-templates [organization]
  (let [published (published-available-templates (metadata/unwrap-all organization))]
    (->> published
         (map (fn [{:keys [category] :as template}]
                (assoc template
                       :permit-type
                       (schema-util/category->permit-type category))))
         (filter :permit-type)
         (group-by :permit-type)
         (map (fn [[permit-type templates]]
                [permit-type (map #(select-keys % [:id :name]) templates)]))
         (into {})
         (merge (->> (map schema-util/category->permit-type
                          schema-util/pate-categories)
                     (remove nil?)
                     (map #(vector % []))
                     (into {}))
                (->> schema-util/operation-categories
                     (map (fn [[operation category]]
                            [operation (->> published
                                            (filter #(util/=as-kw category (:category %)))
                                            (map #(select-keys % [:name :id])))]))
                     (into {}))))))
