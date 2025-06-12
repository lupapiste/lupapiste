(ns lupapalvelu.tiedonohjaus-api
  (:require [lupapalvelu.action :refer [boolean-parameters defcommand defquery non-blank-parameters] :as action]
            [lupapalvelu.archive.archiving-util :as archiving-util]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.organization :as o]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.tiedonohjaus :as t]
            [lupapiste-commons.operations :as operations]
            [lupapiste-commons.tos-metadata-schema :as tms]
            [monger.operators :refer :all]
            [sade.core :refer [fail ok]]
            [schema.core :as s]))

(defn- target-is-not-archived [target-type {{:keys [attachmentId fileId]}                   :data
                                            {:keys [id metadata processMetadata attachments
                                                    foreman-list-metadata]}                 :application
                                            organization                                    :organization}]
  (let [md (case target-type
             :application  metadata
             :process      processMetadata
             :attachment   (-> (filter #(= attachmentId (:id %)) attachments) first :metadata)
             :foreman-list foreman-list-metadata
             :linked-file  (-> (linked-files/find-one-linked-file-with-metadata
                                 {:target-entity/file-id        fileId
                                  :target-entity/application-id id})
                               :metadata))]
    (when (and (= :arkistoitu (keyword (:tila md)))
               (not (:replace-archive-with-sftp (force organization))))
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
   :permissions      [{:required [:application/tos-operations]}]
   :states           (conj states/pre-verdict-but-draft :underReview)}
  [command]
  (update-tos-metadata functionCode command))

(defcommand force-fix-tos-function-for-application
  {:parameters       [:id functionCode reason]
   :input-validators [(partial non-blank-parameters [:id :functionCode :reason])]
   :permissions      [{:required [:application/tos-force-fix]}]
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

(defn- metadata-schema-for-ui [field]
  (cond-> field
    (:dependencies field) (->> (:dependencies)
                               (map (fn [[k v]] {k (mapv metadata-schema-for-ui v)}))
                               (into {})
                               (assoc field :dependencies))
    (:subfields field)    (->> (:subfields)
                               (mapv metadata-schema-for-ui)
                               (assoc field :subfields))
    (:schema field)       (-> (assoc :inputType (get schema-to-input-type-map (:schema field)))
                              (dissoc :schema))))

(defquery tos-metadata-schema
  {:user-roles       #{:anonymous}
   :parameters       [schema]
   :input-validators [(partial non-blank-parameters [:schema])]}
  (let [fields (if (= "caseFile" schema) tms/common-metadata-fields t/editable-metadata-fields)]
    (ok :schema (mapv metadata-schema-for-ui fields)
        :salassapitoperuste-kesto-mapping tms/salassapitoperuste-kesto-mapping)))

(defn- update-application-child-metadata! [{:keys [application created user] :as command} child-type id metadata]
  (if-let [child (first (filter #(= (:id %) id) (child-type application)))]
    (let [user-roles         (get-in user [:orgAuthz (keyword (:organization application))])
          processed-metadata (t/process-metadata-update
                               {:old-metadata (:metadata child)
                                :new-metadata metadata
                                :user-roles   user-roles
                                :schema       tms/AsiakirjaMetaDataMap
                                :application  application})
          updated-child      (assoc child :metadata processed-metadata)
          updated-children   (-> (remove #(= % child) (child-type application)) (conj updated-child))]
      (action/update-application command {$set {:modified created child-type updated-children}})
      (t/update-process-retention-period (:id application) created)
      (archiving-util/mark-application-archived-if-done application created user)
      (ok {:metadata (:metadata updated-child)}))
    (fail "error.child.id")))

(defcommand store-tos-metadata-for-attachment
  {:parameters       [:id attachmentId metadata]
   :categories       #{:attachments}
   :input-validators [(partial non-blank-parameters [:id :attachmentId])
                      (partial action/map-parameters [:metadata])]
   :permissions      [{:required [:application/tos-metadata]}]
   :states           states/all-but-draft
   :pre-checks       [(partial target-is-not-archived :attachment)]}
  [command]
  (update-application-child-metadata! command :attachments attachmentId metadata))

(defcommand store-tos-metadata-for-application
  {:parameters       [:id metadata]
   :input-validators [(partial non-blank-parameters [:id])
                      (partial action/map-parameters [:metadata])]
   :permissions      [{:required [:application/tos-metadata]}]
   :states           states/all-but-draft
   :pre-checks       [(partial target-is-not-archived :application)]}
  [{:keys [application created user] :as command}]
  (let [user-roles         (get-in user [:orgAuthz (keyword (:organization application))])
        processed-metadata (t/process-metadata-update
                             {:old-metadata (:metadata application)
                              :new-metadata metadata
                              :user-roles   user-roles
                              :schema       tms/AsiakirjaMetaDataMap
                              :application  application})]
    (action/update-application command {$set {:modified created
                                              :metadata processed-metadata}})
    (t/update-process-retention-period (:id application) created)
    (ok {:metadata processed-metadata})))

(defcommand store-tos-metadata-for-foreman-list
  {:parameters       [:id metadata]
   :input-validators [(partial non-blank-parameters [:id])
                      (partial action/map-parameters [:metadata])]
   :permissions      [{:required [:application/tos-metadata]}]
   :states           states/all-but-draft
   :pre-checks       [(partial target-is-not-archived :foreman-list)]}
  [{:keys [application created user] :as command}]
  (let [user-roles         (get-in user [:orgAuthz (keyword (:organization application))])
        processed-metadata (t/process-metadata-update
                             {:old-metadata (:foreman-list-metadata application)
                              :new-metadata metadata
                              :user-roles   user-roles
                              :schema       tms/AsiakirjaMetaDataMap
                              :application  application})]
    (action/update-application command {$set {:modified              created
                                              :foreman-list-metadata processed-metadata}})
    (ok {:metadata processed-metadata})))

(defcommand store-tos-metadata-for-process
  {:parameters       [:id metadata]
   :input-validators [(partial non-blank-parameters [:id])
                      (partial action/map-parameters [:metadata])]
   :permissions      [{:required [:application/tos-metadata]}]
   :states           states/all-but-draft
   :pre-checks       [(partial target-is-not-archived :process)]}
  [{:keys [application created user] :as command}]
  (let [user-roles         (get-in user [:orgAuthz (keyword (:organization application))])
        processed-metadata (t/process-case-file-metadata-update
                             (:processMetadata application) metadata user-roles application)]
    (action/update-application command {$set {:modified        created
                                              :processMetadata processed-metadata}})
    (ok {:metadata processed-metadata})))

(defcommand store-tos-metadata-for-linked-file
  {:description      "Store TOS metadata for a linked file"
   :parameters       [:id fileId metadata]
   :input-validators [(partial non-blank-parameters [:id :fileId])
                      (partial action/map-parameters [:metadata])]
   :permissions      [{:required [:application/tos-metadata]}]
   :states           states/all-but-draft
   :pre-checks       [(partial target-is-not-archived :linked-file)]}
  [{:keys [application user]}]
  (if-let [linked-file (linked-files/find-one-linked-file-with-metadata
                         {:target-entity/file-id fileId
                          :target-entity/application-id (:id application)})]
    (let [user-roles         (get-in user [:orgAuthz (keyword (:organization application))])
          processed-metadata (t/process-metadata-update
                               {:old-metadata  (:metadata linked-file)
                                :new-metadata  metadata
                                :user-roles    user-roles
                                :schema        tms/AsiakirjaMetaDataMap
                                :application   application
                                :preserve-keys [:name :model-type :model-phase :ryhti]})
          query              (linked-files/gen-linked-file-mongo-query {:file-id fileId :application-id (:id application)})]
      (linked-files/upsert-linked-file query (-> linked-file
                                                 (assoc :metadata processed-metadata)
                                                 (dissoc :id)))
      (ok {:metadata processed-metadata}))
    (fail :error.linked-file-not-found)))

(defcommand set-myyntipalvelu-for-attachment
  {:parameters       [:id attachmentId myyntipalvelu]
   :categories       #{:attachments}
   :input-validators [(partial non-blank-parameters [:id :attachmentId])
                      (partial boolean-parameters [:myyntipalvelu])]
   :permissions      [{:required [:application/tos-operations]}]
   :states           states/all-but-draft
   :pre-checks       [(partial target-is-not-archived :attachment)]}
  [{:keys [application created]}]
  (if (pos? (t/update-metadata-attribute! application created attachmentId :myyntipalvelu myyntipalvelu))
    (ok)
    (fail :error.invalid-id)))

(defquery case-file-data
  {:parameters       [:id lang]
   :input-validators [(partial non-blank-parameters [:lang])]
   :permissions      [{:required [:application/tos-metadata]}]
   :states           states/all-application-states
   :pre-checks       [(permit/validate-permit-type-is-not permit/ARK)]}
  [{:keys [application]}]
  (ok :process (t/generate-case-file-data application lang)))

(defquery tos-operations-enabled
  {:parameters       [id]
   :permissions      [{:required [:application/tos-metadata]}]
   :categories       #{:attachments}
   :states           states/all-application-or-archiving-project-states}
  (ok))

(defquery common-area-application
  {:parameters [id]
   :states     states/all-states
   :user-roles #{:applicant :authority}
   :pre-checks [(permit/validate-permit-type-is permit/YA)]}
  [_]
  (ok))
