(ns lupapalvelu.linked-file-api
  "API for file upload directly to GCS (using GCS resumable upload), download these files and update their metadata"
  (:require [lupapalvelu.action :as action :refer [defcommand defquery defraw]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.automatic-assignment.factory :as factory]
            [lupapalvelu.bim.checking-plan.schema :as bim-cp-schema]
            [lupapalvelu.bim.core :as bim]
            [lupapalvelu.bim.solibri.checkset :as solbri-checkset]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.organization :as org]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.ryhti.util :as ryhti-util]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.storage.file-utils :as file-utils]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.tiedonohjaus :as tos]
            [monger.operators :refer [$or]]
            [sade.core :refer [fail ok]]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(defn ensure-bim-tos-metadata!
  "Make sure that the linked file is either a BIM model or
  attachment. If it is, then we apply the archiving metadata from TOS
  if it exists. All other linked file types will be unaffected."
  [linked-file-target user]
  (when (env/feature? :bim-archiving)
    (let [linked-file-metadata-db (linked-files/find-one-linked-file-with-metadata
                                   linked-file-target
                                   :include-query true)
          linked-file-metadata    (:result linked-file-metadata-db)
          target-type             (get-in linked-file-metadata [:target-entity :target-type])]
      (when (#{"bim-model-file" "bim-inspection-report" "bim-additional-file"} target-type)
        (let [application         (domain/get-application-as {:_id (:application-id linked-file-target)}
                                                             user
                                                             :include-canceled-apps? true)
              updated-linked-file (tos/linked-file-with-updated-metadata linked-file-metadata
                                                                         (:organization application)
                                                                         (:tosFunction application)
                                                                         application)]
          (linked-files/upsert-linked-file (:query linked-file-metadata-db) (dissoc updated-linked-file :id)))))))

(defcommand init-resumable-upload-for-application
  {:description         "Initializes a resumable upload link to GCS (Google Cloud Storage).
                         Content type is parsed from the filename."
   :parameters          [:id
                         filename
                         md5-digest]
   :optional-parameters [file-id]
   :permissions         linked-files/linked-file-edit-permissions
   :input-validators    [(comp mime/allowed-file? :filename)
                         (partial action/non-blank-parameters [:id :filename :md5-digest])]}
  [{:keys [user system]}]
  ;; NOTE: The application (or rather its id) is only used for authorization,
  ;; the link itself is not tied to a particular application.
  ;; NOTE: The filename is not stored anywhere, we simply use it to guess the mime type
  (let [uid          (:id user)
        content-type (mime/mime-type filename)
        host         (components/system->host system)
        res          (linked-files/init-resumable-upload-for-application host uid content-type md5-digest file-id)]
    (ok res)))

(defn file&version-meta-with-ryhti-ids
  "Generates and merges Ryhti IDs into version and file metadata when ryhti-data-2026 is enabled.
   Only queries existing file metadata when ryhti-data-2026 is true."
  [{:keys [version-metadata file-metadata]}
   {:keys [ryhti-data-2026 bim-model-file? linked-file-target]}]
  (let [existing-file       (when ryhti-data-2026
                              (:result (linked-files/find-one-linked-file-with-metadata linked-file-target)))
        add-file-ryhti-ids? (and ryhti-data-2026
                                 (not (get-in existing-file [:metadata :ryhti])))]
    {:version-metadata (cond-> version-metadata
                         ryhti-data-2026 (util/deep-merge (ryhti-util/ryhti-id :fileKey)))
     :file-metadata    (cond-> file-metadata
                         add-file-ryhti-ids? (util/deep-merge (ryhti-util/ryhti-id :attachmentDocumentKey))
                         (and add-file-ryhti-ids? bim-model-file?) (util/deep-merge (ryhti-util/ryhti-id :buildingInformationModelKey)))}))

(defn- linked-file-target-precheck
  [{{:keys [id linked-file-target]} :data
    application                     :application}]
  (when (and id linked-file-target)
    (let [{:keys [application-id
                  operation-id]} linked-file-target]
      (or
        ;; Verify both `id` and `linked-file-target` refer to the same application
        (when (not= id application-id)
          (fail :error.not-found))
        ;; Verify `operation-id` refers to a valid operation
        (when-not (domain/get-document-by-operation application operation-id)
          (fail :error.not-found))))))

(defcommand link-resumable-upload-for-application
  {:description         "Ensure that resumable file linked to and entity. I.e.
                         1) If unlinked file is found in unlinked file bucket with file id and user id/session id, it is moved
                            to a linked-files bucket.

                         2a) When file is moved from linked-files bucket:
                         2a.1) Ensure that mongo linked-file-metadata document exists for the link-target.
                         2a.2) Append new version to the linked-file-entity.

                         2b) When linked file does not exist in file bucket check that there is linked-file-metadata document
                         having file-id in versions array. If the file is not found, error.cannot-link-resumable-file is
                         returned. I.e. linked file is idempotent. After is it called, unlinked file is moved a linked file
                         bucket and linked-file-metadata document is upserted and have linked file in the version list."
   :parameters          [:id
                         file-version-id
                         linked-file-target
                         version-metadata
                         file-metadata]
   :optional-parameters []
   :permissions         linked-files/linked-file-edit-permissions
   :pre-checks          [(bim/model-phase-pre-check [:linked-file-target :file-id])
                         linked-file-target-precheck]
   :input-validators    [(partial action/non-blank-parameters [:id :file-version-id])
                         (partial action/parameters-matching-schema [:linked-file-target] linked-files/LinkedFileTarget)]}
  [{:keys [user application system]
    :as   command}]
  (let [ryhti-data-2026 (:ryhti-data-2026 application)
        bim-model-file? (-> linked-file-target :target-type (= "bim-model-file"))
        result          (linked-files/ensure-file-version
                          file-version-id
                          linked-file-target
                          user
                          system
                          (file&version-meta-with-ryhti-ids {:version-metadata version-metadata
                                                             :file-metadata    file-metadata}
                                                            {:ryhti-data-2026    ryhti-data-2026
                                                             :bim-model-file?    bim-model-file?
                                                             :linked-file-target linked-file-target}))]
    (when (:ok result)
      (ensure-bim-tos-metadata! linked-file-target user)
      (when bim-model-file?
        (factory/process-bim-model-automatic-assignments command
                                                         file-version-id
                                                         file-metadata)))
    ;; NOTE: The result contains a `post-process-data` map with BIM `job` details, but
    ;; the frontend seems to ignore it..
    result))

