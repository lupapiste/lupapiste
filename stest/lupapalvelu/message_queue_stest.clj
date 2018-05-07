(ns lupapalvelu.message-queue-stest
  "Tests that Message Queue is online"
  (:require [midje.sweet :refer :all]
            [lupapalvelu.integrations.jms :as jms]
            [sade.core :refer :all]
            [sade.env :as env])
  (:import (javax.jms Session)))

(when (and (env/feature? :jms) (env/value :jms :broker-url))
  (def msgs (atom []))
  (def queue-name (str "message_queue_stest_" (now)))
  (def redelivery-queue-name (str "mq_redelivery_stest_" (now)))
  (defn handler [data] (swap! msgs conj data))
  (def producer (jms/create-producer queue-name))
  (facts {:midje/description (str "JMS broker " (env/value :jms :broker-url))}
    (facts "Basic case"
      (with-open [consumer (jms/create-consumer queue-name handler)]
        (fact "Message is passed to broker"
          (producer "foo") => nil
          (Thread/sleep 1000)                                 ; 'deterministic' estimate :)
          (first @msgs) => "foo")))
    (facts "Redelivery with transacted session"
      (let [state (atom {:deliveries 0
                         :msgs       []})
            transacted-session (jms/register-session (jms/create-session (:conn @jms/state) Session/SESSION_TRANSACTED) :consumer)
            prod     (jms/create-producer redelivery-queue-name)
            consumer (jms/create-consumer
                       transacted-session
                       redelivery-queue-name
                       (fn [data]
                         (println "data:" data)
                         (swap! state update :deliveries inc)
                         (try
                           (if (= "boom!" data)
                             (throw (IllegalAccessException. "sorry"))
                             (swap! state update :msgs conj data))
                           (.commit transacted-session)
                           (catch Exception _
                             (.rollback transacted-session)))))]
        (fact "Normal delivery -> 1"
          (prod "test1") => nil
          (Thread/sleep 200)
          (:deliveries @state) => 1
          (count (:msgs @state)) => 1
          (first (:msgs @state)) => "test1")
        (fact "Delivery with exception"
          (prod "boom!") => nil
          ; wait for all redeliveris to happen
          (Thread/sleep 1000)
          (fact "Redeliveries occured"
            (:deliveries @state) => 11)
          (fact "Just the previous messsage handled"
            (count (:msgs @state)) => 1
            (first (:msgs @state)) => "test1"))))))
