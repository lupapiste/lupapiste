(ns lupapalvelu.property-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.property :refer :all]
            [sade.env :as env]))


(facts "municipality-id-by-property-id"
  (municipality-id-by-property-id nil) => nil
  (municipality-id-by-property-id "") => nil
  (fact "not in db format"
    (municipality-id-by-property-id "123") => nil)
  (fact "not in db format, but looks like human readable so fallbacks to string resolver"
    (municipality-id-by-property-id "245-003-0105-0006") => "245"
    (provided
      (location-data-by-property-id-from-wfs "245-003-0105-0006") => anything :times 0))
  (fact "returns from WFS"
    (municipality-id-by-property-id "24500301050006") => "245test"
    (provided
      (location-data-by-property-id-from-wfs "24500301050006") => {:municipality "245test"}))
  (fact "Fallbacks if not found from WFS"
    (municipality-id-by-property-id "24500301050006") => "245"
    (provided
      (location-data-by-property-id-from-wfs "24500301050006") => nil))
  (fact "KTJ disabled, no requests to KTJKii WFS"
    (municipality-id-by-property-id "24500301050006") => "245"
    (provided
      (env/feature? :disable-ktj-on-create) => true
      (location-data-by-property-id-from-wfs anything) => anything :times 0))
  (fact "KTJ enabled, requests would be sent to KTJKii WFS"
    (municipality-id-by-property-id "24500301050006") => "245test"
    (provided
      (env/feature? :disable-ktj-on-create) => false
      (location-data-by-property-id-from-wfs "24500301050006") => {:municipality "245test"} :times 1)))
