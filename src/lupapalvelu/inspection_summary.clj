(ns lupapalvelu.inspection-summary
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error errorf fatal]]
            [sade.core :refer [now unauthorized fail!]]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [clojure.string :as s]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [lupapalvelu.mongo :as mongo]
            [sade.util :as util]
            [sade.schemas :as ssc]
            [schema.core :as sc :refer [defschema]]
            [lupapalvelu.domain :as domain]))

(defschema InspectionSummaryTargets
           {:target-name sc/Str   ;Tarkastuskohde
            :id          ssc/ObjectIdStr
            :finished    sc/Bool
            sc/Keyword   sc/Any})

(defschema InspectionSummary
  {:id ssc/ObjectIdStr
   :name sc/Str
   :op {:id ssc/ObjectIdStr
        (sc/optional-key :name) sc/Str
        (sc/optional-key :description) sc/Str}
   :targets [InspectionSummaryTargets]})

(defn- split-into-template-items [text]
  (remove ss/blank? (map ss/trim (s/split-lines text))))

(defn organization-has-inspection-summary-feature? [organizationId]
  (pos? (mongo/count :organizations {:_id organizationId :inspection-summaries-enabled true})))

(defn inspection-summary-api-auth-admin-pre-check
  [{user :user}]
  (let [org-set (usr/organization-ids-by-roles user #{:authorityAdmin})]
    (when (or (empty? org-set) (not (some organization-has-inspection-summary-feature? org-set)))
      unauthorized)))

(defn inspection-summary-api-authority-pre-check
  [{organization :organization}]
  (when-not (and organization (-> @organization :inspection-summaries-enabled))
    unauthorized))

(defn inspection-summary-api-applicant-pre-check
  [{application :application organization :organization {user-id :id} :user}]
  (when-not (and organization
                 (-> @organization :inspection-summaries-enabled)
                 (domain/owner-or-write-access? application user-id))
    unauthorized))

(defn settings-for-organization [organizationId]
  (get (mongo/by-id :organizations organizationId) :inspection-summary {}))

(defn create-template-for-organization [organizationId name templateText]
  (org/update-organization organizationId
                           {$push {:inspection-summary.templates {:name     name
                                                                  :modified (now)
                                                                  :id       (mongo/create-id)
                                                                  :items    (split-into-template-items templateText)}}}))

(defn update-template [organizationId templateId name templateText]
  (let [query (assoc {:inspection-summary.templates {$elemMatch {:id templateId}}} :_id organizationId)
        changes {$set {:inspection-summary.templates.$.name     name
                       :inspection-summary.templates.$.modified (now)
                       :inspection-summary.templates.$.items    (split-into-template-items templateText)}}]
    (mongo/update-by-query :organizations query changes)))

(defn delete-template [organizationId templateId]
  "Deletes the template and removes all references to it from the operations-templates mapping"
  (let [current-settings    (settings-for-organization organizationId)
        operations-to-unset (util/filter-map-by-val #{templateId} (:operations-templates current-settings))
        operations-to-unset (util/map-keys (partial util/kw-path :inspection-summary :operations-templates) operations-to-unset)]
    (when (not-empty operations-to-unset)
      (mongo/update-by-query :organizations {:_id organizationId} {$unset operations-to-unset}))
    (mongo/update-by-query :organizations {:_id organizationId} {$pull {:inspection-summary.templates {:id templateId}}})))

(defn set-default-template-for-operation [organizationId operationName templateId]
  (let [field  (str "inspection-summary.operations-templates." operationName)
        update (if (= templateId "_unset")
                 {$unset {field 1}}
                 {$set   {field templateId}})]
    (org/update-organization organizationId update)))

(defn- new-target [name]
  (hash-map :target-name name :finished false :id (mongo/create-id)))

(defn- targets-from-template [template]
  (map new-target (:items template)))

(defn new-summary-for-operation [{appId :id orgId :organization} {opId :id :as operation} templateId]
  (let [template (util/find-by-key :id templateId (:templates (settings-for-organization orgId)))
        new-id   (mongo/create-id)
        summary  (assoc (select-keys template [:name])
                   :id      new-id
                   :op      (select-keys operation [:id :name :description])
                   :targets (targets-from-template template))]
    (mongo/update :applications {:_id appId} {$push {:inspection-summaries summary}})
    new-id))

(defn default-template-id-for-operation [organization {opName :name}]
  (get-in organization [:inspection-summary :operations-templates (keyword opName)]))

(defn process-verdict-given [{:keys [organization primaryOperation inspection-summaries] :as application}]
  (let [organization (org/get-organization organization)]
    (when (and (:inspection-summaries-enabled organization) (empty? inspection-summaries))
      (when-let [templateId (default-template-id-for-operation organization primaryOperation)]
        (new-summary-for-operation application primaryOperation templateId)))))

(defn- elem-match-query [appId summaryId]
  {:_id appId :inspection-summaries {$elemMatch {:id summaryId}}})

(defn remove-target [appId summaryId targetId]
  (mongo/update-by-query :applications
                         (elem-match-query appId summaryId)
                         {$pull {:inspection-summaries.$.targets {:id targetId}}}))

(defn add-target [appId summaryId targetName]
  (let [new-target (new-target targetName)]
    (mongo/update :applications
                  (elem-match-query appId summaryId)
                  {$push {:inspection-summaries.$.targets new-target}})
    (:id new-target)))

(defn edit-target [application summaryId targetId & kvs]
  (let [summary (->> application :inspection-summaries (util/find-by-id summaryId))
        index   (->> summary :targets (util/position-by-id targetId))
        updates (util/map-keys #(util/kw-path :inspection-summaries.$.targets index %) (apply hash-map kvs))]
    (when-not index
      (fail! :error.summary-target.edit.not-found))
    (mongo/update-by-query :applications
                           {:_id (:id application) :inspection-summaries {$elemMatch {:id summaryId}}}
                           {$set updates})))