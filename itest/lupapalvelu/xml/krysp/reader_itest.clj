(ns lupapalvelu.xml.krysp.reader-itest
  (:use [lupapalvelu.xml.krysp.reader]
        [sade.xml]
        [midje.sweet]))

(def id "75300301050006")

(fact "two buildings can be extracted"
  (let [xml (building-xml local-test-legacy id)]
    xml => truthy

    (let [buildings (->buildings xml)]
      buildings => truthy
      (count buildings) => 2

      (-> buildings first :propertyId) => "75300301050006"
      (-> buildings first :buildingId) => "001"

      (-> buildings second :propertyId) => "75300301050006"
      (-> buildings second :buildingId) => "002")))
