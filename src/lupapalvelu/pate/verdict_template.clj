(ns lupapalvelu.pate.verdict-template
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as ops]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.date :as date]
            [lupapalvelu.pate.schemas :as schemas]
            [lupapalvelu.pate.shared :as shared]
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
  (set (map (comp shared/permit-type->category :permitType) scope)))

(defn operation->category [operation]
  (shared/permit-type->category (ops/permit-type-of-operation operation)))

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
  repeating. Only the current language version of (plan/review) is
  copied."
  [lang s-rep t-rep]
  (reduce-kv (fn [acc k v]
               (assoc-in acc [k :text]
                         (get v
                              (keyword lang)
                              (i18n/localize lang :pate.no-name))))
             (select-keys t-rep (keys s-rep))
             s-rep))

(def template-settings-dependencies [:plans :reviews])

(defn verdict-template-settings-dependencies
  "Reviews and plans information from setting is merged into the
  corresponding template repeatings. Copying is done when the template
  dictionary has :reviews or :plans. Returns the updated template."
  [org-id lang {:keys [category draft] :as template}]
  (let [{dic :dictionary} (shared/verdict-template-schema category)
        {s-data :draft}   (settings (org/get-organization org-id
                                                          {:verdict-templates 1})
                                    category)]
    (reduce (fn [acc rep-dict]
              (assoc-in acc [:draft rep-dict]
                     (sync-repeatings lang
                                      (rep-dict s-data)
                                      (rep-dict draft))))
            template
            (util/intersection-as-kw template-settings-dependencies
                                     (keys dic)))))

(defn new-verdict-template
  ([org-id timestamp lang category draft name]
   (let [data (verdict-template-settings-dependencies
               org-id lang
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
                         (i18n/localize lang :pate-verdict-template))))

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
                                       (-> command :application :permitType
                                           shared/permit-type->category)))
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
                                                                        lang
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

