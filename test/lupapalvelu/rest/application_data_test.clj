(ns lupapalvelu.rest.application-data-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.rakennuslupa-canonical-test :refer [application-rakennuslupa
                                                                      application-tyonjohtajan-nimeaminen
                                                                      application-tyonjohtajan-nimeaminen-v2]]
            [lupapalvelu.rest.applications-data :as applications-data]
            [lupapalvelu.rest.schemas :refer :all]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]
            [sade.coordinate :as coord]))

(testable-privates lupapalvelu.rest.applications-data process-applications operation-building-updates)

(facts "Open application data tests"
  (let [rl (select-keys application-rakennuslupa applications-data/required-fields-from-db)]
    (fact "Schema verify OK"
      (process-applications [rl]) =not=> nil?)
    (fact "No documents -> Should fail"
      (process-applications [(dissoc rl :documents)]) => [])
    (fact "Unsupported permit type -> Should fail"
      (process-applications [(assoc rl :permitType "YA")]) => [])
    (fact "Broken municipality code -> Should fail"
      (process-applications [(assoc rl :municipality "lasdflasdfaa")]) => [])))

(facts operation-operation-building-updates
  (fact "no operation"
    (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus"}}]}
                                  "op-id" "123456789A" irrelevant)
    => nil)

  (fact "one document"
    (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id"}}}]}
                                  "op-id" "123456789A" irrelevant)
    => {"$set" {"documents.0.data.valtakunnallinenNumero.value" "123456789A"}})

  (fact "no document"
    (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id"}}}]}
                                  "unknown-op-id" "123456789A" irrelevant)
    => nil)

  (fact "multiple documents"
    (operation-building-updates {:documents [{:id "doc-id1" :schema-info {:name "uusiRakennus" :op {:id "op-id1"}}}
                                             {:id "doc-id2" :schema-info {:name "uusiRakennus" :op {:id "op-id2"}}}
                                             {:id "doc-id3" :schema-info {:name "uusiRakennus" :op {:id "op-id3"}}}]}
                                  "op-id2" "123456789A" irrelevant)
    => {"$set" {"documents.1.data.valtakunnallinenNumero.value" "123456789A"}})

  (fact "document and multiple buildings"
    (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id-c"}}}]
                                 :buildings [{:operationId "op-id-a"}
                                             {:operationId "op-id-b"}
                                             {:operationId "op-id-c"}
                                             {:operationId "op-id-d"}]}
                                "op-id-c" "123456789A" irrelevant)
    => {"$set" {"documents.0.data.valtakunnallinenNumero.value" "123456789A"
                "buildings.2.nationalId" "123456789A"}})

  (facts "with location"
    (let [location-map (ssg/generate ssc/Location)
          wgs-coords   (coord/convert "EPSG:3067" "WGS84" 5 [(:x location-map) (:y location-map)])]
      (fact "no document"
        (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id"}}}]}
                                    "unknown-op-id" "123456789A" location-map) => nil)
      (fact "one document, no buildings"
        (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id"}}}]}
                                    "op-id" "123456789A" location-map)
        => {"$set" {"documents.0.data.valtakunnallinenNumero.value" "123456789A"}})
      (fact "with buildings location updates are generated"
        (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id-c"}}}]
                                     :buildings [{:operationId "op-id-a"}
                                                 {:operationId "op-id-b"}
                                                 {:operationId "op-id-c"}
                                                 {:operationId "op-id-d"}]}
                                    "op-id-c" "123456789A" location-map)
        => {"$set" {"documents.0.data.valtakunnallinenNumero.value" "123456789A"
                    "buildings.2.nationalId" "123456789A"
                    "buildings.2.location" [(:x location-map) (:y location-map)]
                    "buildings.2.location-wgs84" wgs-coords}})
      (fact "with wrong buildings"
        (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id-c"}}}]
                                     :buildings [{:operationId "op-id-a"}]}
                                    "op-id-c" "123456789A" location-map)
        => {"$set" {"documents.0.data.valtakunnallinenNumero.value" "123456789A"}})
      (fact "multiple buildings and operations"
        (operation-building-updates {:documents [{:id "doc-id1" :schema-info {:name "uusiRakennus" :op {:id "op-id1"}}}
                                                 {:id "doc-id2" :schema-info {:name "uusiRakennus" :op {:id "op-id2"}}}
                                                 {:id "doc-id3" :schema-info {:name "uusiRakennus" :op {:id "op-id3"}}}]
                                     :buildings [{:operationId "op-id1"}
                                                 {:operationId "op-id-b"}
                                                 {:operationId "op-id-c"}
                                                 {:operationId "op-id3"}]}
                                    "op-id3" "123456789A" location-map)
        => {"$set" {"documents.2.data.valtakunnallinenNumero.value" "123456789A"
                    "buildings.3.nationalId" "123456789A"
                    "buildings.3.location" [(:x location-map) (:y location-map)]
                    "buildings.3.location-wgs84" wgs-coords}})
      (fact "nil location is discarded"
        (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id-c"}}}]
                                     :buildings [{:operationId "op-id-a"}
                                                 {:operationId "op-id-c"}]}
                                    "op-id-c" "123456789A" nil)
        => {"$set" {"documents.0.data.valtakunnallinenNumero.value" "123456789A"
                    "buildings.1.nationalId" "123456789A"}}))))
