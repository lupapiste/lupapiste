(ns lupapalvelu.core-itest
  (:require [midje.sweet  :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(facts "one can"
  (fact ".. list actions"
    (query mikko :actions) => truthy)
  (fact ".. list allowed actions"
    (let [result (query mikko :allowed-actions)]
      result => truthy
      result => (has some :allowed-actions))))

