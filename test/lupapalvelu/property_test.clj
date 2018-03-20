(ns lupapalvelu.property-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer [expected-failure?]]
            [lupapalvelu.property :refer :all]
            [sade.env :as env]))


(facts "municipality-id-by-property-id"
  (municipality-by-property-id nil) => (partial expected-failure? :error.invalid-property-id)
  (municipality-by-property-id "") =>  (partial expected-failure? :error.invalid-property-id)
  (fact "not in db format"
    (municipality-by-property-id "123") => (partial expected-failure? :error.invalid-property-id)
    (municipality-by-property-id "245-003-0105-0006") => (partial expected-failure? :error.invalid-property-id))
  (fact "returns from WFS"
    (municipality-by-property-id "24500301050006") => "245test"
    (provided
      (location-data-by-property-id-from-wfs "24500301050006") => {:municipality "245test"}))
  (fact "Fallbacks if not found from WFS"
    (municipality-by-property-id "24500301050006") => "245"
    (provided
      (location-data-by-property-id-from-wfs "24500301050006") => nil))
  (fact "KTJ disabled, no requests to KTJKii WFS"
    (municipality-by-property-id "24500301050006") => "245"
    (provided
      (env/feature? :disable-ktj-on-create) => true
      (location-data-by-property-id-from-wfs anything) => anything :times 0))
  (fact "KTJ enabled, requests would be sent to KTJKii WFS"
    (municipality-by-property-id "24500301050006") => "245test"
    (provided
      (env/feature? :disable-ktj-on-create) => false
      (location-data-by-property-id-from-wfs "24500301050006") => {:municipality "245test"} :times 1)))
