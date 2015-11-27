(ns lupapalvelu.states-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.states :refer :all]))

(fact "initial-state"
  (initial-state default-application-state-graph) => :draft
  (initial-state tj-hakemus-state-graph) => :draft
  (initial-state tj-ilmoitus-state-graph) => :draft
  (initial-state default-inforequest-state-graph) => :info)

(fact "all-next-states"
  (all-next-states default-application-state-graph :verdictGiven) => #{:verdictGiven :constructionStarted :closed :canceled})

(fact "post-submitted-states"
  post-submitted-states => (contains #{:sent :complementNeeded :verdictGiven :constructionStarted :closed})
  post-submitted-states =not=> (contains #{:open :submitted}))

(fact "post-verdict-states"
  post-verdict-states => (contains #{:verdictGiven :foremanVerdictGiven :constructionStarted :closed})
  post-verdict-states =not=> (contains #{:sent :complementNeeded :canceled :submitted}))

(fact "all states"
  all-inforequest-states => #{:info :answered}

  all-application-states => #{:draft :open :submitted :sent :complementNeeded
                              :verdictGiven :constructionStarted :closed :canceled
                              :extinct
                              :acknowledged :foremanVerdictGiven
                              :hearing :proposal :proposalApproved
                              :survey :sessionProposal :sessionHeld :registered
                              :appealed :final})

(fact "terminal states"
  terminal-states => #{:answered :canceled :closed :final :extinct :registered :acknowledged :foremanVerdictGiven})
