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
                                   :matti.verdict-template)}]
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

(defn template-update [organization template-id update]
  (mongo/update :organizations
                {:_id               (:id organization)
                 :verdict-templates {$elemMatch {:id template-id}}}
                update))

(defn save-draft-value [organization template-id timestamp path value]
  (let [template (verdict-template organization template-id)
        draft    (assoc-in (:draft template) path value)]
    (template-update organization
                     template-id
                     {$set {:verdict-templates.$.draft    draft
                            :verdict-templates.$.modified timestamp}})))

(defn publish-verdict-template [organization template-id timestamp]
  (mongo/update organization
                template-id
                {$push {:verdict-templates.$.versions
                        {:id        (mongo/create-id)
                         :published timestamp
                         :data      (:draft (verdict-template organization
                                                              template-id))}}}))
(defn set-name [organization template-id name]
  (template-update organization
                   template-id
                   {$set {:verdict-templates.$.name name}}))
