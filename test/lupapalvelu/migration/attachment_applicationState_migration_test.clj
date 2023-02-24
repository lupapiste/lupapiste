(ns lupapalvelu.migration.attachment-applicationState-migration-test
  (:require [lupapalvelu.migration.migrations :refer :all]
            [midje.util :refer [testable-privates]]
            [midje.sweet :refer :all]))

(testable-privates lupapalvelu.migration.migrations attachments-with-applicationState)

(defn- get-applicationState [application] 
  (-> application attachments-with-applicationState first :applicationState))

(defn- get-applicationStates [application] 
  (map :applicationState (attachments-with-applicationState application)))

(defn- attachment [version-created] 
  (if version-created {:modified (+ version-created 5) :versions [{:created version-created}]} 
    {:modified 10}))

(defn- verdicts [given] 
  (if given [{:timestamp given}] []))

(defn- application 
  ([state] (application state nil nil))
  ([state version-created] (application state version-created nil))
  ([state version-created verdict-given]
    {:state state :attachments [(attachment version-created)] :verdicts (verdicts verdict-given)}))

(fact "Draft" 
  (get-applicationState (application :draft)) => :draft
  (get-applicationState (application :draft 11)) => :draft)

(fact "Open" 
  (get-applicationState (application :open)) => :open
  (get-applicationState (application :open 11)) => :open)

(fact "Submitted" 
  (get-applicationState (application :submitted)) => :submitted
  (get-applicationState (application :submitted 11)) => :submitted)

(fact "Sent" 
  (get-applicationState (application :sent)) => :submitted
  (get-applicationState (application :sent 11)) => :submitted)

(fact "Complement-needed" 
  (get-applicationState (application :complementNeeded)) => :submitted
  (get-applicationState (application :complementNeeded 11)) => :submitted)

(fact "Verdict given - post verdict" 
  (get-applicationState (application :verdictGiven nil 9)) => :verdictGiven
  (get-applicationState (application :verdictGiven 11 9)) => :verdictGiven)

(fact "Verdict given - pre verdict" 
  (get-applicationState (application :verdictGiven nil 11)) => :submitted
  (get-applicationState (application :verdictGiven 9 11)) => :submitted)

(fact "Construction started - post verdict" 
  (get-applicationState (application :constructionStarted nil 9)) => :verdictGiven
  (get-applicationState (application :constructionStarted 11 9)) => :verdictGiven)

(fact "Construction started - pre verdict" 
  (get-applicationState (application :constructionStarted nil 11)) => :submitted
  (get-applicationState (application :constructionStarted 9 11)) => :submitted)

(fact "Closed - post verdict" 
  (get-applicationState (application :closed nil 9)) => :verdictGiven
  (get-applicationState (application :closed 11 9)) => :verdictGiven)

(fact "Closed - pre verdict" 
  (get-applicationState (application :closed nil 11)) => :submitted
  (get-applicationState (application :closed 9 11)) => :submitted)

(facts "Pre and post verdict attachments"
  (let [application {:state :verdictGiven
                     :attachments [(attachment nil) (attachment 12)] 
                     :verdicts (verdicts 11)}
        applicationStates (get-applicationStates application)]
    (first applicationStates) => :submitted
    (second applicationStates) => :verdictGiven))
  
