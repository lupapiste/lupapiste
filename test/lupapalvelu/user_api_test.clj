(ns lupapalvelu.user-api-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [midje.util.exceptions :refer :all]
            [slingshot.slingshot :refer [try+]]
            [lupapalvelu.user-api :refer :all]
            [lupapalvelu.user :as user]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.activation :as activation]))

(defchecker fails-with [error-message]
  (fn [e]
    (when (and (captured-throwable? e)
               (= (some-> e throwable bean :data :object :text) (name error-message)))
      true)))

(def forbidden (fails-with "error.unauthorized"))

;;
;; ==============================================================================
;; validate-create-new-user!
;; ==============================================================================
;;

(testable-privates lupapalvelu.user-api validate-create-new-user!)

(facts validate-create-new-user!

  (fact (validate-create-new-user! nil nil) => (fails-with :error.missing-parameters))
  (fact (validate-create-new-user! nil {})  => (fails-with :error.missing-parameters))
  (fact (validate-create-new-user! {:role "admin"} {:role "applicant" :email "x"}) => forbidden)

  (fact "only known roles are accepted"
    (validate-create-new-user! {:role "admin"} {:role "x" :email "x"}) => (fails-with :error.invalid-role))

  (fact (validate-create-new-user! {:role "applicant"}      {:role "authorityAdmin" :email "x"}) => forbidden)
  (fact (validate-create-new-user! {:role "authority"}      {:role "authorityAdmin" :email "x"}) => forbidden)
  (fact (validate-create-new-user! {:role "authorityAdmin"} {:role "authorityAdmin" :email "x"}) => forbidden)
  (fact (validate-create-new-user! {:role "admin"}          {:role "authorityAdmin" :email "x"}) => (fails-with :error.missing-parameters))

  (fact (validate-create-new-user! {:role "applicant"}      {:role "authority" :email "x"}) => forbidden)
  (fact (validate-create-new-user! {:role "authority"}      {:role "authority" :email "x"}) => forbidden)
  (fact (validate-create-new-user! {:role "authorityAdmin" :organizations ["o"]} {:role "authority" :email "x" :organization "o"}) => truthy)
  (fact (validate-create-new-user! {:role "authorityAdmin" :organizations ["o"]} {:role "authority" :email "x" :organization "q"}) => forbidden)
  (fact (validate-create-new-user! {:role "admin"}          {:role "authority" :email "x"}) => forbidden)
  (fact (validate-create-new-user! {:role "admin"}          {:role "authorityAdmin" :email "x" :organization "o"}) => truthy)

  (fact (validate-create-new-user! {:role "applicant"}      {:role "applicant" :email "x"}) => forbidden)
  (fact (validate-create-new-user! {:role "authority"}      {:role "applicant" :email "x"}) => forbidden)
  (fact (validate-create-new-user! {:role "authorityAdmin"} {:role "applicant" :email "x"}) => forbidden)
  (fact (validate-create-new-user! {:role "admin"}          {:role "applicant" :email "x"}) => forbidden)
  (fact (validate-create-new-user! nil                      {:role "applicant" :email "x"}) => truthy)

  (fact (validate-create-new-user! {:role "authorityAdmin" :organizations ["o"]} {:role "dummy" :email "x" :organization "o"}) => forbidden)

  (fact "not even admin can create another admin"
    (validate-create-new-user! {:role "admin"} {:role "admin" :email "x"}) => (fails-with :error.invalid-role))

  (fact "authorityAdmin can create authority users to her own organization only"
    (fact (validate-create-new-user! {:role "authorityAdmin"}                      {:role "authority" :organization "x" :email "x"}) => forbidden)
    (fact (validate-create-new-user! {:role "authorityAdmin" :organizations nil}   {:role "authority" :organization "x" :email "x"}) => forbidden)
    (fact (validate-create-new-user! {:role "authorityAdmin" :organizations []}    {:role "authority" :organization "x" :email "x"}) => forbidden)
    (fact (validate-create-new-user! {:role "authorityAdmin" :organizations ["y"]} {:role "authority" :organization "x" :email "x"}) => forbidden)
    (fact (validate-create-new-user! {:role "authorityAdmin" :organizations ["x"]} {:role "authority" :organization "x" :email "x"}) => truthy))

  (fact "invalid passwords are rejected"
    (validate-create-new-user! {:role "admin"} {:password "z" :role "dummy" :email "x"}) => (fails-with :password-too-short)
    (provided (security/valid-password? "z") => false))

  (fact "valid passwords are ok"
    (validate-create-new-user! {:role "admin"} {:password "z" :role "dummy" :email "x"}) => truthy
    (provided (security/valid-password? "z") => true))

  (fact "only admin can create users with apikeys"
    (fact (validate-create-new-user! {:role "admin"} {:role "authorityAdmin" :organization "x" :email "x" :apikey "true"}) => truthy)
    (fact (validate-create-new-user! {:role "authorityAdmin" :organizations ["x"]} {:role "authority" :organization "x" :email "x"}) => truthy)
    (fact (validate-create-new-user! {:role "authorityAdmin" :organizations ["x"]} {:role "authority" :organization "x" :email "x" :apikey "true"}) => forbidden)))

