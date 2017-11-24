(ns lupapalvelu.rest.application-data-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.rakennuslupa-canonical-test :refer [application-rakennuslupa
                                                                      application-tyonjohtajan-nimeaminen
                                                                      application-tyonjohtajan-nimeaminen-v2]]
            [lupapalvelu.rest.applications-data :as applications-data]
            [lupapalvelu.rest.schemas :refer :all]))

(testable-privates lupapalvelu.rest.applications-data process-applications national-building-id-updates)

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

(facts national-building-id-updates
  (fact "no operation"
    (national-building-id-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus"}}]}
                                  "op-id" "123456789A")
    => nil)

  (fact "one document"
    (national-building-id-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id"}}}]}
                                  "op-id" "123456789A")
    => {"$set" {"documents.0.data.valtakunnallinenNumero.value" "123456789A"}})

  (fact "no document"
    (national-building-id-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id"}}}]}
                                  "unknown-op-id" "123456789A")
    => nil)

  (fact "multiple documents"
    (national-building-id-updates {:documents [{:id "doc-id1" :schema-info {:name "uusiRakennus" :op {:id "op-id1"}}}
                                               {:id "doc-id2" :schema-info {:name "uusiRakennus" :op {:id "op-id2"}}}
                                               {:id "doc-id3" :schema-info {:name "uusiRakennus" :op {:id "op-id3"}}}]}
                                  "op-id2" "123456789A")
    => {"$set" {"documents.1.data.valtakunnallinenNumero.value" "123456789A"}})

  (fact "document and multiple buildings"
    (national-building-id-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id-c"}}}]
                                   :buildings [{:operationId "op-id-a"}
                                               {:operationId "op-id-b"}
                                               {:operationId "op-id-c"}
                                               {:operationId "op-id-d"}]}
                                  "op-id-c" "123456789A")
    => {"$set" {"documents.0.data.valtakunnallinenNumero.value" "123456789A"
                "buildings.2.nationalId" "123456789A"}}))
