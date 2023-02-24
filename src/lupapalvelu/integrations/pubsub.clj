(ns lupapalvelu.integrations.pubsub
  (:require [clojure.string :as str]
            [lupapiste-pubsub.bootstrap :as pubsub-bootstrap]
            [lupapiste-pubsub.core :as pubsub-client]
            [lupapiste-pubsub.protocol :as pubsub]
            [mount.core :refer [defstate]]
            [sade.env :as env]
            [taoensso.timbre :as timbre]))


(defn- executor-thread-count []
(let [threads (or (env/value :gcp :pubsub :executor-thread-count) 1)]
  (if (string? threads)
    (try (Integer/parseInt threads)
         (catch NumberFormatException _
          1))
    threads)))


(defn- ack-deadline-seconds []
  (let [deadline (or (env/value :gcp :pubsub :ack-deadline-seconds) 300)]
    (if (string? deadline)
      (try (Integer/parseInt deadline)
           (catch NumberFormatException _
             300))
      deadline)))


(defn- make-config []
  (let [endpoint             (env/value :gcp :pubsub :endpoint) ;; Define endpoint only for local emulator
        service-account-file (env/value :gcs :service-account-file)
        credentials-provider (cond
                               endpoint (pubsub-bootstrap/no-credentials-provider)
                               service-account-file (pubsub-bootstrap/fixed-credentials-provider service-account-file)
                               :else (pubsub-bootstrap/google-credentials-provider))
        channel-provider     (pubsub-bootstrap/transport-channel-provider endpoint)
        providers            {:channel-provider     channel-provider
                              :credentials-provider credentials-provider}]
    (merge providers
           {:topic-admin                   (pubsub-bootstrap/topic-admin-client providers)
            :subscription-admin            (pubsub-bootstrap/subscription-admin-client providers)
            :project-id                    (env/value :gcs :project)
            :thread-count                  (executor-thread-count)
            :ack-deadline-seconds          (ack-deadline-seconds)
            ;; Seems this is still a bit buggy and causes slowness and weird gcp ack errors
            :enable-exactly-once-delivery? false
            :publisher-delay-threshold-ms  100})))


(defn- terminate-clients [{:keys [topic-admin subscription-admin channel-provider]}]
  (timbre/debug "Shutting down Pub/Sub topic-admin, subscription-admin and channel-provider")
  (pubsub-bootstrap/shutdown-client topic-admin)
  (pubsub-bootstrap/shutdown-client subscription-admin)
  (pubsub-bootstrap/terminate-transport! channel-provider))


(defstate client
  :start (when (env/feature? :pubsub)
           (pubsub-client/init (make-config)))
  :stop (when (env/feature? :pubsub)
          (let [config (:config client)]
            (pubsub/halt client)
            (when config
              (terminate-clients config)))))


(defn actual-topic-name [topic-name]
  (->> [(env/value :integration-message-queue-user-prefix)
        env/target-env
        topic-name]
       (remove str/blank?)
       (str/join ".")))


(defn publish
  "Publishes a message to the specified topic-name which is prefixed with the environment identifier."
  [topic-name msg]
  (pubsub/publish client
                  (actual-topic-name topic-name)
                  msg))


(defn subscribe
  "Subscribes to the specified topic-name which is prefixed with the environment identifier. "
  ([topic-name message-handler]
   (subscribe topic-name message-handler nil))
  ([topic-name message-handler additional-config]
   (pubsub/subscribe client (actual-topic-name topic-name) message-handler additional-config)))


(defn stop-subscriber [topic-name]
  (pubsub/stop-subscriber client (actual-topic-name topic-name)))


(defn remove-subscription!
  "Removes the subscriber and subscription completely. Pending messages will be lost."
  [topic-name]
  (pubsub/remove-subscription client (actual-topic-name topic-name)))
