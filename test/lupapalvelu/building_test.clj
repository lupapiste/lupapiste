(ns lupapalvelu.building-test
  (:require [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [lupapalvelu.building :refer :all]))

(def buildings [{:description "Talo A",
                 :localShortId "101",
                 :operationId "321"
                 :buildingId "123456001M",
                 :index "1",
                 :created "2013",
                 :localId nil,
                 :usage "039 muut asuinkerrostalot",
                 :nationalId "123456001M",
                 :area "2000",
                 :propertyId "12345678912"}])

(def test-docs [{:id "123" :schema-info {:name "testi1"}}
                {:id "1234" :schema-info {:name "testi2" :op {:id "321"}}}])

(def test-operation (-> buildings first :operationId))

(against-background
  [(lupapalvelu.document.schemas/get-schema anything) => {:body [{:name "valtakunnallinenNumero"}]}]

  (facts "Building ID updates are in correct form"
    (buildingid-updates-for-operation {:documents test-docs} "1234M" test-operation) => {"documents.1.data.valtakunnallinenNumero.value" "1234M"}
    (buildingid-updates-for-operation {:documents []} "1234M" test-operation) => {}
    (buildingid-updates-for-operation {:documents test-docs} "1234M" {:foo "bar"}) => {}
    (buildingid-updates-for-operation {:documents test-docs} "1234M" nil) => {}))

(fact "No update if schema doesn't have valtakunnallinenNumero"
  (buildingid-updates-for-operation {:documents test-docs} "1234M" test-operation) => {}
  (provided
    (lupapalvelu.document.schemas/get-schema anything) => {:body [{:name "foobar"}]}))

(against-background
  [(lupapalvelu.document.schemas/get-schema anything) => {:body [{:name "valtakunnallinenNumero"}]}]

  (facts "Building and document updates together"
    (fact "buildings array and document update OK"
      (building-updates {:documents test-docs} buildings) => {$set {:buildings buildings, "documents.1.data.valtakunnallinenNumero.value" "123456001M"}})
    (let [first-tag-unknown (map #(assoc % :operationId "foobar") buildings)]
      (fact "no document updates if unknown operation in buildings"
        (building-updates {:documents test-docs} first-tag-unknown) => {$set {:buildings first-tag-unknown}}))))
