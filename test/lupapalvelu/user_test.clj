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
  (user-query {:id "x" :username "UserName" :email "Email@AddreSS.FI" :foo "BoZo"}) => {:_id "x" :username "username" :email "email@address.fi" :foo "BoZo"}
  (user-query {:organization "x"}) => {:organizations "x"})

;;
;; jQuery data-tables:
;;

(testable-privates lupapalvelu.user users-for-datatables-base-query)

(facts users-for-datatables-base-query
  (fact (users-for-datatables-base-query {:role :admin} {})                          => {})
  (fact (users-for-datatables-base-query {:role :admin} {:organizations ["a" "b"]})  => {:organizations {$in ["a" "b"]}})
  (fact (users-for-datatables-base-query {:role :admin} {:organizations ["a" "b"]})  => {:organizations {$in ["a" "b"]}})
  
  (fact (users-for-datatables-base-query {:organizations ["a" "b"]} {:organizations ["a"]})           => (contains {:organizations {$in ["a"]}}))
  (fact (users-for-datatables-base-query {:organizations ["a" "b"]} {:organizations ["b"]})           => (contains {:organizations {$in ["b"]}}))
  (fact (users-for-datatables-base-query {:organizations ["a" "b"]} {:organizations ["a" "b"]})       => (contains {:organizations {$in ["a" "b"]}}))
  (fact (users-for-datatables-base-query {:organizations ["a" "b"]} {:organizations ["a" "b" "c"]})   => (contains {:organizations {$in ["a" "b"]}}))
  (fact (users-for-datatables-base-query {:organizations ["a" "b"]} {:organizations ["c"]})           => (contains {:organizations {$in []}}))
  (fact (users-for-datatables-base-query {:organizations ["a" "b"]} {:organizations []})              => (contains {:organizations {$in []}}))
  (fact (users-for-datatables-base-query {:organizations ["a" "b"]} {:organizations nil})             => (contains {:organizations {$in ["a" "b"]}}))
  (fact (users-for-datatables-base-query {:organizations ["a" "b"]} {})                               => (contains {:organizations {$in ["a" "b"]}}))
  (fact (users-for-datatables-base-query {} {})                                                       => (contains {:organizations {$in []}})))

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

