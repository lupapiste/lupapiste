(ns sade.status-test
  (:require [midje.sweet :refer :all]
            [sade.status :refer :all]))

(facts "statuses"

  (fact "no statuses"
    (reset! statuses {})
    (status) => {:ok true :data {}})

  (fact "one ok"
    (reset! statuses {})
    (defstatus :ping 1)
    (status) => {:ok true
                 :data {:ping {:ok true
                               :data 1}}})

  (fact "two ok"
    (reset! statuses {})
    (defstatus :ping 1)
    (defstatus :pong 2)
    (status) => {:ok true
                 :data {:ping {:ok true
                               :data 1}
                        :pong {:ok true
                                 :data 2}}})

  (fact "one returns false"
    (reset! statuses {})
    (defstatus :ping 1)
    (defstatus :pong false)
    (status) => {:ok false
                 :data {:ping {:ok true
                               :data 1}
                        :pong {:ok false
                               :data false}}})

  (fact "one throws exception"
    (reset! statuses {})
    (let [exception (RuntimeException. "kosh")]
      (defstatus :ping 1)
      (defstatus :pong (throw exception))
      (status) => {:ok false
                   :data {:ping {:ok true
                                 :data 1}
                          :pong {:ok false
                                 :data (str exception)}}})))
