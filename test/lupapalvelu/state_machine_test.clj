(ns lupapalvelu.state-machine-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.operations]
            [lupapalvelu.states :as states]
            [lupapalvelu.state-machine :refer :all]))

(facts "state-graph"
  (fact "defaults"
    (state-graph {}) => states/default-application-state-graph
    (state-graph {:infoRequest true}) => states/default-inforequest-state-graph


    (state-graph {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                  :documents [{:schema-info {:name "tyonjohtaja-v2"}}]}) => states/tj-hakemus-state-graph
    (state-graph {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                  :permitSubtype "tyonjohtaja-hakemus"}) => states/tj-hakemus-state-graph
    (state-graph {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                  :permitSubtype "tyonjohtaja-ilmoitus"}) => states/tj-ilmoitus-state-graph))

(facts "can-proceed?"
  (can-proceed? {:infoRequest true :state "info"} :answered) => true
  (can-proceed? {:infoRequest true :state "answered"} :info) => true
  (can-proceed? {:infoRequest false :state "draft"} :open) => true
  (can-proceed? {:infoRequest false :state "open"} :draft) => false)

(facts "next-state"
  (next-state {:infoRequest false :state "draft"}) => :open
  (next-state {:infoRequest true :state "info"}) => :answered)

(facts "can proceed to next state"
  (doseq [state (map name (keys states/default-application-state-graph))
          :let [app {:state state}
                next (next-state app)]
          :when next]
    (fact {:midje/description state}
      (fact "can-proceed?"
        (can-proceed? app next) => true)
      (fact "validator"
        (validate-state-transition next {} app) => nil))))

(facts "default states are valid"
  (doseq [state (keys states/default-application-state-graph)]
    (fact {:midje/description state}
      (valid-state? {:permitType "R", :infoRequest false} state) => true)))

(facts "validate-state-transition"
  (validate-state-transition :info ..anything.. {:infoRequest true :state "answered"}) => nil
  (validate-state-transition :info ..anything.. {:infoRequest true :state "canceled"}) => (contains {:ok false})
  (validate-state-transition :canceled ..anything.. {:infoRequest true :state "answered"}) => (contains {:ok false})
  (validate-state-transition :canceled ..anything.. {:infoRequest true :state "canceled"}) => (contains {:ok false})
  (validate-state-transition :canceled ..anything.. {:infoRequest true :state "info"}) => nil
  )
