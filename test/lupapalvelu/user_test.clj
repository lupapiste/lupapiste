(ns lupapalvelu.user-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [schema.core :as sc]
            [slingshot.slingshot :refer [try+]]
            [lupapalvelu.itest-util :refer [expected-failure? unauthorized?]]
            [lupapalvelu.user :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.activation :as activation]))

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

(facts session-summary
  (fact (session-summary nil) => nil)
  (let [user {:id "1"
              :firstName "Simo"
              :username  "simo@salminen.com"
              :lastName "Salminen"
              :role "comedian"
              :private "SECRET"
              :orgAuthz {:753-R ["authority" "approver"]}
              :company {:id "Firma Oy"
                        :role "admin"
                        :submit true}}]
    (fact (:expires (session-summary user)) => number?)
    (fact (-> (session-summary user) :orgAuthz :753-R) => set?)
    (fact (= (summary user) (summary (session-summary user))) => truthy)))

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
;; validate-create-new-user!
;; ==============================================================================
;;

(testable-privates lupapalvelu.user validate-create-new-user!)

(facts "validate-create-new-user!"
  (against-background
    [(mongo/by-id :organizations "o" anything) => {:id "o"}
     (mongo/by-id :organizations "other" anything) => nil
     (mongo/by-id :organizations "q" anything) => {:id "q"}
     (mongo/by-id :organizations "x" anything) => {:id "x"}]

  (fact (validate-create-new-user! nil nil) => (partial expected-failure? :error.missing-parameters))
  (fact (validate-create-new-user! nil {})  => (partial expected-failure? :error.missing-parameters))
  (fact (validate-create-new-user! {:role "admin"} {:role "applicant" :email "x"}) => unauthorized?)

  (fact "only known roles are accepted"
    (validate-create-new-user! {:role "admin"} {:role "x" :email "x"}) => (partial expected-failure? :error.invalid-role))

  (fact (validate-create-new-user! {:role "applicant"}      {:role "authorityAdmin" :email "x"}) => unauthorized?)
  (fact (validate-create-new-user! {:role "authority"}      {:role "authorityAdmin" :email "x"}) => unauthorized?)
  (fact (validate-create-new-user! {:role "authorityAdmin"} {:role "authorityAdmin" :email "x"}) => unauthorized?)
  (fact (validate-create-new-user! {:role "admin"}          {:role "authorityAdmin" :email "x"}) => (partial expected-failure? :error.missing-parameters))

  (fact (validate-create-new-user! {:role "applicant"}      {:role "authority" :email "x"}) => unauthorized?)
  (fact (validate-create-new-user! {:role "authority"}      {:role "authority" :email "x"}) => unauthorized?)
  (fact (validate-create-new-user! {:role "authorityAdmin" :orgAuthz {:o "authorityAdmin"}} {:role "authority" :email "x" :orgAuthz {:o ["authority"]}}) => truthy)
  (fact (validate-create-new-user! {:role "authorityAdmin" :orgAuthz {:o "authorityAdmin"}} {:role "authority" :email "x" :orgAuthz {:q ["authority"]}}) => unauthorized?)
  (fact (validate-create-new-user! {:role "admin"}          {:role "authority" :email "x"}) => unauthorized?)
  (fact (validate-create-new-user! {:role "admin"}          {:role "authorityAdmin" :email "x" :orgAuthz {:o ["authorityAdmin"]}}) => truthy)
  (fact (validate-create-new-user! {:role "admin"}          {:role "authorityAdmin" :email "x" :orgAuthz {:other ["authorityAdmin"]}}) => (partial expected-failure? :error.organization-not-found))

  (fact (validate-create-new-user! {:role "applicant"}      {:role "applicant" :email "x"}) => unauthorized?)
  (fact (validate-create-new-user! {:role "authority"}      {:role "applicant" :email "x"}) => unauthorized?)
  (fact (validate-create-new-user! {:role "authorityAdmin"} {:role "applicant" :email "x"}) => unauthorized?)
  (fact (validate-create-new-user! {:role "admin"}          {:role "applicant" :email "x"}) => unauthorized?)
  (fact (validate-create-new-user! nil                      {:role "applicant" :email "x"}) => truthy)

  (fact (validate-create-new-user! {:role "authorityAdmin" :orgAuthz {:o ["authorityAdmin"]}} {:role "dummy" :email "x" :orgAuthz {:o ["authority"]}}) => unauthorized?)

  (fact "not even admin can create another admin"
    (validate-create-new-user! {:role "admin"} {:role "admin" :email "x"}) => (partial expected-failure? :error.invalid-role))

  (fact "authorityAdmin can create authority users to her own organization only"
    (fact (validate-create-new-user! {:role "authorityAdmin"}                 {:role "authority" :orgAuthz {:x ["authority"]} :email "x"}) => unauthorized?)
    (fact (validate-create-new-user! {:role "authorityAdmin" :orgAuthz nil}   {:role "authority" :orgAuthz {:x ["authority"]} :email "x"}) => unauthorized?)
    (fact (validate-create-new-user! {:role "authorityAdmin" :orgAuthz {}}    {:role "authority" :orgAuthz {:x ["authority"]} :email "x"}) => unauthorized?)
    (fact (validate-create-new-user! {:role "authorityAdmin" :orgAuthz {:y "authorityAdmin"}} {:role "authority" :orgAuthz {:x ["authority"]} :email "x"}) => unauthorized?)
    (fact (validate-create-new-user! {:role "authorityAdmin" :orgAuthz {:x "authorityAdmin"}} {:role "authority" :orgAuthz {:x ["authority"]} :email "x"}) => truthy))

  (fact "invalid passwords are rejected"
    (validate-create-new-user! {:role "admin"} {:password "z" :role "dummy" :email "x"}) => (partial expected-failure? :error.password.minlengt)
    (provided (security/valid-password? "z") => false))

  (fact "valid passwords are ok"
    (validate-create-new-user! {:role "admin"} {:password "z" :role "dummy" :email "x"}) => truthy
    (provided (security/valid-password? "z") => true))

  ))
