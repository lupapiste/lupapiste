(ns lupapalvelu.bim.core
  "The main namespace for BIM integration(s).
  There are three types of BIM action:
  - `extraction`, which uses the IFC file for filling the application's document fields
  - `checking`, which checks the IFC file against a set of checks, either for generic validation
  or against compliance checks for specific operations.
  - `xkt`, which generates an XKT file from the IFC file for the BIM viewer
  The actions are performed as independent jobs, mediated through pubsub messages."
  (:require [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.bim.bim-util :as bim-util]
            [lupapalvelu.bim.bimmer.bimmer-to-lp :as btl]
            [lupapalvelu.bim.bimmer.core :as bimmer-core]
            [lupapalvelu.bim.bimmer.ifc-to-xkt :as ifc-to-xkt]
            [lupapalvelu.bim.checking-plan.core :as bim-cp]
            [lupapalvelu.bim.checking-plan.schema :as bim-cp-schema]
            [lupapalvelu.bim.data-update :as bim-data-update]
            [lupapalvelu.bim.protocol :as bim-proto]
            [lupapalvelu.bim.solibri.core :as bim-solibri]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.integrations.pubsub :as lip]
            [lupapalvelu.job :as job]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.logging :as log :refer [errorf]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.storage.gcs :as gcs]
            [lupapalvelu.system.util-components :as components]
            [monger.operators :refer [$elemMatch $or]]
            [sade.core :refer [fail]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]))

(def bim-viewer-max-filesize (env/value :bim :viewer-max-filesize))

(set! *warn-on-reflection* true)

(def ^:dynamic *extraction-instance* (bimmer-core/->Extraction))
(def ^:dynamic *checking-instance*
  (if (= "bimmer" (env/value :bim :integration :checking))
    (bimmer-core/->Checking)
    (bim-solibri/->Checking)))

(defn send-bim-job-pubsub [{:keys [request-fn handler-kw response-topic context job system]}]
  {:pre [(map? system)]}
  (let [data      (request-fn context)
        publisher (components/system->publisher system (:topic-id data))]
    (log/infof "topic %s, resp %s, pub %s" (:topic-id data) response-topic publisher)
    ;; TODO: we should have schema checks to verify what we are sending
    (if publisher
      (publisher {:handler            handler-kw
                  :response-expected? true
                  :response-topic     response-topic
                  :message-id         (:id job)
                  :data               (dissoc data :topic-id)})
      (log/warnf "No publisher for %s!" (:topic-id data)))
    job))

(defn make-extraction-job
  "Create a job to track the BIM operation and send a request for filling the application fields with information
  extracted from the model"
  [context system]
  {:pre [(map? system)]}
  (send-bim-job-pubsub {:request-fn     #(bim-proto/-extraction-request *extraction-instance* %)
                        :handler-kw     :get-model-properties
                        :response-topic (components/system->actual-topic-name system :bim/extraction-response)
                        :context        context
                        :system         system
                        :job            (job/start {"task" context})}))

(defn make-checking-job
  "Create a job to track the BIM operation and send a request for validating or checking compliance for the model"
  [{:keys [check-type] :as context} system]
  {:pre [(map? system)]}
  (send-bim-job-pubsub {:request-fn     #(bim-proto/-checking-request *checking-instance* %)
                        :handler-kw     :run-model-check
                        :response-topic (components/system->actual-topic-name system :bim/checking-response)
                        :context        context
                        :system         system
                        :job            (job/start {:application-id   (:application-id context)
                                                    :target-file-path (:object-key context)}
                                                   {(bim-cp-schema/check-type->sub-task-name check-type)
                                                    (assoc context :status :initialized)})}))

(defn ifc-file-size [application-id object-key]
  (some->> (mongo/select-one :linked-file-metadata
                             {:target-entity.application-id application-id
                              :target-entity.target-type    "bim-model-file"
                              :versions                     {$elemMatch {:target-file-path object-key}}}
                             [:versions.metadata.size :versions.target-file-path])
           :versions
           (util/find-by-key :target-file-path object-key)
           :metadata
           :size))

