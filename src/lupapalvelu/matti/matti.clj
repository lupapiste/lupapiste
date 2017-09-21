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
            [sade.util :as util]))

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
                                        {:deleted  false
                                         :versions []})}})
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
                     {$push {:verdict-templates.templates.$.versions
                             {:id        (mongo/create-id)
                              :published timestamp
                              :data      (:draft template)
                              :settings (published-settings organization
                                                            (:category template))}}})))

(defn set-name [organization template-id timestamp name]
  (template-update organization
                   template-id
                   {$set {:verdict-templates.templates.$.name name}}
                   timestamp))

(defn verdict-template-summary [{versions :versions :as template}]
  (assoc (select-keys template
                      [:id :name :modified :deleted :category])
         :published (-> versions last :published)))

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
  (new-generic organization-id category :matti.suunnitelmat :plans))

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
                       (filter (util/fn-> :versions seq))
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
         (filter (fn [{:keys [deleted versions category]}]
                   (and (not deleted)
                        (seq versions)
                        (util/=as-kw category app-category))))
         (map (fn [{:keys [id name]}]
                {:id       id
                 :name     name
                 :default? (= id (get operation-verdict-templates
                                      app-operation))})))))

(defn neighbors
  "Application neighbors data in a format suitable for verdicts: list
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
  empty string)."
  [kmap template]
  (let [data (-> template :versions last :data)]
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
            kmap)))

(defn- map-unremoved-section
  "Map (for data-draft) section only it has not been removed. If
  target-key is not given, source-key is used. Result is key-key map
  or nil."
  [source-data source-key & [target-key]]
  (when-not (some-> source-data :removed-sections source-key)
    (hash-map (or target-key source-key) source-key)))


(defmulti initial-draft-data (fn [template & _]
                               (-> template :category keyword)))

(defn- kw-format [& xs]
  (keyword (apply format (map ss/->plain-string xs))))

(defmethod initial-draft-data :r
  [template application]
  (let [source-data (-> template :versions last :data)]
    (data-draft (merge {:giver        :giver
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
                                        kw  {:fn   #(when (some-> % kw :enabled)
                                                      "")
                                             :skip-nil? true}))
                               {}
                               [:julkipano :anto :valitus :lainvoimainen
                                :aloitettava :voimassa])
                       (reduce (fn [acc k]
                                 (merge acc (map-unremoved-section source-data k)))
                               {}
                               [:conditions :neighbors :appeal :statements :collateral
                                :complexity :rights :purpose]))
                template)))

(declare enrich-verdict)

(defn new-verdict-draft [template-id {:keys [application organization created]
                                      :as   command}]
  (let [template (verdict-template @organization template-id)
        version  (-> template :versions last)
        draft    {:id       (mongo/create-id)
                  :data     (initial-draft-data template application)
                  :modified created}]
    (action/update-application command
                               {$push {:matti-verdicts
                                       (assoc draft
                                              :template {:id         template-id
                                                         :version-id (:id version)})}})
    {:verdict  (enrich-verdict command draft version)
     :settings (:settings version)}))

(defn verdict-summary [verdict]
  (select-keys verdict [:id :published :modified]))

(defn command->verdict [{:keys [data application organization]}]
  (util/find-by-id (:verdict-id data)
                   (:matti-verdicts application)))

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
                                                   (keyword (str "matti-verdicts.$.data."
                                                                 (name k)))
                                                   v))
                                          {}
                                          changes)})))

(defn update-automatic-verdict-dates [{:keys [category template verdict-data]}]
  (let [verdict-schema  (category shared/verdict-schemas)
        template-schema shared/default-verdict-template
        datestring      (:verdict-date verdict-data)
        automatic?      (:automatic-verdict-dates verdict-data)]
    (when (and automatic? (ss/not-blank? datestring))
      (reduce (fn [acc kw]
                (let [unit (-> template-schema :dictionary kw :date-delta :unit)
                      {:keys [delta
                              enabled]} (-> template :data kw)]
                  (if enabled
                    (assoc acc
                           kw
                           (date/parse-and-forward datestring
                                                   (util/->long delta)
                                                   unit))
                    acc)))
              {}
              [:julkipano :anto :valitus :lainvoimainen
               :aloitettava :voimassa]))))

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

(defn edit-verdict [{{:keys [verdict-id path value]} :data
                     organization                    :organization
                     application                     :application
                     created                         :created
                     :as                             command}]
  (let [verdict  (command->verdict command)
        category (-> application
                     :permitType
                     shared/permit-type->category)
        schema   (category shared/verdict-schemas)
        template (verdict-template-for-verdict verdict
                                               @organization)]
    (if-let [error (schemas/validate-path-value
                    schema
                    path value
                    (:settings template))]
      {:errors [[path error]]}
      (let [path    (map keyword path)
            updated (assoc-in (:data verdict) path value)]
        (verdict-update command {$set {:matti-verdicts.$.data updated}})
        {:modified created
         :changes  (let [options {:path     path
                                  :value    value
                                  :verdict-data updated
                                  :template template
                                  :category category}
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
                          (keyword id) {:operation   name
                                        :description description
                                        :building-id (->> [:valtakunnallinenNumero
                                                           :manuaalinen_rakennusnro]
                                                          (select-keys data)
                                                          vals
                                                          (util/find-first ss/not-blank?)
                                                          ss/->plain-string)
                                        :tag         (:tunnus data)})))
               {})))

(defn- merge-buildings [app-buildings verdict-buildings]
  (reduce (fn [acc [k v]]
            (assoc acc k (merge (k verdict-buildings) v)))
          {}
          app-buildings))

(defn- buildings?
  "True if the template (really) supports buildings."
  [{data :data}]
  (let [{:keys [removed-sections autopaikat
                paloluokka vss-luokka]} data]
    (and (-> removed-sections :buildings not)
         (some true? [autopaikat paloluokka vss-luokka]))))

;; Augments verdict data, but MUST NOT update mongo (this is called
;; from query actions, too).
(defmulti enrich-verdict (fn [{app :application} & _]
                           (shared/permit-type->category (:permitType app))))

(defmethod enrich-verdict :default [_ verdict _]
  verdict)

(defmethod enrich-verdict :r
  [{:keys [application]} {data :data :as verdict} template]
  (let [addons {;; Buildings added only if the buildings section is in
                ;; the template AND at least one of the checkboxes has
                ;; been selected.
                :buildings (when (buildings? template)
                             (merge-buildings (buildings application)
                                              (:buildings data)))
                }]

    (if (some->> addons vals (remove nil?) seq)
      (assoc-in verdict [:data] (merge data addons))
      verdict)))

(defn open-verdict [{:keys [application organization] :as command}]
  (let [{:keys [data published] :as verdict} (command->verdict command)
        template (verdict-template-for-verdict verdict
                                               @organization)]
    {:verdict  (assoc (select-keys verdict [:id :modified :published])
                      :data (if published
                              data
                              (:data (enrich-verdict command
                                                     verdict
                                                     template))))
     :settings (:settings template)}))
