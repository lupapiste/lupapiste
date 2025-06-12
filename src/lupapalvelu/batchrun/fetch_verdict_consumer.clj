(ns lupapalvelu.batchrun.fetch-verdict-consumer
  (:require [lupapalvelu.batchrun.fetch-verdict :as fetch-verdict]
            [lupapalvelu.integrations.pubsub :as lip]
            [lupapalvelu.logging :as log :refer [errorf]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [monger.operators :refer :all])
  (:import [java.util.concurrent Semaphore]))

(set! *warn-on-reflection* true)

;; Each organization (id) has a separate Semaphore with 2 "permits", so that we can fire at most 2 simultaneous
;; requests per organization to avoid overwhelming the municipal servers even if we have lots of threads to handle
;; Pub/Sub messages. With bad luck this can lead to a situation where we are not doing very much parallel work.
(def *semaphores (atom {}))

(defn- get-semaphore [organization]
  ;; It's possible two threads with the same organization end up creating duplicate Semaphores the first time the
  ;; organization is encountered, but that does not matter much, one of them will be used further on.
  (or (get @*semaphores organization)
      (let [semaphore (Semaphore. 2)]
        (swap! *semaphores assoc organization semaphore)
        semaphore)))

(defn- handle-pubsub-message [system {:keys [id organization foreman-doc-id]}]
  {:pre [(map? system)]}
  (if id
    (let [^Semaphore lock (get-semaphore organization)]
      (.acquire lock)
      (try
        (if-let [application (mongo/select-one :applications (merge {:_id id}
                                                                    (if foreman-doc-id
                                                                      {:documents {$elemMatch {:tj-document true
                                                                                               :id          foreman-doc-id
                                                                                               :state       "approved"}}}
                                                                      {:state "sent"})))]
          (let [batchrun-user (user/batchrun-user [(:organization application)])
                batchrun-name "Automatic verdicts checking"]
            (log/with-logging-context {:applicationId id, :userId (:id batchrun-user)}
              (fetch-verdict/fetch-verdict system batchrun-name batchrun-user application foreman-doc-id)))
          (do (if foreman-doc-id
                (errorf "Could not find foreman document %s in application %s when fetching verdict." foreman-doc-id id)
                (errorf "Could not find application for fetching verdict: %s" id))
              true))
        (finally
          (.release lock))))
    true)) ; Invalid message, nothing to be done

(defmethod lip/get-pubsub-subscription-handler :batchrun/fetch-verdicts
  [_id]
  handle-pubsub-message)