(defn file-size-under-bim-viewer-limit? [file-size]
  (cond
    (not (int? file-size))                false
    (not (int? bim-viewer-max-filesize))  true
    (> bim-viewer-max-filesize file-size) true
    :else                                 false))

(defn make-ifc-to-xkt-job
  "Create a job to track the BIM operation and send a request for converting the model IFC file to an XKT file
  for the BIM viewer"
  [context system]
  {:pre [(map? system)]}
  (send-bim-job-pubsub {:request-fn     ifc-to-xkt/request
                        :handler-kw     :generate-xkt-from-model
                        :response-topic (components/system->actual-topic-name system :bim/ifc-to-xkt-response)
                        :context        context
                        :system         system
                        :job            (job/start {:application-id   (:application-id context)
                                                    :target-file-path (:ifc-object-key context)}
                                                   {:xkt-generation (assoc context :status :initialized)})}))

;; Post-upload actions for BIM IFC files; running checks and generating the XKT file for the BIM viewer
;; under a single job
(defmethod linked-files/post-process-linked-file :bim-model-file
  [{{:keys [application-id file-id operation-id]} :target-entity
    {:keys [model-type model-phase]}              :metadata
    :as                                           linked-file}
   system]
  {:pre [(map? system)]}
  (let [{:keys [bucket
                file-version-id
                metadata
                target-file-path]} (linked-files/latest-version linked-file)
        application                (domain/get-application-no-access-checking application-id)
        operation-name             (some-> (app-utils/operation-by-id application operation-id) :name)
        base-context               {:application-id application-id
                                    :schema-name    (op/get-operation-schema operation-name application)
                                    :operation-id   operation-id
                                    :operation-name operation-name
                                    :file-id        file-id
                                    :bucket         (gcs/actual-bucket-name bucket)
                                    :object-key     target-file-path
                                    :status         :initialized}
        job-data                   {:application-id   application-id
                                    :target-file-path target-file-path}
        checking-plans             #(bim-cp/default-application-checking-plans application-id operation-id % model-type)
        validation-plans           (checking-plans "model-validation")
        compliance-plans           (when (and (not= model-phase "completion-time")
                                              (org/check-org-flag-by-app-id application-id :bim-compliance-check-enabled))
                                     (checking-plans "code-compliance"))
        job-subtasks               (util/assoc-when {:ifc-data-snapshot (assoc (btl/assoc-ifc-data-query base-context)
                                                                               :linked-metadata-file-version-id file-version-id)}
                                                    :validation-check (when (seq validation-plans)
                                                                        (assoc base-context
                                                                               :check-type "ids"
                                                                               :model-type model-type))
                                                    :compliance-check (when (seq compliance-plans)
                                                                        (assoc base-context
                                                                               :check-type "compliance"
                                                                               :model-type model-type))
                                                    :xkt-generation (when (file-size-under-bim-viewer-limit? (:size metadata))
                                                                      base-context))
        job                        (when (seq job-subtasks)
                                     (job/start job-data job-subtasks))]
    (run! (fn [[subtask-kw subtask-context]]
            (->> (case subtask-kw
                   :xkt-generation     {:request-fn     ifc-to-xkt/request
                                        :handler-kw     :generate-xkt-from-model
                                        :response-topic (components/system->actual-topic-name system :bim/ifc-to-xkt-response)}
                   :ifc-data-snapshot  {:request-fn     #(bim-proto/-extraction-request *extraction-instance* %)
                                        :handler-kw     :get-model-properties
                                        :response-topic (components/system->actual-topic-name system :bim/extraction-response)}
                   (:validation-check
                    :compliance-check) {:request-fn     #(bim-proto/-checking-request *checking-instance* %)
                                        :handler-kw     :run-model-check
                                        :response-topic (components/system->actual-topic-name system :bim/checking-response)}
                   (throw (ex-info "Unknown subtask after IFC file upload" {:subtask subtask-kw})))
                 (merge {:context subtask-context
                         :job     job
                         :system  system})
                 send-bim-job-pubsub))
          job-subtasks)
    {:job job}))

