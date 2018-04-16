(ns lupapalvelu.integrations.jms
  (:require [taoensso.timbre :refer [error errorf info infof tracef warnf]]
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

  (defn message-listener [cb]
    (reify MessageListener
      (onMessage [_ m]
        (when-some [delivery-count (.getIntProperty m "JMSXDeliveryCount")]
          (tracef "Delivery count of message: %d" delivery-count)
          (when (< 1 delivery-count)
            (warnf "Message delivered already %d times" delivery-count)))
        (condp instance? m
          BytesMessage (let [data (byte-array (.getBodyLength ^BytesMessage m))]
                         (.readBytes ^BytesMessage m data)
                         (cb data))
          ObjectMessage (cb (.getObject ^ObjectMessage m))
          TextMessage   (cb (.getText ^TextMessage m))
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

  (defonce broker-connection ^Connection (create-connection))

  (defonce session (.createSession broker-connection Session/AUTO_ACKNOWLEDGE))

  (do
    (.start broker-connection)
    (infof "Started JMS broker connection to %s" broker-url))

  ;;
  ;; Producers
  ;;

  (defn register-producer
    "Creates a producer to queue in given session.
    Returns one arity function which takes data to be sent to queue."
    [^Session session ^Destination queue message-fn]
    (let [producer (.createProducer session queue)]
      (register :producers producer)
      (fn [data]
        (.send producer (message-fn data)))))

  (defn create-producer
    "Creates a producer to given queue (string) in default session.
    Returns function, which is called with data enroute to destination.
    message-fn must return instance of javax.jms.Message.
    If no message-fn is given, by default a TextMessage (string) is created.
    Producer is internally registered and closed on shutdown."
    ([^String queue-name]
     (register-producer session (queue queue-name) #(.createTextMessage session %)))
    ([^String queue-name message-fn]
     (register-producer session (queue queue-name) message-fn)))

  (defn create-nippy-producer
    "Producer that serializes data to byte message with nippy." ; props to bowerick/jms
    ([^String queue-name]
      (create-nippy-producer session queue-name))
    ([^Session session ^String queue-name]
     (letfn [(nippy-data [data]
               (doto
                 (.createBytesMessage session)
                 (.writeBytes ^bytes (nippy/freeze data))))]
       (create-producer queue-name nippy-data))))

  ;;
  ;; Consumers
  ;;

  (defn consumer [^Session session ^Destination queue]
    (.createConsumer session queue))

  (defn register-consumer
    "Creates, register and starts consumer to given endpoint. Returns consumer instance."
    ([^String endpoint callback-fn]
     (register-consumer endpoint callback-fn message-listener))
    ([^String endpoint callback-fn listener-fn]
     (let [consumer-instance (doto (consumer session (queue endpoint))
                               (.setMessageListener (listener-fn callback-fn)))]
       (register :consumers consumer-instance)
       consumer-instance)))

  (def create-consumer register-consumer)

  (defn create-nippy-consumer
    [^String endpoint callback-fn]
    (register-consumer endpoint (fn [^bytes data] (callback-fn (nippy/thaw data)))))

  ;;
  ;; misc
  ;;

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
  (def testiprod (create-producer "testi"))
  ; => #'lupapalvelu.integrations.jms/testiprod
  (testiprod "stringi dataa")
  ;=> nil
  ; jee sai jotain: stringi dataa

  ;; Nippy
  (def nippy (create-nippy-producer "nippy"))
  (create-nippy-consumer "nippy" (fn [data] (println "nippy dataa:" data)))
  (nippy {:testi true :nimi "Nippy" :version 1})
  ;=> nil
  ; nippy dataa: {:testi true, :nimi Nippy, :version 1}
  )
