(ns lupapalvelu.user-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.user :refer :all]))

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

;; Metaconstant cannot be cast to java.lang.String
(def ^:private some-password "p4sswodr")

(facts "user entity mongo model"
  (fact "is a map with all the data"
     (create-user-entity "Foo@Bar.Com" some-password ..userid.. ..role.. ..firstname.. ..lastname.. ..phone.. ..city.. ..street.. ..zip.. ..enabled.. ..organizations..)
     => (contains {:email        "foo@bar.com"
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
     (let [entity   (create-user-entity ..email.. some-password ..userid.. ..role.. ..firstname.. ..lastname.. ..phone.. ..city.. ..street.. ..zip.. ..enabled.. ..organizations..)
           password (get-in entity [:private :password])]
       password => truthy
       (.contains password some-password) => false
       (.contains (str entity) some-password) => false))

  ;; FIXME: fix after refactoring
  #_(fact "applicant does not have organizations"
     (:organizations
       (create-user-entity ..email.. some-password ..userid.. :applicant ..firstname.. ..lastname.. ..phone.. ..city.. ..street.. ..zip.. ..enabled.. ..organizations..))
     => nil)

  (fact "authority does have organizations"
     (create-user-entity ..email.. some-password ..userid.. :authority ..firstname.. ..lastname.. ..phone.. ..city.. ..street.. ..zip.. ..enabled.. ..organizations..)
     => (contains {:organizations ..organizations..}))

  (fact "authorityAdmin does have organizations"
     (create-user-entity ..email.. some-password ..userid.. "authorityAdmin" ..firstname.. ..lastname.. ..phone.. ..city.. ..street.. ..zip.. ..enabled.. ..organizations..)
     => (contains {:organizations ..organizations..})))

(facts "same-user?"
  (same-user? {:id "123"} {:id "123"}) => true
  (same-user? {:id "123"} {:id "234"}) => false)
