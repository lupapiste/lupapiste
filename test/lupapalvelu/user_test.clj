(ns lupapalvelu.user-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.user :refer :all]
            [slingshot.slingshot :refer [try+]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]))

;;
;; ==============================================================================
;; Utils:
;; ==============================================================================
;;

(facts non-private
  (fact "strips away private keys from map"
    (non-private {:name "tommi" :private {:secret "1234"}}) => {:name "tommi"})
  (fact ".. but not non-recursively"
    (non-private {:name "tommi" :child {:private {:secret "1234"}}}) => {:name "tommi" :child {:private {:secret "1234"}}}))

(facts summary
  (fact (summary nil) => nil)
  (let [user {:id "1"
              :firstName "Simo"
              :username  "simo@salminen.com"
              :lastName "Salminen"
              :role "comedian"
              :private "SECRET"}]
    (fact (summary user) => (just (dissoc user :private)))))

(fact authority?
  (authority? {:role "authority"})  => truthy
  (authority? {:role :authority})   => truthy
  (authority? {:role "applicant"})  => falsey
  (authority? {})                   => falsey
  (authority? nil)                  => falsey)

(fact applicant?
  (applicant? {:role "applicant"})  => truthy
  (applicant? {:role :applicant})   => truthy
  (applicant? {:role "authority"})  => falsey
  (applicant? {})                   => falsey
  (applicant? nil)                  => falsey)

(fact same-user?
  (same-user? {:id "foo"} {:id "foo"}) => truthy
  (same-user? {:id "foo"} {:id "bar"}) => falsey)

;;
;; ==============================================================================
;; Finding user data:
;; ==============================================================================
;;

(facts user-query
  (user-query) => (throws AssertionError)
  (user-query :id "x") => {:_id "x"}
  (user-query :email "x") => {:email "x"}
  (user-query :email "XyZq") => {:email "xyzq"}
  (user-query :id "x" :username "UserName" :email "Email@AddreSS.FI" :foo "BoZo") => {:_id "x" :username "username" :email "email@address.fi" :foo "BoZo"})

;;
;; ==============================================================================
;; Getting non-private user data:
;; ==============================================================================
;;

(facts get-user-by-id
  (get-user-by-id ...id...) => {:id ...id... :email ...email...}
    (provided (mongo/select-one :users {:_id ...id...}) => {:id ...id... :email ...email... :private ...private...}))

(facts get-user-by-email
  (get-user-by-email "email") => {:id ...id... :email "email"}
    (provided (mongo/select-one :users {:email "email"}) => {:id ...id... :email "email" :private ...private...}))

(facts get-user-with-password
  
  (fact happy-case
    (get-user-with-password "username" "password") => {:id ...id... :enabled true}
      (provided (mongo/select-one :users {:username "username"}) => {:id ...id... :enabled true :private {:password "from-db"}}
                (security/check-password "password" "from-db") => true))
    
  (fact wrong-password
    (get-user-with-password "username" "password") => nil
      (provided (mongo/select-one :users {:username "username"}) => {:id ...id... :enabled true :private {:password "from-db"}}
                (security/check-password "password" "from-db") => false))
  
  (fact disabled-user
    (get-user-with-password "username" "password") => nil
      (provided (mongo/select-one :users {:username "username"}) => {:id ...id... :enabled false :private {:password "from-db"}}))
  
  (fact unknown-user
    (get-user-with-password "username" "password") => nil
      (provided (mongo/select-one :users {:username "username"}) => nil)))

;;
;; ==============================================================================
;; User role:
;; ==============================================================================
;;

(facts
  (applicationpage-for "applicant")      => "applicant"
  (applicationpage-for "authority")      => "authority"
  (applicationpage-for "authorityAdmin") => "authority-admin"
  (applicationpage-for "admin")          => "admin")

(facts user-in-role
  
  (fact "role is overridden"
    (user-in-role {:id 1 :role :applicant} :reader) => {:id 1 :role :reader})
  
  (fact "takes optional name & value parameter pair"
    (user-in-role {:id 1 :role :applicant} :reader :age 16) => {:id 1 :role :reader :age 16})
  
  (fact "takes optional name & value parameter pairS"
    (user-in-role {:id 1 :role :applicant} :reader :age 16 :size :L) => {:id 1 :role :reader :age 16 :size :L})
  
  (fact "fails with uneven optional parameter pairs"
    (user-in-role {:id 1 :role :applicant} :reader :age) => (throws Exception)))

;;
;; ==============================================================================
;; Creating users:
;; ==============================================================================
;;












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

(fact
  (with-user-by-email "email" user) => (contains {:id "123" :email "email"})
  (provided (get-user-by-email "email") => {:id "123" :email "email"}))

(fact
  (with-user-by-email ...email...) => (throws clojure.lang.ExceptionInfo #"error\.user-not-found")
  (provided (get-user-by-email ...email...) => nil))
