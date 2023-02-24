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
                           (case data
                             "boom!" (throw (IllegalAccessException. "sorry"))
                             "commit" (.commit transacted-session)
                             (swap! state update :msgs conj data))
                           (catch Exception _
                             (.rollback transacted-session)))))]
        (fact "Normal delivery -> 1"
          (prod "test1") => nil
          (Thread/sleep 200)
          (:deliveries @state) => 1
          (count (:msgs @state)) => 1
          (first (:msgs @state)) => "test1")
        ; commit previously sent messages
        (prod "commit") => nil
        (fact "Redelivery after rollback"
          (prod "boom!") => nil
          ; wait for all redeliveris to happen
          (Thread/sleep 500)
          (fact "Redeliveries occured"
            (:deliveries @state) => 12)                     ; "test1" + "commit" + (* 10 "boom!")
          (fact "Just the previous messsage handled"
            (count (:msgs @state)) => 1
            (first (:msgs @state)) => "test1"))
        ; reset state
        (reset! state {:deliveries 0 :msgs []})
        (fact "Rollback uncommited messages"
          (fact "commit msg1"
            (prod "msg1") => nil
            (prod "commit") => nil)
          (fact "don't commit msg2 before rollback"
            (prod "msg2") => nil
            (prod "boom!") => nil)
          (Thread/sleep 500)
          (fact "msg2 was also redelivered each time"
            (:deliveries @state) => 22)                     ; "msg1" + "commit" + (* 10 "msg2") + (* 10 "boom!")
          )))))
