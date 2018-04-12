(ns lupapalvelu.integrations.jms
  (:require [taoensso.timbre :refer [error errorf info infof]]
            [taoensso.nippy :as nippy]
            [sade.env :as env])
  (:import (javax.jms ExceptionListener Connection Session Destination Queue
                      MessageProducer Message MessageListener
                      BytesMessage ObjectMessage TextMessage)
           (org.apache.activemq.artemis.jms.client ActiveMQJMSConnectionFactory)
           (org.apache.activemq.artemis.api.jms ActiveMQJMSClient)))

(when (env/feature? :embedded-artemis)
  ; This works only with :dev profile
  (require 'artemis-server)
  ((ns-resolve 'artemis-server 'start)))

(when (env/feature? :jms)

  (defonce connections (atom {:producers []
                              :consumers []}))

  (defn register [type object]
    (swap! connections update type conj object)
    object)

  (defn register-producer [producer]
    (register :producers producer))

  (defn message-listener [cb]
    (proxy [MessageListener] []
      (onMessage [^Message m]
        (condp instance? m
          BytesMessage (let [data (byte-array (.getBodyLength ^BytesMessage m))]
                                       (.readBytes ^BytesMessage m data)
                                       (cb data))
          ObjectMessage (cb (.getObject ^ObjectMessage m))
          TextMessage (cb (.getText ^TextMessage m))
          (error "Unknown JMS message type:" (type m))))))

  (def exception-listener
    (reify ExceptionListener
      (onException [_ e]
        (error e (str "JMS exception: " (.getMessage e))))))

  (defn queue ^Queue [name]
    (ActiveMQJMSClient/createQueue name))

  ;;
  ;; Connection
  ;;

  (def broker-url (or (env/value :jms :broker-url) "vm://0"))

  (defn create-connection ^Connection
    ([] (create-connection broker-url))
    ([host]
     (let [conn (.createConnection (ActiveMQJMSConnectionFactory. host))]
       (.setExceptionListener conn exception-listener)
       conn)))

  (def broker-connection ^Connection (create-connection))

  (def session (.createSession broker-connection false Session/AUTO_ACKNOWLEDGE))

  (do
    (.start broker-connection)
    (infof "Started JMS broker connection to %s" broker-url))

  ;;
  ;; Producers
  ;;

  (defn create-producer
    "Creates a producer in given session to given destination (queue).
    Returns function, which is called with data enroute to destination.
    If no message-fn is given, by default a TextMessage is created.
    Producer is internally registered and closed on shutdown."
    ([^Session session ^Destination queue]
     (create-producer session queue #(.createTextMessage session %)))
    ([^Session session ^Destination queue message-fn]
     (let [producer (.createProducer session queue)]
       (register-producer producer)
       (fn [data]
         (.send producer (message-fn data))))))

  (defn create-nippy-producer
    "Producer that serializes data to byte message with nippy." ; props to bowerick/jms
    [^Session session ^Destination queue]
    (letfn [(nippy-data [data]
              (doto
                (.createBytesMessage session)
                (.writeBytes ^bytes (nippy/freeze data))))]
      (create-producer session queue nippy-data)))

  ;;
  ;; Consumers
  ;;

  (defn create-consumer [^Session session ^Destination queue]
    (.createConsumer session queue))

  (defn register-consumer
    "Creates, register and starts consumer to given endpoint. Returns consumer instance."
    [^String endpoint callback-fn]
    (let [consumer (doto (create-consumer session (queue endpoint))
                     (.setMessageListener (message-listener callback-fn)))]
      (register :consumers consumer)
      consumer))

  (defn create-nippy-consumer
    [^String endpoint callback-fn]
    (register-consumer endpoint (fn [^bytes data] (callback-fn (nippy/thaw data)))))

  ;;
  ;; misc
  ;;

  (defn send-jms-message
    [^MessageProducer producer data]
    (producer data))

  (defn close-all!
    "Closes all registered consumers and the broker connection."
    []
    (doseq [conn (concat (:consumers @connections) (:producers @connections))]
      (.close conn))
    (.close broker-connection)

    (when-let [artemis (ns-resolve 'artemis-server 'embedded-broker)]
      (info "Stopping Artemis...")
      (.stop artemis))))

(comment
  ; replissä testailut:

  ;; TextMessage
  (register-consumer "testi" (fn [jee] (println "jee sai jotain:" jee)))
  (def testiprod (create-producer session (queue "testi")))
  ; => #'lupapalvelu.integrations.jms/testiprod
  (testiprod "stringi dataa")
  ;=> nil
  ; jee sai jotain: stringi dataa

  ;; Nippy
  (def nippy (create-nippy-producer session (queue "nippy")))
  (create-nippy-consumer "nippy" (fn [data] (println "nippy dataa:" data)))
  (nippy {:testi true :nimi "Nippy" :version 1})
  ;=> nil
  ; nippy dataa: {:testi true, :nimi Nippy, :version 1}
  )
