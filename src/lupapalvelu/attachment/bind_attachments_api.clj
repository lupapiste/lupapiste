(ns lupapalvelu.attachment.bind-attachments-api
  (:require [lupapalvelu.action :as action :refer [defcommand defquery]]
            [lupapalvelu.application :as app]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.bind :as bind]
            [lupapalvelu.attachment.ram :as ram]
            [lupapalvelu.job :as job]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [taoensso.timbre :refer [warn]]))

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

(defn- trigger-target [{:keys [attachment-id type status]}]
  (when (= status :done)
    (let [{:keys [type-group type-id]} type]
      (if (and type-group type-id)
        {:id attachment-id
         :trigger-type (ss/join "." (map name [type-group type-id]))}
        (warn (str "could not create trigger type for attachment id "
                   attachment-id
                   ": type-group " (or type-group "nil")
                   ", type-id " (or type-id "nil")))))))

(defn- job-response-fn [command job]
  {:targets          (->> job (map trigger-target) (remove nil?))
   :user             (:user command)
   :organization     @(:organization command)
   :application      (:application command)
   :assignment-group "attachments"
   :timestamp        (:created command)})

(defcommand bind-attachment
  {:description         "API to bind file to attachment, returns job that can be polled for status."
   :parameters          [id attachmentId fileId]
   :user-roles          #{:applicant :authority :oirAuthority}
   :categories          #{:attachments}
   :user-authz-roles    (conj roles/all-authz-writer-roles :foreman)
   :pre-checks          [app/validate-authority-in-drafts
                         att/attachment-matches-application
                         att/upload-to-target-allowed
                         att/attachment-not-locked
                         att/attachment-not-readOnly
                         att/attachment-is-needed
                         att/attachment-editable-by-application-state
                         (action/some-pre-check att/allowed-only-for-authority-when-application-sent
                                                (permit/validate-permit-type-is :YI :YL :YM :VVVL :MAL))
                         att/foreman-must-be-uploader
                         att/validate-not-included-in-published-bulletin]
   :input-validators    [(partial action/non-blank-parameters [:id :attachmentId :fileId])]
   :states              {:applicant    (conj (states/all-states-but states/terminal-states) :answered)
                         :authority    (states/all-states-but :canceled)
                         :oirAuthority (states/all-states-but :canceled)}}
  [command]
  (ok :job (bind/make-bind-job command [{:attachmentId attachmentId :fileId fileId}]
                               :postprocess-fn (assignment/run-assignment-triggers (partial job-response-fn command)))))

(defn filedatas-precheck
  "Executes given pre-check against each individual :filedatas from command"
  [check]
  (fn [{data :data :as command}]
    (reduce #(or %1 (check (assoc command :data %2))) nil (:filedatas data))))

(defn- notify-on-ram-attachments [{:keys [application created]}]
  (fn [filedatas]
    (let [ram-ids (->> application
                       :attachments
                       (filter :ramLink)
                       (map :id))]
      (doseq [att-id (util/intersection-as-kw ram-ids
                                              (map :attachment-id filedatas))]
        (ram/notify-new-ram-attachment! application att-id created)))))

(defcommand bind-attachments
  {:description         "API to bind files to attachments, returns job that can be polled for status per file."
   :parameters          [id filedatas]
   :optional-parameters [password]
   :input-validators    [(partial action/vector-parameters-with-map-items-with-required-keys [:filedatas] [:fileId])]
   :user-roles          #{:applicant :authority :oirAuthority}
   :user-authz-roles    (conj roles/all-authz-writer-roles :foreman)
   :pre-checks          [app/validate-authority-in-drafts
                         (action/some-pre-check att/allowed-only-for-authority-when-application-sent
                                                (permit/validate-permit-type-is :YI :YL :YM :VVVL :MAL))
                         att/foreman-must-be-uploader
                         (filedatas-precheck att/upload-to-target-allowed)
                         validate-attachment-ids
                         validate-attachment-groups
                         check-password-for-sign]
   :states              {:applicant    (conj (states/all-states-but states/terminal-states) :answered)
                         :authority    (states/all-states-but :canceled)
                         :oirAuthority (states/all-states-but :canceled)}}
  [command]
  (ok :job (bind/make-bind-job command filedatas
                               :postprocess-fn [(assignment/run-assignment-triggers (partial job-response-fn command))
                                                (notify-on-ram-attachments command)])))

(defquery bind-attachments-job
  {:parameters [jobId version]
   :user-roles          #{:applicant :authority :oirAuthority}
   :input-validators    [(partial action/numeric-parameters [:jobId :version])]
   :states              (states/all-states-but :canceled)}
  [{{job-id :jobId version :version timeout :timeout :or {version "0" timeout "10000"}} :data}]
  (ok (job/status job-id (util/->long version) (util/->long timeout))))
