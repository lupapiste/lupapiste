(ns lupapalvelu.states-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.states :refer :all]))

(fact "initial-state"
  (initial-state default-application-state-graph) => :draft
  (initial-state tj-hakemus-state-graph) => :draft
  (initial-state tj-ilmoitus-state-graph) => :draft
  (initial-state default-inforequest-state-graph) => :info)

(fact "all-next-states"
  (all-next-states full-application-state-graph :verdictGiven) => #{:verdictGiven :constructionStarted :closed :canceled :extinct :inUse :onHold :appealed}
  (all-next-states full-application-state-graph :unknown) => empty?
  (facts "YA"
    (all-next-states ya-kayttolupa-state-graph :verdictGiven) => #{:verdictGiven :canceled :extinct :finished :appealed}
    (all-next-states ya-tyolupa-state-graph :verdictGiven) => #{:verdictGiven :constructionStarted :closed :canceled :extinct :appealed}
    (all-next-states ya-sijoituslupa-state-graph :verdictGiven) => #{:verdictGiven :extinct :finished :appealed}
    (all-next-states ya-sijoitussopimus-state-graph :agreementPrepared) => #{:agreementPrepared :agreementSigned :canceled}
    (all-next-states ya-sijoitussopimus-state-graph :verdictGiven) => empty?))

(fact "post-submitted-states"
  post-submitted-states => (contains #{:submitted :sent :complementNeeded :verdictGiven :constructionStarted :closed})
  post-submitted-states =not=> (contains #{:open :draft}))

(fact "post-sent-states"
  post-sent-states => (contains #{:sent :complementNeeded :verdictGiven :constructionStarted :closed})
  post-sent-states =not=> (contains #{:open :submitted}))

(fact "post-verdict-states"
  post-verdict-states => (contains #{:verdictGiven :foremanVerdictGiven :constructionStarted :closed :acknowledged})
  post-verdict-states =not=> (contains #{:sent :complementNeeded :canceled :submitted}))

(fact "all states"
  all-inforequest-states => #{:info :answered}

  all-application-states => #{:draft :open :submitted :sent :complementNeeded
                              :verdictGiven :constructionStarted :closed :canceled
                              :extinct :inUse :onHold :finished
                              :acknowledged :foremanVerdictGiven
                              :hearing :proposal :proposalApproved
                              :survey :sessionProposal :sessionHeld :registered
                              :agreementSigned :agreementPrepared ; YA sijoitussopimus
                              :appealed :final :ready})

(fact "terminal states"
  terminal-states => #{:answered :canceled :closed :final :extinct :registered :acknowledged :agreementSigned :finished :archived})
