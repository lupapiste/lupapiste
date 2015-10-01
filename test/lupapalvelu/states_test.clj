(ns lupapalvelu.states-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.states :refer :all]))

(fact "all-next-states"
  (all-next-states default-application-state-graph :verdictGiven) => #{:verdictGiven :constructionStarted :closed :canceled})

(fact "post-submitted-states"
  post-submitted-states => (contains #{:sent :complement-needed :verdictGiven :constructionStarted :closed})
  post-submitted-states =not=> (contains #{:open :submitted}))

(fact "post-verdict-states"
  post-verdict-states =not=> (contains #{:sent :complement-needed}))

(fact "all states"
  all-inforequest-states => #{:info :answered}

  all-application-states => #{:draft :open :submitted :sent :complement-needed
                              :verdictGiven :constructionStarted :closed :canceled
                              :extinct
                              :hearing :proposal :proposalApproved
                              :survey :sessionProposal :sessionHeld :registered
                              :appealed :final})

(fact "terminal states"
  terminal-states => #{:canceled :closed :final :extinct :registered})
