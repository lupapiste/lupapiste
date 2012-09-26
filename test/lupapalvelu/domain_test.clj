(ns lupapalvelu.domain-test
  (:use lupapalvelu.domain
        clojure.test
        midje.sweet))

(def application
  {:roles {:role-x {:userId :user-x}
           :role-y {:userId :user-y}}})

(facts
  (fact (role-in-application :user-x application) => :role-x)
  (fact (role-in-application :user-y application) => :role-y)
  (fact (role-in-application :user-z application) => nil)
  (fact (role-in-application nil application) => nil))
