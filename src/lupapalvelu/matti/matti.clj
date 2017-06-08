(ns lupapalvelu.matti.matti
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.matti.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.util :as util]))

(defn new-verdict-template [org-id timestamp lang]
  (let [data {:id (mongo/create-id)
              :draft {}
              :name (i18n/localize lang
                                   :matti-verdict-template)}]
    (mongo/update-by-id :organizations
                        org-id
                        {$push {:verdict-templates
                                (merge data
                                       {:deleted false
                                        :modified timestamp
                                        :versions []})}})
    data))

(defn verdict-template [{templates :verdict-templates} template-id]
  (util/find-by-id template-id templates))

(defn latest-version [organization template-id]
  (-> (verdict-template organization template-id) :versions last))

(defn- template-update [organization template-id update  & [timestamp]]
  (mongo/update :organizations
                {:_id               (:id organization)
                 :verdict-templates {$elemMatch {:id template-id}}}
                (if timestamp
                  (assoc-in update
                            [$set :verdict-templates.$.modified]
                            timestamp)
                  update)))

(defn save-draft-value [organization template-id timestamp path value]
  (let [template (verdict-template organization template-id)
        draft    (assoc-in (:draft template) (map keyword path) value)]
    (template-update organization
                     template-id
                     {$set {:verdict-templates.$.draft draft}}
                     timestamp)))

(defn publish-verdict-template [organization template-id timestamp]
  (template-update organization
                   template-id
                   {$push {:verdict-templates.$.versions
                           {:id        (mongo/create-id)
                            :published timestamp
                            :data      (:draft (verdict-template organization
                                                                 template-id))}}}))
(defn set-name [organization template-id timestamp name]
  (template-update organization
                   template-id
                   {$set {:verdict-templates.$.name name}}
                   timestamp))

(defn verdict-template-summary [{versions :versions :as template}]
  (assoc (select-keys template
                      [:id :name :modified :deleted])
         :published (-> versions last :published)))

(defn set-deleted [organization template-id deleted?]
  (template-update organization
                   template-id
                   {$set {:verdict-templates.$.deleted deleted?}}))
