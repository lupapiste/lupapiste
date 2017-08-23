(ns lupapalvelu.matti.matti
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.i18n :as i18n]
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
  (let [template (verdict-template organization template-id)
        draft    (assoc-in (:draft template) (map keyword path) value)]
    (or (schemas/validate-path-value shared/default-verdict-template path value
                                     {:schema-overrides {:section shared/MattiVerdictSection}
                                      :references {:settings (:draft (settings organization
                                                                               (:category template)))}})
        (template-update organization
                         template-id
                         {$set {:verdict-templates.templates.$.draft draft}}
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

(defn save-settings-value [organization category timestamp path value]
  (let [draft        (assoc-in (:draft (settings organization category))
                               (map keyword path)
                               value)
        settings-key (settings-key category)]
    (mongo/update-by-id :organizations
                        (:id organization)
                        {$set {settings-key {:draft    draft
                                             :modified timestamp}}})))

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

(defn data-draft
  "Kmap keys are draft targets (kw-paths). Values are either kw-paths
  or maps with :path, :fn and :skip-nil? properties. Paths denote data
  sources. :fn is the source handler function (default identity),
  if :skip-nil? is true the nil value entries are skipped (default
  false)."
  [kmap template]
  (let [data (-> template :versions last :data)]
    (reduce (fn [acc [k v]]
              (let [v-path (get v :path v)
                    v-fn   (get v :fn identity)
                    value  (v-fn (get-in data (util/split-kw-path v-path)))]
                (if (and (nil? value) (:skip-nil? v))
                  acc
                  (assoc-in acc
                            (util/split-kw-path k)
                            value))))
            {}
            kmap)))

(defmulti initial-draft-data (fn [template & _]
                               (-> template :category keyword)))

(defn- kw-format [& xs]
  (keyword (apply format xs)))

(defmethod initial-draft-data :r
  [template application]
  (assoc-in (data-draft (merge {:matti-verdict.0.giver.giver  :matti-verdict.2.giver
                                :matti-verdict.0.verdict-code :matti-verdict.2.verdict-code
                                :matti-verdict.1.paatosteksti :matti-verdict.3.paatosteksti}
                               (reduce-kv (fn [acc i v]
                                            (assoc acc
                                                   (kw-format "requirements.%s.0" i)
                                                   (kw-format "matti-%s.0.0" (name v))
                                                   (kw-format "requirements.%s.included" i)
                                                   {:path (kw-format "matti-%s.removed" (name v))
                                                    :fn   not}))
                                          {}
                                          [:foremen :plans :reviews])
                               {:requirements.3.other :matti-conditions.0.0}
                               (reduce (fn [acc k]
                                         (let [s (name k)]
                                           (assoc acc
                                                  (kw-format "matti-dates.deltas.%s" s)
                                                  {:path      (kw-format "matti-verdict.1.%s" s)
                                                   :fn #(when (:enabled %) "")
                                                   :skip-nil? true})))
                                       {}
                                       [:julkipano :anto :valitus :lainvoimainen
                                        :aloitettava :voimassa]))
                        template)
            (util/split-kw-path :matti-verdict.1.application-id)
            (:id application)))

(defn new-verdict-draft [template-id {:keys [application organization created] :as command}]
  (let [template (verdict-template @organization template-id)
        data     (initial-draft-data template application)]
    (action/update-application command
                               {$push {:matti-verdicts
                                       {:id (mongo/create-id)
                                        :template-id template-id
                                        :data        data}}})
    {:data     (assoc data :modified created)
     :settings (-> template :versions last :settings)}))