;;
;; ==============================================================================
;; create-new-user-entity
;; ==============================================================================
;;

(testable-privates lupapalvelu.user create-new-user-entity)

(facts "create-new-user-entity"

  (facts "emails are converted to lowercase"
    (fact (create-new-user-entity {:email "foo"})         => (contains {:email "foo"}))
    (fact (create-new-user-entity {:email "Foo@Bar.COM"}) => (contains {:email "foo@bar.com"})))

  (facts "default values"
    (fact (create-new-user-entity {:email "Foo"}) => (contains {:email "foo"
                                                                :username "foo"
                                                                :firstName ""
                                                                :lastName  ""
                                                                :enabled   false}))
    (fact (create-new-user-entity {:email "Foo" :username "bar"}) => (contains {:email "foo"
                                                                                :username "bar"
                                                                                :firstName ""
                                                                                :lastName  ""
                                                                                :enabled   false})))



  (fact "password (if provided) is put under :private"
    (fact (create-new-user-entity {:email "email"}) => (contains {:private {}}))
    (fact (create-new-user-entity {:email "email" :password "foo"}) => (contains {:private {:password "bar"}})
      (provided (security/get-hash "foo") => "bar")))

  (fact "does not contain extra fields"
    (-> (create-new-user-entity {:email "email" :foo "bar"}) :foo) => nil)

  (facts "apikey is not created"
    (fact (-> (create-new-user-entity  {:email "..anything.." :apikey "true"}) :private :apikey) => nil?)
    (fact (-> (create-new-user-entity {:email "..anything.." :apikey "false"}) :private) => {})
    (fact (-> (create-new-user-entity {:email "..anything.." :apikey "foo"}) :private :apikey) => nil?)))

;;
;; ==============================================================================
;; Creating users:
;; ==============================================================================
;;

(facts "create-new-user"

  (fact "register new applicant user, user did not exists before"
    (create-new-user nil {:email "email" :role "applicant"}) => ..result..
    (provided
      (get-user-by-email "email") =streams=> [nil ..result..]
      (mongo/create-id) => ..id..
      (mongo/insert :users (contains {:email "email" :id ..id..})) => nil
      (mongo/update-by-id :users anything anything) => anything :times 0))

  (fact "create new applicant user, user exists before as dummy user"
    (create-new-user nil {:email "email" :role "applicant"}) => ..result..
    (provided
      (get-user-by-email "email") =streams=> [{:id ..old-id.. :role "dummy"} ..result..]
      (mongo/insert :users anything) => anything :times 0
      (mongo/update-by-id :users ..old-id.. (contains {:email "email"})) => nil))

  (fact "create new authorityAdmin user, user exists before as dummy user"
    (create-new-user {:role "admin"} {:email "email" :orgAuthz {:x ["authorityAdmin"]} :role "authorityAdmin"}) => ..result..
    (provided
      (get-user-by-email "email") =streams=> [{:id ..old-id.. :role "dummy"} ..result..]
      (mongo/by-id :organizations "x" anything) => {:id "x"}
      (mongo/insert :users anything) => anything :times 0
      (mongo/update-by-id :users ..old-id.. (contains {:email "email"})) => nil))

  (fact "create new authorityAdmin user, user exists before, but role is not 'dummy'"
    (create-new-user {:role "admin"} {:email "email" :orgAuthz {:x ["authorityAdmin"]} :role "authorityAdmin"}) => (partial expected-failure? :error.duplicate-email)
    (provided
      (get-user-by-email "email") => {:id ..old-id.. :role "authorityAdmin"} :times 1
      (mongo/by-id :organizations "x" anything) => {:id "x"}
      (mongo/insert :users anything) => anything :times 0
      (mongo/update-by-id :users ..old-id.. (contains {:email "email"})) => anything :times 0)))

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

(facts "email-recipient?"
  (email-recipient? {}) => true
  (email-recipient? {:id 1}) => false
  (provided
    (find-user {:id 1}) => nil)
  (email-recipient? {:id 2}) => true
  (provided
    (find-user {:id 2}) => {:id 2 :dummy true})
  (email-recipient? {:id 3}) => true                        ; no password set
  (provided
    (find-user {:id 3}) => {:id 3 :dummy false})
  (email-recipient? {:id 4}) => true
  (provided
    (find-user {:id 4}) => {:id 4 :dummy false :enabled true})
  (email-recipient? {:id 5}) => true
  (provided
    (find-user {:id 5}) => {:id 5 :dummy false :enabled true, :private {:password "foo"}})
  (email-recipient? {:id 6}) => false                       ; has been enabled (has password), but now disabled
  (provided
    (find-user {:id 6}) => {:id 6 :dummy false :enabled false, :private {:password "foo"}})
  (email-recipient? {:id 7}) => true
  (provided
    (find-user {:id 7}) => {:id 7 :dummy false :enabled true, :private {:password "foo"}})
  (email-recipient? {:id 8}) => true
  (provided
    (find-user {:id 8}) => {:id 8 :dummy false :enabled true, :private {:password ""}}))
