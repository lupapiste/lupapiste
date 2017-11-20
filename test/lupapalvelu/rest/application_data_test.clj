(ns lupapalvelu.rest.application-data-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.rakennuslupa-canonical-test :refer [application-rakennuslupa
                                                                      application-tyonjohtajan-nimeaminen
                                                                      application-tyonjohtajan-nimeaminen-v2]]
            [lupapalvelu.rest.applications-data :as applications-data]
            [lupapalvelu.rest.schemas :refer :all]))

(testable-privates lupapalvelu.rest.applications-data process-applications vtj-prt-updates)

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

(facts vtj-prt-updates
  (fact "no operation"
    (vtj-prt-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus"}}]}
                     "doc-id" "123456789A")
    => nil)

  (fact "one document"
    (vtj-prt-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id"}}}]}
                     "doc-id" "123456789A")
    => {"$set" {"documents.0.data.valtakunnallinenNumero.value" "123456789A"}})

  (fact "no document"
    (vtj-prt-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id"}}}]}
                     "unknown-doc-id" "123456789A")
    => nil)

  (fact "multiple documents"
    (vtj-prt-updates {:documents [{:id "doc-id1" :schema-info {:name "uusiRakennus" :op {:id "op-id1"}}}
                                  {:id "doc-id2" :schema-info {:name "uusiRakennus" :op {:id "op-id2"}}}
                                  {:id "doc-id3" :schema-info {:name "uusiRakennus" :op {:id "op-id3"}}}]}
                     "doc-id2" "123456789A")
    => {"$set" {"documents.1.data.valtakunnallinenNumero.value" "123456789A"}})

  (fact "document and multiple buildings"
    (vtj-prt-updates {:documents [{:id "doc-id" :schema-info {:name "uusiRakennus" :op {:id "op-id-c"}}}]
                      :buildings [{:operationId "op-id-a"}
                                  {:operationId "op-id-b"}
                                  {:operationId "op-id-c"}
                                  {:operationId "op-id-d"}]}
                     "doc-id" "123456789A")
    => {"$set" {"documents.0.data.valtakunnallinenNumero.value" "123456789A"
                "buildings.2.nationalId" "123456789A"}}))
