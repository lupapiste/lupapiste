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

(defn new-verdict-template
  ([org-id timestamp lang category draft name]
   (let [data {:id       (mongo/create-id)
               :draft    draft
               :name     name
               :category category
               :modified timestamp}]
     (mongo/update-by-id :organizations
                         org-id
                         {$push {:verdict-templates.templates
                                 (sc/validate schemas/PateSavedTemplate
                                              (assoc data
                                                     :deleted false))}})
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

   Template's existence is always checked unless :blank matches."
  [& conditions]
  (let [{:keys [editable published blank named application]} (zipmap conditions
                                                                     (repeat true))]
    (fn [{{template-id :template-id} :data :as command}]
      (when template-id
        (if (ss/blank? template-id)
          (when-not blank
            (fail :error.missing-parameters))
          (let [template (some-> (command->organization command)
                                 (verdict-template template-id)
                                 verdict-template-summary)]
            (when-not template
              (fail! :error.verdict-template-not-found))
            (when (and editable (:deleted template))
              (fail! :error.verdict-template-deleted))
            (when (and published (not (:published template)))
              (fail! :error.verdict-template-not-published))
            (when (and named (-> template :name ss/blank?))
              (fail! :error.verdict-template-name-missing))
            (when (and application
                       (util/not=as-kw (:category template)
                                       (-> command :application :permitType
                                           shared/permit-type->category)))
              (fail! :error.invalid-category))))))))

(defn- template-update [organization template-id update  & [timestamp]]
  (mongo/update :organizations
                {:_id               (:id organization)
                 :verdict-templates.templates {$elemMatch {:id template-id}}}
                (if timestamp
                  (assoc-in update
                            [$set :verdict-templates.templates.$.modified]
                            timestamp)
                  update)))

(defn settings [organization category]
  (get-in organization [:verdict-templates :settings (keyword category)]))

(defn- pack-generics [organization gen-key template-data]
  (->> organization
       :verdict-templates
       gen-key
       (remove :deleted)
       (filter #(util/includes-as-kw? (gen-key template-data) (:id %)))
       (map #(select-keys % [:id :name :type]))))

(defn- pack-verdict-dates
  "Since the date calculation is cumulative we always store every delta
  into kw-delta map. Empty deltas are zeros. For board-verdicts the
  appeal date (muutoksenhaku) is different."
  [draft board-verdict?]
  (cond-> (->> shared/verdict-dates
               (map (fn [k]
                      [k (-> draft k :delta schemas/parse-int)]))
               (into {}))
    board-verdict? (assoc :muutoksenhaku (-> draft :lautakunta-muutoksenhaku
                                             :delta schemas/parse-int))))

(defn- published-settings
  "The published settings only include lists without schema-ordained
  structure. Term list items contain the localisations. In other words,
  subsequent localisation changes do not affect already published
  verdict templates."
  [organization category template-data]
  (let [draft (:draft (settings organization category))
        data  (into {}
                    (for [[k v] (select-keys draft
                                             [:verdict-code
                                              :foremen])]
                      [k (loop [v v]
                           (if (map? v)
                             (recur (-> v vals first))
                             v))]))
        board-verdict? (util/=as-kw (:giver template-data) :lautakunta)]
    (merge data
           {:date-deltas (pack-verdict-dates draft board-verdict?)
            :plans       (pack-generics organization :plans template-data)
            :reviews     (pack-generics organization :reviews template-data)}
           (when board-verdict?
             {:boardname (:boardname draft)}))))

(declare generic-list)

(defn save-draft-value
  "Error code on failure (see schemas for details)."
  [organization template-id timestamp path value]
  (let [{:keys [category] :as template} (verdict-template organization template-id)]
    (or (schemas/validate-path-value shared/default-verdict-template
                                     path
                                     value
                                     (merge
                                      {:settings (:draft (settings organization
                                                                   category))}
                                      (when-let [ref-gen (some->> path
                                                                  (util/intersection-as-kw [:plans :reviews])
                                                                  first)]
                                        (hash-map ref-gen
                                                  (map :id (generic-list organization
                                                                         category
                                                                         ref-gen))))))
        (template-update organization
                         template-id
                         {$set {(util/kw-path (cons :verdict-templates.templates.$.draft
                                                    path)) value}}
                         timestamp))))

(defn- prune-template-data
  "Upon publishing the settings generics and template data must by
  synchronized."
  [settings gen-key template-data]
  (update template-data gen-key (fn [ids]
                                  (->> settings
                                       gen-key
                                       (map :id)
                                       (util/intersection-as-kw ids)))))

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
                             :data      (->> (dissoc draft :giver)
                                             (prune-template-data settings :reviews)
                                             (prune-template-data settings :plans))
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
  (let [draft        (assoc-in (:draft (settings organization category))
                               (map keyword path)
                               value)
        settings-key (settings-key category)]
    (or (schemas/validate-path-value (get shared/settings-schemas
                                          (keyword category))
                                     path
                                     value
                                     )
     (mongo/update-by-id :organizations
                         (:id organization)
                         {$set {(util/kw-path settings-key :draft path) value
                                (util/kw-path settings-key :modified)   timestamp}}))))

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