(defn job-status
  "Utility for checking the status of a BIM operation"
  [job-id version timeout]
  (job/status job-id (util/->long version) (util/->long timeout)))

(defn external-job-id->internal-job-sub-task
  "Returns the internal job id as a string and the sub-task related to external-job-id"
  [external-job-id]
  (when-let [job (mongo/select-one :jobs
                                   {$or [{:value.validation-check.external-job-id external-job-id}
                                         {:value.compliance-check.external-job-id external-job-id}]}
                                   [:id :value])]
    {:internal-job-id (-> job :id str)
     :sub-task        (some->> job
                               :value
                               vals
                               (util/find-by-key :external-job-id external-job-id))}))

(defn xkt-file-for-ifc-file
  "Returns the bucket and object-key for the XKT file that has been generated from the given IFC file"
  [application-id ifc-version-id]
  (some->> (mongo/select-one :linked-file-metadata
                             {:target-entity.application-id application-id
                              :target-entity.target-type    "bim-model-file"
                              :versions.file-version-id     ifc-version-id}
                             [:versions])
           :versions
           (util/find-by-key :file-version-id ifc-version-id)
           :bim
           :xkt-file))

(defn active-xkt-job-for-ifc-file
  "Returns any job that is currently generating an XKT file for the given IFC file"
  [application-id target-file-path]
  (mongo/select-one :jobs
                    {:data.application-id         application-id
                     :data.target-file-path       target-file-path
                     :status                      "running"
                     :value.xkt-generation.status "initialized"}))

(defn- handler [handler-fn]
  (fn [system message]
    (try
      (handler-fn system message)
      (catch Throwable e
        (errorf e "Invalid message '%s' for BIM handler subscription %s" message (ex-message e))))
    ;; Return true to remove the invalid message from queue
    true))

;; TODO internal-extraction-request-pubsub-topic subscriber goes here

(defmethod lip/get-pubsub-subscription-handler :bim/checking-response
  [_id]
  (handler (fn [system message] (bim-data-update/update-checking-data *checking-instance* system message))))

(defmethod lip/get-pubsub-subscription-handler :bim/extraction-response
  [_id]
  (handler (fn [system message] (bim-data-update/store-ifc-data-snapshot! *extraction-instance* system message))))

(defmethod lip/get-pubsub-subscription-handler :bim/ifc-to-xkt-response
  [_id]
  (handler ifc-to-xkt/store-xkt-response))

(defmethod lip/get-pubsub-subscription-handler :bim/internal-checking-request
  [_id]
  (handler (fn [system message] (bim-proto/-checking-handler *checking-instance* system message))))

(defn model-phase-pre-check
  "Returns a pre-checker function that uses the given path to check
  if the targeted BIM model is in the correct state (e.g. no deleting
  submission-time models after verdict).

  Only checks mongo when actually performing the action; for frontend
  have to use the `:valid-model-phases` given by query `location-operations`."
  [file-id-path & {:keys [is-version-id?]}]
  (fn [{:keys [data] {:keys [state]} :application}]
    (when-let [file-id (get-in data file-id-path)]
      (let [model-phase (or (-> data :file-metadata :model-phase) ; new model being uploaded
                            (-> (mongo/select-one :linked-file-metadata
                                                  (if is-version-id?
                                                    {:versions.file-version-id file-id}
                                                    {:target-entity.file-id file-id})
                                                  [:metadata.model-phase])
                                :metadata
                                :model-phase))]
        (when (and model-phase ; future-proofing so pre-check only applies to BIM models
                   (not (bim-util/valid-model-phase? state model-phase)))
          (fail :error.invalid-model-phase))))))

(def validate-bim-application
  "Pre-check for making sure the application can have BIM files"
  (permit/validate-permit-type-is permit/R))

(defn validate-bim-organization
  "Pre-checker that fails if org does not have R permit type apps and therefore can't have BIM models"
  [{{:keys [organizationId]} :data}]
  (when (and (ss/not-blank? organizationId)
             (-> organizationId
                 (org/get-organization [:scope])
                 (org/has-permit-type? :R)
                 not))
    (fail :error.not-bim-organization)))
