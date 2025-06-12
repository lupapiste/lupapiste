(ns lupapalvelu.integrations.pubsub-schema
  (:require [lupapalvelu.system.util :as system-util]
            [lupapiste-pubsub.protocol :as pubsub]
            [sade.schemas :as ssc]
            [schema-tools.core :as st]
            [schema.core :as sc :refer [defschema]]))

(defschema ClientArgs
  {(sc/optional-key :endpoint)             ssc/NonBlankStr
   (sc/optional-key :service-account-file) ssc/NonBlankStr
   :ack-deadline-seconds                   sc/Int
   :enable-exactly-once-delivery?          sc/Bool
   :project-id                             ssc/NonBlankStr
   :publisher-delay-threshold-ms           sc/Int
   :thread-count                           sc/Int})

(def valid-client-args! (system-util/config-validator! ClientArgs))

(defschema Options
  {(sc/optional-key :thread-count)            sc/Int
   (sc/optional-key :max-elements-per-thread) sc/Int})

(defschema Topic
  {(sc/optional-key :topic)               ssc/NonBlankStr
   (sc/optional-key :actual-topic)        ssc/NonBlankStr
   (sc/optional-key :options)             Options
   (sc/optional-key :publish?)            sc/Bool
   (sc/optional-key :subscribe?)          sc/Bool
   ;; Special flag for testing: remove the queue on shutdown
   ;; NOTE: Only enable in itests!
   (sc/optional-key :remove-on-shutdown?) sc/Bool})

(defn- with-topic-constraint
  [schema]
  (sc/constrained
    schema
    (fn [args]
      (or (:topic args)
          (:actual-topic args)))
    'has-topic-or-actual-topic?))

(defn- client?
  [client]
  (satisfies? pubsub/MessageQueueClient client))

(defschema SubscriberArgs
  (st/merge
    (st/select-keys Topic #{:topic :options :remove-on-shutdown?})
    {:client       (sc/pred client? 'pubsub-client)
     :components   sc/Any
     :actual-topic ssc/NonBlankStr}))

(def valid-subscriber-args! (system-util/config-validator! SubscriberArgs))

(defschema PublisherArgs
  (with-topic-constraint
    (st/merge
      (st/select-keys Topic #{:topic})
      {:client       (sc/pred client? 'pubsub-client)
       :actual-topic ssc/NonBlankStr})))

(def valid-publisher-args! (system-util/config-validator! PublisherArgs))

(defschema Topics {sc/Keyword (with-topic-constraint Topic)})

(defschema Config
  {:enabled?                       sc/Bool
   :client                         ClientArgs
   :topics                         Topics
   (sc/optional-key :queue-prefix) ssc/NonBlankStr})