#_(defn- pack-generics [organization gen-key template-data]
  (->> organization
       :verdict-templates
       gen-key
       (remove :deleted)
       (filter #(util/includes-as-kw? (gen-key template-data) (:id %)))
       (map #(select-keys % [:id :name :type]))))

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
  into kw-delta map. Empty deltas are zeros. For board-verdicts the
  appeal date (muutoksenhaku) is different."
  [category draft board-verdict?]
  (let [{dic :dictionary} (shared/settings-schema category)]
    (cond-> (->> shared/verdict-dates
                 (map (fn [k]
                        [k {:delta (-> draft k schemas/parse-int)
                            :unit (name (get-in dic [k :date-delta :unit]))}]))
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

(declare generic-list)

(defn save-draft-value
  "Error code on failure (see schemas for details)."
  [organization template-id timestamp path value]
  (let [{:keys [category draft]
         :as   template}  (verdict-template organization template-id)
        {:keys [path value op]
         :as   processed} (schemas/validate-and-process-value
                           (shared/verdict-template-schema category)
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
    processed))

#_(defn- prune-template-data
  "Upon publishing the settings generics and template data must by
  synchronized."
  [settings gen-key template-data]
  (update template-data gen-key (fn [ids]
                                  (->> settings
                                       gen-key
                                       (map :id)
                                       (util/intersection-as-kw ids)))))

(defn- draft-for-publishing
  "Extracts template draft data for publishing. Keys with empty values
  are omitted. However, removed-sections do not affect the data
  selection, since the verdicts may handle removed-sections
  differently (e.g., foremen and reviews). Transforms :repeating in
  template draft from map of maps to sequence of maps."
  [{:keys [category draft]}]
  (let [{:keys [dictionary]} (shared/verdict-template-schema category)
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

(defn publish-verdict-template [organization template-id timestamp]
  (let [{:keys [draft category]
         :as   template} (verdict-template organization template-id)
        settings         (sc/validate schemas/PatePublishedSettings
                                      (published-settings organization
                                                          category
                                                          draft))]
    (template-update organization
                     template-id
                     {$set {:verdict-templates.templates.$.published
                            {:published timestamp
                             :data      (dissoc (draft-for-publishing template)
                                                :reviews :plans)
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
         :as   processed} (schemas/validate-and-process-value (shared/settings-schema category)
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
  (schemas/required-filled? (shared/settings-schema category)
                            (or data
                                (:draft (or ready
                                            (settings (organization-templates org-id)
                                                      category))))))

(defn template-filled?
  "Template is filled when every required field has been filled."
  [{:keys [org-id template template-id data]}]
  (let [category (or (:category data)
                     (:category template)
                     (if (some? org-id) (:category (verdict-template (organization-templates org-id) template-id)))
                     (str "r"))]
    (schemas/required-filled? (shared/verdict-template-schema category)
                              (or data
                                  (:draft (or template
                                              (verdict-template (organization-templates
                                                                 org-id)
                                                                template-id)))))))

;; Generic is a placeholder term that means either review or plan
;; depending on the context. Namely, the subcollection argument in
;; functions below is either :reviews or :plans.

(defn new-generic [organization-id category name-key subcollection & extra]
  (let [data (merge {:id       (mongo/create-id)
                     :name     {:fi (i18n/localize :fi name-key)
                                :sv (i18n/localize :sv name-key)
                                :en (i18n/localize :en name-key)}
                     :category category
                     :deleted  false}
                    (apply hash-map extra))]
    (mongo/update-by-id :organizations
                        organization-id
                        {$push {(util/kw-path :verdict-templates subcollection)
                                data}})
    data))

(defn generic-list [{verdict-templates :verdict-templates} category subcollection]
  (filter #(util/=as-kw category (:category %))
          (subcollection verdict-templates)))

(defn generic [organization gen-id subcollection]
  (some->> organization
           :verdict-templates
           subcollection
           (util/find-by-id gen-id)))

(defn generic-update [organization gen-id update subcollection & [timestamp]]
  (mongo/update :organizations
                {:_id                         (:id organization)
                 (util/kw-path :verdict-templates
                               subcollection) {$elemMatch {:id gen-id}}}
                (if timestamp
                  (assoc-in update
                            [$set (settings-key (:category (generic organization
                                                                    gen-id
                                                                    subcollection))
                                                :modified)]
                            timestamp)
                  update)))

(defn set-generic-details [organization timestamp gen-id data subcollection & extra-keys]
  (let [detail-updates
        (reduce (fn [acc [k v]]
                  (let [k      (keyword k)
                        others (conj extra-keys :deleted)]
                    (merge acc
                           (cond
                             (k #{:fi :sv :en})              {(str "name." (name k)) (ss/trim v)}
                             (util/includes-as-kw? others k) {k v}))))
                {}
                data)]
    (when (seq detail-updates)
      (generic-update organization
                     gen-id
                     {$set (->> detail-updates
                                (map (fn [[k v]]
                                       [(util/kw-path :verdict-templates subcollection :$ k) v]))
                                (into {}))}
                     subcollection
                     timestamp))))

;; Reviews

(defn new-review [organization-id category]
  (new-generic organization-id category :pate.katselmus :reviews
               :type :muu-katselmus))

(defn review [organization review-id]
  (generic organization review-id :reviews))

(defn set-review-details [organization timestamp review-id data]
  (set-generic-details organization timestamp review-id data :reviews :type))

;; Plans

(defn new-plan [organization-id category]
  (new-generic organization-id category :pate.plans :plans))

(defn plan [organization review-id]
  (generic organization review-id :plans))

(defn set-plan-details [organization timestamp review-id data]
  (set-generic-details organization timestamp review-id data :plans))

;; Default operation verdict templates

(defn operation-verdict-templates [organization]
  (let [published (->> organization
                       :verdict-templates
                       :templates
                       (remove :deleted)
                       (filter :published)
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
                                     {:keys [permitType primaryOperation]}]
  (let [app-category  (shared/permit-type->category permitType)
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
