(ns lupapalvelu.pate.verdict-template
  (:require [clojure.set :as set]
            [lupapalvelu.application :as app]
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
            [lupapalvelu.pate.verdict-util :as pate-vutil]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer :all]))

(defn command->organization
  "User-organizations is not available for input-validators."
  [{:keys [user-organizations organization data]}]
  (or (force organization)
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

(defn organization-categories
  "Returns the verdict-template categories available to the organization"
  [{scope :scope :as organization}]
  (->> scope
       (filter #(get-in % [:pate :enabled]))
       (map (fn [{:keys [permitType]}]
              (into (schema-util/permit-type->categories permitType)
                    (when (:rectification-verdicts-enabled organization)
                      [:oik]))))
       (reduce into #{})
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
  "Enriches given response (changes and removals) with dictionary changes that need to be passed to
  the frontend. Typically, the frontend is already updated before the command call."
  [{:keys [changes removals] :as response} {:keys [op path value]}]
  (merge response
         (case op
           ;; New repeating grid row or :row-order change for a (non-repeating, top-level) grid.
           :add    {:changes (into [] (concat changes [[path value]]))}
           ;; Repeating grid row has been removed.
           :remove {:removals (into [] (concat removals [path]))}
           ;; Repeating grid row has been reordered.
           :order  {:changes (->> value
                                  (map (fn [[k v]]
                                         (let [[dict value] (first v)]
                                           [(into [] (concat path [k dict])) value])))
                                  (concat changes)
                                  (into []))}
           {})))

(declare settings)

(defn- sync-repeatings
  "Sync the template repeating (t-rep) according to settings repeating. Every language version
  of (plan/review/handler/etc) is copied. In addition, the manual order dicts are copied if present.
  For verdict givers with first name/last name, the full name is shown instead of the language text.
  For verdict-givers only, validity flags are added for UI warnings."
  [rep-dict manual-dict s-rep t-rep]
  (letfn [(fullname [source]
            (let [fname (ss/blank-as-nil (:first-name source))
                  lname (ss/blank-as-nil (:last-name source))]
              (when (or fname lname)
                (ss/trim (str fname " " lname)))))
          (update-target [target source dict lang]
            (let [suffix   (some->> (:code source) ss/blank-as-nil (format " (%s)"))
                  name-txt (or (fullname source)
                               (get source lang (i18n/localize lang :pate.no-name)))]
              (assoc target
                     (->> (map name [dict lang])
                          (ss/join "-")
                          keyword)
                     (cond-> name-txt
                       suffix (str suffix)))))
          (reduce-langs [target source dict]
            (reduce #(update-target %1 source dict %2)
                    target
                    helper/supported-languages))]
    (reduce-kv (fn [acc k v]
                 (cond-> (update acc k reduce-langs v :text)
                   (= rep-dict :verdict-givers)
                   (-> (assoc-in [k :valid-for-viranhaltija] (pate-vutil/valid-verdict-giver? :viranhaltija v))
                       (assoc-in [k :valid-for-lautakunta] (pate-vutil/valid-verdict-giver? :lautakunta v)))
                   manual-dict (assoc-in [k manual-dict] (manual-dict v))))
               (select-keys t-rep (keys s-rep))
               s-rep)))

(def template-settings-dependencies
  [:plans :reviews :handler-titles :verdict-givers :verdict-giver-titles])

(defn verdict-template-settings-dependencies
  "Reviews and plans information from setting is merged into the
  corresponding template repeatings. Copying is done when the template
  dictionary has :reviews, :plans, :handlers or other tables.
  Note that the copied information is not wrapped. Returns the updated template."
  [options {:keys [category draft] :as template}]
  (let [{dic :dictionary} (template-schemas/verdict-template-schema category)
        {s-data :draft}   (ss/trimwalk (settings (assoc options :category category)))]
    (reduce (fn [acc rep-dict]
              (let [repeating-data (sync-repeatings rep-dict
                                                    (get-in dic [rep-dict :sort-by :manual])
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
                {:id           (mongo/create-id)
                 :organization organizationId
                 :draft        (or draft {})
                 :name         (wrapper (or name
                                            (i18n/localize lang
                                                           (if (util/=as-kw category :contract)
                                                             :pate.contract.template
                                                             :pate-verdict-template))))
                 :category     category
                 :modified     timestamp
                 :created      timestamp
                 :deleted      (wrapper false)})]
     (mongo/insert :verdict-templates data)
     (metadata/unwrap-all data))))

(def- ^:private no-sheriff-notes {:_sheriffi-notes 0})

(defn- make-query
  [organization-id {:keys [published? deleted? id category]}]
  (let [published (when (some? published?)
                    {:published {$exists published?}})
        deleted   (cond
                    (or published? (false? deleted?)) {:deleted._value {$ne true}}
                    deleted?                          {:deleted._value true})
        in-or-v   (fn [v]
                    (util/pcond-> v
                      coll? (some->> seq (hash-map $in))))]
    (-> {:organization organization-id}
         (merge published deleted)
         (util/assoc-when :category (in-or-v category)
                          :_id (in-or-v id)))))

(defn verdict-templates [organization-id & {:keys [projection raw?]
                                            :or   {raw?       false
                                                   projection no-sheriff-notes}
                                            :as   options}]
  ;; Regular select does not support exclusionary projection.
  (cond->> (mongo/select-options :verdict-templates
                                 (make-query organization-id options)
                                 {:projection projection})
    (not raw?) metadata/unwrap-all
    true       vec))

(defn verdict-template
  ([organization-id template-id]
   (first (verdict-templates organization-id :id template-id)))
  ([{:keys [organization template-id]}]
   (verdict-template (:id organization) template-id)))

(defn remove-verdict-templates
  "The only use case for removing templates is import/export. Otherwise templates can be
  marked deleted, but not actually removed from mongo."
  [organization-id & {:as options}]
  (mongo/remove-many :verdict-templates (make-query organization-id options)))

(defn verdict-template-summary [{published :published :as template}]
  (assoc (select-keys template
                      [:id :name :modified :deleted :category])
         :published (:published published)))

(declare settings-filled? template-filled?)

(defn verdict-template-response-data [options]
  (let [template (verdict-template options)]
    (assoc (verdict-template-summary template)
           :draft (:draft template)
           :filled (template-filled? {:template template}))))

(defn verdict-template-check
  "Returns prechecker for template-id parameters.
   Condition parameters:
     :editable    Template must be editable (not deleted)
     :published   Template must have been published
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
    (fn [{{template-id :template-id
           foreman-doc-id :foreman-doc-id} :data :as command}]
      (when template-id
        (if (ss/blank? template-id)
          (when-not blank
            (fail :error.missing-parameters))
          (let [options  (command->options command)
                template (verdict-template options)]
            (when-not template
              (fail! :error.verdict-template-not-found))
            (when (and editable (:deleted template))
              (fail! :error.verdict-template-deleted))
            (when (and published (not (:published (verdict-template-summary template))))
              (fail! :error.verdict-template-not-published))
            (when (and named (-> template :name ss/blank?))
              (fail! :error.verdict-template-name-missing))
            (when (and application
                       (-> template :category #{"oik"} not)
                       (util/not=as-kw (:category template)
                                       (-> command :application
                                           schema-util/application->category))
                       (not foreman-doc-id))
              (fail! :error.invalid-category))
            (when (and filled (or (not (template-filled? {:template template}))
                                  (not (settings-filled? (assoc options
                                                                :category (:category template))))))
              (fail! :pate.required-fields))))))))

(defn- template-update [{:keys [organization template-id timestamp]} update]
  (mongo/update :verdict-templates
                {:_id          template-id
                 :organization (:id organization)}
                (cond-> (ss/trimwalk update)
                  timestamp (assoc-in [$set :modified] timestamp))))

(defn verdict-template-update-and-open [{:keys [wrapper timestamp] :as options}]
  (let [{draft :draft :as data}        (verdict-template-response-data options)
        {new-draft :draft :as updated} (verdict-template-settings-dependencies options data)
        updates                        (reduce (fn [acc dict]
                                                 (let [new-dict-value (dict new-draft)]
                                                   (if (not= (dict draft) new-dict-value)
                                                     (assoc-in acc
                                                               [$set (util/kw-path :draft dict)]
                                                               (wrapper new-dict-value))
                                                     acc)))
                                               nil
                                               template-settings-dependencies)]
    (if updates
      (do
        (template-update options updates)
        (assoc updated :modified timestamp))
      data)))

(defn settings [{:keys [organization category]}]
  (some->> [:verdict-settings (keyword category)]
           (get-in organization)
           metadata/unwrap-all))

(defn- pack-dependencies
  "Packs settings dependency map (e.g., :plans, :reviews). Only included items are packed. The
  selection status is packed as well. The result is a list with (at least) localisation (e.g., :fi)
  and :selected properties.

  If the packed repeating is sorted manually, the result is sorted accordingly, but the sort
  key (manual dict) is not included.

  If `include-id?` is true, the item id (key) is packed as an `:id` field.

  Returns nil if the result is empty."
  ([category settings dep-key template-data include-id?]
   (let [manual (get-in (settings-schemas/settings-schema category)
                        [:dictionary dep-key :sort-by :manual])
         t-data (->> template-data
                     dep-key
                     (filter (util/fn-> last :included))
                     (into {}))
         packed (->> (select-keys (dep-key settings) (keys t-data))
                     (map (fn [[k v]]
                            (cond-> (assoc v
                                           :selected (boolean (-> t-data
                                                                  k
                                                                  :selected)))
                              include-id? (assoc :id (name k)))))
                     seq)]
     (if manual
       (some->> packed (sort-by manual) (map #(dissoc % manual)))
       packed)))
  ([category settings dep-key template-data]
   (pack-dependencies category settings dep-key template-data false)))

(defn- pack-verdict-dates
  "Since the date calculation is cumulative we always store every delta _in the schema_ into
  kw-delta map. Including those that are not selected in the template. Empty deltas are
  zeros. For board-verdicts the appeal date (muutoksenhaku) is different.

  The `draft` can be template data, if the `custom-date-deltas` is true (then also `board-verdict?`
  is false)"
  [category draft board-verdict?]
  (let [{dic :dictionary} (settings-schemas/settings-schema category)]
    (cond-> (->> (helper/all-verdict-dates category)
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
        board-verdict? (util/=as-kw (:giver template-data) :lautakunta)
        removed        (->> (:removed-sections template-data)
                            (keep (fn [[k v]]
                                    (when v k)))
                            set)
        included?      (complement removed)]
    (merge (select-keys draft (cond-> [:verdict-code :organization-name]
                                board-verdict? (conj :boardname)))
           (util/strip-nils
             {:date-deltas          (if (:custom-date-deltas template-data)
                                      (pack-verdict-dates category template-data false)
                                      (pack-verdict-dates category draft board-verdict?))
              ;; Reviews and plans are included, since their section selection can be
              ;; overridden in the verdict.
              :plans                (pack-dependencies category draft :plans
                                                       template-data)
              :reviews              (pack-dependencies category draft :reviews
                                                       template-data)
              :handler-titles       (pack-dependencies category draft :handler-titles
                                                       template-data)
              :verdict-givers       (when (included? :verdict-givers)
                                      (not-empty
                                        (cond->> (pack-dependencies category draft :verdict-givers
                                                                    template-data true)
                                          (#{:r :p} (keyword category))
                                          (filter #(pate-vutil/valid-verdict-giver? (:giver template-data) %)))))
              :verdict-giver-titles (when (included? :verdict-giver-titles)
                                      (pack-dependencies category draft :verdict-giver-titles
                                                         template-data true))}))))

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
      (let [mongo-path (util/kw-path :draft path)]
        (template-update options
                         (if (= op :remove)
                          {$unset {mongo-path 1}}
                          {$set {mongo-path (wrapper value)}}))))
    (assoc processed :category category)))

(defn- template-inclusions
  "List of included top-level dicts. Dict is excluded if it belongs (only) to removed
  section and the section is not included by `policies` (default `:include`). The list is
  used when resolving the `:template-dict` references in verdicts."
  ([{:keys [category draft]} & policies]
   (let [{:keys [dictionary
                 sections]} (template-schemas/verdict-template-schema category)
         dictionary         (util/strip-matches dictionary :excluded?)
         dict-secs          (schemas/dict-sections sections)
         always-included    (->> sections
                                 (util/filter-by-key :policy #(util/includes-as-kw? policies %))
                                 (map :id)
                                 set)
         removed-sections   (set/difference (->> (:removed-sections draft)
                                                 (map (fn [[k v]]
                                                        (when v k)))
                                                 (remove nil?)
                                                 set)
                                            always-included)]
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
  ([template]
   (template-inclusions template :include)))

(defn- process-draft-dictionary [dictionary data]
  (into {}
        (for [[dict {:keys [repeating]}] dictionary
              :let                       [v (get data dict)]
              :when                      (if (coll? v)
                                           (not-empty v)
                                           (ss/not-blank? (str v)))]
          [dict (if repeating
                  (map (partial process-draft-dictionary repeating) (vals v))
                  v)])))

(defn- draft-for-publishing
  "Extracts template draft data for publishing. Keys with empty values or not in template
  inclusions are omitted. Transforms :repeating in template draft from map of maps to
  sequence of maps."
  [{:keys [category draft] :as template}]
  (let [draft (->> (template-inclusions template :include :copy)
                   (mapv keyword)
                   ;; Needed by verdict for resolving initial values for
                   ;; toggleable "sections" (e.g., plans).
                   (cons :removed-sections)
                   (select-keys draft))]
    (-> (template-schemas/verdict-template-schema category)
        :dictionary
        (util/strip-matches :excluded?)
        (process-draft-dictionary draft))))

(defn publish-verdict-template [{:keys [timestamp]
                                 :as   options}]
  (let [{:keys [draft category]
         :as   template} (verdict-template options)
        settings         (schemas/valid-pate-published-template-settings!
                           (published-settings options
                                               category
                                               draft))
        inclusions       (template-inclusions template)]
    (template-update (dissoc options :timestamp)
                     {$set {:published
                            {:published  timestamp
                             :data       (apply dissoc (draft-for-publishing template)
                                                template-settings-dependencies)
                             :inclusions inclusions
                             :settings   settings}}})))

(defn set-name [{:keys [name wrapper] :as options}]
  (template-update options
                   {$set {:name (wrapper name)}}))

(defn set-deleted [{:keys [delete wrapper] :as options}]
  (template-update (dissoc options :timestamp)
                   {$set {:deleted (wrapper delete)}}))

(defn copy-verdict-template [{:keys [lang wrapper] :as options}]
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
  (->> [:verdict-settings category extra]
       flatten
       util/kw-path))

(defn save-settings-value [{:keys [organization wrapper category timestamp path value]
                            :as   options}]
  (let [settings-key      (settings-key category)
        {:keys [path value op]
         :as   processed} (ss/trimwalk
                            (schemas/validate-and-process-value (settings-schemas/settings-schema category)
                                                                path
                                                                value
                                                                (:draft (settings options))))]
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

(defn operation-verdict-templates [{:keys [organization]}]
  (let [published (->> (verdict-templates (:id organization)
                                          :published? true
                                          :projection [:id])
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

(defn- template-date-set?
  [template date-set-kw date-kw]
  (boolean (some-> template :published :data date-set-kw (util/includes-as-kw? date-kw))))

(defn template-warning
  "Returns the primary issue for warning the user from using the given template, if any"
  [{:keys [ryhti-data-2026 permitType] :as application} template]
  (let [voimassa-set?      (or (template-date-set? template :verdict-dates :voimassa)
                               (template-date-set? template :kuulutus-dates :k-voimassa))
        kuulutus-set?      (template-date-set? template :kuulutus-dates :k-kuulutus)
        public-notice-set? (or kuulutus-set?
                               (template-date-set? template :verdict-dates :julkipano))]
    (cond
      (-> template :category #{"tj"})
      nil ; ignore foreman verdicts; don't go through the same pipeline as "proper" verdicts
      (and ryhti-data-2026
           (#{"R" "P"} permitType)
           (not public-notice-set?))
      :ryhti-data-2026-public-notice-date-missing
      (and ryhti-data-2026
           (= permitType "P")
           (not voimassa-set?))
      :ryhti-data-2026-in-force-until-date-missing
      (and (= :kuulutus (app/bulletin-process application))
           (not kuulutus-set?))
      :kuulutus-date-missing)))

(defn application-verdict-templates
  "Returns a list of the verdict templates available for the application,
  with the default permit type dependent templates (r, p, contract, etc.) under the :permit-templates keyword
  and templates shared between permit types separately (only :rectification-templates for now)."
  [{:keys [organization]} {:keys [primaryOperation] :as application} & [category]]
  (let [{:keys [operation-verdict-templates]} organization
        app-category                          (or category (schema-util/application->category application))
        app-operation                         (-> primaryOperation :name keyword)
        templates                             (verdict-templates (:id organization) :published? true
                                                                 :category #{app-category :oik})]
    (reduce (fn [acc {:keys [category id name] :as template}]
              (update acc (if (util/=as-kw :oik category)
                            :rectification-templates
                            :permit-templates)
                      conj
                      {:id       id
                       :name     name
                       :default? (= id (get operation-verdict-templates app-operation))
                       :warning  (template-warning application template)}))
            {}
            templates)))

(defn selectable-verdict-templates [{:keys [organization]}]
  (let [published (verdict-templates (:id organization) :published? true)]
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
         (merge (->> (keep schema-util/category->permit-type
                           schema-util/pate-categories)
                     (map #(vector % []))
                     (into {}))
                (->> schema-util/operation-categories
                     (map (fn [[operation category]]
                            [operation (->> published
                                            (filter #(util/=as-kw category (:category %)))
                                            (map #(select-keys % [:name :id])))]))
                     (into {}))))))
