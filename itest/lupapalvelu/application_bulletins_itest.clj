(ns lupapalvelu.application-bulletins-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(facts "Publishing bulletins"
  (let [app    (create-and-submit-application pena)
        app-id (:id app)]
    (fact "Authority can publish bulletin"
      (command sonja :publish-bulletin :id app-id) => ok?)
    (fact "Regular user can't publish bulletin"
      (command pena :publish-bulletin :id app-id) => fail?)))
