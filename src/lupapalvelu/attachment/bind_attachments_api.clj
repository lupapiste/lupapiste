(ns lupapalvelu.attachment.bind-attachments-api
  (:require [lupapalvelu.action :as action :refer [defcommand defquery]]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.attachment.bind :as bind]
            [lupapalvelu.job :as job]
            [lupapalvelu.states :as states]
            [schema.core :as sc]))

(defcommand bind-attachments
  {:description         "API to bind files to attachments, returns job that can be polled for status per file."
   :parameters          [id filedatas]
   :optional-parameters [password]
   :input-validators    [(partial action/vector-parameters-with-map-items-with-required-keys [:filedatas] [:fileId])]
   :user-roles          #{:applicant :authority :oirAuthority}
   :user-authz-roles    (conj auth/all-authz-writer-roles :foreman)
   :states              (states/all-application-states-but states/terminal-states)}
  [command]
  (ok :job (bind/make-bind-job command filedatas)))

(defquery bind-attachments-job
  {:parameters [id jobId version]}
  [{job-id :jobId version :version timeout :timeout :or {version "0" timeout "10000"}} :data]
  (ok (job/status job-id version timeout)))


