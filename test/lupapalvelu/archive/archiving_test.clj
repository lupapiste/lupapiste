(ns lupapalvelu.archive.archiving-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.archive.archiving :refer :all]
            [lupapalvelu.permit :as permit]))


(facts "Should archiving valid YA applications"
  (fact "Valid YA application with valid state - should be archived"
    (valid-ya-state? {:id "LP-XXX-2017-00001" :permitType "YA" :state "verdictGiven"}) => true
    (valid-ya-state? {:id "LP-XXX-2017-00002" :permitType "YA" :state "finished"}) => true
    (valid-ya-state? {:id "LP-XXX-2017-00003" :permitType "YA" :state "extinct"}) => true
    (valid-ya-state? {:id "LP-XXX-2017-00004" :permitType "YA" :state "appealed"}) => true
    (valid-ya-state? {:id "LP-XXX-2017-00005" :permitType "YA" :state "closed"}) => true
    (valid-ya-state? {:id "LP-XXX-2017-00005" :permitType "YA" :state "agreementSigned"}) => true)
  (fact "Valid YA application with invalid state - should not be archived"
    (valid-ya-state? {:id "LP-XXX-2017-00006" :permitType "YA" :state "open"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00007" :permitType "YA" :state "submitted"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00008" :permitType "YA" :state "sent"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00009" :permitType "YA" :state "draft"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00010" :permitType "YA" :state "complementNeeded"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00005" :permitType "YA" :state "agreementPrepared"}) => false)
  (fact "Valid R application with valid R state - should not be in valid YA state"
    (valid-ya-state? {:id "LP-XXX-2017-00011" :permitType "R" :state "verdictGiven"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00012" :permitType "R" :state "closed"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00013" :permitType "R" :state "foremanVerdictGiven"}) => false)
  (fact "Invalid R application with invalid YA state - should not be in valid YA state"
    (valid-ya-state? {:id "LP-XXX-2017-00014" :permitType "R" :state "finished"}) => false))

(facts "Archiving project"
  (fact "Should set attachment specific backendId as first permit id"
    (let [verdicts [{:kuntalupatunnus "LX-0001"} {:kuntalupatunnus "LX-0002"} {:kuntalupatunnus "LX-0003"}]]
      (permit-ids-for-archiving verdicts {:backendId "LX-0002"} permit/ARK) => ["LX-0002" "LX-0001" "LX-0003"]
      (permit-ids-for-archiving verdicts {:backendId "LX-0002"} permit/R) => ["LX-0001" "LX-0002" "LX-0003"]))
  (fact "Should use attachment specific verdict for verdict date"
    (let [verdicts [{:kuntalupatunnus "LX-0001" :paatokset [{:poytakirjat {:0 {:paatospvm 1482530400000}}}]}
                    {:kuntalupatunnus "LX-0002" :paatokset [{:poytakirjat {:0 {:paatospvm 1512597600000}}}]}
                    {:kuntalupatunnus "LX-0003" :paatokset [{:poytakirjat {:0 {:paatospvm 1510057163483}}}]}]]
      (get-ark-paatospvm verdicts {:backendId "LX-0002"}) => "2017-12-07T00:00:00+02:00")))
