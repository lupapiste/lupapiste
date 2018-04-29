(ns lupapalvelu.file-upload-api
  (:require [noir.response :as resp]
            [taoensso.timbre :refer [errorf]]
            [clojure.set :refer [rename-keys]]
            [sade.core :refer :all]
            [sade.env :as env]
            [lupapalvelu.action :as action :refer [defcommand defraw disallow-impersonation]]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.vetuma :as vetuma]
            [lupapalvelu.storage.file-storage :as storage]))

(defn- file-mime-type-accepted [{{files :files} :data}]
  (when-not (every? mime/allowed-file? (map :filename files))
    (fail :error.file-upload.illegal-file-type)))

(defraw upload-file
  {:user-roles #{:anonymous}
   :parameters [files]
   :input-validators [file-mime-type-accepted file-upload/file-size-legal]
   :pre-checks [vetuma/session-pre-check]}
  (let [file-info (pmap
                    #(file-upload/save-file % :sessionId (vetuma/session-id) :linked false)
                    (map #(rename-keys % {:tempfile :content}) files))]
    (->> {:files file-info :ok true}
         (resp/json)
         (resp/status 200))))

(defraw upload-file-authenticated
  {:user-roles       #{:authority :applicant}
   :user-authz-roles (conj roles/writer-roles-with-foreman :statementGiver)
   :parameters       [files]
   :optional-parameters [id]
   :input-validators [file-mime-type-accepted
                      file-upload/file-size-legal]
   :pre-checks       [disallow-impersonation]
   :states           states/all-states}
  [{:keys [application]}]
  (let [{:keys [ok error] :as result} (file-upload/save-files application files (vetuma/session-id))]
    (when-not ok
      (errorf "upload failed, error: %s" error))
    (->> result
         (file-upload/mark-duplicates application)
         (resp/json)
         (resp/status (if ok 200 400)))))

(defcommand remove-uploaded-file
  {:parameters [attachmentId]
   :input-validators [(partial action/non-blank-parameters [:attachmentId])]
   :user-roles #{:anonymous}}
  (if (storage/session-files-exist? (vetuma/session-id) [attachmentId])
    (do
      (storage/delete-session-file (vetuma/session-id) attachmentId)
      (ok :attachmentId attachmentId))
    (fail :error.file-upload.removing-file-failed)))
