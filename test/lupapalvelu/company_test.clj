(ns lupapalvelu.company-test
  (:require [lupapalvelu.authorization :as auth]
            [lupapalvelu.company :as com]
            [lupapalvelu.itest-util :refer [expected-failure?]]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [sade.core :as core]
            [sade.util :as util]))

(def base-data-5 {:name "foo" :y "2341528-4" :accountType "account5" :customAccountLimit nil
                  :billingType "monthly" :address1 "katu" :zip "33100" :po "Tampere"})
(def base-data-15 (assoc base-data-5 :accountType "account15"))
(def base-data-custom (assoc base-data-5 :accountType "custom" :customAccountLimit 100))
(def base-to-db (assoc base-data-5
                  :id "012345678901234567890123"
                  :created 1))

(facts create-company
  (fact
    (com/create-company {}) => (throws clojure.lang.ExceptionInfo))
  (fact
    (com/create-company base-data-5)
    => base-to-db
    (provided
      (core/now) => 1
      (mongo/create-id) => "012345678901234567890123"
      (mongo/insert :companies base-to-db) => true)))

(let [id       "012345678901234567890123"
      data     (assoc base-data-15 :id id :created 1)
      expected (-> data (dissoc :id) (assoc :name "bar"))]
  (against-background [(com/find-company-by-id! id) => data
                       (mongo/update :companies {:_id id} anything) => true]
    (fact "Can change company name"
      (com/update-company! id {:name "bar"} false) => expected
      (provided (mongo/update-by-query :users {:company.id id} {"$set" {:companyName "bar"}}) => 1))
    (fact "Extra keys are not persisted"
       (com/update-company! id {:name "bar" :bozo ..irrelevant..} false) => (partial expected-failure? "error.unknown"))
    (fact "Can't change Y or id"
      (com/update-company! id {:name "bar" :y "7208863-8"} false) => (partial expected-failure? "error.unauthorized")
      (com/update-company! id {:name "bar" :id ..irrelevant..} false) => (partial expected-failure? "bad-request"))
    (fact "Can't downgrade account type"
      (com/update-company! id {:accountType "account5" :name "bar"} false) => (partial expected-failure? "company.account-type-not-downgradable"))
    (fact "Can upgrade account type"
      (com/update-company! id {:accountType "account30" :name "bar"} false) => (assoc expected :accountType "account30")
      (provided (mongo/update-by-query :users {:company.id id} {"$set" {:companyName "bar"}}) => 1))
    (fact "Only name and Y changes trigger user updates"
      (com/update-company! id {:accountType "account30"} false) => truthy
      (provided (mongo/update-by-query :users anything anything) => ..irrelevant.. :times 0))))

(facts "Custom account"
       (let [id              "0987654321"
             custom-id       "123456789"
             data            (assoc base-data-5 :id id :name "normal")
             custom-data     (assoc base-data-custom :id custom-id :name "custom")
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

       (fact "Normal user can't change Y but admin can"
         (com/update-company! id {:y "7208863-8" :accountType "custom" :customAccountLimit 1000} admin-caller) => (merge expected {:y "7208863-8"})
         (provided (mongo/update-by-query :users {:company.id id} anything) => ..irrelevant..)
         (com/update-company! id {:y "7208863-8" :accountType "custom" :customAccountLimit 1000} normal-caller) => unauthorized)

       (fact "Normal user can't set/change account to/from custom, but admin/company admins can"
             (com/update-company! id {:accountType "custom" :customAccountLimit 1000} normal-caller) => unauthorized
             (com/update-company! id {:accountType "custom" :customAccountLimit 1000} admin-caller) => expected
             (provided (mongo/update-by-query :users anything anything) => ..irrelevant.. :times 0)

             (com/update-company! custom-id {:accountType "account5"} normal-caller) => unauthorized
             (com/update-company! custom-id {:accountType "account5"} admin-caller) => custom-expected
             (provided (mongo/update-by-query :users anything anything) => ..irrelevant.. :times 0))

       (fact "Can't set custom account when no customAccountLimit is given"
             (com/update-company! id {:accountType "custom"} admin-caller) => (partial expected-failure? "company.missing.custom-limit"))

       (fact "customAccountLimit is set to nil when using other than custom account"
         (com/update-company! id {:accountType "account5" :customAccountLimit 123} admin-caller) => (dissoc data :id)
         (provided (mongo/update-by-query :users anything anything) => ..irrelevant.. :times 0))

       (fact "customAccountLimit can't be set less than current count of users in company"
             (com/update-company! id {:accountType "custom" :customAccountLimit 1} admin-caller) => (partial expected-failure? "company.limit-too-small")
             (com/update-company! id {:accountType "custom" :customAccountLimit 2} admin-caller) => (assoc expected :customAccountLimit 2)
             (provided (mongo/update-by-query :users anything anything) => ..irrelevant.. :times 0)
             (com/update-company! id {:accountType "custom" :customAccountLimit 3} admin-caller) => (assoc expected :customAccountLimit 3)
             (provided (mongo/update-by-query :users anything anything) => ..irrelevant.. :times 0)))))

