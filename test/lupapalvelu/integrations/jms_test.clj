(ns lupapalvelu.integrations.jms-test
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :refer [warnf]]
            [sade.env :as env]
            [lupapalvelu.integrations.jms :refer :all :as jms])
  (:import (javax.jms Queue MessageConsumer)
           (org.apache.activemq.artemis.jms.client ActiveMQConnection)))

(def test-atom (atom nil))

(if-not (env/feature? :jms)
  (warnf "JMS not enabled for unit testing")
  (facts "JMS"
    (fact "Embedded Artemis OK"
      (let [broker @(ns-resolve 'artemis-server 'embedded-broker)]
        (.isStarted broker) => true
        (str broker) => #"ActiveMQServerImpl"))
    (fact "Connection started" (.isStarted ^ActiveMQConnection (:conn @jms/state)) => true)
    (fact "Queue" (queue "tester") => (partial instance? Queue))
    (facts "Consumers"
      (fact "own fn"
        (let [consumer (create-consumer "tester" #(reset! test-atom %))]
          consumer => (partial instance? MessageConsumer)))
      (fact "nippy"
        (let [nippy-consumer (create-nippy-consumer "nippy.queue" #(reset! test-atom %))]
          nippy-consumer => (partial instance? MessageConsumer))))
    (facts "Producers"
      (fact "Default producer is text message"
        (let [prod (create-producer "tester")]
          prod => fn?
          (fact "successful send"
            (prod "test1") => nil
            (Thread/sleep 150)                               ; non-deterministic wait for message delivery
            @test-atom => "test1")
          (fact "wrong type -> exception"
            (prod {:foo 1}) => (throws IllegalArgumentException))))
      (fact "Nippy producer can be also used"
        (let [nippy (create-nippy-producer "nippy.queue")]
          nippy => fn?
          (fact "successful send"
            (nippy {:works? true}) => nil
            (Thread/sleep 150) ; non-deterministic wait for message delivery
            @test-atom => {:works? true}))))))
