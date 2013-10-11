(ns lupapalvelu.user-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
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

(testable-privates lupapalvelu.user user-query)

(facts user-query
  (user-query "hello")          => (throws AssertionError)
  (user-query nil)              => (throws AssertionError)
  (user-query {})               => {}
  (user-query {:id "x"})        => {:_id "x"}
  (user-query {:email "x"})     => {:email "x"}
  (user-query {:email "XyZq"})  => {:email "xyzq"}
  (user-query {:id "x" :username "UserName" :email "Email@AddreSS.FI" :foo "BoZo"}) => {:_id "x" :username "username" :email "email@address.fi" :foo "BoZo"})

;;
;; ==============================================================================
;; Getting non-private user data:
;; ==============================================================================
;;

(facts get-user-by-id
  (get-user-by-id ..id..) => {:id ..id.. :email ..email..}
    (provided (mongo/select-one :users {:_id ..id..}) => {:id ..id.. :email ..email.. :private ..private..}))

(facts get-user-by-email
  (get-user-by-email "email") => {:id ..id.. :email "email"}
    (provided (mongo/select-one :users {:email "email"}) => {:id ..id.. :email "email" :private ..private..}))

(facts get-user-with-password
  
  (fact happy-case
    (get-user-with-password "username" "password") => {:id ..id.. :enabled true}
      (provided (mongo/select-one :users {:username "username"}) => {:id ..id.. :enabled true :private {:password "from-db"}}
                (security/check-password "password" "from-db") => true))
    
  (fact wrong-password
    (get-user-with-password "username" "password") => nil
      (provided (mongo/select-one :users {:username "username"}) => {:id ..id.. :enabled true :private {:password "from-db"}}
                (security/check-password "password" "from-db") => false))
  
  (fact disabled-user
    (get-user-with-password "username" "password") => nil
      (provided (mongo/select-one :users {:username "username"}) => {:id ..id.. :enabled false :private {:password "from-db"}}))
  
  (fact unknown-user
    (get-user-with-password "username" "password") => nil
      (provided (mongo/select-one :users {:username "username"}) => nil)))

(facts with-user-by-email
  (fact
    (with-user-by-email "email" user) => (contains {:id "123" :email "email"})
      (provided (get-user-by-email "email") => {:id "123" :email "email"}))

  (fact
    (with-user-by-email ..email..) => (throws clojure.lang.ExceptionInfo #"error\.user-not-found")
      (provided (get-user-by-email ..email..) => nil)))

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

(facts create-user-entity
  
  (fact "can't create with nil"
    (create-user-entity nil) => (throws Exception))
  
  (fact "need both :email and :id"
    (create-user-entity {:email "foo"}) => (throws Exception)
    (create-user-entity {:id "foo"}) => (throws Exception)
    (create-user-entity {:email "foo" :id "bar"}) => (contains {:email "foo" :id "bar"}))
  
  (fact "default values"
    
    (create-user-entity {:email "foo"
                         :id    ..id..})
      => (contains {:email     "foo"
                    :id        ..id..
                    :firstName ""
                    :lastName  ""
                    :enabled   false
                    :role      :dummy})
    
    (create-user-entity {:email     "foo"
                         :id        ..id..
                         :firstName ..firstName..
                         :lastName  ..lastName..
                         :enabled   ..enabled..
                         :role      ..role..})
      => (contains {:email     "foo"
                    :id        ..id..
                    :firstName ..firstName..
                    :lastName  ..lastName..
                    :enabled   ..enabled..
                    :role      ..role..}))
  
  (fact "email is converted to lowercase"
    (create-user-entity {:id  ..id.. :email "Foo@Bar.Com"}) => (contains {:email "foo@bar.com"}))

  (fact "does not contain plaintext password"
    (let [entity   (create-user-entity {:password  "some-password"
                                        :id        ..id..
                                        :email     "email"
                                        :role      ..role..})
          password (get-in entity [:private :password])]
      password     =>      truthy
      password     =not=>  #"some-password"
      (str entity) =not=>  #"some-password")))
