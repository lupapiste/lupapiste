(ns lupapalvelu.integrations.pubsub
  (:require [integrant.core :as ig]
            [lupapalvelu.integrations.pubsub-schema :as pubsub-schema]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapiste-pubsub.bootstrap :as pubsub-bootstrap]
            [lupapiste-pubsub.core :as pubsub-client]
            [lupapiste-pubsub.protocol :as pubsub]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.env :as env]))

(set! *warn-on-reflection* true)

(defn- make-config
  [{:keys [endpoint service-account-file]
    :as   config}]
  (let [credentials-provider (cond
                               endpoint             (pubsub-bootstrap/no-credentials-provider)
                               service-account-file (pubsub-bootstrap/fixed-credentials-provider service-account-file)
                               :else                (pubsub-bootstrap/google-credentials-provider))
        channel-provider     (pubsub-bootstrap/transport-channel-provider endpoint)
        providers            {:channel-provider     channel-provider
                              :credentials-provider credentials-provider}]
    (merge providers
           {:topic-admin        (pubsub-bootstrap/topic-admin-client providers)
            :subscription-admin (pubsub-bootstrap/subscription-admin-client providers)}
           (select-keys config #{:ack-deadline-seconds
                                 :enable-exactly-once-delivery?
                                 :project-id
                                 :publisher-delay-threshold-ms
                                 :thread-count}))))

(defn- terminate-clients [{:keys [topic-admin subscription-admin channel-provider]}]
  (log/debug "Shutting down Pub/Sub topic-admin, subscription-admin and channel-provider")
  (pubsub-bootstrap/shutdown-client topic-admin)
  (pubsub-bootstrap/shutdown-client subscription-admin)
  (pubsub-bootstrap/terminate-transport! channel-provider))

(defmethod ig/assert-key ::client [_ args]
  (pubsub-schema/valid-client-args! (dissoc args :deps)))

(defmethod ig/init-key ::client [k config]
  (log/infof "Starting %s..." k)
  (pubsub-client/init (make-config config)))

(defmethod ig/halt-key! ::client [k {:keys [config] :as client}]
  (log/infof "Stopping %s..." k)
  (assert client)
  (pubsub/halt client)
  (when config
    (terminate-clients config)))

(defn actual-topic-name
  [{:keys [queue-prefix target-env topic]}]
  {:pre [(string? target-env) (string? topic)]}
  (->> [queue-prefix target-env topic]
       (remove ss/blank?)
       (ss/join ".")))

;; NOTE: Alternatively, maybe the individual handlers could be Integrant components too?
(defmulti get-pubsub-subscription-handler
  "Return a handler fn for the given subscription id"
  identity)

(defmethod ig/assert-key ::subscriber [_ args]
  (pubsub-schema/valid-subscriber-args! args))

(defmethod ig/init-key ::subscriber [[k  topic-id] {:keys [client components actual-topic
                                                           topic options remove-on-shutdown?]}]
  (log/infof "Starting %s..." [k topic-id])
  (let [{:keys [mongo]}     components
        handler             (get-pubsub-subscription-handler topic-id)
        handler-with-system (fn [message]
                              ;; Ideally we'd like to access mongo using `components`,
                              ;; but maybe refactor that later..
                              (binding [mongo/*connection* mongo]
                                (try
                                  (handler components message)
                                  (catch AssertionError e
                                    (log/error e "Assertion processing message")
                                    false)
                                  (catch Exception e
                                    (log/error e "Error processing message")
                                    false))))]
    (log/infof "Subscribing, id: %s, topic: %s, handler: %s" topic-id actual-topic handler)
    (pubsub/subscribe client actual-topic handler-with-system options)
    (util/mmap topic-id client topic actual-topic remove-on-shutdown?)))

(defmethod ig/halt-key! ::subscriber [k {:keys [topic-id client actual-topic remove-on-shutdown?]}]
  (log/infof "Stopping %s..." k)
  (log/infof "Unsubscribing, id :%s, topic: %s" topic-id actual-topic)
  (pubsub/stop-subscriber client actual-topic)

  ;; If we are running integration tests, remove the subscription
  ;; and any pending message. Some tests (e.g. allu itest) typically leave messages
  ;; behind in the test queue and we want to get rid of them.
  ;; We do NOT want to do this in prod so also check dev-mode just to be safe
  (when (and (env/dev-mode?)
             remove-on-shutdown?)
    (log/infof "Removing subscription, id :%s, topic: %s!" topic-id actual-topic)
    ;; This removes the subscription and any messages within
    (pubsub/remove-subscription client actual-topic)))

(defmethod ig/assert-key ::publisher [_ args]
  (pubsub-schema/valid-publisher-args! args))

(defmethod ig/init-key ::publisher [[k  topic-id] {:keys [client topic actual-topic]}]
  (log/infof "Starting %s..." [k topic-id])
  (log/infof "Creating publisher, id: %s, topic: %s" topic-id actual-topic)
  {:publisher-fn (fn topic-publisher [message]
                   {:pre [message]}
                   (pubsub/publish client
                                   actual-topic
                                   message))
   ;; Topic identifier, e.g. `:backing-system/http`
   :topic-id     topic-id
   ;; Topic name, e.g. `"lupapiste.kuntagml.http"`
   :topic        topic
   ;; Real GCP topic name, e.g. `"<username>.local.lupapiste.kuntagml.http"`
   :actual-topic actual-topic})
