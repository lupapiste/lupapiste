(ns lupapalvelu.company-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.company :as com]
            [lupapalvelu.mongo :as mongo]
            [sade.core :as core]
            [lupapalvelu.itest-util :refer [expected-failure?]]))

(facts create-company
  (fact
    (com/create-company {}) => (throws clojure.lang.ExceptionInfo))
  (fact
    (com/create-company {:name "foo" :y "2341528-4" :accountType "account5" :customAccountLimit nil
                       :address1 "katu" :zip "33100" :po "Tampere"})
    => {:name "foo"
        :y "2341528-4"
        :id "012345678901234567890123"
        :accountType "account5"
        :address1 "katu"
        :zip "33100"
        :po "Tampere"
        :created 1
        :customAccountLimit nil}
    (provided
      (core/now) => 1
      (mongo/create-id) => "012345678901234567890123"
      (mongo/insert :companies {:name "foo"
                                :y "2341528-4"
                                :id "012345678901234567890123"
                                :address1 "katu"
                                :zip "33100"
                                :po "Tampere"
                                :created 1
                                :accountType "account5"
                                :customAccountLimit nil}) => true)))

(let [id       "012345678901234567890123"
      data     {:id id :name "foo" :y "2341528-4" :created 1 :accountType "account15"
                :address1 "katu" :zip "33100" :po "Tampere" :customAccountLimit nil}
      expected (-> data (dissoc :id) (assoc :name "bar"))]
  (against-background [(com/find-company-by-id! id) => data
                       (mongo/update :companies {:_id id} anything) => true]
    (fact "Can change company name"
      (com/update-company! id {:name "bar"} false) => expected)
    (fact "Extra keys are not persisted"
       (com/update-company! id {:name "bar" :bozo ..irrelevant..} false) => (partial expected-failure? "error.unknown"))
    (fact "Can't change Y or id"
      (com/update-company! id {:name "bar" :y ..irrelevant..} false) => (partial expected-failure? "bad-request")
      (com/update-company! id {:name "bar" :id ..irrelevant..} false) => (partial expected-failure? "bad-request"))
    (fact "Cant downgrade account type, but can upgrade"
      (com/update-company! id {:accountType "account5" :name "bar"} false) => (partial expected-failure? "company.account-type-not-downgradable")
      (com/update-company! id {:accountType "account30" :name "bar"} false) => (assoc expected :accountType "account30"))))

(facts "Custom account"
       (let [id              "0987654321"
             custom-id       "123456789"
             data            {:id       id     :name "normal" :y  "2341528-4" :created            1 :accountType "account5"
                              :address1 "katu" :zip  "33100"  :po "Tampere"   :customAccountLimit nil}
             custom-data     {:id       custom-id :name "custom" :y  "2341528-4" :created            1 :accountType "custom"
                              :address1 "katu"    :zip  "33100"  :po "Tampere"   :customAccountLimit 100}
             expected        (-> (assoc data :accountType "custom" :customAccountLimit 1000)
                                 (dissoc :id))
             custom-expected (-> (assoc custom-data :accountType "account5" :customAccountLimit nil)
                                 (dissoc :id))
             admin-caller    {:username "test" :role "admin"}
             normal-caller   {:username "test" :role "applicant"}
             unauthorized    (partial expected-failure? :error.unauthorized)]
     (against-background [(com/find-company-by-id! id) => data
                          (com/find-company-by-id! custom-id) => custom-data
                          (mongo/update :companies {:_id id} anything) => true
                          (mongo/update :companies {:_id custom-id} anything) => true
                          (mongo/count :users {:company.id id}) => 2]

       (fact "Normal user can't set/change account to/from custom, but admin/company admins can"
             (com/update-company! id {:accountType "custom" :customAccountLimit 1000} normal-caller) => unauthorized
             (com/update-company! id {:accountType "custom" :customAccountLimit 1000} admin-caller) => expected

             (com/update-company! custom-id {:accountType "account5"} normal-caller) => unauthorized
             (com/update-company! custom-id {:accountType "account5"} admin-caller) => custom-expected)

       (fact "Can't set custom account when no customAccountLimit is given"
             (com/update-company! id {:accountType "custom"} admin-caller) => (partial expected-failure? "company.missing.custom-limit"))

       (fact "customAccountLimit is set to nil when using other than custom account"
             (com/update-company! id {:accountType "account5" :customAccountLimit 123} admin-caller) => (dissoc data :id))

       (fact "customAccountLimit can't be set less than current count of users in company"
             (com/update-company! id {:accountType "custom" :customAccountLimit 1} admin-caller) => (partial expected-failure? "company.limit-too-small")
             (com/update-company! id {:accountType "custom" :customAccountLimit 2} admin-caller) => (assoc expected :customAccountLimit 2)
             (com/update-company! id {:accountType "custom" :customAccountLimit 3} admin-caller) => (assoc expected :customAccountLimit 3)))))

(facts "Pre-checkers"
       (let [unauthorized (partial expected-failure? :error.unauthorized)]
         (fact "validate-has-company-role"
               ((com/validate-has-company-role :user) {:user {:company {:role "user"}}}) => nil
               ((com/validate-has-company-role :user) {:user {:company {:role "admin"}}}) => unauthorized
               ((com/validate-has-company-role :any) {:user {:company {:role "admin"}}}) => nil
               ((com/validate-has-company-role "admin") {:user {:company {:role "admin"}}}) => nil
               ((com/validate-has-company-role :user) {:user {}}) => unauthorized
               ((com/validate-has-company-role "any") {:user {}}) => unauthorized)
         (fact "validate-is-admin"
               (com/validate-is-admin {:user {:role "admin"}}) => nil
               (com/validate-is-admin {:user {:role "applicant"}}) => unauthorized)
         (fact "validate-belongs-to-company"
               (com/validate-belongs-to-company {:user {:company {:id "foo"}}
                                                 :data {:company "foo"}}) => nil
               (com/validate-belongs-to-company {:user {:company {:id "foo"}}
                                                 :data {:company "bar"}})=> unauthorized
               (com/validate-belongs-to-company {:user {}
                                                 :data {:company "bar"}})=> unauthorized
               (com/validate-belongs-to-company {:user {:company {:id "foo"}}
                                                 :data {}}) => unauthorized)))