(defn err [kw]
  (fn [res]
    (util/=as-kw kw (:text res))))

(facts "Locked and company auth"
       (fact "Locked"
             (com/locked? {} 123) => false
             (com/locked? {} -123) => false
             (com/locked? {:locked 0} -1) => false
             (com/locked? {:locked nil} 123) => false
             (com/locked? {:locked 100} 123) => true)
       (fact "company auth"
             (com/company->auth {:id "hii" :name "Hii" :y "Not real Y"})
             => {:id "hii" :name "Hii" :y "Not real Y"
                 :role "writer" :type "company" :username "not real y"
                 :firstName "Hii" :lastName ""}
             (com/company->auth {:locked (+ (core/now) 10000)
                                 :id "hii" :name "Hii" :y "Not real Y"})
             => {:id "hii" :name "Hii" :y "Not real Y"
                 :role "writer" :type "company" :username "not real y"
                 :firstName "Hii" :lastName ""}
             (com/company->auth {:locked (- (core/now) 10000)
                                 :id "hii" :name "Hii" :y "Not real Y"})
             => nil)
       (fact "company auth with invite"
         (com/company->auth {:id "foo" :name "Foo Ltd." :y "000-0"} :writer)
         => {:id "foo" :role "reader" :company-role :admin
             :y "000-0" :name "Foo Ltd."
             :type "company" :username "000-0" :firstName "Foo Ltd."
             :lastName "" :invite {:user {:id "foo"}
                                   :created 12345
                                   :role :writer}}
         (provided
          (core/now) => 12345)))

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
                                                 :data {}}) => unauthorized)
         (facts "company-not-locked"
                (fact "No locked property"
                      (com/company-not-locked {:data {:company "foo"}
                                               :created 12345}) => nil?
                      (provided (com/find-company-by-id! "foo") =>  {}))
                (fact "Locked property is zero"
                      (com/company-not-locked {:data {:company "foo"}
                                               :created 12345}) => nil?
                      (provided (com/find-company-by-id! "foo") =>  {:locked 0}))
                (fact "Will be locked, but is not locked now."
                      (com/company-not-locked {:data {:company "foo"}
                                               :created 12345}) => nil?
                      (provided (com/find-company-by-id! "foo") =>  {:locked 23456}))
                (fact "company is locked"
                      (com/company-not-locked {:data {:company "foo"}
                                               :created 12345}) => (err :error.company-locked)
                      (provided (com/find-company-by-id! "foo") =>  {:locked 11111})))
         (facts "user-company-is-locked"
                (fact "No locked property"
                      (com/user-company-is-locked {:user {:company {:id "foo"}}
                                                   :created 12345}) => (err :error.company-not-locked)
                      (provided (com/find-company-by-id! "foo") =>  {}))
                (fact "Locked property is zero"
                      (com/user-company-is-locked {:user {:company {:id "foo"}}
                                                   :created 12345}) => (err :error.company-not-locked)
                      (provided (com/find-company-by-id! "foo") =>  {:locked 0}))
                (fact "Will be locked, but is not locked now."
                      (com/user-company-is-locked {:user {:company {:id "foo"}}
                                                   :created 12345}) => (err :error.company-not-locked)
                      (provided (com/find-company-by-id! "foo") =>  {:locked 23456}))
                (fact "user company is locked"
                      (com/user-company-is-locked {:user {:company {:id "foo"}}
                                                   :created 12345}) => nil?
                      (provided (com/find-company-by-id! "foo") =>  {:locked 11111}))))
       (let [already-invited (partial expected-failure? :company.already-invited)]
         (fact "Company already invited and accepted"
           (com/company-not-already-invited {:data {:company-id "foo"}
                                             :application {:auth [{:id "hei"} {:id "foo"}]}})
           => already-invited)
         (fact "Company already invited but not yet accepted"
           (com/company-not-already-invited {:data {:company-id "foo"}
                                             :application {:auth [{:id "hei"} {:invite {:user {:id "foo"}}}]}})
           => already-invited)
         (fact "Company not already invited"
           (com/company-not-already-invited {:data {:company-id "foo"}
                                             :application {:auth [{:id "hei"} {:invite {:user {:id "foobar"}}}]}})
           => nil))
       (facts "check-company-authorized"
         (let [company-not-authorized (partial expected-failure? :error.company-has-not-accepted-invite)]
           (fact "Company is not in auth"
             (com/check-company-authorized {:application {:auth []}
                                            :user {:company {:id "com1"}}})
             => company-not-authorized)

           (fact "Company has not accepted invitation"
             (com/check-company-authorized {:application {:auth [{:id "com1"
                                                                  :invite "not-empty"}]}
                                            :user {:company {:id "com1"}}})
             => company-not-authorized)

           (fact "Company is authorized" ;; company in auth, but :invite key is not present
             (com/check-company-authorized {:application {:auth [{:id "com1"}]}
                                            :user {:company {:id "com1"}}})
             => nil?))))

