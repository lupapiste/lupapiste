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
    (util/find-by-id (:org-id data)
                     user-organizations)))

(defn command->options
  "Returns map with every data argument and also organization,
  timestamp, user, lang and wrapper. Also application, if
  available. The original command is stored just in case."
  [{:keys [created lang data application user] :as command}]
  {:pre [(empty? (util/intersection-as-kw (keys data)
                                          [:timestamp :lang :wrapper
                                           :organization :user :application
                                           :command]))]}
  (cond-> (assoc data
                 :timestamp created
                 :lang lang
                 :wrapper (metadata/wrapper command)
                 :organization (command->organization command)
                 :user user
                 :command command)
    application (assoc :application application)))

(defn organization-categories [{scope :scope}]
  (->> scope
       (filter #(true? (:pate-enabled %)))
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
  "Encriches given response with changes and removals properties that
  are resulting from repeating add and remove operations updates
  respectively. Existing properties are augmented"
  [{:keys [changes removals] :as response} {:keys [op path value]}]
  (merge response
         (case op
           :add {:changes (concat (or changes []) [[path value]])}
           :remove {:removals (concat (or removals []) [path])}
           {})))

(declare settings)

(defn- sync-repeatings
  "Sync the template repeating (t-rep) according to settings
  repeating. Every language version of (plan/review) is copied."
  [s-rep t-rep]
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
                 (update acc k reduce-langs v :text))
               (select-keys t-rep (keys s-rep))
               s-rep)))

(def template-settings-dependencies [:plans :reviews])

(defn verdict-template-settings-dependencies
  "Reviews and plans information from setting is merged into the
  corresponding template repeatings. Copying is done when the template
  dictionary has :reviews or :plans. Note that the copied information
  is not wrapped. Returns the updated template."
  [{:keys [wrapper] :as options} {:keys [category draft] :as template}]
  (let [{dic :dictionary} (template-schemas/verdict-template-schema category)
        {s-data :draft}   (settings (assoc options :category category))]
    (reduce (fn [acc rep-dict]
              (let [repeating-data (sync-repeatings (rep-dict s-data)
                                                    (rep-dict draft))]
                (if (empty? repeating-data)
                  (util/dissoc-in acc [:draft rep-dict])
                  (assoc-in acc [:draft rep-dict] repeating-data))))
            template
            (util/intersection-as-kw template-settings-dependencies
                                     (keys dic)))))

(defn new-verdict-template
  ([{:keys [org-id wrapper timestamp lang category draft name] :as options}]
   (let [data (verdict-template-settings-dependencies
               options
               {:id       (mongo/create-id)
                :draft    (or draft {})
                :name     (metadata/wrap wrapper
                                         (or name
                                             (i18n/localize lang
                                                            (if (util/=as-kw category :contract)
                                                              :pate.contract.template
                                                              :pate-verdict-template))))
                :category category
                :modified timestamp
                :deleted  false})]
     (mongo/update-by-id :organizations
                         org-id
                         {$push {:verdict-templates.templates
                                 (sc/validate schemas/PateSavedTemplate
                                              data)}})
     (metadata/unwrap-all wrapper data))))

(defnk verdict-template [organization wrapper template-id]
  (some->> organization :verdict-templates :templates
           (util/find-by-id template-id)
           (metadata/unwrap-all wrapper)))

(defn verdict-template-summary [{published :published :as template}]
  (assoc (select-keys template
                      [:id :name :modified :deleted :category])
         :published (:published published)))

(declare settings-filled? template-filled?)


(defnk verdict-template-response-data [org-id :as options]
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
                (cond-> update
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
                                                        (metadata/wrap wrapper new-dict-value))
                                              acc)))
                                        nil
                                        template-settings-dependencies)]
    (if updates
      (do
        (template-update options updates)
        (assoc updated :modified timestamp))
      data)))

(defnk settings [organization wrapper category]
  (some->> [:verdict-templates :settings (keyword category)]
           (get-in organization)
           (metadata/unwrap-all wrapper)))

(defn- pack-dependencies
  "Packs settings dependency (either :plans or :reviews). Only included
  items are packed. The selection status is packed as well. The result
  is a list with (at least) localisation (e.g., :fi) and :selected
  properties."
  [settings dep-key template-data]
  (let [t-data (->> template-data
                    dep-key
                    (filter (util/fn-> last :included))
                    (into {}))]
    (map (fn [[k v]]
           (assoc v
                  :selected (boolean (-> t-data
                                         k
                                         :selected))))
         (select-keys (dep-key settings) (keys t-data)))))

