(ns lupapalvelu.matti.matti
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.matti.date :as date]
            [lupapalvelu.matti.schemas :as schemas]
            [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as ops]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer :all]))

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
                                 (merge data
                                        {:deleted false})}})
     data))
  ([org-id timestamp lang category]
   (new-verdict-template org-id timestamp lang category {}
                         (i18n/localize lang :matti-verdict-template))))

(defn verdict-template [{templates :verdict-templates} template-id]
  (util/find-by-id template-id (:templates templates)))

(defn latest-version [organization template-id]
  (-> (verdict-template organization template-id) :versions last))

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

(defn- pack-generics [gen-ids generics]
  (->> gen-ids
       (map #(util/find-by-id % generics))
       (remove :deleted)
       (map #(select-keys % [:id :name :type]))))

(defn- published-settings
  "The published settings only include lists without schema-ordained
  structure. List items contain the localisations. In other words,
  subsequent localisation changes do not affect already published
  verdict templates."
  [organization category]
  (let [draft                   (:draft (settings organization category))
        {plan-ids   :plans
         review-ids :reviews
         :as        data}       (into {}
                                      (for [[k v] draft]
                                        [k (loop [v v]
                                             (if (map? v)
                                               (recur (-> v vals first))
                                               v))]))
        {:keys [plans reviews]} (:verdict-templates organization)]
    (assoc data
           :plans   (pack-generics plan-ids plans)
           :reviews (pack-generics review-ids reviews))))

(defn save-draft-value
  "Error code on failure (see schemas for details)."
  [organization template-id timestamp path value]
  (let [template (verdict-template organization template-id)]
    (or (schemas/validate-path-value shared/default-verdict-template
                                     path
                                     value
                                     {:settings (:draft (settings organization
                                                                  (:category template)))})
        (template-update organization
                         template-id
                         {$set {(util/kw-path (cons :verdict-templates.templates.$.draft
                                                    path)) value}}
                         timestamp))))

(defn publish-verdict-template [organization template-id timestamp]
  (let [template (verdict-template organization template-id)]
    (template-update organization
                     template-id
                     {$set {:verdict-templates.templates.$.published
                             {:published timestamp
                              :data      (:draft template)
                              :settings (published-settings organization
                                                            (:category template))}}})))

(defn set-name [organization template-id timestamp name]
  (template-update organization
                   template-id
                   {$set {:verdict-templates.templates.$.name name}}
                   timestamp))

(defn verdict-template-summary [{published :published :as template}]
  (assoc (select-keys template
                      [:id :name :modified :deleted :category])
         :published (:published published)))

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
                                  (i18n/localize lang :matti-copy-postfix)))))

(defn- settings-key [category & extra]
  (->> [:verdict-templates.settings category extra]
       flatten
       (remove nil?)
       (map name)
       (ss/join ".")
       keyword))

(declare generic-list)

