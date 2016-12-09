(ns lupapalvelu.tiedonohjaus-api
  (:require [lupapalvelu.action :refer [defquery defcommand non-blank-parameters] :as action]
            [sade.core :refer [ok fail fail!]]
            [lupapalvelu.tiedonohjaus :as t]
            [lupapalvelu.organization :as o]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]
            [monger.operators :refer :all]
            [lupapalvelu.action :as action]
            [lupapiste-commons.tos-metadata-schema :as tms]
            [schema.core :as s]
            [taoensso.timbre :as timbre]
            [lupapiste-commons.schema-utils :as schema-utils]
            [lupapalvelu.attachment-api :as aa]
            [lupapalvelu.application :as a]
            [lupapalvelu.archiving-api :as archiving-api]
            [lupapalvelu.permit :as permit]))

(defquery available-tos-functions
  {:user-roles #{:anonymous}
   :parameters [organizationId]
   :input-validators [(partial non-blank-parameters [:organizationId])]}
  (let [functions (t/available-tos-functions organizationId)]
    (ok :functions functions)))

(defn- store-function-code [operation function-code user]
  (let [orgId (user/authority-admins-organization-id user)
        organization (o/get-organization orgId)
        operation-valid? (some #{operation} (:selected-operations organization))
        code-valid? (some #{function-code} (map :code (t/available-tos-functions orgId)))]
    (if (and operation-valid? code-valid?)
      (do (o/update-organization orgId {$set {(str "operations-tos-functions." operation) function-code}})
          (ok))
      (fail "error.unknown-operation"))))

(defcommand set-tos-function-for-operation
  {:parameters [operation functionCode]
   :user-roles #{:authorityAdmin}
   :input-validators [(partial non-blank-parameters [:functionCode :operation])]}
  [{user :user}]
  (store-function-code operation functionCode user))

(defcommand remove-tos-function-from-operation
  {:parameters [operation]
   :user-roles #{:authorityAdmin}
   :input-validators [(partial non-blank-parameters [:operation])]}
  [{user :user}]
  (let [orgId (user/authority-admins-organization-id user)]
    (o/update-organization orgId {$unset {(str "operations-tos-functions." operation) ""}})
    (ok)))

(defn- update-tos-metadata [function-code {:keys [application created user] :as command} & [correction-reason]]
  (if-let [tos-function-map (t/tos-function-with-name function-code (:organization application))]
    (let [orgId (:organization application)
          updated-attachments (map #(t/document-with-updated-metadata % orgId function-code application) (:attachments application))
          {updated-metadata :metadata} (t/document-with-updated-metadata application orgId function-code application "hakemus")
          process-metadata (t/calculate-process-metadata (t/metadata-for-process orgId function-code) updated-metadata updated-attachments)]
      (action/update-application command
                                 {$set {:modified created
                                        :tosFunction function-code
                                        :metadata updated-metadata
                                        :processMetadata process-metadata
                                        :attachments updated-attachments}
                                  $push {:history (a/tos-history-entry tos-function-map created user correction-reason)}})
      (ok))
    (fail "error.invalid-tos-function")))

(defcommand set-tos-function-for-application
  {:parameters [:id functionCode]
   :input-validators [(partial non-blank-parameters [:id :functionCode])]
   :user-roles #{:authority}
   :states states/pre-verdict-but-draft}
  [command]
  (update-tos-metadata functionCode command))

(defcommand force-fix-tos-function-for-application
  {:parameters [:id functionCode reason]
   :input-validators [(partial non-blank-parameters [:id :functionCode :reason])]
   :user-roles #{:authority}
   :states states/all-application-states-but-draft-or-terminal
   :pre-checks [archiving-api/check-user-is-archivist]}
  [command]
  (update-tos-metadata functionCode command reason))

(def schema-to-input-type-map
  {s/Str "text"
   tms/NonEmptyStr "text"
   tms/Vuodet "number"
   s/Bool "checkbox"
   s/Inst "date"})

(def editable-metadata-fields
  (->> (remove #(= tms/Tila %) tms/asiakirja-metadata-fields)
       (concat tms/common-metadata-fields)))

(defn- metadata-schema-for-ui [field]
  (cond-> field
          (:dependencies field) (->> (:dependencies)
                                     (map (fn [[k v]] {k (map metadata-schema-for-ui v)}))
                                     (into {})
                                     (assoc field :dependencies))
          (:subfields field) (->> (:subfields)
                                  (map metadata-schema-for-ui)
                                  (assoc field :subfields))
          (:schema field) (-> (assoc :inputType (get schema-to-input-type-map (:schema field)))
                              (dissoc :schema))))

(defquery tos-metadata-schema
  {:user-roles #{:anonymous}
   :parameters [schema]
   :input-validators [(partial non-blank-parameters [:schema])]}
  (let [fields (if (= "caseFile" schema) tms/common-metadata-fields editable-metadata-fields)]
    (ok :schema (map metadata-schema-for-ui fields))))

(defn- revert-unauthorized-modifications [new-metadata old-metadata roles]
  (let [disallowed-metadata (filter (fn [field] (when-let [role (:require-role field)]
                                                  (not (contains? roles role))))
                              editable-metadata-fields)
        disallowed-keys (cond-> (map :type disallowed-metadata)
                                (= (get-in old-metadata [:sailytysaika :arkistointi]) :ikuisesti) (conj :sailytysaika))
        replacement-metadata (select-keys old-metadata disallowed-keys)]
    (merge new-metadata replacement-metadata)))

(defn- sanitize-metadata [m]
  (try
    (tms/sanitize-metadata m)
    (catch Throwable t
      (timbre/warn t)
      (fail! "error.invalid.metadata"))))

(defn- update-document-metadata [document metadata user-roles application]
  (let [old-metadata (schema-utils/coerce-metadata-to-schema tms/AsiakirjaMetaDataMap (:metadata document))
        metadata (-> (schema-utils/coerce-metadata-to-schema tms/AsiakirjaMetaDataMap metadata)
                     (revert-unauthorized-modifications old-metadata user-roles)
                     (t/update-end-dates (:verdicts application))
                     (assoc :tila (or (:tila old-metadata) :luonnos))
                     sanitize-metadata)]
    (assoc document :metadata metadata)))

(defn- update-application-child-metadata! [{:keys [application created user] :as command} type id metadata]
  (if-let [child (first (filter #(= (:id %) id) (type application)))]
    (let [user-roles (get-in user [:orgAuthz (keyword (:organization application))])
          updated-child (update-document-metadata child metadata user-roles application)
          updated-children (-> (remove #(= % child) (type application)) (conj updated-child))]
      (action/update-application command {$set {:modified created type updated-children}})
      (t/update-process-retention-period (:id application) created)
      (ok {:metadata (:metadata updated-child)}))
    (fail "error.child.id")))

(defn- process-case-file-metadata [old-metadata new-metadata user-roles]
  (let [coerced-old-metadata (schema-utils/coerce-metadata-to-schema tms/MetaDataMap old-metadata)]
    (-> (schema-utils/coerce-metadata-to-schema tms/MetaDataMap new-metadata)
        (revert-unauthorized-modifications coerced-old-metadata user-roles)
        sanitize-metadata)))

(defcommand store-tos-metadata-for-attachment
  {:parameters [:id attachmentId metadata]
   :categories #{:attachments}
   :input-validators [(partial non-blank-parameters [:id :attachmentId])
                      (partial action/map-parameters [:metadata])]
   :user-roles #{:authority}
   :states states/all-but-draft}
  [{:keys [application created] :as command}]
  (update-application-child-metadata! command :attachments attachmentId metadata))

(defcommand store-tos-metadata-for-application
  {:parameters [:id metadata]
   :input-validators [(partial non-blank-parameters [:id])
                      (partial action/map-parameters [:metadata])]
   :user-roles #{:authority}
   :states states/all-but-draft}
  [{:keys [application created user] :as command}]
  (let [user-roles (get-in user [:orgAuthz (keyword (:organization application))])
        {processed-metadata :metadata} (update-document-metadata application metadata user-roles application)]
    (action/update-application command {$set {:modified created
                                              :metadata processed-metadata}})
    (t/update-process-retention-period (:id application) created)
    (ok {:metadata processed-metadata})))

(defcommand store-tos-metadata-for-process
  {:parameters [:id metadata]
   :input-validators [(partial non-blank-parameters [:id])
                      (partial action/map-parameters [:metadata])]
   :user-roles #{:authority}
   :states states/all-but-draft}
  [{:keys [application created user] :as command}]
  (let [user-roles (get-in user [:orgAuthz (keyword (:organization application))])
        processed-metadata (-> (process-case-file-metadata (:processMetadata application) metadata user-roles)
                               (t/update-end-dates (:verdicts application))
                               (t/calculate-process-metadata (:metadata application) (:attachments application)))]
    (action/update-application command {$set {:modified created
                                              :processMetadata processed-metadata}})
    (ok {:metadata processed-metadata})))

(defquery case-file-data
  {:parameters [:id lang]
   :input-validators [(partial action/non-blank-parameters [:lang])]
   :user-roles #{:authority}
   :states states/all-application-states}
  [{:keys [application]}]
  (ok :process (t/generate-case-file-data application lang)))

(defquery tos-operations-enabled
  {:user-roles #{:authority}
   :categories #{:attachments}
   :states states/all-application-states}
  (ok))


(defquery common-area-application
  {:parameters [id]
   :states states/all-states
   :user-roles #{:applicant :authority}
   :pre-checks [(permit/validate-permit-type-is permit/YA)]}
  [_])
