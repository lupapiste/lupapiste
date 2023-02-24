(ns lupapalvelu.rest.application-data-test
  (:require [lupapalvelu.organization :as org]
            [lupapalvelu.rakennuslupa-canonical-util :refer [application-rakennuslupa]]
            [lupapalvelu.rest.applications-data :as applications-data]
            [lupapalvelu.rest.schemas :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.coordinate :as coord]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]))

(testable-privates lupapalvelu.rest.applications-data
                   get-toimenpiteet process-applications operation-building-updates)

(facts "Open application data tests"
  (against-background
    (org/pate-scope? irrelevant) => false
    (org/get-application-organization anything) => {})
  (let [rl (select-keys application-rakennuslupa applications-data/required-fields-from-db)]
    (fact "Schema verify OK"
      (process-applications [rl]) =not=> nil?)
    (fact "No documents -> Should fail"
      (process-applications [(dissoc rl :documents)]) => [])
    (fact "Unsupported permit type -> Should fail"
      (process-applications [(assoc rl :permitType "YA")]) => [])
    (fact "Broken municipality code -> Should fail"
      (process-applications [(assoc rl :municipality "1")]) => []
      (process-applications [(assoc rl :municipality "12")]) => []
      (process-applications [(assoc rl :municipality "1234")]) => []
      (process-applications [(assoc rl :municipality "123foo")]) => []
      (process-applications [(assoc rl :municipality "a123")]) => [])

    (fact "Poistuma date is in the correct format. Extinct date is purged."
      (-> (process-applications [rl]) first :toimenpiteet
          last :purkaminen :poistumaPvm) => "2013-04-17")
    (fact "Extinct date is purged."
      (let [app (assoc-in rl [:primaryOperation :extinct] 1624222800000)]
        (fact "raukeamisPvm is in raw/canonical"
          (-> (get-toimenpiteet app) first :muuMuutosTyo :raukeamisPvm)
          =>  "2021-06-21+03:00")
        (fact ".. but not in processed"
          (-> (process-applications [app])
              first :toimenpiteet first :muuMuutosTyo keys)
          => (just :kuvaus :perusparannusKytkin
                   :rakennustietojaEimuutetaKytkin :muutostyonLaji
                   :in-any-order))))))

(def ts 12345)

(def dummy-location (ssg/generate ssc/Location))

(def dummy-coords [(:x dummy-location) (:y dummy-location)])

(def dummy-wgs-coords (coord/convert "EPSG:3067" "WGS84" 5 dummy-coords))

(def apartments {:huoneistot {:0 {:jakokirjain {:value "b", :modified 1581571852493},
                                  :huoneistonumero {:value "154", :modified 1581571840154}
                                  :porras {:value "B", :modified 1581571840154}}

                              :1 {:jakokirjain {:value "c", :modified 1581571852493}
                                  :huoneistonumero {:value "154", :modified 1581571840154}
                                  :porras {:value "B", :modified 1581571840154}}

                              :2 {:jakokirjain {:value nil, :modified 1581571852493}
                                  :huoneistonumero {:value "154", :modified 1581571840154}
                                  :porras {:value nil, :modified 1581571840154}}

                              :3 {:jakokirjain {:value "b", :modified 1581571852493}
                                  :huoneistonumero {:value "155", :modified 1581571840154}
                                  :porras {:value "B", :modified 1581571840154}}

                              :4 {:jakokirjain {:value nil, :modified 1581571852493}
                                  :huoneistonumero {:value "156", :modified 1581571840154}
                                  :porras {:value nil, :modified 1581571840154}}

                              :5 {:jakokirjain {:value "c", :modified 1581571852493}
                                  :huoneistonumero {:value "157", :modified 1581571840154}
                                  :porras {:value "b", :modified 1581571840154}}}})

(def apartment-updates
  [{:huoneistonumero "154"
    :jakokirjain "b"
    :porras "b"
    :pysyvaHuoneistotunnus "567"}
   {:huoneistonumero "155"
    :jakokirjain "b"
    :porras "b"
    :pysyvaHuoneistotunnus "678"}
   {:huoneistonumero "154"
    :jakokirjain "c"
    :porras "b"
    :pysyvaHuoneistotunnus "789"}
   {:huoneistonumero "154"
    :pysyvaHuoneistotunnus "890"}
   {:huoneistonumero "157"
    :jakokirjain "c"
    :porras "b"
    :pysyvaHuoneistotunnus "345"}
   {:huoneistonumero "157"
    :jakokirjain "c"
    :porras "b"
    :pysyvaHuoneistotunnus "346"}])

(def apartments-for-building [{:jakokirjain "b"
                               :huoneistonumero "154"
                               :porras "b"}
                              {:jakokirjain "c"
                               :huoneistonumero "154"
                               :porras "b"}
                              {:jakokirjain nil
                               :huoneistonumero "154"
                               :porras nil}
                              {:jakokirjain "b"
                               :huoneistonumero "155"
                               :porras "b"}
                              {:jakokirjain nil
                               :huoneistonumero "156"
                               :porras nil}
                              {:jakokirjain "c"
                               :huoneistonumero "157"
                               :porras "b"}])

