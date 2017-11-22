(ns lupapalvelu.integrations.activemq
  (:require [taoensso.timbre :refer [errorf]]
            [clamq.protocol.connection :as conn]
            [clamq.protocol.consumer :as consumer]
            [clamq.protocol.producer :as producer]
            [clamq.activemq :as clamq]
            [sade.env :as env]
            [clojure.walk :as walk]))

(def broker-url (or (env/value :activemq :broker-url) "tcp://localhost:61666"))

(def broker (clamq/activemq-connection broker-url))

(defonce connections (atom {:producers []
                            :consumers []}))

(defn register [type object]
  (swap! connections update type conj object)
  object)

(defn register-producer [producer]
  (register :producers producer))

(defn register-consumer
  "Creates, register and starts consumer to given endpoint. Returns consumer instance."
  [endpoint callback-fn]
  (let [consumer (conn/consumer broker {:endpoint endpoint
                                        :on-message callback-fn
                                        :transacted false})]
    (register :consumers consumer)
    (consumer/start consumer)
    consumer))

(def producer (register-producer (conn/producer broker)))

(defn send-jms-message
  ([queue msg] (send-jms-message queue msg nil))
  ([queue msg attributes]
  (try
    (producer/publish producer queue msg attributes)
    (catch Exception e
      (errorf
        "Got exception %s with message '%s' when sending message to broker @ %s"
        (type e)
        (.getMessage e) broker-url)))))

(defn close-all!
  "Closes all registered consumers and the broker connection."
  []
  (doseq [conn (concat (:consumers @connections))]
    (.close conn))
  (.close broker))
