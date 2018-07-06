(ns lupapalvelu.pate.verdict-template
  (:require [clojure.set :as set]
            [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as ops]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.date :as date]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.schemas :as schemas]
            [lupapalvelu.pate.settings-schemas :as settings-schemas]
            [lupapalvelu.pate.verdict-template-schemas :as template-schemas]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(defn command->organization
  "User-organizations is not available for input-validators."
  [{:keys [user user-organizations organization]}]
  (if organization
    @organization
    (util/find-by-id (usr/authority-admins-organization-id user)
                     user-organizations)))

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
  dictionary has :reviews or :plans. Returns the updated template."
  [org-id {:keys [category draft] :as template}]
  (let [{dic :dictionary} (template-schemas/verdict-template-schema category)
        {s-data :draft}   (settings (org/get-organization org-id
                                                          {:verdict-templates 1})
                                    category)]
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
  ([org-id timestamp _ category draft name]
   (let [data (verdict-template-settings-dependencies
               org-id
               {:id       (mongo/create-id)
                :draft    draft
                :name     name
                :category category
                :modified timestamp
                :deleted  false})]
     (mongo/update-by-id :organizations
                         org-id
                         {$push {:verdict-templates.templates
                                 (sc/validate schemas/PateSavedTemplate
                                              data)}})
     data))
  ([org-id timestamp lang category]
   (new-verdict-template org-id timestamp lang category {}
                         (i18n/localize lang (if (util/=as-kw category :contract)
                                               :pate.contract.template
                                               :pate-verdict-template)))))

(defn verdict-template [{templates :verdict-templates} template-id]
  (util/find-by-id template-id (:templates templates)))

(defn verdict-template-summary [{published :published :as template}]
  (assoc (select-keys template
                      [:id :name :modified :deleted :category])
         :published (:published published)))

(declare settings-filled? template-filled?)


(defn verdict-template-response-data [organization template-id]
  (let [template     (verdict-template organization
                                       template-id)]
    (assoc (verdict-template-summary template)
           :draft (:draft template)
           :filled (template-filled? {:org-id   (:id organization)
                                      :template template}))))

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
          (let [organization (command->organization command)
                template     (verdict-template organization template-id)]
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
                                  (not (settings-filled? {:org-id (:id organization)}
                                                         (:category template)))))
              (fail! :pate.required-fields))))))))

(defn- template-update [organization template-id update  & [timestamp]]
  (mongo/update :organizations
                {:_id               (:id organization)
                 :verdict-templates.templates {$elemMatch {:id template-id}}}
                (if timestamp
                  (assoc-in update
                            [$set :verdict-templates.templates.$.modified]
                            timestamp)
                  update)))

(defn verdict-template-update-and-open [{:keys [lang data created] :as command}]
  (let [{:keys [template-id]}   data
        organization            (command->organization command)
        {draft :draft :as data} (verdict-template-response-data organization template-id)
        {new-draft :draft
         :as       updated}     (verdict-template-settings-dependencies (:id organization)
                                                                        data)
        updates                 (reduce (fn [acc dict]
                                          (let [new-dict-value (dict new-draft)]
                                            (if (not= (dict draft) new-dict-value)
                                              (assoc-in acc
                                                        [$set
                                                         (util/kw-path :verdict-templates.templates.$.draft
                                                                       dict)]
                                                        new-dict-value)
                                              acc)))
                                        nil
                                        template-settings-dependencies)]
    (if updates
      (do
        (template-update organization template-id updates created)
        (assoc updated :modified created))
      data)))