;;
;; ==============================================================================
;; create-new-user-entity
;; ==============================================================================
;;

(testable-privates lupapalvelu.user-api create-new-user-entity)

(facts create-new-user-entity

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

  (facts "apikey is created"
    (fact (-> (create-new-user-entity  {:email ..anything.. :apikey "true"}) :private :apikey) => string?)
    (fact (-> (create-new-user-entity {:email ..anything.. :apikey "false"}) :private) => {})
    (fact (-> (create-new-user-entity {:email ..anything.. :apikey "foo"}) :private :apikey) => "foo")))

;;
;; ==============================================================================
;; Creating users:
;; ==============================================================================
;;

(facts create-new-user

  (fact "register new applicant user, user did not exists before"
    (create-new-user nil {:email "email" :role "applicant"}) => ..result..
    (provided
      (user/get-user-by-email "email") =streams=> [nil ..result..]
      (mongo/create-id) => ..id..
      (mongo/insert :users (contains {:email "email" :id ..id..})) => nil
      (mongo/update-by-id :users anything anything) => anything :times 0
      (activation/send-activation-mail-for (contains {:email "email" :id ..id..})) => nil))

  (fact "create new applicant user, user exists before as dummy user"
    (create-new-user nil {:email "email" :role "applicant"}) => ..result..
    (provided
      (user/get-user-by-email "email") =streams=> [{:id ..old-id.. :role "dummy"} ..result..]
      (mongo/insert :users anything) => anything :times 0
      (mongo/update-by-id :users ..old-id.. (contains {:email "email"})) => nil
      (activation/send-activation-mail-for (contains {:email "email" :id ..old-id..})) => nil))

  (fact "create new authorityAdmin user, user exists before as dummy user"
    (create-new-user {:role "admin"} {:email "email" :organization "x" :role "authorityAdmin"}) => ..result..
    (provided
      (user/get-user-by-email "email") =streams=> [{:id ..old-id.. :role "dummy"} ..result..]
      (mongo/insert :users anything) => anything :times 0
      (mongo/update-by-id :users ..old-id.. (contains {:email "email"})) => nil
      (activation/send-activation-mail-for (contains {:email "email" :id ..old-id..})) => nil))

  (fact "create new authorityAdmin user, user exists before, but role is not 'dummy'"
    (create-new-user {:role "admin"} {:email "email" :organization "x" :role "authorityAdmin"}) => (fails-with :error.duplicate-email)
    (provided
      (user/get-user-by-email "email") => {:id ..old-id.. :role "authorityAdmin"} :times 1
      (mongo/insert :users anything) => anything :times 0
      (mongo/update-by-id :users ..old-id.. (contains {:email "email"})) => anything :times 0
      (activation/send-activation-mail-for anything) => anything :times 0)))

;;
;; ==============================================================================
;; Updating user data:
;; ==============================================================================
;;

(testable-privates lupapalvelu.user-api validate-update-user!)

(def admin-data {:role "admin" :email "admin"})

(facts validate-update-user!
  (facts "admin can change only others data"
    (fact (validate-update-user! admin-data {:email "admin"}) => forbidden)
    (fact (validate-update-user! admin-data {:email "foo"})   => truthy))
  (fact "non-admin users can change only their own data"
    (fact (validate-update-user! {:role ..anything.. :email "foo"} {:email "foo"}) => truthy)
    (fact (validate-update-user! {:role ..anything.. :email "foo"} {:email "bar"}) => forbidden)))
