(ns lupapalvelu.linked-file-api
  "API for file upload directly to GCS (using GCS resumable upload), download these files and update their metadata"
  (:require [lupapalvelu.action :as action :refer [defcommand defquery defraw]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [sade.core :refer [fail ok]]))

(def linked-file-api-edit-permissions
  [{:context  {:application {:state #{:draft}}}
    :required [:application/edit-draft]}
   {:context  {:application {:state states/all-but-draft-or-terminal}}
    :required [:application/edit]}])

(defcommand init-resumable-upload-for-application
  {:description         "Initializes a resumable upload link to GCS (Google Cloud Storage).
                         Content type is parsed from the filename."
   :parameters          [id
                         filename
                         md5-digest]
   :optional-parameters [file-id]
   :permissions         linked-file-api-edit-permissions
   :input-validators    [(comp mime/allowed-file? :filename)
                         (partial action/non-blank-parameters [:id :filename :md5-digest])]}
  [{:keys [user data]}]
  (let [uid (:id user)
        content-type (mime/mime-type filename)
        res (linked-files/init-resumable-upload-for-application uid content-type md5-digest file-id)]
    (ok res)))

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
   :parameters          [id
                         file-version-id
                         linked-file-target]
   :optional-parameters [version-metadata file-metadata]
   :permissions         linked-file-api-edit-permissions
   :input-validators    [(partial action/non-blank-parameters [:id :file-version-id])
                         (partial action/parameters-matching-schema [:linked-file-target] linked-files/LinkedFileTarget)]}
  [{:keys [user] :as cmd}]
  (linked-files/ensure-file-version file-version-id
                                    linked-file-target
                                    user
                                    {:version-metadata version-metadata
                                     :file-metadata    file-metadata}))

(defcommand set-linked-application-file-metadata-field
  {:description      "Update value of a linked field metadata field."
   :parameters       [id linked-file-target field value]
   :input-validators [(partial action/non-blank-parameters [:id])
                      (partial action/parameters-matching-schema [:linked-file-target] linked-files/LinkedFileTarget)
                      (partial action/non-blank-parameters [:field])]
   :permissions      linked-file-api-edit-permissions}
  [_]
  (if (= id (:application-id linked-file-target))
    (ok :data (linked-files/update-metadata-field linked-file-target field value))
    (fail :error.invalid-request)))

(defquery fetch-linked-file-metadatas-for-application
  {:description      "Get all linked-files that are type of target-type"
   :parameters       [target-type id]
   :input-validators [(partial action/non-blank-parameters [:id])
                      (partial action/parameters-matching-schema [:target-type] linked-files/LinkedFileTargetTypes)]
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/edit-draft]}
                      {:required [:application/read]}]}
  [cmd]
  (linked-files/get-linked-application-files-for {:target-type target-type :application-id id}))

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
