(ns lupapalvelu.user-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [lupapalvelu.user :refer :all]
            [slingshot.slingshot :refer [try+]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [schema.core :as sc]))

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

(fact "virtual-user?"
  (virtual-user? {:role "authority"})  => false
  (virtual-user? {:role :authorityAdmin})   => false
  (virtual-user? {:role "oirAuthority"}) => true
  (virtual-user? {:role :oirAuthority}) => true
  (virtual-user? {:role "applicant"})  => false
  (virtual-user? {:role "admin"})  => false
  (virtual-user? {:role "authorityAdmin"})  => false
  (virtual-user? {})                   => false
  (virtual-user? nil)                  => false)

(fact authority?
  (authority? {:role "authority"})  => truthy
  (authority? {:role :authority})   => truthy
  (authority? {:role :oirAuthority}) => truthy
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
  (user-query {:organization "x"}) => {"orgAuthz.x" {$exists true}})

;;
;; jQuery data-tables:
;;

(testable-privates lupapalvelu.user users-for-datatables-base-query)

(facts users-for-datatables-base-query
  (fact "admin" (users-for-datatables-base-query {:role :admin} {})                          => {})
  (fact "admin" (users-for-datatables-base-query {:role :admin} {:organizations ["a" "b"]})  => {:organizations ["a" "b"]})
  (fact "admin" (users-for-datatables-base-query {:role :admin} {:organizations ["a" "b"]})  => {:organizations ["a" "b"]})

  (fact (users-for-datatables-base-query {:orgAuthz {:a "some-role", :b "some-role"}} {:organizations ["a"]})           => (contains {:organizations ["a"]}))
  (fact (users-for-datatables-base-query {:orgAuthz {:a "some-role", :b "some-role"}} {:organizations ["b"]})           => (contains {:organizations ["b"]}))
  (fact (users-for-datatables-base-query {:orgAuthz {:a "some-role", :b "some-role"}} {:organizations ["a" "b"]})       => (contains {:organizations ["a" "b"]}))
  (fact (users-for-datatables-base-query {:orgAuthz {:a "some-role", :b "some-role"}} {:organizations ["a" "b" "c"]})   => (contains {:organizations ["a" "b"]}))
  (fact (users-for-datatables-base-query {:orgAuthz {:a "some-role", :b "some-role"}} {:organizations ["c"]})           =not=> (partial contains? :organizations))
  (fact (users-for-datatables-base-query {:orgAuthz {:a "some-role", :b "some-role"}} {:organizations []})              =not=> (partial contains? :organizations))
  (fact (users-for-datatables-base-query {:orgAuthz {:a "some-role", :b "some-role"}} {:organizations nil})             => (contains {:organizations ["a" "b"]}))
  (fact (users-for-datatables-base-query {:orgAuthz {:a "some-role", :b "some-role"}} {})                               => (contains {:organizations ["a" "b"]}))
  (fact (users-for-datatables-base-query {} {})                                                                         =not=> (partial contains? :organizations)))

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

;; ==========================================
;; User schema
;; =========================================



(facts "User validation"
  (fact "user skeleton is valid" (sc/check User user-skeleton) => nil)
  (fact "id should exist"        (sc/check User (dissoc user-skeleton :id)) => {:id 'missing-required-key})
  (fact "required keys should exist"
        (sc/check User (dissoc user-skeleton :username :email :role :enabled :firstName :lastName))
        => {:firstName 'missing-required-key
            :lastName 'missing-required-key
            :email 'missing-required-key
            :role 'missing-required-key
            :enabled 'missing-required-key
            :username 'missing-required-key})
  (fact "unknown key is not valid"        (sc/check User (assoc user-skeleton :unknown "nope")) => {:unknown 'disallowed-key})

  (fact "invalid email is not valid" (-> (sc/check User (assoc user-skeleton :email "invalid")) :email) =not=> nil)
  (fact "role is enumerated" (-> (sc/check User (assoc user-skeleton :role "wrong")) :role) =not=> nil)
  (fact "role applicant is allowed" (sc/check User (assoc user-skeleton :role "applicant")) => nil)

  (fact "enabled can only be bool" (and
                                     (nil? (sc/check User (assoc user-skeleton :enabled false))) ; true
                                     (:enabled (sc/check User (assoc user-skeleton :enabled "false")))) => truthy) ; false
  (fact "only valid finnish zips" (and
                                    (nil? (sc/check User (assoc user-skeleton :zip "33100"))) ; true
                                    (:zip (sc/check User (assoc user-skeleton :zip 33100)))
                                    (:zip (sc/check User (assoc user-skeleton :zip "123")))) => truthy)
  (fact "only valid finnish personids" (and
                                         (nil? (sc/check User (assoc user-skeleton :personId "090615-690S"))) ; true
                                         (:personId (sc/check User (assoc user-skeleton :personId "090615-690X")))) => truthy)
  (fact "only valid finnish companyIds" (and
                                          (nil? (sc/check User (assoc user-skeleton :companyId "3856417-8"))) ; true
                                          (:companyId (sc/check User (assoc user-skeleton :companyId "3856417-7")))) => truthy)

  (fact "degree is enumerated"
        (-> (sc/check User (assoc user-skeleton :degree "wrong")) :degree) =not=> nil
        (-> (sc/check User (assoc user-skeleton :degree "arkkitehti")) :degree) => nil
        (-> (sc/check User (assoc user-skeleton :degree "Arkkitehti")) :degree) =not=> nil))