(defn building-updates [location & [op-id apartment-updates]]
  {:building-updates {:location location
                      :nationalBuildingId "123456789A"
                      :operationId (or op-id "op-id")
                      :timestamp ts
                      :apartments (or apartment-updates [])}})

(facts operation-operation-building-updates
  (fact "no operation"
    (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus"}}]}
                                "op-id" "123456789A" irrelevant [] ts)
    => nil)

  (fact "one document"
    (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id"}}
                                              :data apartments}]}
                                  "op-id" "123456789A" dummy-location [{:porras "b", :huoneistonumero "154", :jakokirjain "b", :pysyvaHuoneistotunnus "567"}
                                                                       {:porras "b", :huoneistonumero "155", :jakokirjain "b", :pysyvaHuoneistotunnus "678"}
                                                                       {:porras "b", :huoneistonumero "154", :jakokirjain "c", :pysyvaHuoneistotunnus "789"}
                                                                       {:huoneistonumero "154", :pysyvaHuoneistotunnus "890"}
                                                                       {:porras "b", :huoneistonumero "157", :jakokirjain "c", :pysyvaHuoneistotunnus "345"}
                                                                       {:porras "b", :huoneistonumero "157", :jakokirjain "c", :pysyvaHuoneistotunnus "346"}] ts)
    => {"$push" (building-updates dummy-location nil apartment-updates)
        "$set"  {"documents.0.data.valtakunnallinenNumero.value" "123456789A"
                 :documents.0.data.huoneistot.0.pysyvaHuoneistotunnus {:modified 12345 :value "567"}
                 :documents.0.data.huoneistot.1.pysyvaHuoneistotunnus {:modified 12345 :value "789"}
                 :documents.0.data.huoneistot.2.pysyvaHuoneistotunnus {:modified 12345 :value "890"}
                 :documents.0.data.huoneistot.3.pysyvaHuoneistotunnus {:modified 12345 :value "678"}}})

  (fact "no document"
    (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id"}}}]
                                 :data apartments}
                                  "unknown-op-id" "123456789A" dummy-location apartment-updates ts)
    => nil)

  (fact "multiple documents"
    (operation-building-updates {:documents [{:id "doc-id1" :schema-info {:name "uusiRakennus" :op {:id "op-id1"}}
                                              :data apartments}
                                             {:id "doc-id2" :schema-info {:name "uusiRakennus" :op {:id "op-id2"}}
                                              :data apartments}
                                             {:id "doc-id3" :schema-info {:name "uusiRakennus" :op {:id "op-id3"}}}]}
                                  "op-id2" "123456789A" dummy-location apartment-updates ts)
    => {"$push" (building-updates dummy-location "op-id2" apartment-updates)
        "$set"  {"documents.1.data.valtakunnallinenNumero.value" "123456789A"
                 :documents.1.data.huoneistot.0.pysyvaHuoneistotunnus {:modified 12345 :value "567"}
                 :documents.1.data.huoneistot.1.pysyvaHuoneistotunnus {:modified 12345 :value "789"}
                 :documents.1.data.huoneistot.2.pysyvaHuoneistotunnus {:modified 12345 :value "890"}
                 :documents.1.data.huoneistot.3.pysyvaHuoneistotunnus {:modified 12345 :value "678"}}})

  (fact "document and multiple buildings"
    (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id-c"}}
                                              :data apartments}]
                                 :buildings [{:operationId "op-id-a"
                                              :apartments apartments-for-building}
                                             {:operationId "op-id-b"
                                              :apartments apartments-for-building}
                                             {:operationId "op-id-c"
                                              :apartments apartments-for-building}
                                             {:operationId "op-id-d"}]}
                                "op-id-c" "123456789A" dummy-location apartment-updates ts)
    => {"$push" (building-updates dummy-location "op-id-c" apartment-updates)
        "$set"  {"documents.0.data.valtakunnallinenNumero.value" "123456789A"
                 "buildings.2.nationalId"                        "123456789A"
                 "buildings.2.location"                          dummy-coords
                 "buildings.2.location-wgs84"                    dummy-wgs-coords
                 :buildings.2.apartments.0.pysyvaHuoneistotunnus "567"
                 :buildings.2.apartments.1.pysyvaHuoneistotunnus "789"
                 :buildings.2.apartments.2.pysyvaHuoneistotunnus "890"
                 :buildings.2.apartments.3.pysyvaHuoneistotunnus "678"
                 :documents.0.data.huoneistot.0.pysyvaHuoneistotunnus {:modified 12345 :value "567"}
                 :documents.0.data.huoneistot.1.pysyvaHuoneistotunnus {:modified 12345 :value "789"}
                 :documents.0.data.huoneistot.2.pysyvaHuoneistotunnus {:modified 12345 :value "890"}
                 :documents.0.data.huoneistot.3.pysyvaHuoneistotunnus {:modified 12345 :value "678"}}})

  (facts "with location"
    (let [location-map (ssg/generate ssc/Location)
          wgs-coords   (coord/convert "EPSG:3067" "WGS84" 5 [(:x location-map) (:y location-map)])]
      (fact "no document"
        (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id"}}}]}
                                    "unknown-op-id" "123456789A" location-map [] irrelevant) => nil)
      (fact "one document, no buildings"
        (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id"}}}]}
                                    "op-id" "123456789A" location-map [] ts)
        => {"$push" (building-updates location-map)
            "$set"  {"documents.0.data.valtakunnallinenNumero.value" "123456789A"}})
      (fact "with buildings location updates are generated"
        (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id-c"}}}]
                                     :buildings [{:operationId "op-id-a"}
                                                 {:operationId "op-id-b"}
                                                 {:operationId "op-id-c"}
                                                 {:operationId "op-id-d"}]}
                                    "op-id-c" "123456789A" location-map [] ts)
        => {"$push" (building-updates location-map "op-id-c")
            "$set"  {"documents.0.data.valtakunnallinenNumero.value" "123456789A"
                     "buildings.2.nationalId"                        "123456789A"
                     "buildings.2.location"                          [(:x location-map) (:y location-map)]
                     "buildings.2.location-wgs84"                    wgs-coords}})
      (fact "with wrong buildings"
        (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id-c"}}}]
                                     :buildings [{:operationId "op-id-a"}]}
                                    "op-id-c" "123456789A" location-map [] ts)
        => {"$push" (building-updates location-map "op-id-c")
            "$set"  {"documents.0.data.valtakunnallinenNumero.value" "123456789A"}})
      (fact "multiple buildings and operations"
        (operation-building-updates {:documents [{:id "doc-id1" :schema-info {:name "uusiRakennus" :op {:id "op-id1"}}}
                                                 {:id "doc-id2" :schema-info {:name "uusiRakennus" :op {:id "op-id2"}}}
                                                 {:id "doc-id3" :schema-info {:name "uusiRakennus" :op {:id "op-id3"}}}]
                                     :buildings [{:operationId "op-id1"}
                                                 {:operationId "op-id-b"}
                                                 {:operationId "op-id-c"}
                                                 {:operationId "op-id3"}]}
                                    "op-id3" "123456789A" location-map [] ts)
        => {"$push" (building-updates location-map "op-id3")
            "$set"  {"documents.2.data.valtakunnallinenNumero.value" "123456789A"
                     "buildings.3.nationalId"                        "123456789A"
                     "buildings.3.location"                          [(:x location-map) (:y location-map)]
                     "buildings.3.location-wgs84"                    wgs-coords}})
      (fact "nil location is discarded"
        (operation-building-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id-c"}}}]
                                     :buildings [{:operationId "op-id-a"}
                                                 {:operationId "op-id-c"}]}
                                    "op-id-c" "123456789A" nil [] ts)
        => {"$push" (building-updates nil "op-id-c")
            "$set"  {"documents.0.data.valtakunnallinenNumero.value" "123456789A"
                     "buildings.1.nationalId"                        "123456789A"}})))
  (facts "with tasks"
    (let [national-id "123456789A"]
      (fact "Only one task matches building"
        (operation-building-updates {:documents [{:id          "doc-id"
                                                  :schema-info {:name "uusiRakennus"
                                                                :op   {:id "op-id-c"}}}]
                                     :buildings [{:operationId "op-id-a"
                                                  :index       "1"
                                                  :nationalId  "987654321B"}
                                                 {:operationId "op-id-c"
                                                  :index       "2"
                                                  :nationalId  national-id}]
                                     :tasks     [{:schema-info {:name "task-lupamaarays"}
                                                  :data        {}}
                                                 {:schema-info {:name "task-katselmus"}
                                                  :data        {:rakennus {:0 {:rakennus {:jarjestysnumero {:value "1"}}}
                                                                           :1 {:rakennus {:jarjestysnumero        {:value "2"}
                                                                                          :valtakunnallinenNumero {:value    "old"
                                                                                                                   :modified 1000}}}}}}
                                                 {:schema-info {:name "task-katselmus"}
                                                  :data        {:rakennus {:1 {:rakennus {:jarjestysnumero        {:value "2"}
                                                                                          :valtakunnallinenNumero {:value national-id}}}}}}
                                                 {:schema-info {:name "task-katselmus"}
                                                  :data        {:rakennus {:0 {:rakennus {:jarjestysnumero {:value "1"}}}
                                                                           :8 {:rakennus {:jarjestysnumero {:value "2"}}}}}}]}
                                    "op-id-c" national-id nil [] ts)
        => {"$push" (building-updates nil "op-id-c")
            "$set"  {"documents.0.data.valtakunnallinenNumero.value"          "123456789A"
                     "buildings.1.nationalId"                                 "123456789A"
                     :tasks.1.data.rakennus.1.rakennus.valtakunnallinenNumero {:value    national-id
                                                                               :modified ts}
                     :tasks.3.data.rakennus.8.rakennus.valtakunnallinenNumero {:value    national-id
                                                                               :modified ts}}}))))
