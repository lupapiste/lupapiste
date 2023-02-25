(ns lupapalvelu.attachment.bind-attachments-api
  (:require [lupapalvelu.action :as action :refer [defcommand defquery]]
            [lupapalvelu.application :as app]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.bind :as bind]
            [lupapalvelu.attachment.ram :as ram]
            [lupapalvelu.automatic-assignment.factory :refer [attachment-assignment-processor]]
            [lupapalvelu.job :as job]
            [lupapalvelu.pate.verdict :as pate-verdict]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [taoensso.timbre :refer [warn]]))

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

(defn- notify-on-ram-attachments [{:keys [application created]}]
  (fn [filedatas]
    (let [ram-ids (->> application
                       :attachments
                       (filter :ramLink)
                       (map :id))]
      (doseq [att-id (util/intersection-as-kw ram-ids
                                              (map :attachment-id filedatas))]
        (ram/notify-new-ram-attachment! application att-id created)))))

(def attachment-param-pre-checks
  "Pre-checks that expect `attachmentId` param."
  [att/attachment-matches-application
   att/attachment-not-locked
   att/attachment-not-readOnly
   att/attachment-is-needed
   pate-verdict/attachment-not-in-published-verdict
   att/attachment-editable-by-application-state
   att/validate-not-included-in-published-bulletin])

(def bind-attachment-pre-checks
  (concat attachment-param-pre-checks
          [app/validate-authority-in-drafts
           att/upload-to-target-allowed
           (action/some-pre-check att/allowed-only-for-authority-when-application-sent
                                  (permit/validate-permit-type-is :YI :YL :YM :VVVL :MAL))
           att/foreman-must-be-uploader]))

(defcommand bind-attachment
  {:description      "API to bind file to attachment, returns job that can be polled for status."
   :parameters       [id attachmentId fileId]
   :user-roles       #{:applicant :authority :oirAuthority}
   :categories       #{:attachments}
   :user-authz-roles (conj roles/all-authz-writer-roles :foreman)
   :pre-checks       bind-attachment-pre-checks
   :input-validators [(partial action/non-blank-parameters [:id :attachmentId :fileId])]
   :states           {:applicant    (conj (states/all-states-but states/terminal-states) :answered)
                      :authority    (states/all-states-but :canceled)
                      :oirAuthority (states/all-states-but :canceled)}}
  [command]
  (ok :job (bind/make-bind-job command :attachment [{:attachmentId attachmentId :fileId fileId}]
                               :postprocess-fn [(attachment-assignment-processor (partial job-response-fn command))
                                                (notify-on-ram-attachments command)])))

(defcommand resolve-multi-attachment-updates
  {:description      "Takes a list file information (fileId, filename) and returns the guidance
  for the update: acceptable `attachmentId` - `fileId` pairs."
   :parameters       [id files]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles (conj roles/all-authz-writer-roles :foreman)
   :input-validators [{:id    ssc/ApplicationId
                       :files [bind/FileInfo]}]
   :states           {:applicant    (conj (states/all-states-but states/terminal-states) :answered)
                      :authority    (states/all-states-but :canceled)
                      :oirAuthority (states/all-states-but :canceled)}}
  [{:keys [application] :as command}]
  (letfn [(passes-pre-checks? [candidate]
            (not-any? #(% (assoc command :data (assoc candidate :id id)))
                      bind-attachment-pre-checks))]
    (->> (bind/resolve-attachment-update-candidates application files)
         (filter passes-pre-checks?)
         (ok :updates))))

(defn filedatas-precheck
  "Executes given pre-check against each individual :filedatas from command"
  [check]
  (fn [{data :data :as command}]
    (reduce #(or %1 (check (assoc command :data %2))) nil (:filedatas data))))

(defn attachment-id-filedatas-pre-checks
  [{data :data :as command}]
  (when-let [filedatas (some->> data
                                :filedatas
                                (filter :attachmentId)
                                (map (partial merge {:id (:id data)})))]
    (some (fn [params]
            (some #(% (assoc command :data params)) attachment-param-pre-checks))
          filedatas)))

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
                         validate-attachment-groups
                         check-password-for-sign
                         attachment-id-filedatas-pre-checks]
   :states              {:applicant    (conj (states/all-states-but states/terminal-states) :answered)
                         :authority    (states/all-states-but :canceled)
                         :oirAuthority (states/all-states-but :canceled)}}
  [command]
  (ok :job (bind/make-bind-job command :attachment filedatas
                               :postprocess-fn [(attachment-assignment-processor (partial job-response-fn command))
                                                (notify-on-ram-attachments command)])))

(defquery bind-attachments-job
  {:description         "Polls the status of the given job so the frontend can know when the binding is done"
   :parameters          [jobId version]
   :user-roles          #{:applicant :authority :oirAuthority}
   :input-validators    [(partial action/non-blank-parameters [:jobId])
                         (partial action/numeric-parameters [:version])]
   :states              (states/all-states-but :canceled)}
  [{{job-id :jobId version :version timeout :timeout :or {version "0" timeout "10000"}} :data}]
  (ok (job/status job-id (util/->long version) (util/->long timeout))))

(defn- update-draft-attachment-metadata [{:keys [application] :as command}]
  (fn [filedatas]
    (let [att-ids (map :attachment-id filedatas)]
      (action/update-application command
                                 {$set (att/attachment-array-updates (:id application)
                                                                     (util/fn->> :id (util/includes-as-kw? att-ids))
                                                                     :metadata.draftTarget true
                                                                     :metadata.nakyvyys    "viranomainen")}))))

(defcommand bind-draft-attachments
  {:description         "Attachments belong to drafts: metadata: {:draftTarget true :nakyvyys 'viranomainen'}."
   :parameters          [id filedatas]
   :input-validators    [(partial action/vector-parameters-with-map-items-with-required-keys [:filedatas] [:fileId])]
   :user-roles          #{:authority}
   :pre-checks          [app/validate-authority-in-drafts
                         (filedatas-precheck att/upload-to-target-allowed)
                         validate-attachment-groups
                         attachment-id-filedatas-pre-checks]
   :states              (states/all-states-but :canceled)}
  [command]
  (ok :job (bind/make-bind-job command :attachment filedatas
                               :postprocess-fn [(update-draft-attachment-metadata command)])))
