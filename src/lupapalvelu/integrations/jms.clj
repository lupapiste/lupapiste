(ns lupapalvelu.integrations.jms
  (:require [taoensso.timbre :refer [error info]]
            [sade.env :as env])
  (:import (javax.jms ExceptionListener Connection Session)
           (org.apache.activemq.artemis.jms.client ActiveMQJMSConnectionFactory)
           (org.apache.activemq.artemis.api.jms ActiveMQJMSClient)))

(when (env/feature? :embedded-artemis)
  ; This works only with :dev profile
  (require 'artemis-server)
  ((ns-resolve 'artemis-server 'start)))

(when (env/feature? :jms)

  (defonce connections (atom {:producers []
                              :consumers []}))
  (def exception-listener
    (reify ExceptionListener
      (onException [_ e]
        (error e (str "JMS exception: " (.getMessage e))))))

  (def broker-url (or (env/value :jms :broker-url) "vm://0"))

  (defn create-connection ^Connection
    ([] (create-connection broker-url))
    ([host]
     (let [conn (.createConnection (ActiveMQJMSConnectionFactory. host))]
       (.setExceptionListener conn exception-listener)
       conn)))

  (def broker-connection ^Connection (create-connection))

  (def session (.createSession broker-connection false Session/AUTO_ACKNOWLEDGE))

  (defn create-producer [session queue]
    (.createProducer session queue))

  (defn create-consumer [session queue]
    (.createConsumer session queue))

  (defn register [type object]
    (swap! connections update type conj object)
    object)

  (defn register-producer [producer]
    (register :producers producer))

  (defn register-consumer
    "Creates, register and starts consumer to given endpoint. Returns consumer instance."
    [endpoint callback-fn]
    (let [consumer (create-consumer session endpoint)]
      #_(register :consumers consumer)
      #_(consumer/start consumer)
      consumer))

  (defn queue [name]
    (ActiveMQJMSClient/createQueue name))

  (def msg (.createTextMessage session "FooFaaFii"))

  (defn send-jms-message
    ([queue msg] (send-jms-message queue msg nil))
    ([queue msg attributes]
     #_(try
       (send producer queue msg attributes)
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
    (.close broker-connection)

    (when-let [artemis (ns-resolve 'artemis-server 'embedded-broker)]
      (info "Stopping Artemis...")
      (.stop artemis))))

