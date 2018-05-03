(ns lupapalvelu.message-queue-stest
  "Tests that Message Queue is online"
  (:require [midje.sweet :refer :all]
            [lupapalvelu.integrations.jms :as jms]
            [sade.core :refer :all]
            [sade.env :as env]))

(when (and (env/feature? :jms) (env/value :jms :broker-url))
  (def msgs (atom []))
  (def queue-name (str "message_queue_stest_" (now)))
  (defn handler [data] (swap! msgs conj data))
  (def producer (jms/create-producer queue-name))
  (with-open [consumer (jms/create-consumer queue-name handler)]
    (fact {:midje/description (str "Message is passed to broker " (env/value :jms :broker-url))}
      (producer "foo") => nil
      (Thread/sleep 1000)                                   ; 'deterministic' estimate :)
      (first @msgs) => "foo")))