(defn- pack-verdict-dates
  "Since the date calculation is cumulative we always store every delta
  into kw-delta map. Including those that are not even in the current
  schema. Empty deltas are zeros. For board-verdicts the appeal
  date (muutoksenhaku) is different."
  [category draft board-verdict?]
  (let [{dic :dictionary} (settings-schemas/settings-schema category)]
    (cond-> (->> helper/verdict-dates
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
  (let [draft (:draft (settings (assoc options :category category)))
        data  (into {}
                    (for [[k v] (select-keys draft
                                             [:verdict-code])]
                      [k (loop [v v]
                           (if (map? v)
                             (recur (-> v vals first))
                             v))]))
        board-verdict? (util/=as-kw (:giver template-data) :lautakunta)]
    (merge data
           {:date-deltas (pack-verdict-dates category draft board-verdict?)
            :plans       (pack-dependencies draft :plans template-data)
            :reviews     (pack-dependencies draft :reviews template-data)}
           (when board-verdict?
             {:boardname (:boardname draft)}))))

(defn save-draft-value
  "Error code on failure (see schemas for details)."
  [{:keys [organization wrapper path value] :as options}]
  (let [{:keys [category draft]} (verdict-template options)
        {:keys [path value op] :as  processed} (schemas/validate-and-process-value
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
                          {$set {mongo-path (metadata/wrap wrapper value)}}))))
    (assoc processed :category category)))

(defn- draft-for-publishing
  "Extracts template draft data for publishing. Keys with empty values
  are omitted. However, removed-sections do not affect the data
  selection, since the verdicts may handle removed-sections
  differently (e.g., foremen and reviews). Transforms :repeating in
  template draft from map of maps to sequence of maps."
  [{:keys [category draft]}]
  (let [{:keys [dictionary]} (template-schemas/verdict-template-schema category)
        good? (util/fn-> str ss/not-blank?)]

    (reduce (fn [acc dict]
              (let [value (dict draft)]
                (if (good? value)
                  (assoc acc
                         dict
                         (if (-> dictionary dict :repeating)
                           (filter (util/fn->> vals (every? good?))
                                   (vals value))
                           value))
                  acc)))
            {}
            (keys dictionary))))

(defn- template-inclusions
  "List if included top-level dicts. Dict is excluded if it
  belongs (only) to removed section and the section is not always
  included. The list is used when resolving the :template-dict
  references in verdicts."
  [{:keys [category draft]}]
  (let [{:keys [dictionary
                sections]}     (template-schemas/verdict-template-schema category)
        dict-secs              (schemas/dict-sections sections)
        always-included        (->> (filter :always-included? sections)
                                    (map :id)
                                    set)
        removed-sections       (set/difference (->> (:removed-sections draft)
                                                    (map (fn [[k v]]
                                                           (when v k)))
                                                    (remove nil?)
                                                    set)
                                               always-included)

        ]
    (->> dict-secs
         (reduce-kv (fn [acc dict sections]
                      (cond-> acc
                        (or (empty? sections)
                            (not-empty (set/difference sections
                                                       removed-sections)))
                        (conj dict)))
                    (util/difference-as-kw (keys dictionary)
                                           (keys dict-secs)
                                           [:removed-sections]))
         ;; Strings due to smoke tests (values are strings in mongo)
         (map name))))

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
                            {:published  (metadata/wrap wrapper timestamp)
                             :data       (dissoc (draft-for-publishing template)
                                                 :reviews :plans)
                             :inclusions (template-inclusions template)
                             :settings   settings}}})))

(defnk set-name [name wrapper :as options]
  (template-update options
                   {$set {:verdict-templates.templates.$.name
                          (metadata/wrap wrapper name)}}))

(defnk set-deleted [delete :as options]
  (template-update (dissoc options :timestamp)
                   {$set {:verdict-templates.templates.$.deleted delete}}))

(defnk copy-verdict-template [lang wrapper :as options]
  (let [{name :name :as template} (verdict-template (assoc options
                                                           :wrapper (metadata/->Identity)))]
    (new-verdict-template (merge options
                                 (metadata/rewrap-all wrapper
                                                      (select-keys template
                                                                   [:draft :category]))
                                 {:name (format "%s (%s)"
                                                (metadata/unwrap wrapper name)
                                                (i18n/localize lang
                                                               :pate-copy-postfix))}))))

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
                            (assoc-in (if (= op :remove)
                                        {$unset {mongo-path 1}}
                                        {$set {mongo-path (metadata/wrap wrapper value)}})
                                      [$set (util/kw-path settings-key :modified)]
                                      timestamp))))
    processed))

(defn- organization-templates [org-id]
  (org/get-organization org-id {:verdict-templates 1}))

(defn settings-filled?
  "Settings are filled properly if every requireid field has been filled."
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

(defnk operation-verdict-templates [organization wrapper]
  (let [organization (metadata/unwrap-all wrapper organization)
        published (->> (published-available-templates organization)
                       (map :id)
                       set)]
    (->> organization
         :operation-verdict-templates
         (filter (fn [[_ v]] (contains? published v)))
         (into {}))))

(defn set-operation-verdict-template [org-id operation template-id]
  (let [path (util/kw-path :operation-verdict-templates operation)]
    (org/update-organization org-id
                             (if (ss/blank? template-id)
                               {$unset {path true}}
                               {$set {path template-id}}))))

(defn application-verdict-templates [{:keys [organization wrapper]}
                                     {:keys [primaryOperation]
                                      :as   application}]
  (let [{:keys [operation-verdict-templates
                verdict-templates]} organization
        app-category                (schema-util/application->category application)
        app-operation               (-> primaryOperation :name keyword)]
    (->> verdict-templates
         :templates
         (metadata/unwrap-all wrapper)
         (filter (fn [{:keys [deleted published category]}]
                   (and (not deleted)
                        published
                        (util/=as-kw category app-category))))
         (map (fn [{:keys [id name]}]
                {:id       id
                 :name     name
                 :default? (= id (get operation-verdict-templates
                                      app-operation))})))))

(defnk selectable-verdict-templates [organization wrapper]
  (let [published (published-available-templates (metadata/unwrap-all wrapper
                                                                      organization))]
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
