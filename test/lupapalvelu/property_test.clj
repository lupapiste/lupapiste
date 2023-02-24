(ns lupapalvelu.property-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer [expected-failure?]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.property :refer :all]
            [sade.env :as env]
            [sade.property :as sprop]))

(facts "municipality-id-by-property-id"
  (against-background (env/feature? :disable-ktj-on-create) => false)
  (facts "without mongo cache"
    (against-background
      (mongo/select-one :propertyMunicipalityCache anything anything) => nil
      (mongo/insert :propertyMunicipalityCache anything anything) => nil)
    (municipality-by-property-id nil) => (partial expected-failure? :error.invalid-property-id)
    (municipality-by-property-id "") =>  (partial expected-failure? :error.invalid-property-id)
    (fact "not in db format"
      (municipality-by-property-id "123") => (partial expected-failure? :error.invalid-property-id)
      (municipality-by-property-id "245-003-0105-0006") => (partial expected-failure? :error.invalid-property-id))
    (fact "returns from WFS"
      (municipality-by-property-id "24500301050006") => "245test"
      (provided
        (property-info-from-wfs "24500301050006") => {:municipality "245test"
                                                      :propertyId "24500301050006"}))
    (fact "Fallbacks if not found from WFS"
      (municipality-by-property-id "24500301050006") => "245"
      (provided
        (property-info-from-wfs "24500301050006") => nil))
    (fact "KTJ disabled, no requests to KTJKii WFS"
      (municipality-by-property-id "24500301050006") => "245"
      (provided
        (env/feature? :disable-ktj-on-create) => true
        (property-info-from-wfs anything) => anything :times 0))
    (fact "KTJ enabled, requests would be sent to KTJKii WFS"
      (municipality-by-property-id "24500301050006") => "245test"
      (provided
        (env/feature? :disable-ktj-on-create) => false
        (property-info-from-wfs "24500301050006") => {:municipality "245test"
                                                      :propertyId "24500301050006"} :times 1)))
  (facts "mongo with cache"
    (fact "No initial data, mongo/insert is called"
      (municipality-by-property-id "24500301050006") => "245test"
      (provided
        (mongo/insert :propertyMunicipalityCache anything anything) => nil :times 1
        (mongo/select-one :propertyMunicipalityCache {:propertyId "24500301050006"} anything) => nil
        (property-info-from-wfs "24500301050006") => {:municipality "245test"
                                                      :propertyId "24500301050006"}))
    (fact "mongo returns, no mongo/insert call"
      (municipality-by-property-id "24500301050006") => "245mongo"
      (provided
        (mongo/insert :propertyMunicipalityCache anything anything) => nil :times 0
        (mongo/select-one :propertyMunicipalityCache {:propertyId "24500301050006"} anything) => {:municipality "245mongo"
                                                                                                  :propertyId "24500301050006"}
        (property-info-from-wfs "24500301050006") => nil :times 0
        (sprop/municipality-id-by-property-id "24500301050006") => nil :times 0))))
