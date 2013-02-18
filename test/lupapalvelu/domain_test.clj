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

(facts
  (let [mikko-auth {:id "777777777777777777000010"
                    :username "mikko@example.com"
                    :firstName "Mikko"
                    :lastName "Intonen"
                    :role "owner"}
        teppo-auth {:id "5073c0a1c2e6c470aef589a5"
                    :username "teppo@example.com"
                    :firstName "Teppo"
                    :lastName "Nieminen"
                    :role "writer"}
        pena-auth  {:id "123"
                    :username "pena@example.com"
                    :firstName "Pena"
                    :lastName "Pena"
                    :role "writer"}
        auth       [mikko-auth teppo-auth pena-auth]]
  (fact "get owner" (get-auths-by-role {:auth auth} :owner) => (just mikko-auth))
  (fact "get writers" (get-auths-by-role {:auth auth} :writer) => (just teppo-auth pena-auth))))

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