(defcommand set-linked-application-file-metadata-field
  {:description      "Update value of a linked field metadata field."
   :parameters       [id linked-file-target field value]
   :pre-checks       [(bim/model-phase-pre-check [:linked-file-target :file-id])]
   :input-validators [(partial action/non-blank-parameters [:id :field])
                      (partial action/parameters-matching-schema [:linked-file-target] linked-files/LinkedFileTarget)]
   :permissions      linked-files/linked-file-edit-permissions}
  [{:keys [user]}]
  (if (= id (:application-id linked-file-target))
    (let [metadata-update-response (linked-files/update-metadata-field linked-file-target field value)]
      (ensure-bim-tos-metadata! linked-file-target user)
      (ok :data metadata-update-response))
    (fail :error.invalid-request)))

(defn ryhti-data-2026-pre-check
  [{:keys [application]}]
  (when-not (:ryhti-data-2026 application)
    (fail :error.invalid-request)))

(defcommand update-linked-file-version-metadata
  {:description      "Update metadata of a linked file. Currently only used for updating ryhti related metadata fields"
   :parameters       [id file-version-id value field-key]
   :pre-checks       [ryhti-data-2026-pre-check]
   :input-validators [(partial action/non-blank-parameters [:id :file-version-id :field-key])]
   :permissions      linked-files/linked-file-edit-permissions}
  [{:keys [user]}]
  (let [linked-file-target {:application-id  id
                            :file-version-id file-version-id}]
    (if-let [_response (linked-files/update-version-metadata-field linked-file-target field-key value)]
      (do
        (ensure-bim-tos-metadata! linked-file-target user)
        (ok))
      (fail :error.invalid-request))))

(defquery fetch-linked-file-metadatas-for-application
  {:description      "Get all linked-files that are type of target-type"
   :parameters       [target-type id]
   :input-validators [(partial action/non-blank-parameters [:id])
                      (partial action/parameters-matching-schema [:target-type] linked-files/LinkedFileTargetTypes)]
   :permissions      linked-files/linked-file-read-permissions}
  [_]
  (->> (linked-files/get-linked-application-files-for {:target-type target-type :application-id id})
       (mapv (fn [metadata]
               (update metadata :id str)))
       (ok :data)))

(defquery fetch-linked-file-metadata
  {:description      "Get metadata for application's linked-file by :target-entity/file-id"
   :parameters       [file-id id]
   :input-validators [{:id      ssc/ApplicationId
                       :file-id sssc/FileId}]
   :permissions      linked-files/linked-file-read-permissions}
  [{:keys [application organization]}]
  (if-let [linked-file-metadata (linked-files/enriched-linked-file-metadata application organization file-id)]
    (merge (-> organization force (select-keys [:bim-compliance-check-enabled]))
           (ok :linked-file-metadata linked-file-metadata
               :linked-file-running-jobs (linked-files/linked-file-running-jobs id (-> linked-file-metadata
                                                                                       linked-files/latest-version
                                                                                       :target-file-path))
               :linked-file-children-metadata (->> (linked-files/find-many-linked-files-with-metadata
                                                     {:target-entity/parent-file-id file-id
                                                      :target-entity/application-id id}
                                                     :include-query false :only-fields [:target-entity :metadata :versions])
                                                   (into []))
               :application-data (select-keys application [:address :primaryOperation :secondaryOperations :ryhti-data-2026])
               :rakennusluokka-enabled (tools/has-rakennusluokka? application organization)))
    (fail :error.file-not-found)))

