(ns lupapalvelu.ryhti.datamodel.issue-test
  (:require [clojure.test :refer [deftest is]]
            [lupapalvelu.ryhti.api.mappings :as ryhti-mappings]
            [lupapalvelu.ryhti.datamodel.issue :as issue]
            [lupapalvelu.states :as states]))

(deftest state->rakvalv-asian-elinkaaren-tila-test
  (is (= {(:ennakkoneuvottelu-tai-valmistelu ryhti-mappings/rakvalv-asian-elinkaaren-tila) #{:answered
                                                                                             :draft
                                                                                             :info
                                                                                             :open
                                                                                             :underReview}
          (:vireilla ryhti-mappings/rakvalv-asian-elinkaaren-tila)                         #{:complementNeeded
                                                                                             :hearing
                                                                                             :proposal
                                                                                             :proposalApproved
                                                                                             :registered
                                                                                             :sent
                                                                                             :sessionHeld
                                                                                             :sessionProposal
                                                                                             :submitted
                                                                                             :survey}
          (:paatos-annettu ryhti-mappings/rakvalv-asian-elinkaaren-tila)                   #{:acknowledged
                                                                                             :agreementPrepared
                                                                                             :agreementSigned
                                                                                             :appealed
                                                                                             :archived
                                                                                             :closed
                                                                                             :constructionStarted
                                                                                             :extinct
                                                                                             :final
                                                                                             :finished
                                                                                             :foremanVerdictGiven
                                                                                             :inUse
                                                                                             :onHold
                                                                                             :ready
                                                                                             :verdictGiven}
          (:kasittely-keskeytetty ryhti-mappings/rakvalv-asian-elinkaaren-tila)            #{:canceled}}
         (-> (group-by issue/state->rakvalv-asian-elinkaaren-tila states/all-states)
             (update-vals set)))))
