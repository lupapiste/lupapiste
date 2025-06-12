(ns lupapalvelu.bim.solibri.core
  "BIM integration using the Solibri BIM API.
  Integration is still performed through pubsub messages but unlike the original implementations,
  the messages are handled in this repo and not in the separate `bimmer` service"
  (:require [lupapalvelu.bim.checking-plan.core :as bim-cp]
            [lupapalvelu.bim.checking-plan.schema :as bim-cp-schema]
            [lupapalvelu.bim.protocol :as bim-protocol]
            [lupapalvelu.bim.solibri.checkset :as solibri-checkset]
            [lupapalvelu.job :as job]
            [lupapalvelu.logging :refer [errorf]]
            [monger.operators :refer :all]
            [swiss.arrows :refer :all]))

;; No extraction implementation (yet?)

(defrecord Checking []
  bim-protocol/BIMChecking

  (-checking-request [_ context]
    (-> context
        (select-keys [:bucket :object-key :file-id :check-type :application-id :operation-id :model-type])
        ;; internal = not consumed by bimmer, has handler below
        (assoc :topic-id :bim/internal-checking-request)))

  (-checking-handler [_self
                      system
                      {{:keys [check-type object-key file-id application-id operation-id model-type]} :data
                       lupapiste-job-id                                                               :message-id}]
    (let [checking-type  (bim-cp-schema/check-type->solibri-checking-type check-type)
          sub-task-name  (bim-cp-schema/check-type->sub-task-name check-type)
          checking-plans (bim-cp/default-application-checking-plans application-id operation-id checking-type model-type)]
      (if (seq checking-plans)
        (try
          (let [response (solibri-checkset/create-solibri-checking-job system object-key checking-plans)]
            (if-let [solibri-job-id (get response "id")]
              (job/merge-by-id lupapiste-job-id
                               sub-task-name
                               {:external-job-id solibri-job-id
                                :integration     "solibri"
                                :file-id         file-id
                                :status          "waiting"})
              (errorf "Solibri BIM job request returned response %s for %s" response file-id)))
          (catch Exception e
            (errorf e "Exception in Solibri BIM job request for %s" file-id)
            (job/merge-by-id lupapiste-job-id
                             sub-task-name
                             {:integration "solibri"
                              :file-id     file-id
                              :status      "error"})))
        (job/merge-by-id lupapiste-job-id
                         sub-task-name
                         {:integration    "solibri"
                          :file-id        file-id
                          :status         "error"
                          :status-message "error.bim-checking-plans-not-configured"}))))

  (-checking-response [_ {{:keys [external-job-id succeeded? status application-id]} :data}]
    (if succeeded?
      (solibri-checkset/get-and-upload-checking-result external-job-id application-id)
      (solibri-checkset/handle-checking-failure external-job-id status))))
