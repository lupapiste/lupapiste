(ns lupapalvelu.integrations.jms
  (:require [mount.core :refer [defstate]]
            [taoensso.timbre :refer [error errorf info infof tracef warnf fatal]]
            [taoensso.nippy :as nippy]
            [lupapiste-jms.client :as jms]
            [lupapalvelu.mongo :as mongo]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [javax.jms Connection Session JMSContext
                      MessageListener MessageConsumer MessageProducer Message JMSException]
           [org.apache.activemq.artemis.jms.client ActiveMQJMSConnectionFactory ActiveMQConnection]))

;;;; Embedded broker for dev
;;;; ===================================================================================================================

; If external broker is not defined, we start an embedded broker inside the JVM for testing.
(when (env/feature? :jms-embedded)
  (require 'artemis-server))


  (def jms-test-db-property "LP_test_db_name")

  ;;;; Connection
  ;;;; =================================================================================================================


  (defn create-connection-factory ^ActiveMQJMSConnectionFactory [{^String url :broker-url :as connection-options}]
    (let [{:keys [retry-interval retry-multipier max-retry-interval reconnect-attempts consumer-window-size]
           :or   {retry-interval       (* 2 1000)
                  retry-multipier      2
                  max-retry-interval   (* 5 60 1000)        ; 5 mins
                  consumer-window-size 0
                  reconnect-attempts   -1}} connection-options]
      (doto (ActiveMQJMSConnectionFactory. url)
        (.setRetryInterval (util/->long retry-interval))
        (.setRetryIntervalMultiplier (util/->double retry-multipier))
        (.setMaxRetryInterval (util/->long max-retry-interval))
        (.setReconnectAttempts (util/->int reconnect-attempts))
        (.setConsumerWindowSize (util/->int consumer-window-size)))))

  (declare state)

  (def exception-listener
    (jms/exception-listener
      (fn [^Throwable e]
        (error e "JMS exception, maybe it was a reconnect?" (.getMessage e))
        (info "After exception, is connection started:" (.isStarted ^ActiveMQConnection (:conn @state))))))

  (defn create-connection [factory {:keys [broker-url username password]} ex-listener]
    (try
      (let [conn (if (ss/not-blank? username)
                   (jms/create-connection factory {:username username :password password :ex-listener ex-listener})
                   (jms/create-connection factory {:ex-listener ex-listener}))]
        (.start conn)
        conn)
      (catch Exception e
        (error e "Error while connecting to JMS broker " broker-url ": " (.getMessage e)))))

  (defn ensure-connection [state options]
    (loop [sleep-time 2000
           try-times 5]
      (when-not (get @state :conn)
        (if-let [conn (create-connection (:factory @state) options exception-listener)]
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
              (recur (min (* 2 sleep-time) 15000) (dec try-times))))))))

  ;;;; State
  ;;;; =================================================================================================================

  (defstate state
    :start (when (env/feature? :jms)
             (let [broker-url       (if (env/feature? :jms-embedded)
                                      "vm://0"
                                      (or (env/value :jms :broker-url) "vm://0"))
                   connection-props (merge (env/value :jms)
                                           {:broker-url broker-url})
                   state            (atom {:producers         []
                                           :consumers         []
                                           :connection-props  connection-props
                                           :factory           (create-connection-factory connection-props)
                                           :conn              nil
                                           :producer-sessions []
                                           :consumer-sessions []})]
               (try
                 (ensure-connection state connection-props)
                 state
                 (catch Exception e
                   (fatal e "Couldn't initialize JMS connections" (.getMessage e))))))
    :stop (when (env/feature? :jms)
            (doseq [^MessageConsumer conn (:consumers @state)]
              (.close conn))
            (doseq [^MessageProducer conn (:producers @state)]
              (.close conn))
            (when-let [conn (:conn @state)]
              (.close ^Connection conn))
            (.close (:factory @state))
            (info "JMS connections closed")))

  ;;;; Tools
  ;;;; =================================================================================================================

  (defn get-default-connection []
    (get @state :conn))

  (defn register [type fn object]
    (swap! state update type fn object)
    object)

  (defn register-conj [type object]
    (register type conj object))

  (defn message-listener ^MessageListener [cb]
    (reify MessageListener
      (onMessage [_ m]
        (when-some [delivery-count (.getIntProperty m "JMSXDeliveryCount")]
          (tracef "Delivery count of message: %d" delivery-count)
          (when (< 1 delivery-count)
            (warnf "Message delivered already %d times" delivery-count)))
        (let [test-db-name (.getStringProperty m jms-test-db-property)
              db-name (if (and (env/dev-mode?) (ss/not-blank? test-db-name))
                        test-db-name
                        mongo/*db-name*)]
          (mongo/with-db db-name
            (cb (jms/message-content m)))))))

  (def queue jms/create-queue)

  (def create-session jms/create-session)

  (def create-transacted-session jms/create-transacted-session)

  (defn ^Session register-session [session type]
    (swap! state update (keyword (str (name type) "-session")) conj session)
    session)

  (def commit jms/commit)
  (def rollback jms/rollback)

  (defn create-jms-message ^Message [data session-or-context]
    (let [message (jms/create-message data session-or-context)]
      (when (env/dev-mode?)
        (.setStringProperty message jms-test-db-property mongo/*db-name*))
      message))

  ;;;; Producers
  ;;;; =================================================================================================================

  (defn producer-session []
    (or (get-in @state [:producer-session 0])
        (register-session (jms/create-session ^Connection (get-default-connection) Session/AUTO_ACKNOWLEDGE) :producer)))

  (defn register-producer
    "Register producer to state and return it."
    [producer]
    (register-conj :producers producer))

  (defn create-producer
    "Creates a producer to given queue (string) into producer-session.
    Returns function, which is called with data enroute to destination.
    message-fn must return instance of javax.jms.Message.
    If no message-fn is given, message type is inferred from data with jms/MessageCreator protocol.
    Producer is internally registered and closed on shutdown."
    ([queue-name]
     (create-producer (producer-session) queue-name #(create-jms-message % (producer-session))))
    ([session queue-name message-fn]
     (-> (jms/create-producer session (queue session queue-name))
         (register-producer)
         (jms/producer-fn message-fn))))

  (defn create-nippy-producer
    "Producer that serializes data to byte message with nippy." ; props to bowerick/jms
    ([queue-name]
     (create-nippy-producer (producer-session) queue-name))
    ([session queue-name]
     (create-producer session queue-name #(create-jms-message (nippy/freeze %) session))))

  (defn produce-with-context [destination-name data]
    (jms/send-with-context
      (:factory @state)
      (queue (producer-session) destination-name)
      data
      (merge (select-keys (get @state :connection-props) [:username :password])
             {:session-mode JMSContext/AUTO_ACKNOWLEDGE
              :message-fn   create-jms-message
              :ex-listener  exception-listener})))

  ;;;; Consumers
  ;;;; =================================================================================================================

  (def listen jms/listen)

  (defn consumer-session []
    (or (get-in @state [:consumer-session 0])
        (register-session (jms/create-session ^Connection (get-default-connection) Session/AUTO_ACKNOWLEDGE) :consumer)))

  (defn register-consumer
    "Register consumer to state and return it."
    [consumer-instance]
    (register-conj :consumers consumer-instance))

  (defn create-consumer
    "Creates, register and starts consumer to given endpoint. Returns consumer instance."
    ([endpoint-name callback-fn]
     (create-consumer (consumer-session) endpoint-name callback-fn))
    ([session endpoint-name callback-fn]
     (-> (listen session (queue session endpoint-name) (message-listener callback-fn))
         (register-consumer))))

  (defn nippy-callbacker [callback]
    (fn [^bytes data]
      (try
        (let [clj-data (nippy/thaw data)]
          (callback clj-data))
        (catch ClassCastException e
          (errorf e "Couldn't cast JMS message to Clojure data with nippy, ignoring callback.")))))

  (defn create-nippy-consumer
    "Creates and returns consumer to endpoint, that deserializes JMS data with nippy/thaw.
    Uses default consumer session created in this namespace."
    ([endpoint callback-fn]
     (create-consumer endpoint (nippy-callbacker callback-fn)))
    ([session endpoint callback-fn]
     (create-consumer session endpoint (nippy-callbacker callback-fn))))

(comment
  ; replissa testailut:

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

  ; testing redeliveries with SESSION_TRANSACTED
  (def error-prod (create-producer "boom"))
  (def transacted-session (create-transacted-session (get-default-connection)))
  (def consumer (create-consumer
                  transacted-session
                  "boom"
                  (fn [msg]
                    (try
                      (if (= "boom" msg)
                        (throw (IllegalAccessException. "sorry"))
                        (println "got message:" msg))
                      (.commit transacted-session)
                      (catch Exception e
                        (println "errori")
                        (.rollback transacted-session))))))
  (error-prod "moi")
  ;=> nil
  ;got message: moi
  (error-prod "boom")
  ;=> nil
  ;errori
  ;WARN 2018-05-07 12:32:31.145 [] [] [] lupapalvelu.integrations.jms - Message delivered already 2 times
  ;errori
  ;WARN 2018-05-07 12:32:31.151 [] [] [] lupapalvelu.integrations.jms - Message delivered already 3 times
  ;errori
  ;WARN 2018-05-07 12:32:31.156 [] [] [] lupapalvelu.integrations.jms - Message delivered already 4 times
  ;errori
  ;WARN 2018-05-07 12:32:31.161 [] [] [] lupapalvelu.integrations.jms - Message delivered already 5 times
  ;errori
  ;WARN 2018-05-07 12:32:31.166 [] [] [] lupapalvelu.integrations.jms - Message delivered already 6 times
  ;errori
  ;WARN 2018-05-07 12:32:31.170 [] [] [] lupapalvelu.integrations.jms - Message delivered already 7 times
  ;errori
  ;WARN 2018-05-07 12:32:31.175 [] [] [] lupapalvelu.integrations.jms - Message delivered already 8 times
  ;errori
  ;WARN 2018-05-07 12:32:31.180 [] [] [] lupapalvelu.integrations.jms - Message delivered already 9 times
  ;errori
  ;WARN 2018-05-07 12:32:31.183 [] [] [] lupapalvelu.integrations.jms - Message delivered already 10 times
  ;errori

  )