(defn save-settings-value [organization category timestamp path value]
  (let [draft        (assoc-in (:draft (settings organization category))
                               (map keyword path)
                               value)
        settings-key (settings-key category)]
    (or (schemas/validate-path-value (get shared/settings-schemas
                                          (keyword category))
                                     path
                                     value
                                     (when-let [ref-gen (some->> path
                                                                 (util/intersection-as-kw [:plans :reviews])
                                                                 first)]
                                       (hash-map ref-gen
                                                 (map :id (generic-list organization
                                                                        category
                                                                        ref-gen)))))
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
  (new-generic organization-id category :matti.katselmus :reviews
               :type :muu-katselmus))

(defn review [organization review-id]
  (generic organization review-id :reviews))

(defn set-review-details [organization timestamp review-id data]
  (set-generic-details organization timestamp review-id data :reviews :type))

;; Plans

(defn new-plan [organization-id category]
  (new-generic organization-id category :matti.plans :plans))

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

(defn neighbor-states
  "Application neighbor-states data in a format suitable for verdicts: list
  of property-id, done (timestamp) maps."
  [{neighbors :neighbors}]
  (map (fn [{:keys [propertyId status]}]
         {:property-id propertyId
          :done (:created (util/find-by-key :state
                                            "mark-done"
                                            status))})
       neighbors))

(defn data-draft
  "Kmap keys are draft targets (kw-paths). Values are either kw-paths or
  maps with :fn and :skip-nil? properties. :fn is the source
  (full) data handler function. If :skip-nil? is true the nil value
  entries are skipped (default false and nil value is substituted with
  empty string). The second argument is the published template
  snapshot."
  [kmap {data :data}]
  (reduce (fn [acc [k v]]
            (let [v-fn   (get v :fn identity)
                  value  (if-let [v-fn (get v :fn)]
                           (v-fn data)
                           (get-in data (util/split-kw-path v)))]
              (if (and (nil? value) (:skip-nil? v))
                acc
                (assoc-in acc
                          (util/split-kw-path k)
                          (if (nil? value)
                            ""
                            value)))))
          {}
          kmap))

(defn- map-unremoved-section
  "Map (for data-draft) section only it has not been removed. If
  target-key is not given, source-key is used. Result is key-key map
  or nil."
  [source-data source-key & [target-key]]
  (when-not (some-> source-data :removed-sections source-key)
    (hash-map (or target-key source-key) source-key)))

(defn- template-category [template]
  (-> template :category keyword))

(defmulti initial-draft
  "Creates a map with :data and :references"
  (fn [template & _]
    (template-category template)))

(defn- kw-format [& xs]
  (keyword (apply format (map ss/->plain-string xs))))

(def date-deltas [:julkipano :anto :valitus :lainvoimainen
                  :aloitettava :voimassa])

(defmethod initial-draft :r
  [{snapshot :published} application]
  {:data       (data-draft
                (merge {:giver        :giver
                        :verdict-code :verdict-code
                        :verdict-text :paatosteksti}
                       (reduce (fn [acc kw]
                                 (assoc acc
                                        kw kw
                                        (kw-format "%s-included" kw)
                                        {:fn (util/fn-> (get-in [:removed-sections kw]) not)}))
                               {}
                               [:foremen :plans :reviews])
                       (reduce (fn [acc kw]
                                 (assoc acc
                                        kw  {:fn        #(when (some-> % kw :enabled)
                                                           "")
                                             :skip-nil? true}))
                               {}
                               date-deltas)
                       (reduce (fn [acc k]
                                 (merge acc (map-unremoved-section (:data snapshot) k)))
                               {}
                               [:conditions :neighbors :appeal :statements :collateral
                                :complexity :rights :purpose]))
                snapshot)
   :references (:settings snapshot)})

(declare enrich-verdict)

(defmulti template-info
  "Contents of the verdict's template property. The actual contents
  depend on the category. Typical keys:

  :exclusions A map where each key matches a verdict dictionary
  key. Value is either true (dict is excluded) or another exclusions
  map (for repeating).

  :date-deltas A map with delta information (the units are taken from
  the schema)."
  (fn [template]
    (template-category template)))

(defmethod template-info :default [_] nil)

(defmethod template-info :r
  [{snapshot :published}]
  (let [data (:data snapshot)
        removed? #(when (get-in data [:removed-sections %])
                    true)
        building-details (->> [(when-not (:autopaikat data)
                                 [:rakennetut-autopaikat
                                  :kiinteiston-autopaikat
                                  :autopaikat-yhteensa])
                               (map  (fn [kw]
                                       (when-not (kw data) kw))
                                     [:vss-luokka :paloluokka])]
                              flatten
                              (remove nil?))
        exclusions (-<>> [(remove #(get-in data [% :enabled])
                                  date-deltas)
                          (filter removed? [:conditions :appeal :statements
                                            :collateral :rights :purpose])
                          (when (removed? :neighbors)
                            [:neighbors :neighbor-states])
                          (when (removed? :complexity)
                            [:complexity :complexity-text])]
                         flatten
                         (remove nil?)
              (zipmap <> (repeat true))
              (util/assoc-when <> :buildings (or (removed? :buildings)
                                                 (zipmap building-details
                                                         (repeat true))))
              not-empty)]
    {:exclusions exclusions
     :date-deltas (->> date-deltas
                       (remove #(% exclusions))
                       (reduce (fn [acc k]
                                 (assoc acc k (get-in data [k :delta])))
                               {}))}))

(defn new-verdict-draft [template-id {:keys [application organization created]
                                      :as   command}]
  (let [template (verdict-template @organization template-id)
        draft    (assoc (initial-draft template application)
                        :template (template-info template)
                        :id       (mongo/create-id)
                        :modified created)]
    (action/update-application command
                               {$push {:matti-verdicts
                                       (util/assoc-when draft
                                                        :category (:category template))}})
    {:verdict  (enrich-verdict command draft)
     :references (:references draft)}))

(defn verdict-summary [verdict]
  (select-keys verdict [:id :published :modified]))

(defn command->verdict [{:keys [data application organization]}]
  (update (util/find-by-id (:verdict-id data)
                           (:matti-verdicts application))
          :category keyword))

(defn verdict-template-for-verdict [verdict organization]
  (let [{:keys [id version-id]} (:template verdict)]
    (->>  id
          (verdict-template organization)
          :versions
          (util/find-by-id version-id))))

(defn delete-verdict [verdict-id command]
  (action/update-application command
                             {$pull {:matti-verdicts {:id verdict-id}}}))

(defn- listify
  "Transforms argument into list if it is not sequential. Nil results
  in empty list."
  [a]
  (cond
    (sequential? a) a
    (nil? a)        '()
    :default        (list a)))

(defn- verdict-update [{:keys [data created application] :as command} update]
  (let [{verdict-id :verdict-id} data]
    (action/update-application command
                               {:matti-verdicts {$elemMatch {:id verdict-id}}}
                               (assoc-in update
                                         [$set :matti-verdicts.$.modified]
                                         created))))

(defn- verdict-changes-update
  "Write the auxiliary changes into mongo."
  [command changes]
  (when (seq changes)
    (verdict-update command {$set (reduce (fn [acc [k v]]
                                            (assoc acc
                                                   (util/kw-path :matti-verdicts.$.data k)
                                                   v))
                                          {}
                                          changes)})))

(defn update-automatic-verdict-dates [{:keys [category template verdict-data]}]
  (let [datestring      (:verdict-date verdict-data)
        template-schema shared/default-verdict-template
        {:keys [exclusions date-deltas]} template
        automatic?      (:automatic-verdict-dates verdict-data)]
    (when (and automatic? (ss/not-blank? datestring))
      (reduce (fn [acc kw]
                (let [unit (-> template-schema :dictionary
                               kw :date-delta :unit)]
                  (assoc acc
                         kw
                         (date/parse-and-forward datestring
                                                 ;; Delta could be empty.
                                                 (or (util/->long (kw date-deltas)) 0)
                                                 unit))))
              {}
              (remove #(% exclusions)
                      [:julkipano :anto :valitus :lainvoimainen
                       :aloitettava :voimassa])))))

;; Additional changes to the verdict data.
;; Methods options include category, template, verdict-data, path and value.
;; Changes is called after value has already been updated into mongo.
;; The method result is a changes for verdict data.
(defmulti changes (fn [{:keys [category path]}]
                    ;; Dispatcher result: [:category :last-path-part]
                    [category (keyword (last path))]))

(defmethod changes :default [_])

(defmethod changes [:r :verdict-date]
  [options]
  (update-automatic-verdict-dates options))

(defmethod changes [:r :automatic-verdict-dates]
  [options]
  (update-automatic-verdict-dates options))


(defn- strip-exclusions
  "Removes excluded target keys from dictionary schema."
  [exclusions dictionary]
  (cond
    (nil? exclusions)  dictionary
    (true? exclusions) nil

    :else
    (reduce (fn [acc [k v]]
              (let [excluded (k exclusions)]
                (if (-> v keys first (= :repeating))
                  (if-let [dic (strip-exclusions excluded
                                                 (-> v vals first))]
                    (assoc acc k {:repeating dic})
                    acc)
                  (util/assoc-when acc k (when-not excluded
                                           v)))))
            {}
            dictionary)))

(defn edit-verdict
  "Updates the verdict data. Validation takes the template exclusions
  into account. Some updates (e.g., automat dates) can propagate other
  changes as well. Returns errors or modified and (possible
  additional) changes."
  [{{:keys [verdict-id path value]} :data
    organization                    :organization
    application                     :application
    created                         :created
    :as                             command}]
  (let [{:keys [data category
                template
                references]} (command->verdict command)
        schema               (update (-> category shared/verdict-schemas)
                                     :dictionary
                                     (partial strip-exclusions (:exclusions template)))]
    (if-let [error (schemas/validate-path-value
                    schema
                    path value
                    references)]
      {:errors [[path error]]}
      (let [path    (map keyword path)
            updated (assoc-in data path value)]
        (verdict-update command {$set {(util/kw-path :matti-verdicts.$.data
                                                     path)
                                       value}})
        {:modified created
         :changes  (let [options {:path         path
                                  :value        value
                                  :verdict-data updated
                                  :template     template
                                  :category     category}
                         changed (changes options)]
                     (verdict-changes-update command changed)
                     (map (fn [[k v]]
                            [(util/split-kw-path k) v])
                          changed))}))))

(defn buildings
  "Map of building infos: operation id is key and value map contains
  operation (loc-key), building-id (either national or manual id),
  tag (tunnus) and description."
  [{:keys [documents] :as application}]
  (->> documents
       tools/unwrapped
       (filter (util/fn-> :data (contains? :valtakunnallinenNumero)))
       (map (partial app/populate-operation-info
                     (app/get-operations application)))
       (reduce (fn [acc {:keys [schema-info data]}]
                 (let [{:keys [id name
                               description]} (:op schema-info)]
                   (assoc acc
                          (keyword id)
                          (util/convert-values
                           {:operation   name
                            :description description
                            :building-id (->> [:valtakunnallinenNumero
                                               :manuaalinen_rakennusnro]
                                              (select-keys data)
                                              vals
                                              (util/find-first ss/not-blank?))
                            :tag         (:tunnus data)}
                           ss/->plain-string))))
               {})))

(defn- merge-buildings [app-buildings verdict-buildings defaults]
  (reduce (fn [acc [op-id v]]
            (assoc acc op-id (merge defaults (op-id verdict-buildings) v)))
          {}
          app-buildings))

(defn- building-defaults
  "Verdict building defaults (keys with empty values). Nil if building
  or every detail is not to be included in the verdict."
  [exclusions]
  (when-not (true? (:buildings exclusions))
    (let [subkeys (remove #(get-in exclusions [:buildings %])
                          [:rakennetut-autopaikat
                           :kiinteiston-autopaikat
                           :autopaikat-yhteensa
                           :vss-luokka
                           :paloluokka])]
      (when (seq subkeys)
        (assoc (zipmap subkeys (repeat ""))
               :show-building true)))))


(defmulti enrich-verdict
  "Augments verdict data, but MUST NOT update mongo (this is called from
  query actions, too)."
  (fn [{app :application} & _]
    (shared/permit-type->category (:permitType app))))

(defmethod enrich-verdict :default [_ verdict _]
  verdict)

(defmethod enrich-verdict :r
  [{:keys [application]} {:keys [data template]:as verdict}]
  (let [{:keys [exclusions]} template
        addons (merge
                ;; Buildings added only if the buildings section is in
                ;; the template AND at least one of the checkboxes has
                ;; been selected.
                (when-let [defaults (building-defaults exclusions)]
                  {:buildings (merge-buildings (buildings application)
                                               (:buildings data)
                                               defaults)})
                ;; Neighbors added if in the template
                (when-not (:neighbors exclusions)
                  {:neighbor-states (neighbor-states application)}))]
    (assoc-in verdict [:data] (merge data addons))))

(defn open-verdict [{:keys [application organization] :as command}]
  (let [{:keys [data published] :as verdict} (command->verdict command)]
    {:verdict  (assoc (select-keys verdict [:id :modified :published])
                      :data (if published
                              data
                              (:data (enrich-verdict command
                                                     verdict))))
     :references (:references verdict)}))
