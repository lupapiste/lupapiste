(ns lupapalvelu.integrations.jms
  (:require [taoensso.timbre :refer [error errorf info infof tracef warnf fatal]]
            [taoensso.nippy :as nippy]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import (javax.jms ExceptionListener Connection Session Destination Queue
                      MessageListener BytesMessage ObjectMessage TextMessage MessageConsumer MessageProducer JMSException)
           (org.apache.activemq.artemis.jms.client ActiveMQJMSConnectionFactory ActiveMQConnection)
           (org.apache.activemq.artemis.api.jms ActiveMQJMSClient)))

(when (env/feature? :embedded-artemis)
  ; This works only with :dev profile
  (require 'artemis-server)
  ((ns-resolve 'artemis-server 'start)))

(when (env/feature? :jms)

  (defonce state (atom {:producers        []
                        :consumers        []
                        :conn             nil
                        :producer-session nil
                        :consumer-session nil}))

  (defn register [type fn object]
    (swap! state update type fn object)
    object)

  (defn register-conj [type object]
    (register type conj object))

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
        (error e "JMS exception, maybe it was a reconnect?" (.getMessage e))
        (info "After exception, is connection started:" (.isStarted ^ActiveMQConnection (:conn @state))))))

  (defn queue ^Queue [name]
    (ActiveMQJMSClient/createQueue name))

  ;;
  ;; Connection
  ;;

  (def broker-url (or (env/value :jms :broker-url) "vm://0"))

  (defn create-connection-factory ^ActiveMQJMSConnectionFactory [^String url connection-options]
    (let [{:keys [retry-interval retry-multipier max-retry-interval reconnect-attempts]
           :or   {retry-interval (* 2 1000)
                  retry-multipier 2
                  max-retry-interval (* 5 60 1000)          ; 5 mins
                  reconnect-attempts -1}} connection-options]
      (doto (ActiveMQJMSConnectionFactory. url)
        (.setRetryInterval (util/->long retry-interval))
        (.setRetryIntervalMultiplier (util/->double retry-multipier))
        (.setMaxRetryInterval (util/->long max-retry-interval))
        (.setReconnectAttempts (util/->int reconnect-attempts)))))

  (defn create-connection
    ([] (create-connection {:broker-url broker-url}))
    ([options]
     (create-connection options exception-listener))
    ([{:keys [broker-url username password] :as opts} ex-listener]
     (try
       (let [conn (if (ss/not-blank? username)
                    (.createConnection (create-connection-factory broker-url opts) username password)
                    (.createConnection (create-connection-factory broker-url opts)))]
         (.setExceptionListener conn ex-listener)
         (.start conn)
         conn)
       (catch Exception e
         (error e "Error while connecting to JMS broker " broker-url ": " (.getMessage e))))))

  (defn ensure-connection [state options]
    (loop [sleep-time 2000
           try-times 5]
      (when-not (:conn @state)
        (if-let [conn (create-connection options)]
          (do
            (swap! state assoc :conn conn)
            (infof "Started JMS broker connection to %s" (:broker-url options))
            state)
          (if (zero? try-times)
            (do
              (error "Can't connect to JMS broker, aborting")
              (throw (JMSException. "Connection failure")))
            (do
              (warnf "Couldn't connect to broker %s, reconnecting in %s seconds" (:broker-url options) (/ sleep-time 1000))
              (Thread/sleep sleep-time)
              (recur (min (* 2 sleep-time) 60000) (dec try-times))))))))

  (defn register-session [type]
    (swap! state assoc (keyword (str (name type) "-session")) (.createSession ^Connection (:conn @state) Session/AUTO_ACKNOWLEDGE)))

  (defn start! []
    (try
      (ensure-connection state (merge (env/value :jms) {:broker-url broker-url}))
      (when-not (:consumer-session @state)
        (register-session :consumer))
      (when-not (:producer-session @state)
        (register-session :producer))
      (catch Exception e
        (fatal e "Couldn't initialize JMS connections" (.getMessage e)))))

  ;;
  ;; Producers
  ;;

  (defn producer-session []
    (get @state :producer-session))

  (defn register-producer
    "Creates a producer to queue in given session.
    Returns one arity function which takes data to be sent to queue."
    [^Session session ^Destination queue message-fn]
    (let [producer (.createProducer session queue)]
      (register-conj :producers producer)
      (fn [data]
        (.send producer (message-fn data)))))

  (defn create-producer
    "Creates a producer to given queue (string) in default session.
    Returns function, which is called with data enroute to destination.
    message-fn must return instance of javax.jms.Message.
    If no message-fn is given, by default a TextMessage (string) is created.
    Producer is internally registered and closed on shutdown."
    ([^String queue-name]
     (register-producer (producer-session) (queue queue-name) #(.createTextMessage ^Session (producer-session) %)))
    ([^String queue-name message-fn]
     (register-producer (producer-session) (queue queue-name) message-fn)))

  (defn create-nippy-producer
    "Producer that serializes data to byte message with nippy." ; props to bowerick/jms
    ([^String queue-name]
     (create-nippy-producer (producer-session) queue-name))
    ([^Session session ^String queue-name]
     (letfn [(nippy-data [data]
               (doto
                 (.createBytesMessage session)
                 (.writeBytes ^bytes (nippy/freeze data))))]
       (create-producer queue-name nippy-data))))

  ;;
  ;; Consumers
  ;;

  (defn consumer-session []
    (get @state :consumer-session))

  (defn register-consumer
    "Create consumer to queue in given session.
    callback-fn receives the data, listener-fn creates the MessageListener."
    [^Session session ^Destination queue callback-fn listener-fn]
    (let [consumer-instance (doto (.createConsumer session queue)
                              (.setMessageListener (listener-fn callback-fn)))]
      (register-conj :consumers consumer-instance)
      consumer-instance))

  (defn create-consumer
    "Creates, register and starts consumer to given endpoint. Returns consumer instance."
    ([^String endpoint callback-fn]
     (create-consumer endpoint callback-fn message-listener))
    ([^String endpoint callback-fn listener-fn]
     (register-consumer (consumer-session) (queue endpoint) callback-fn listener-fn)))

  (defn create-nippy-consumer
    "Creates and returns consumer to endpoint, that deserializes JMS data with nippy/thaw."
    [^String endpoint callback-fn]
    (create-consumer endpoint (fn [^bytes data] (callback-fn (nippy/thaw data)))))

  ;;
  ;; misc
  ;;

  (defn close-all!
    "Closes all registered consumers and the broker connection."
    []
    (doseq [^MessageConsumer conn (:consumers @state)]
      (.close conn))
    (doseq [^MessageProducer conn (:producers @state)]
      (.close conn))
    (.close ^Connection (:conn @state))

    (when-let [artemis (ns-resolve 'artemis-server 'embedded-broker)]
      (info "Stopping Artemis...")
      (.stop artemis)))

  (start!))

(comment
  ; replissä testailut:

  ;; TextMessage
  (create-consumer "testi" (fn [jee] (println "jee sai jotain:" jee)))
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
