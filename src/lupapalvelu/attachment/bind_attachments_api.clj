(ns lupapalvelu.attachment.bind-attachments-api
  (:require [lupapalvelu.action :as action :refer [defcommand defquery]]
            [lupapalvelu.application :as app]
            [lupapalvelu.attachment.bind :as bind]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.job :as job]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.util :as util]
            [sade.strings :as ss]))

(def bind-states (states/all-application-states-but states/terminal-states))

(defn- attachment-id-is-present-in-application-or-not-set [{{:keys [attachments]} :application} attachmentId]
  (when-not (or (ss/blank? attachmentId) (some #(= (:id %) attachmentId) attachments))
    (fail :error.attachment.id)))

(defn- validate-attachment-ids [command]
  (reduce
    (fn [res id] (or res (attachment-id-is-present-in-application-or-not-set command id)))
    nil
    (remove nil? (map :attachmentId (get-in command [:data :filedatas])))))

(def check-password-for-sign
  (fn [{{:keys [filedatas]} :data :as command}]
    (when ((complement not-any?) :sign filedatas)
      (usr/check-password-pre-check command))))

(defcommand bind-attachments
  {:description         "API to bind files to attachments, returns job that can be polled for status per file."
   :parameters          [id filedatas]
   :optional-parameters [password]
   :input-validators    [(partial action/vector-parameters-with-map-items-with-required-keys [:filedatas] [:fileId])]
   :user-roles          #{:applicant :authority :oirAuthority}
   :user-authz-roles    (conj auth/all-authz-writer-roles :foreman)
   :pre-checks          [(partial app/validate-authority-in-drafts)
                         validate-attachment-ids
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

