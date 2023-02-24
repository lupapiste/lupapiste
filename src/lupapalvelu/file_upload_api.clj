(ns lupapalvelu.file-upload-api
  "API endpoints for fileupload to the lupapiste.

  NOTE: Endpoints for file-upload directly to GCP are located in linked-file-api"
  (:require [noir.response :as resp]
            [taoensso.timbre :refer [errorf]]
            [clojure.set :refer [rename-keys]]
            [sade.core :refer [fail ok]]
            [lupapalvelu.action :as action :refer [defcommand defraw disallow-impersonation]]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.vetuma :as vetuma]
            [lupapalvelu.storage.file-storage :as storage]))

(defn- file-mime-type-accepted [{{files :files} :data}]
  (when-not (every? mime/allowed-file? (map :filename files))
    (fail :error.file-upload.illegal-file-type)))

(defraw upload-file
        {:user-roles       #{:anonymous}
         :parameters       [files]
         :input-validators [file-mime-type-accepted file-upload/file-size-legal]
         :pre-checks       [vetuma/session-pre-check]}
        (let [file-info (pmap
                          #(file-upload/save-file % :sessionId (vetuma/session-id) :linked false)
                          (map #(rename-keys % {:tempfile :content}) files))]
          (->> {:files file-info :ok true}
               (resp/json)
               (resp/status 200))))

(defraw upload-file-authenticated
        {:user-roles          #{:authority :applicant}
         :user-authz-roles    (conj roles/writer-roles-with-foreman :statementGiver)
         :parameters          [files]
         :optional-parameters [id]
         :input-validators    [file-mime-type-accepted
                               file-upload/file-size-legal]
         :pre-checks          [disallow-impersonation]
         :states              states/all-states}
        [{:keys [application user]}]
        (let [{:keys [ok error] :as result} (file-upload/save-files application files (:id user))]
          (when-not ok
            (errorf "upload failed, error: %s" error))
          (->> result
               (file-upload/mark-duplicates application)
               (resp/json)
               (resp/status (if ok 200 400)))))

(defcommand remove-uploaded-file
  {:parameters       [attachmentId]
   :input-validators [(partial action/non-blank-parameters [:attachmentId])]
   :user-roles       #{:anonymous}}
  [{:keys [user]}]
  (let [uid (or (:id user) (vetuma/session-id))]
    (if (storage/unlinked-file-exists? uid attachmentId)
      (do
        (storage/delete-unlinked-file uid attachmentId)
        (ok :attachmentId attachmentId))
      (fail :error.file-upload.not-found))))
