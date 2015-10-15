(ns lupapalvelu.document.canonical-common-test
  (:require [lupapalvelu.document.canonical-common :refer :all]
            [midje.sweet :refer :all]))

(facts "timestamps"
  (let [day (* 24 60 60 1000)
        app (-> lupapalvelu.domain/application-skeleton
              (assoc
                :created 0
                :opened day
                :complementNeeded (* 2 day)
                :submitted (* 3 day)
                :sent (* 4 day)
                :started (* 5 day)
                :closed (* 6 day))
              (update-in [:verdicts] conj {:timestamp (* 7 day)}))]

   (fact "complement-needed" (state-timestamp (assoc app :state "complement-needed")) => (* 2 day))
   (fact "submitted" (state-timestamp (assoc app :state "submitted")) => (* 3 day))
   (fact "sent" (state-timestamp (assoc app :state "sent")) => (* 3 day))
   (fact "constructionStarted" (state-timestamp (assoc app :state "constructionStarted")) => (* 5 day))
   (fact "closed" (state-timestamp (assoc app :state "closed")) => (* 6 day))
   (fact "verdictGiven" (state-timestamp (assoc app :state "verdictGiven")) => (* 7 day))

   (all-state-timestamps app) => {:complement-needed (* 2 day)
                                  :submitted (* 3 day)
                                  :sent (* 3 day)
                                  :constructionStarted (* 5 day)
                                  :closed (* 6 day)
                                  :verdictGiven (* 7 day)
                                  :foremanVerdictGiven (* 7 day)}

   (all-state-timestamps (dissoc app :closed :verdicts :started :complementNeeded)) => {:complement-needed nil
                                                                                        :submitted (* 3 day)
                                                                                        :sent (* 3 day)
                                                                                        :constructionStarted nil
                                                                                        :closed nil
                                                                                        :verdictGiven nil
                                                                                        :foremanVerdictGiven nil}

   ))
