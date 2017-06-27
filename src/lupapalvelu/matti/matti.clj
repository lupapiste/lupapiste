(ns lupapalvelu.matti.matti
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.matti.schemas :as schemas]
            [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn organization-categories [{scope :scope}]
  (->> (map (comp keyword ss/lower-case :permitType) scope)
       (map #(cond
               (#{:r :p :ya} %) %
               (#{:kt :mm} %)   :kt
               :else            :ymp))
       set))

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

(defn save-draft-value
  "Error code on failure (see schemas for details)."
  [organization template-id timestamp path value]
  (let [template (verdict-template organization template-id)
        draft    (assoc-in (:draft template) (map keyword path) value)]
    (or (schemas/validate-path-value shared/default-verdict-template path value
                                     {:schema-overrides {:section shared/MattiVerdictSection}
                                      :references {:settings (:draft (settings organization (:category template)))}})
        (template-update organization
                         template-id
                         {$set {:verdict-templates.templates.$.draft draft}}
                         timestamp))))

(defn publish-verdict-template [organization template-id timestamp]
  (template-update organization
                   template-id
                   {$push {:verdict-templates.templates.$.versions
                           {:id        (mongo/create-id)
                            :published timestamp
                            :data      (:draft (verdict-template organization
                                                                 template-id))}}}))
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
                        {$set {settings-key {:draft draft
                                             :modified timestamp}}})))

;; Settings reviews

(defn new-review [organization-id category]
  (let [data {:id       (mongo/create-id)
              :name     {:fi (i18n/localize :fi :matti.katselmus)
                         :sv (i18n/localize :sv :matti.katselmus)
                         :en (i18n/localize :en :matti.katselmus)}
              :category category
              :type     :muu-katselmus
              :deleted  false}]
    (mongo/update-by-id :organizations
                        organization-id
                        {$push {:verdict-templates.reviews
                                data}})
    data))

(defn reviews [{verdict-templates :verdict-templates} category]
  (filter #(util/=as-kw category (:category %))
          (:reviews verdict-templates)))

(defn review [organization-id review-id]
  (some->> (org/get-organization organization-id)
           :verdict-templates
           :reviews
           (util/find-by-id review-id)))

(defn review-update [organization-id review-id update  & [timestamp]]
  (mongo/update :organizations
                {:_id     organization-id
                 :verdict-templates.reviews {$elemMatch {:id review-id}}}
                (if timestamp
                  (assoc-in update
                            [$set (settings-key (:category (review organization-id
                                                                   review-id))
                                                :modified)]
                            timestamp)
                  update)))


(defn set-review-details [organization-id timestamp review-id data]
  (let [detail-updates
        (reduce (fn [acc [k v]]
                  (let [k (keyword k)]
                    (merge acc
                           (cond
                             (k #{:fi :sv :en}) {(str "name." (name k)) (ss/trim v)}
                             (= k :type)        {:type v}
                             (= k :deleted)     {:deleted v}))))
                {}
                data)]
    (when (seq detail-updates)
      (review-update organization-id
                     review-id
                     {$set (->> detail-updates
                                (map (fn [[k v]]
                                       [(keyword (str "verdict-templates.reviews.$." (name k))) v]))
                                (into {}))}
                     timestamp))))
