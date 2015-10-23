(ns lupapalvelu.application-bulletins-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(apply-remote-minimal)

(facts "Publishing bulletins"
  (let [app (create-and-submit-application pena :operation "jatteen-keraystoiminta"
                                                :propertyId oulu-property-id
                                                :x 430109.3125 :y 7210461.375
                                                :address "Oulu 10")
        app-id (:id app)]
    (fact "Authority can publish bulletin"
      (command olli :publish-bulletin :id app-id) => ok?)
    (fact "Regular user can't publish bulletin"
      (command pena :publish-bulletin :id app-id) => fail?)))
