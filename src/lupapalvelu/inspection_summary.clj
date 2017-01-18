(ns lupapalvelu.inspection-summary
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error errorf fatal]]
            [sade.core :refer [now unauthorized]]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [clojure.string :as s]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [lupapalvelu.mongo :as mongo]))

(defn- split-into-template-items [text]
  (remove ss/blank? (map ss/trim (s/split-lines text))))

(defn organization-has-inspection-summary-feature? [organizationId]
  (pos? (mongo/count :organizations {:_id organizationId :inspection-summaries-enabled true})))

(defn inspection-summary-api-auth-admin-pre-check
  [{user :user}]
  (let [org-set (usr/organization-ids-by-roles user #{:authorityAdmin})]
    (when (or (empty? org-set) (not (some organization-has-inspection-summary-feature? org-set)))
      unauthorized)))

(defn create-template-for-organization [organizationId name templateText]
  (org/update-organization organizationId
                           {$push {:inspection-summary-templates {:name     name
                                                                  :modified (now)
                                                                  :id       (mongo/create-id)
                                                                  :items    (split-into-template-items templateText)}}}))

(defn update-template [organizationId templateId name templateText]
  (let [query (assoc {:inspection-summary-templates {$elemMatch {:id templateId}}} :_id organizationId)]
    (mongo/update-by-query :organizations query
                           {$set {:inspection-summary-templates.$.name name
                                  :inspection-summary-templates.$.modified (now)
                                  :inspection-summary-templates.$.items (split-into-template-items templateText)}})))

(defn delete-template [organizationId templateId]
  (org/update-organization organizationId {$pull {:inspection-summary-templates {:id templateId}}}))