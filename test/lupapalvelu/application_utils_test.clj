(ns lupapalvelu.application-utils-test
  (:require [lupapalvelu.application-utils :refer :all]
            [midje.sweet :refer :all]))

(facts "Operation description"
  (let [app        {:primaryOperation {:name "kerrostalo-rivitalo"}}
        legacy-app {:primaryOperation nil}]
    (fact "Normal application"
      (operation-description app :fi) => "Asuinkerrostalon tai rivitalon rakentaminen"
      (operation-description app :sv) => "Byggande av flerv\u00E5ningshus eller radhus")
    (fact "Legacy application"
      (operation-description legacy-app :fi) => ""
      (operation-description legacy-app :sv) => "")))
