(ns lupapalvelu.building-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.building :refer :all]))

(def test-docs [{:id "123" :schema-info {:name "testi1"}}
                {:id "1234" :schema-info {:name "testi2" :op {:id "321"}}}])

(def test-operation {:tag "321" :id "toimenpideId"})

(fact "Building ID updates are in correct form"
  (buildingid-updates-for-operation {:documents test-docs} "1234M" test-operation) => {"documents.1.data.valtakunnallinenNumero.value" "1234M"}
  (provided
    (lupapalvelu.document.schemas/get-schema anything) => {:body [{:name "valtakunnallinenNumero"}]}))
