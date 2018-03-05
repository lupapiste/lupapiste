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

(def ts 12345)

(def dummy-location (ssg/generate ssc/Location))

(def dummy-coords [(:x dummy-location) (:y dummy-location)])

(def dummy-wgs-coords (coord/convert "EPSG:3067" "WGS84" 5 dummy-coords))

(defn building-updates [location & [op-id]]
  {:building-updates {:location location
                      :nationalBuildingId "123456789A"
                      :operationId (or op-id "op-id")
                      :timestamp ts}})

(facts operation-operation-building-updates
  (fact "no operation"
    (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus"}}]}
                                "op-id" "123456789A" irrelevant ts)
    => nil)

  (fact "one document"
    (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id"}}}]}
                                  "op-id" "123456789A" dummy-location ts)
    => {"$push" (building-updates dummy-location)
        "$set" {"documents.0.data.valtakunnallinenNumero.value" "123456789A"}})

  (fact "no document"
    (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id"}}}]}
                                  "unknown-op-id" "123456789A" dummy-location ts)
    => nil)

  (fact "multiple documents"
    (operation-building-updates {:documents [{:id "doc-id1" :schema-info {:name "uusiRakennus" :op {:id "op-id1"}}}
                                             {:id "doc-id2" :schema-info {:name "uusiRakennus" :op {:id "op-id2"}}}
                                             {:id "doc-id3" :schema-info {:name "uusiRakennus" :op {:id "op-id3"}}}]}
                                  "op-id2" "123456789A" dummy-location ts)
    => {"$push" (building-updates dummy-location "op-id2")
        "$set" {"documents.1.data.valtakunnallinenNumero.value" "123456789A"}})

  (fact "document and multiple buildings"
    (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id-c"}}}]
                                 :buildings [{:operationId "op-id-a"}
                                             {:operationId "op-id-b"}
                                             {:operationId "op-id-c"}
                                             {:operationId "op-id-d"}]}
                                "op-id-c" "123456789A" dummy-location ts)
    => {"$push" (building-updates dummy-location "op-id-c")
        "$set" {"documents.0.data.valtakunnallinenNumero.value" "123456789A"
                "buildings.2.nationalId" "123456789A"
                "buildings.2.location" dummy-coords
                "buildings.2.location-wgs84" dummy-wgs-coords}})

  (facts "with location"
    (let [location-map (ssg/generate ssc/Location)
          wgs-coords   (coord/convert "EPSG:3067" "WGS84" 5 [(:x location-map) (:y location-map)])]
      (fact "no document"
        (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id"}}}]}
                                    "unknown-op-id" "123456789A" location-map irrelevant) => nil)
      (fact "one document, no buildings"
        (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id"}}}]}
                                    "op-id" "123456789A" location-map ts)
        => {"$push" (building-updates location-map)
            "$set" {"documents.0.data.valtakunnallinenNumero.value" "123456789A"}})
      (fact "with buildings location updates are generated"
        (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id-c"}}}]
                                     :buildings [{:operationId "op-id-a"}
                                                 {:operationId "op-id-b"}
                                                 {:operationId "op-id-c"}
                                                 {:operationId "op-id-d"}]}
                                    "op-id-c" "123456789A" location-map ts)
        => {"$push" (building-updates location-map "op-id-c")
            "$set" {"documents.0.data.valtakunnallinenNumero.value" "123456789A"
                    "buildings.2.nationalId" "123456789A"
                    "buildings.2.location" [(:x location-map) (:y location-map)]
                    "buildings.2.location-wgs84" wgs-coords}})
      (fact "with wrong buildings"
        (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id-c"}}}]
                                     :buildings [{:operationId "op-id-a"}]}
                                    "op-id-c" "123456789A" location-map ts)
        => {"$push" (building-updates location-map "op-id-c")
            "$set" {"documents.0.data.valtakunnallinenNumero.value" "123456789A"}})
      (fact "multiple buildings and operations"
        (operation-building-updates {:documents [{:id "doc-id1" :schema-info {:name "uusiRakennus" :op {:id "op-id1"}}}
                                                 {:id "doc-id2" :schema-info {:name "uusiRakennus" :op {:id "op-id2"}}}
                                                 {:id "doc-id3" :schema-info {:name "uusiRakennus" :op {:id "op-id3"}}}]
                                     :buildings [{:operationId "op-id1"}
                                                 {:operationId "op-id-b"}
                                                 {:operationId "op-id-c"}
                                                 {:operationId "op-id3"}]}
                                    "op-id3" "123456789A" location-map ts)
        => {"$push" (building-updates location-map "op-id3")
            "$set" {"documents.2.data.valtakunnallinenNumero.value" "123456789A"
                    "buildings.3.nationalId" "123456789A"
                    "buildings.3.location" [(:x location-map) (:y location-map)]
                    "buildings.3.location-wgs84" wgs-coords}})
      (fact "nil location is discarded"
        (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id-c"}}}]
                                     :buildings [{:operationId "op-id-a"}
                                                 {:operationId "op-id-c"}]}
                                    "op-id-c" "123456789A" nil ts)
        => {"$push" (building-updates nil "op-id-c")
            "$set" {"documents.0.data.valtakunnallinenNumero.value" "123456789A"
                    "buildings.1.nationalId" "123456789A"}}))))