(defraw linked-file-version
  {:description         "Download linked file. If version-id is not set, the latest file version is reaturned.
                        Endpoint is similar to attachment-api's latest-attachment-version and download-attachment.

                        The main differences are:
                        - metadata data is stored in linked-file-metadata collection in mongo.
                        - automatic preview is not supported
                        - the only supported storage system is GCS"
   :parameters          [:file-id]
   :optional-parameters [:download :file-version-id]
   :input-validators    [(partial action/non-blank-parameters [:file-id])]
   :user-roles          #{:applicant :authority :oirAuthority :financialAuthority}
   :user-authz-roles    roles/all-authz-roles}
  [{{:keys [file-id download file-version-id]} :data user :user}]
  (att/output-attachment
    (linked-files/get-linked-file-content user file-id :linked-file-version-id file-version-id)
    (= download "true")))

(defcommand delete-linked-file
  {:description      "Delete linked file metadata and all file versions."
   :parameters       [id file-id]
   :pre-checks       [(bim/model-phase-pre-check [:file-id])]
   :input-validators [(partial action/non-blank-parameters [:id :file-id])]
   :permissions      linked-files/linked-file-edit-permissions}
  [{{file-id :file-id} :data}]
  (let [linked-files (linked-files/find-linked-files-by-query
                       {:target-entity.application-id id
                        $or                           [{:target-entity.file-id file-id}
                                                       {:target-entity.parent-file-id file-id}]})]
    (doseq [linked-file linked-files]
      (linked-files/delete-linked-file linked-file))
    (ok)))

(defn- get-validation-file-content [app-id file-id]
  (when file-id
    ;; TODO: This functionality should probably be hidden behind some storage interface etc
    (file-utils/gzip->str ((:content (storage/download-from-system app-id file-id :gcs))))))

(defquery fetch-linked-file-bim-results
  {:description         "Get bim check results for application's linked-file by :target-entity/file-id and optional version-id"
   :parameters          [file-id id]
   :input-validators    [(partial action/non-blank-parameters [:id :file-id])]
   :optional-parameters [file-version-id]
   :permissions         linked-files/linked-file-read-permissions
   :pre-checks          [bim/validate-bim-application]}
  [{:keys [organization]}]
  (if-let [{:keys [versions]} (linked-files/find-one-linked-file-with-metadata
                                {:target-entity/file-id        file-id
                                 :target-entity/application-id id})]
    (let [compliance-check? (org/check-organization-flag organization :bim-compliance-check-enabled)
          version           (if file-version-id
                              (util/find-by-key :file-version-id file-version-id versions)
                              (apply util/max-key :uploaded-at versions))
          results           (->> (select-keys (:bim version) [:validation-result (when compliance-check? :compliance-result)])
                                 (util/map-values (comp (partial get-validation-file-content id) :result-file-id))
                                 util/strip-nils)]
      (ok :data results))
    (fail :error.file-not-found)))

(defraw linked-file-bim-result-bcf
  {:description      "Acts as a proxy for BCF files. The system retrieves the URL of the BCF file from Solibri and then
                      returns the file."
   :parameters       [file-id file-version-id check-type]
   :input-validators [{:file-id         sssc/FileId
                       :file-version-id sssc/FileId
                       :check-type      (sc/enum "validation" "compliance")}]
   :user-roles       #{:applicant :authority :oirAuthority :financialAuthority}
   :user-authz-roles roles/all-authz-roles}
  [{user :user}]
  (let [{:keys [versions] {:keys [application-id]} :target-entity}
        (linked-files/find-one-linked-file-with-metadata {:target-entity/file-id file-id})

        ;; Check user permissions to the related application.
        application     (domain/get-application-as application-id user :include-canceled-apps? true)
        version         (util/find-by-key :file-version-id file-version-id versions)
        filename        (some-> version :metadata :name (ss/replace #"\.[^\.]+$" (str "_" check-type ".bcf")))
        external-job-id (some-> (bim-cp-schema/linked-file-version-check-result check-type version)
                                :external-job-id)]
    (att/output-attachment
      (when (and application external-job-id)
        (some-> (solbri-checkset/get-solibri-bcf-file-content external-job-id)
                (assoc :filename filename)))
      true)))
