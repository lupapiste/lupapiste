(ns lupapalvelu.domain-test
  (:use lupapalvelu.domain
        clojure.test
        midje.sweet))

(facts
  (let [application {:roles {:role-x {:id :user-x} :role-y {:id :user-y}}}]
    (fact (role-in-application :user-x application) => :role-x)
    (fact (role-in-application :user-y application) => :role-y)
    (fact (role-in-application :user-z application) => nil)
    (fact (role-in-application nil application) => nil)))

(facts
  (let [application {:documents [{:id 1 :data "jee"} {:id 2 :data "juu"} {:id 1 :data "hidden"}]}]
    (fact (get-document application 1) => {:id 1 :data "jee"})
    (fact (get-document application 2) => {:id 2 :data "juu"})))
