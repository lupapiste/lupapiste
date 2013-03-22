(ns lupapalvelu.common-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]))

(facts "one can"
  (fact ".. list actions"
    (query mikko :actions) => truthy)
  (fact ".. list allowed actions"
    (let [result (query mikko :allowed-actions)]
      result => truthy
      result => (has some :ping))))

(fact "ping"
  (query mikko :ping) => {:ok true :text "pong"})
