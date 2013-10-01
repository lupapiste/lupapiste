(ns lupapalvelu.user-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.user :refer :all]
            [slingshot.slingshot :refer [try+]]))

(facts
  (applicationpage-for "applicant")      => "applicant"
  (applicationpage-for "authority")      => "authority"
  (applicationpage-for "authorityAdmin") => "authority-admin"
  (applicationpage-for "admin")          => "admin")

(facts "non-private"
  (fact "strips away private keys from map"
    (non-private {:name "tommi" :private {:secret "1234"}}) => {:name "tommi"})
  (fact ".. but not non-recursively"
    (non-private {:name "tommi" :child {:private {:secret "1234"}}}) => {:name "tommi" :child {:private {:secret "1234"}}}))

(let [user {:id "1"
            :firstName "Simo"
            :username  "simo@salminen.com"
            :lastName "Salminen"
            :role "comedian"
            :private "SECRET"}]
  (facts "summary"
    (fact (summary nil) => nil)
    (fact (summary user) => (just (dissoc user :private)))))

(facts "roles"
  (fact "authority"
    (authority? {:role "authority"}) => true
    (authority? {:role :authority}) => true
    (authority? {}) => false)
  (fact "applicant"
    (applicant? {:role "applicant"}) => true
    (applicant? {:role :applicant}) => true
    (applicant? {}) => false))

(fact "is a map with all the data"
  (create-user-entity {:id             ..id..
                       :email          "Foo@Bar.Com"
                       :password       "some-password"
                       :personId       ..userid..
                       :role           ..role..
                       :firstName      ..firstname..
                       :lastName       ..lastname..
                       :phone          ..phone..
                       :city           ..city..
                       :street         ..street..
                       :zip            ..zip..
                       :enabled        ..enabled..
                       :organizations  ..organizations..})
    => (contains {:id           ..id..
                  :email        "foo@bar.com"
                  :personId     ..userid..
                  :role         ..role..
                  :firstName    ..firstname..
                  :lastName     ..lastname..
                  :phone        ..phone..
                  :city         ..city..
                  :street       ..street..
                  :zip          ..zip..
                  :enabled      ..enabled..}))

(fact "does not contain plaintext password"
  (let [entity   (create-user-entity {:password  "some-password"
                                      :id        ..id..
                                      :email     ..email..
                                      :role      ..role..})
        password (get-in entity [:private :password])]
    password => truthy
    (.contains password "some-password") => false
    (.contains (str entity) "some-password") => false))

;; FIXME: fix after refactoring
#_(fact "applicant does not have organizations"
    (:organizations
      (create-user-entity ..email.. some-password ..userid.. :applicant ..firstname.. ..lastname.. ..phone.. ..city.. ..street.. ..zip.. ..enabled.. ..organizations..))
    => nil)

(fact "authority does have organizations"
  (create-user-entity {:id             ..id..
                       :email          ..email..
                       :role           ..role..
                       :organizations  ..organizations..})
    => (contains {:organizations ..organizations..}))

(facts "same-user?"
  (same-user? {:id "123"} {:id "123"}) => true
  (same-user? {:id "123"} {:id "234"}) => false)

(facts with-user-by-email
  (with-user-by-email "admin@solita.fi" user) => (contains {:id "777777777777777777000099" :email "admin@solita.fi"})
  (with-user-by-email "nobody") => (throws clojure.lang.ExceptionInfo #"error\.user-not-found"))
