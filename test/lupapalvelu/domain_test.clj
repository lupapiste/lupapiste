(ns lupapalvelu.domain-test
  (:require [lupapalvelu.domain :refer :all]
            [lupapalvelu.document.schemas :as schemas]
            [clojure.test :refer :all]
            [midje.sweet :refer :all]))

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
  (let [application {:documents [{:id 1 :data "jee" :schema-info {:name "kukka"}}]}]
    (fact (get-document-by-name application "kukka") => {:id 1 :data "jee" :schema-info {:name "kukka"}})
    (fact (get-document-by-name application "") => nil)))

(facts "invites"
  (let [invite1 {:email "abba@example.com"}
        invite2 {:email "kiss@example.com"}
        app     {:auth [{:role "writer" :invite invite1}
                        {:role "writer" :invite invite2}
                        {:role "owner"}]}]
    (fact "has two invites" (invites app) => (just invite1 invite2))
    (fact "abba@example.com has one invite" (invite app "abba@example.com") => invite1)
    (fact "jabba@example.com has no invite" (invite app "jabba@example.com") => nil)))

(facts
  (let [owner   {:id 1 :role "owner"}
        writer1 {:id 2 :role "writer"}
        writer2 {:id 3 :role "writer"}
        app     {:auth [owner writer1 writer2]}]
    (fact "get owner"   (get-auths-by-role app :owner)  => (just owner))
    (fact "get writers" (get-auths-by-role app :writer) => (just writer1 writer2))
    (fact "'1' is owner" (has-auth-role? app 1 :owner) => true)
    (fact "'2' is not owner" (has-auth-role? app 2 :owner) => false)))

