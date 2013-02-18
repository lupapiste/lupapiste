(ns lupapalvelu.domain-test
  (:use lupapalvelu.domain
        clojure.test
        midje.sweet))

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

(facts "invites"
  (let [invite1 {:id 1}
        invite2 {:id 2}
        app     {:auth [{:id 1 :role "owner"}
                        {:id 2 :role "writer" :invite invite1}
                        {:id 3 :role "writer" :invite invite2}]}]
    (fact "has two invites" (invites app) => (just invite1 invite2))))

(facts
  (fact (invited? {:invites [{:user {:username "mikko@example.com"}}]} "mikko@example.com") => true)
  (fact (invited? {:invites []} "mikko@example.com") => false))

(facts
  (let [owner   {:id 1 :role "owner"}
        writer1 {:id 2 :role "writer"}
        writer2 {:id 3 :role "writer"}
        app     {:auth [owner writer1 writer2]}]
  (fact "get owner"   (get-auths-by-role app :owner)  => (just owner))
  (fact "get writers" (get-auths-by-role app :writer) => (just writer1 writer2))))

(facts
  (let [user {:id        "123"
              :firstName "kari"
              :lastName  "tapio"
              :email     "kari.tapio@example.com"
              :phone     "050"
              :street    "katu"
              :zip       "123"
              :city      "tampere"}]
    (fact (user2henkilo user) => {:userId "123"
                                  :henkilotiedot {:etunimi "kari"
                                                  :sukunimi "tapio"}
                                  :yhteystiedot {:email "kari.tapio@example.com"
                                                 :puhelin "050"}
                                  :osoite {:katu "katu"
                                           :postinumero "123"
                                           :postitoimipaikannimi "tampere"}})))
