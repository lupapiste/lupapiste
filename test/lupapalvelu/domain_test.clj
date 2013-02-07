(ns lupapalvelu.domain-test
  (:use lupapalvelu.domain
        clojure.test
        midje.sweet))

(facts
  (let [application {:roles {:role-x {:id :user-x} :role-y {:id :user-y}}}]
    (fact (role-in-application application :user-x) => :role-x)
    (fact (role-in-application application :user-y) => :role-y)
    (fact (role-in-application application :user-z) => nil)
    (fact (role-in-application application nil) => nil)

    (fact (has-role? application :user-x) => true)
    (fact (has-role? application :user-z) => false)))

(facts
  (let [application {:auth [{:id :user-x} {:id :user-y}]}]
    (fact (has-auth? application :user-x) => true)
    (fact (has-auth? application :user-z) => false)))

(facts
  (let [application {:documents [{:id 1 :data "jee"} {:id 2 :data "juu"} {:id 1 :data "hidden"}]}]
    (fact (get-document-by-id application 1) => {:id 1 :data "jee"})
    (fact (get-document-by-id application 2) => {:id 2 :data "juu"})
    (fact (get-document-by-id application -1) => nil)))

(facts
  (let [application {:documents [{:id 1 :data "jee" :schema {:info {:name "kukka"}}}]}]
    (fact (get-document-by-name application "kukka") => {:id 1 :data "jee" :schema {:info {:name "kukka"}}})
    (fact (get-document-by-name application "") => nil)))

(facts
  (fact (invited? {:invites [{:user {:username "mikko@example.com"}}]} "mikko@example.com") => true)
  (fact (invited? {:invites []} "mikko@example.com") => false))