(defn settings [organization category]
  (get-in organization [:verdict-templates :settings (keyword category)]))

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
  [organization category template-data]
  (let [draft (:draft (settings organization category))
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
  [organization template-id timestamp path value]
  (let [{:keys [category draft]
         :as   template}  (verdict-template organization template-id)
        {:keys [path value op]
         :as   processed} (schemas/validate-and-process-value
                           (template-schemas/verdict-template-schema category)
                           path
                           value
                           draft
                           {:settings (:draft (settings organization
                                                        category))})]
    (when op ;; Value could be nil
      (let [mongo-path (util/kw-path (cons :verdict-templates.templates.$.draft
                                           path))]
        (template-update organization
                        template-id
                        (if (= op :remove)
                          {$unset {mongo-path 1}}
                          {$set {mongo-path value}})
                        timestamp)))
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

(defn publish-verdict-template [organization template-id timestamp]
  (let [{:keys [draft category]
         :as   template} (verdict-template organization template-id)
        settings         (sc/validate schemas/PatePublishedTemplateSettings
                                      (published-settings organization
                                                          category
                                                          draft))]
    (template-update organization
                     template-id
                     {$set {:verdict-templates.templates.$.published
                            {:published timestamp
                             :data      (dissoc (draft-for-publishing template)
                                                :reviews :plans)
                             :inclusions (template-inclusions template)
                             :settings  settings}}})))

(defn set-name [organization template-id timestamp name]
  (template-update organization
                   template-id
                   {$set {:verdict-templates.templates.$.name name}}
                   timestamp))

(defn set-deleted [organization template-id deleted?]
  (template-update organization
                   template-id
                   {$set {:verdict-templates.templates.$.deleted deleted?}}))

(defn copy-verdict-template [organization template-id timestamp lang]
  (let [{:keys [draft name category]} (verdict-template organization
                                                        template-id)]
    (new-verdict-template (:id organization)
                          timestamp
                          lang
                          category
                          draft
                          (format "%s (%s)"
                                  name
                                  (i18n/localize lang :pate-copy-postfix)))))

(defn- settings-key [category & extra]
  (->> [:verdict-templates.settings category extra]
       flatten
       (remove nil?)
       (map name)
       (ss/join ".")
       keyword))

(defn save-settings-value [organization category timestamp path value]
  (let [settings-key      (settings-key category)
        {:keys [path value op]
         :as   processed} (schemas/validate-and-process-value (settings-schemas/settings-schema category)
                                                              path
                                                              value
                                                              (:draft (settings organization
                                                                                category)))]
    (when op  ;; Value could be nil.
      (let [mongo-path (util/kw-path settings-key :draft path)]
        (mongo/update-by-id :organizations
                            (:id organization)
                            (assoc-in (if (= op :remove)
                                        {$unset {mongo-path 1}}
                                        {$set {mongo-path value}})
                                      [$set (util/kw-path settings-key :modified)]
                                      timestamp))))
    processed))

(defn- organization-templates [org-id]
  (org/get-organization org-id {:verdict-templates 1}))

(defn settings-filled?
  "Settings are filled properly if every requireid field has been filled."
  [{org-id :org-id ready :settings data :data} category]
  (schemas/required-filled? (settings-schemas/settings-schema category)
                            (or data
                                (:draft (or ready
                                            (settings (organization-templates org-id)
                                                      category))))))

(defn template-filled?
  "Template is filled when every required field has been filled."
  [{:keys [org-id template template-id data category]}]
  (let [{t-cat  :category
         t-data :draft} (or template
                            (when (and org-id template-id)
                              (verdict-template (organization-templates org-id)
                                                template-id)))
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

(defn operation-verdict-templates [organization]
  (let [published (->> (published-available-templates organization)
                       (map :id)
                       set)]
    (->> organization
         :operation-verdict-templates
         (filter (fn [[k v]]
                   (contains? published v)))
         (into {}))))

(defn set-operation-verdict-template [org-id operation template-id]
  (let [path (util/kw-path :operation-verdict-templates operation)]
    (org/update-organization org-id
                             (if (ss/blank? template-id)
                               {$unset {path true}}
                               {$set {path template-id}}))))

(defn application-verdict-templates [{:keys [operation-verdict-templates
                                             verdict-templates]}
                                     {:keys [primaryOperation]
                                      :as   application}]
  (let [app-category  (schema-util/application->category application)
        app-operation (-> primaryOperation :name keyword)]
    (->> verdict-templates
         :templates
         (filter (fn [{:keys [deleted published category]}]
                   (and (not deleted)
                        published
                        (util/=as-kw category app-category))))
         (map (fn [{:keys [id name]}]
                {:id       id
                 :name     name
                 :default? (= id (get operation-verdict-templates
                                      app-operation))})))))

(defn selectable-verdict-templates [organization]
  (let [published (published-available-templates organization)]
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