(facts "Company allow invitations"
  (let [denying-company {:id "denied" :name "Company Oy" :y  "2341528-4" :created 1 :accountType "account5"
                         :address1 "katu" :zip  "33100"  :po "Tampere" :invitationDenied true}
        allowing-company {:id "allow" :name "Another Company" :y  "2341528-4" :created 1 :accountType "account5"
                          :address1 "katu" :zip  "33100"  :po "Tampere" :invitationDenied false}
        locked-company   {:id "locked" :name "Locked Company Oy" :y  "2341528-4" :created 1 :accountType "account5"
                          :address1 "katu" :zip  "33100"  :po "Tampere" :invitationDenied true :locked 123}
        app-with-auths-1 {:id "LP-123" :auth [{:id "denied" :y "2341528-4" :name "Company Oy"}]}
        app-with-auths-2 {:id "LP-456" :auth [{:id "allow" :y "2341528-4" :name "Another Company"}]}]
    (against-background [(com/find-company-by-id "denied") => denying-company
                         (com/find-company-by-id "allow") => allowing-company
                         (com/find-company-by-id "locked") => locked-company]

      (fact "Company has authorization for application and user invitation is denied"
        (com/company-denies-invitations? app-with-auths-1 "denied") => false)

      (fact "Company has authorization for application and allows invitations"
        (com/company-denies-invitations? app-with-auths-2 "allow") => false)

      (fact "Company doesnt have authorization for application, but allows user invitations"
        (com/company-denies-invitations? app-with-auths-1 "allow") => false)

      (fact "Company denies user invitation but is locked"
        (com/company-denies-invitations? app-with-auths-2 "locked") => false)

      (fact "Company doesnt have authorization for application and user invitation is denied"
        (com/company-denies-invitations? app-with-auths-2 "denied") => true))))

(facts "company-info"
  (let [firm {:id       "firm"
              :name     "Firm"
              :y        "000000-0"
              :address1 "Billing Street"
              :zip      "12345"
              :po       "Dollarville"
              :netbill  "foo"}
        shop {:id             "shop"
              :name           "Shop"
              :y              "888888-8"
              :contactAddress "Contact Road"
              :contactZip     "98765"
              :contactPo      "Shoptown"
              :netbill        "bar"}
        corp {:id             "corporation"
              :name           "Corporation"
              :y              "1234567-8"
              :address1       "Money Main"
              :zip            "12123"
              :po             "Headquarters"
              :contactAddress "Park View"
              :contactZip     "33333"
              :contactPo      "Another HQ"
              :netbill        "baz"}]
    (com/company-info firm) => (dissoc firm :netbill)
    (com/company-info (assoc firm :contactPo "Foobar"))
    => {:id       "firm"
        :name     "Firm"
        :y        "000000-0"
        :address1 "Billing Street"
        :zip      "12345"
        :po       "Foobar"}
    (com/company-info (dissoc firm :zip))
    => {:id       "firm"
        :name     "Firm"
        :y        "000000-0"
        :address1 "Billing Street"
        :po       "Dollarville"}
    (com/company-info shop) => {:id       "shop"
                                :name     "Shop"
                                :y        "888888-8"
                                :address1 "Contact Road"
                                :zip      "98765"
                                :po       "Shoptown"}
    (com/company-info (merge firm shop))
    => {:id       "shop"
        :name     "Shop"
        :y        "888888-8"
        :address1 "Contact Road"
        :zip      "98765"
        :po       "Shoptown"}
    (com/company-info corp)
    => {:id       "corporation"
        :name     "Corporation"
        :y        "1234567-8"
        :address1 "Park View"
        :zip      "33333"
        :po       "Another HQ"}
    (com/company-info (assoc corp :contactPo "  "))
    => {:id       "corporation"
        :name     "Corporation"
        :y        "1234567-8"
        :address1 "Park View"
        :zip      "33333"
        :po       "Headquarters"}
    (fact "Accept company invitation"
      (auth/approve-invite-auth (assoc (com/company->auth firm :wizard)
                                       :inviter {:name "hello"})
                                {:company firm}
                                12345678)
      => {:type           "company"
          :role           :wizard
          :inviter        {:name "hello"}
          :inviteAccepted 12345678
          :username       (:y firm)
          :firstName      "Firm"
          :lastName       ""
          :y              (:y firm)
          :name           "Firm"
          :id             "firm"}
      (provided
       (com/find-company! {:id "firm"}) => firm))))
