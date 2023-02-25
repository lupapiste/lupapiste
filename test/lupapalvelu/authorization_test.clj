(ns lupapalvelu.authorization-test
  (:require [lupapalvelu.authorization :refer :all]
            [lupapalvelu.document.tools :as doc-tools]
            [midje.sweet :refer :all]))

(facts
  (let [application {:auth [{:id :user-x} {:id :user-y}]}]
    (fact (has-auth? application :user-x) => true)
    (fact (has-auth? application :user-z) => false)))

(facts get-auths-by-role
  (let [owner   {:id 1 :role "owner"}
        writer1 {:id 2 :role "writer"}
        writer2 {:id 3 :role "writer"}
        app     {:auth [owner writer1 writer2]}]
    (fact "get owner"   (get-auths-by-role app :owner)  => (just owner))
    (fact "get writers" (get-auths-by-role app :writer) => (just writer1 writer2))))

(facts has-auths-role?
  (let [app     {:auth [{:id 1 :role "owner"}
                        {:id 2 :role "writer"}]}]
    (fact "'1' is owner" (has-auth-role? app 1 :owner) => true)
    (fact "'2' is not owner" (has-auth-role? app 2 :owner) => false)
    (fact "'2' is writer" (has-auth-role? app 2 :writer) => true)
    (fact "'6' has not role" (has-auth-role? app 6 :writer) => false)))

(facts has-some-auth-role?
  (let [app     {:auth [{:id 1 :role "owner"}
                        {:id 2 :role "writer"}
                        {:id 3 :role "fooer"}
                        {:id 4 :role "buster"}
                        {:id 5 :role "writer"}]}]
    (fact "'1' has required role" (has-some-auth-role? app 1 [:owner :fooer])  => true)
    (fact "'2' has not role"      (has-some-auth-role? app 2 [:owner :fooer])  => false)
    (fact "'3' is fooer"          (has-some-auth-role? app 3 [:owner :fooer])  => true)
    (fact "'6' has not role"      (has-some-auth-role? app 6 [:owner :fooer])  => false)
    (fact "'2' has required role" (has-some-auth-role? app 2 [:writer])   => true)
    (fact "'5' has required role" (has-some-auth-role? app 5 [:writer])  => true)))

(facts enrich-auth-information
  (fact "handler authority"
    (let [app {:auth      [{:id "userid-1" :role "owner"}]
               :documents []
               :authority {:id "userid-1"}}]
      (enrich-auth-information app) => [{:id "userid-1" :role "owner" :party-roles [] :other-roles [:authority]}]))

  (fact "handler authority with party-role"
    (let [app {:auth      [{:id "userid-1" :role "owner"}]
               :documents [{:userid "userid-1" :type :some-doc}
                           {:userid "userid-1" :type :clown}
                           {:userid "userid-2" :type :some-doc}]
               :authority {:id "userid-1"}}]

      (enrich-auth-information app) => [{:id "userid-1" :role "owner" :party-roles [:clown] :other-roles [:authority]}]

      (provided (doc-tools/doc-type (as-checker (comp #{:clown :batman :robin} :type))) => :party)
      (provided (doc-tools/doc-type anything) => nil)
      (provided (doc-tools/party-doc-user-id (as-checker (comp #{"userid-1"} :userid))) => "userid-1")
      (provided (doc-tools/party-doc->user-role (as-checker (comp #{:clown} :type))) => :clown)))

  (fact "handler authority with party-role"
    (let [app {:auth      [{:id "userid-1" :role "owner"}]
               :documents [{:userid "userid-1" :type :some-doc}
                           {:userid "userid-1" :type :clown}
                           {:userid "userid-2" :type :some-doc}]
               :authority {:id "userid-1"}}]

      (enrich-auth-information app) => [{:id "userid-1" :role "owner" :party-roles [:clown] :other-roles [:authority]}]

      (provided (doc-tools/doc-type (as-checker (comp #{:clown :batman :robin} :type))) => :party)
      (provided (doc-tools/doc-type anything) => nil)
      (provided (doc-tools/party-doc-user-id (as-checker (comp #{"userid-1"} :userid))) => "userid-1")
      (provided (doc-tools/party-doc->user-role (as-checker (comp #{:clown} :type))) => :clown)))

  (fact "multiple party-roles"
    (let [app {:auth      [{:id "userid-1" :role "owner"}
                           {:id "userid-2" :role "writer"}]
               :documents [{:userid "userid-1" :type :batman}
                           {:userid "userid-1" :type :clown}
                           {:userid "userid-2" :type :robin}]
               :authority {}}]

      (enrich-auth-information app) => [{:id "userid-1" :role "owner" :party-roles [:batman :clown]}
                                        {:id "userid-2" :role "writer" :party-roles [:robin]}]

      (provided (doc-tools/doc-type (as-checker (comp #{:clown :batman :robin} :type))) => :party)
      (provided (doc-tools/party-doc-user-id (as-checker (comp #{"userid-1"} :userid))) => "userid-1")
      (provided (doc-tools/party-doc-user-id (as-checker (comp #{"userid-2"} :userid))) => "userid-2")
      (provided (doc-tools/party-doc->user-role (as-checker (comp #{:clown} :type))) => :clown)
      (provided (doc-tools/party-doc->user-role (as-checker (comp #{:batman} :type))) => :batman)
      (provided (doc-tools/party-doc->user-role (as-checker (comp #{:robin} :type))) => :robin)))

  (fact "multiple party-roles and multiple auths"
    (let [app {:auth      [{:id "userid-1" :role "owner"}
                           {:id "userid-2" :role "writer"}
                           {:id "userid-2" :role "statementGiver"}
                           {:id "userid-3" :role "reader"}]
               :documents [{:userid "userid-1" :type :batman}
                           {:userid "userid-1" :type :clown}
                           {:userid "userid-2" :type :robin}]
               :authority {}}]

      (enrich-auth-information app) => [{:id "userid-1" :role "owner" :party-roles [:batman :clown]}
                                        {:id "userid-2" :role "writer" :party-roles [:robin]}
                                        {:id "userid-2" :role "statementGiver" :party-roles [:robin]}
                                        {:id "userid-3" :role "reader" :party-roles []}]

      (provided (doc-tools/doc-type (as-checker (comp #{:clown :batman :robin} :type))) => :party)
      (provided (doc-tools/party-doc-user-id (as-checker (comp #{"userid-1"} :userid))) => "userid-1")
      (provided (doc-tools/party-doc-user-id (as-checker (comp #{"userid-2"} :userid))) => "userid-2")
      (provided (doc-tools/party-doc->user-role (as-checker (comp #{:clown} :type))) => :clown)
      (provided (doc-tools/party-doc->user-role (as-checker (comp #{:batman} :type))) => :batman)
      (provided (doc-tools/party-doc->user-role (as-checker (comp #{:robin} :type))) => :robin))))
