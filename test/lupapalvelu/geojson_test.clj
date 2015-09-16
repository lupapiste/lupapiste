(ns lupapalvelu.geojson-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.geojson :refer :all]
            [sade.core :refer [fail?]]))

(facts "Validate Point"
  (validate-point [10001 6610000]) => nil
  (fact "fail if coordinates not numbers" (validate-point ["10001" "6610000"]) => fail?)
  (fact "fail if coordinate not pair" (validate-point [10001 6610000 0]) => fail?))

