(ns lupapalvelu.tiedonohjaus-api
  (:require [lupapalvelu.action :refer [defquery defcommand non-blank-parameters boolean-parameters] :as action]
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
            [lupapalvelu.permit :as permit]
            [lupapiste-commons.operations :as operations]
            [lupapalvelu.archive.archiving-util :as archiving-util]))

(defn- target-is-not-archived [target-type {{attachment-id :attachmentId} :data {:keys [metadata processMetadata attachments]} :application}]
  (let [md (case target-type
             :application metadata
             :process processMetadata
             :attachment (-> (filter #(= attachment-id (:id %)) attachments) first :metadata))]
    (when (= :arkistoitu (keyword (:tila md)))
      (fail :error.command-illegal-state))))

(defquery available-tos-functions
  {:user-roles       #{:anonymous}
   :parameters       [organizationId]
   :input-validators [(partial non-blank-parameters [:organizationId])]}
  (let [functions (t/available-tos-functions organizationId)]
    (ok :functions functions)))

(defn- store-function-code [organizationId operation function-code]
  (let [organization (o/get-organization organizationId)
        operation-valid? (some #{operation} (concat (:selected-operations organization)
                                                    (map name operations/archiving-project-operations)))
        code-valid? (some #{function-code} (map :code (t/available-tos-functions organizationId)))]
    (if (and operation-valid? code-valid?)
      (do (o/update-organization organizationId {$set {(str "operations-tos-functions." operation) function-code}})
          (ok))
      (fail "error.unknown-operation"))))

(defcommand set-tos-function-for-operation
  {:parameters       [organizationId operation functionCode]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial non-blank-parameters [:functionCode :operation])]}
  [_]
  (store-function-code organizationId operation functionCode))

(defcommand remove-tos-function-from-operation
  {:parameters       [organizationId operation]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [(partial non-blank-parameters [:operation])]}
  [_]
  (o/update-organization organizationId {$unset {(str "operations-tos-functions." operation) ""}})
  (ok))

(defn- update-tos-metadata [function-code command & [correction-reason]]
  (if (t/update-tos-metadata function-code command correction-reason)
    (ok)
    (fail :error.invalid-tos-function)))

(defcommand set-tos-function-for-application
  {:parameters       [:id functionCode]
   :input-validators [(partial non-blank-parameters [:id :functionCode])]
   :user-roles       #{:authority}
   :org-authz-roles  #{:authority :archivist :digitizer}
   :states           (conj states/pre-verdict-but-draft :underReview)}
  [command]
  (update-tos-metadata functionCode command))

(defcommand force-fix-tos-function-for-application
  {:parameters       [:id functionCode reason]
   :input-validators [(partial non-blank-parameters [:id :functionCode :reason])]
   :user-roles       #{:authority}
   :org-authz-roles  #{:archivist}
   :states           states/all-but-draft
   :pre-checks       [permit/is-not-archiving-project]}
  [command]
  (update-tos-metadata functionCode command reason))

(def schema-to-input-type-map
  {s/Str           "text"
   tms/NonEmptyStr "text"
   tms/Vuodet      "number"
   s/Bool          "checkbox"
   s/Inst          "date"})

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
  {:user-roles       #{:anonymous}
   :parameters       [schema]
   :input-validators [(partial non-blank-parameters [:schema])]}
  (let [fields (if (= "caseFile" schema) tms/common-metadata-fields editable-metadata-fields)]
    (ok :schema (map metadata-schema-for-ui fields))))

(defn- revert-unauthorized-modifications [new-metadata old-metadata roles]
  (let [disallowed-metadata  (filter (fn [field] (when-let [role (:require-role field)]
                                                   (not (contains? roles role))))
                                     editable-metadata-fields)
        disallowed-keys      (map :type disallowed-metadata)
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
        metadata     (-> (schema-utils/coerce-metadata-to-schema tms/AsiakirjaMetaDataMap metadata)
                         (revert-unauthorized-modifications old-metadata user-roles)
                         (t/update-end-dates (:verdicts application))
                         (assoc :tila (or (:tila old-metadata) :luonnos))
                         sanitize-metadata)]
    (assoc document :metadata metadata)))

(defn- update-application-child-metadata! [{:keys [application created user] :as command} child-type id metadata]
  (if-let [child (first (filter #(= (:id %) id) (child-type application)))]
    (let [user-roles       (get-in user [:orgAuthz (keyword (:organization application))])
          updated-child    (update-document-metadata child metadata user-roles application)
          updated-children (-> (remove #(= % child) (child-type application)) (conj updated-child))]
      (action/update-application command {$set {:modified created child-type updated-children}})
      (t/update-process-retention-period (:id application) created)
      (archiving-util/mark-application-archived-if-done application created user)
      (ok {:metadata (:metadata updated-child)}))
    (fail "error.child.id")))

(defn- process-case-file-metadata [old-metadata new-metadata user-roles]
  (let [coerced-old-metadata (schema-utils/coerce-metadata-to-schema tms/MetaDataMap old-metadata)]
    (-> (schema-utils/coerce-metadata-to-schema tms/MetaDataMap new-metadata)
        (revert-unauthorized-modifications coerced-old-metadata user-roles)
        sanitize-metadata)))

(defcommand store-tos-metadata-for-attachment
  {:parameters       [:id attachmentId metadata]
   :categories       #{:attachments}
   :input-validators [(partial non-blank-parameters [:id :attachmentId])
                      (partial action/map-parameters [:metadata])]
   :user-roles       #{:authority}
   :org-authz-roles  #{:authority :archivist}
   :states           states/all-but-draft
   :pre-checks       [(partial target-is-not-archived :attachment)]}
  [command]
  (update-application-child-metadata! command :attachments attachmentId metadata))

(defcommand store-tos-metadata-for-application
  {:parameters       [:id metadata]
   :input-validators [(partial non-blank-parameters [:id])
                      (partial action/map-parameters [:metadata])]
   :user-roles       #{:authority}
   :org-authz-roles  #{:authority :archivist}
   :states           states/all-but-draft
   :pre-checks       [(partial target-is-not-archived :application)]}
  [{:keys [application created user] :as command}]
  (let [user-roles (get-in user [:orgAuthz (keyword (:organization application))])
        {processed-metadata :metadata} (update-document-metadata application metadata user-roles application)]
    (action/update-application command {$set {:modified created
                                              :metadata processed-metadata}})
    (t/update-process-retention-period (:id application) created)
    (ok {:metadata processed-metadata})))

(defcommand store-tos-metadata-for-process
  {:parameters       [:id metadata]
   :input-validators [(partial non-blank-parameters [:id])
                      (partial action/map-parameters [:metadata])]
   :user-roles       #{:authority}
   :org-authz-roles  #{:authority :archivist}
   :states           states/all-but-draft
   :pre-checks       [(partial target-is-not-archived :process)]}
  [{:keys [application created user] :as command}]
  (let [user-roles         (get-in user [:orgAuthz (keyword (:organization application))])
        processed-metadata (-> (process-case-file-metadata (:processMetadata application) metadata user-roles)
                               (t/update-end-dates (:verdicts application))
                               (t/calculate-process-metadata (:metadata application) (:attachments application)))]
    (action/update-application command {$set {:modified        created
                                              :processMetadata processed-metadata}})
    (ok {:metadata processed-metadata})))

(defcommand set-myyntipalvelu-for-attachment
  {:parameters       [:id attachmentId myyntipalvelu]
   :categories       #{:attachments}
   :input-validators [(partial non-blank-parameters [:id :attachmentId])
                      (partial boolean-parameters [:myyntipalvelu])]
   :user-roles       #{:authority}
   :org-authz-roles  #{:authority :archivist :digitizer}
   :states           states/all-but-draft
   :pre-checks       [(partial target-is-not-archived :attachment)]}
  [{:keys [application created]}]
  (if (pos? (t/update-metadata-attribute! application created attachmentId :myyntipalvelu myyntipalvelu))
    (ok)
    (fail :error.invalid-id)))

(defquery case-file-data
  {:parameters       [:id lang]
   :input-validators [(partial action/non-blank-parameters [:lang])]
   :user-roles       #{:authority}
   :org-authz-roles  #{:authority :archivist}
   :states           states/all-application-states
   :pre-checks       [(permit/validate-permit-type-is-not permit/ARK)]}
  [{:keys [application]}]
  (ok :process (t/generate-case-file-data application lang)))

(defquery tos-operations-enabled
  {:parameters       [id]
   :user-roles       #{:authority}
   :org-authz-roles  #{:authority :archivist}
   :categories       #{:attachments}
   :states     states/all-application-or-archiving-project-states}
  (ok))


(defquery common-area-application
  {:parameters [id]
   :states     states/all-states
   :user-roles #{:applicant :authority}
   :pre-checks [(permit/validate-permit-type-is permit/YA)]}
  [_])
