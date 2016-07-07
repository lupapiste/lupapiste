(ns lupapalvelu.state-machine-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.operations]
            [lupapalvelu.states :as states]
            [lupapalvelu.state-machine :refer :all]))

(facts "state-graph"
  (fact "defaults"
    (state-graph {:permitType "R"}) => states/full-application-state-graph
    (state-graph {:permitType "YA"}) => states/default-application-state-graph
    (state-graph {:infoRequest true}) => states/default-inforequest-state-graph


    (state-graph {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                  :documents [{:schema-info {:name "tyonjohtaja-v2"}}]}) => states/tj-hakemus-state-graph
    (state-graph {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                  :permitSubtype "tyonjohtaja-hakemus"}) => states/tj-hakemus-state-graph
    (state-graph {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                  :permitSubtype ""}) => states/tj-hakemus-state-graph
    (state-graph {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                  :permitSubtype nil}) => states/tj-hakemus-state-graph
    (state-graph {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                  :permitSubtype "tyonjohtaja-ilmoitus"}) => states/tj-ilmoitus-state-graph))

(facts "can-proceed?"
  (can-proceed? {:infoRequest true :state "info"} :answered) => true
  (can-proceed? {:infoRequest true :state "answered"} :info) => true
  (can-proceed? {:infoRequest false :state "draft" :permitType "R"} :open) => true
  (can-proceed? {:infoRequest false :state "open"  :permitType "R"} :draft) => false)

(facts "next-state"
  (next-state {:infoRequest false :state "draft" :permitType "R"}) => :open
  (next-state {:infoRequest true :state "info"}) => :answered)

(facts "can proceed to next state"
  (doseq [state (map name (keys states/full-application-state-graph))
          :let [app {:state state :permitType "R"}
                next (next-state app)]
          :when next]
    (fact {:midje/description state}
      (fact "can-proceed?"
        (can-proceed? app next) => true)
      (fact "validator"
        (validate-state-transition next {:application app}) => nil))))

(facts "default states are valid"
  (doseq [state (keys states/default-application-state-graph)]
    (fact {:midje/description state}
      (valid-state? {:permitType "R", :infoRequest false} state) => true)))

(facts "validate-state-transition"
  (validate-state-transition :info  {:application {:infoRequest true :state "answered"}}) => nil
  (validate-state-transition :info {:application {:infoRequest true :state "canceled"}}) => (contains {:ok false})
  (validate-state-transition :canceled {:application {:infoRequest true :state "answered"}}) => (contains {:ok false})
  (validate-state-transition :canceled {:application {:infoRequest true :state "canceled"}}) => (contains {:ok false})
  (validate-state-transition :canceled {:application {:infoRequest true :state "info"}}) => nil)

(facts "state-seq"
  (application-state-seq {:permitType "R"}) => [:draft :open :submitted :sent :verdictGiven :constructionStarted :inUse :closed]
  (application-state-seq {:infoRequest true}) => [:info :answered]

  (application-state-seq {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}}) => [:draft :open :submitted :sent :foremanVerdictGiven :canceled]
  (application-state-seq {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
              :permitSubtype "tyonjohtaja-ilmoitus"}) => [:draft :open :submitted :acknowledged])
