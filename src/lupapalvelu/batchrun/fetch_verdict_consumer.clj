(ns lupapalvelu.batchrun.fetch-verdict-consumer
  (:require [lupapalvelu.batchrun.fetch-verdict :as fetch-verdict]
            [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.integrations.pubsub :as lip]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [sade.env :as env]
            [schema.core :as sc]
            [taoensso.nippy :as nippy]
            [taoensso.timbre :refer [errorf info warn]])
  (:import [java.util.concurrent Semaphore]
           [javax.jms Session]))

(sc/defschema FetchVerdictMessage
  {:id        sc/Str
   sc/Keyword sc/Any})

(def fetch-verdict-message-validator (sc/validator FetchVerdictMessage))

(defn read-message [msg]
  (try
    (let [message (nippy/thaw msg)]
      (fetch-verdict-message-validator message)
      message)
    (catch Throwable t
      (errorf "Invalid message '%s' for fetch-verdict: %s" msg (.getMessage t))
      nil)))

(defn handle-fetch-verdict-message
  "Returns a function for handling fetch verdict messages. The returned function
  calls commit-fn if fetching succeeds, but also if something fails and  there's
  nothing to retry, and alternatively calls rollback-fn fetching fails in a way
  that warrants a retry."
  [commit-fn rollback-fn]
  (fn [msg]
    (if-let [{:keys [id]} (read-message msg)]
      (if-let [application (mongo/select-one :applications {:_id   id
                                                            :state "sent"})]
        (let [batchrun-user (user/batchrun-user [(:organization application)])
              batchrun-name "Automatic verdicts checking"]
          (logging/with-logging-context {:applicationId id, :userId (:id batchrun-user)}
            (if (fetch-verdict/fetch-verdict batchrun-name batchrun-user application)
              (commit-fn)
              (rollback-fn))))
        (do (errorf "Could not find application for fetching verdict: %s" id)
            (commit-fn)))
      (commit-fn)))) ; Invalid message, nothing to be done

(when (= (env/value :integration-message-queue) "jms")

(defn create-transacted-session []
  (-> (jms/get-default-connection)
      (jms/create-transacted-session)
      (jms/register-session :consumer)))

(defn commit-fetch-verdict [^Session session]
  (info "Commiting fetch-verdict message")
  (jms/commit session))

(defn rollback-fetch-verdict [^Session session]
  (warn "Rollbacking fetch-verdict message")
  (jms/rollback session))

(defn create-fetch-verdict-consumer
  "Creates a consumer for fetch-verdict messages with an exclusive
  transacted session."
  [organization-id]
  (let [session (create-transacted-session)]
    (jms/create-consumer session
                         (fetch-verdict/queue-for-organization organization-id)
                         (handle-fetch-verdict-message (partial commit-fetch-verdict session)
                                                       (partial rollback-fetch-verdict session)))))
(defn create-jms-fetch-verdict-consumers! [organization-ids]
  (info (str "Creating JMS fetch-verdict consumer(s) for " (count organization-ids) " organizations."))
  (doseq [org-id organization-ids]
    (create-fetch-verdict-consumer org-id)))
)


(when (= (env/value :integration-message-queue) "pubsub")
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

  (defn- handle-pubsub-message [{:keys [id db-name organization]}]
    ;; Possible test database must be bound for the Mongo query to work
    (mongo/with-db (or db-name mongo/*db-name*)
      (if id
        (let [^Semaphore lock (get-semaphore organization)]
          (.acquire lock)
          (try
            (if-let [application (mongo/select-one :applications {:_id   id
                                                                  :state "sent"})]
              (let [batchrun-user (user/batchrun-user [(:organization application)])
                    batchrun-name "Automatic verdicts checking"]
                (logging/with-logging-context {:applicationId id, :userId (:id batchrun-user)}
                  (fetch-verdict/fetch-verdict batchrun-name batchrun-user application)))
              (do (errorf "Could not find application for fetching verdict: %s" id)
                  true))
            (finally
              (.release lock))))
        true))) ; Invalid message, nothing to be done

  (defn create-pubsub-fetch-verdict-consumer! []
    (info (str "Creating Pub/Sub fetch-verdict consumer"))
    (lip/subscribe fetch-verdict/unified-fetch-verdicts-queue
                   handle-pubsub-message
                   {:thread-count            15
                    :max-elements-per-thread 3})))


(defn create-fetch-verdict-consumers! [organization-ids]
  (if (= (env/value :integration-message-queue) "pubsub")
    (create-pubsub-fetch-verdict-consumer!)
    (create-jms-fetch-verdict-consumers! organization-ids)))
