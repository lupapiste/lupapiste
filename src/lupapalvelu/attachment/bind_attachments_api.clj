(ns lupapalvelu.attachment.bind-attachments-api
  (:require [lupapalvelu.action :as action :refer [defcommand defquery]]
            [lupapalvelu.application :as app]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.bind :as bind]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.job :as job]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.util :as util]
            [sade.strings :as ss]))

(def bind-states (states/all-application-states-but states/terminal-states))

(defn- validate-attachment-ids [command]
  (->> (get-in command [:data :filedatas])
       (map :attachmentId)
       (some (partial att/attachment-matches-application command))))

(defn- validate-attachment-groups [{{filedatas :filedatas} :data :as command}]
  (->> (range (count filedatas))
       (some #(att/validate-group [:filedatas % :group] command))))

(defn- check-password-for-sign [{{:keys [filedatas]} :data :as command}]
  (when (some :sign filedatas)
    (usr/check-password-pre-check command)))

(defcommand bind-attachment
  {:description         "API to bind file to attachment, returns job that can be polled for status."
   :parameters          [id attachmentId fileId]
   :user-roles          #{:applicant :authority :oirAuthority}
   :categories          #{:attachments}
   :user-authz-roles    (conj auth/all-authz-writer-roles :foreman)
   :pre-checks          [app/validate-authority-in-drafts
                         att/attachment-matches-application
                         att/upload-to-target-allowed
                         att/attachment-not-locked
                         att/attachment-not-readOnly
                         att/attachment-is-needed
                         att/attachment-editable-by-application-state
                         att/allowed-only-for-authority-when-application-sent
                         att/foreman-must-be-uploader]
   :input-validators    [(partial action/non-blank-parameters [:id :attachmentId :fileId])]
   :states              bind-states}
  [command]
  (ok :job (bind/make-bind-job command [{:attachmentId attachmentId :fileId fileId}])))

(defcommand bind-attachments
  {:description         "API to bind files to attachments, returns job that can be polled for status per file."
   :parameters          [id filedatas]
   :optional-parameters [password]
   :input-validators    [(partial action/vector-parameters-with-map-items-with-required-keys [:filedatas] [:fileId])]
   :user-roles          #{:applicant :authority :oirAuthority}
   :user-authz-roles    (conj auth/all-authz-writer-roles :foreman)
   :pre-checks          [app/validate-authority-in-drafts
                         att/allowed-only-for-authority-when-application-sent
                         att/foreman-must-be-uploader
                         validate-attachment-ids
                         validate-attachment-groups
                         check-password-for-sign]
   :states              bind-states}
  [command]
  (ok :job (bind/make-bind-job command filedatas)))

(defquery bind-attachments-job
  {:parameters [jobId version]
   :user-roles          #{:applicant :authority :oirAuthority}
   :input-validators    [(partial action/numeric-parameters [:jobId :version])]
   :states              bind-states}
  [{{job-id :jobId version :version timeout :timeout :or {version "0" timeout "10000"}} :data}]
  (ok (job/status job-id (util/->long version) (util/->long timeout))))
