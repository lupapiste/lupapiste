(ns lupapalvelu.company-test
  (:require [lupapalvelu.authorization :as auth]
            [lupapalvelu.company :as com]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.test-util :refer [err]]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [sade.core :as sc]
            [sade.dummy-email-server :as dummy]
            [sade.env :as env]
            [sade.strings :as ss]))

(def base-data-5 {:name        "foo"     :y        "2341528-4" :accountType "account5" :customAccountLimit nil
                  :billingType "monthly" :address1 "katu"      :zip         "33100"    :po                 "Tampere"})
(def base-data-15 (assoc base-data-5 :accountType "account15"))
(def base-data-custom (assoc base-data-5 :accountType "custom" :customAccountLimit 100))
(def base-to-db (assoc base-data-5
                       :id "012345678901234567890123"
                       :created 1))
(env/with-feature-value :company-change-notifications false
  (facts "create-company"
    (fact
        (com/create-company {}) => (throws clojure.lang.ExceptionInfo))
    (fact
        (com/create-company base-data-5)
      => base-to-db
      (provided
        (sc/now) => 1
        (mongo/create-id) => "012345678901234567890123"
        (mongo/insert :companies base-to-db) => true)))

  (let [id       "012345678901234567890123"
        data     (assoc base-data-15 :id id :created 1)
        expected (-> data (dissoc :id) (assoc :name "bar"))]
    (against-background [(com/find-company-by-id! id) => data
                         (mongo/update :companies {:_id id} anything) => true]
                        (fact "Can change company name"
                          (com/update-company! {} id {:name "bar"} false) => expected
                          (provided (mongo/update-by-query :users {:company.id id} {"$set" {:companyName "bar"}}) => 1))
                        (fact "Extra keys are not persisted"
                          (com/update-company! {} id {:name "bar" :bozo ..irrelevant..} false) => (err "error.unknown"))
                        (fact "Can't change Y or id"
                          (com/update-company! {} id {:name "bar" :y "7208863-8"} false) => (err "error.unauthorized")
                          (com/update-company! {} id {:name "bar" :id ..irrelevant..} false) => (err "bad-request"))
                        (fact "Can't downgrade account type"
                          (com/update-company! {} id {:accountType "account5" :name "bar"} false) => (err "company.account-type-not-downgradable"))
                        (fact "Can upgrade account type"
                          (com/update-company! {} id {:accountType "account30" :name "bar"} false) => (assoc expected :accountType "account30")
                          (provided (mongo/update-by-query :users {:company.id id} {"$set" {:companyName "bar"}}) => 1))
                        (fact "Only name and Y changes trigger user updates"
                          (com/update-company! {} id {:accountType "account30"} false) => truthy
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
          unauthorized    (err :error.unauthorized)]
      (against-background [(com/find-company-by-id! id) => data
                           (com/find-company-by-id! custom-id) => custom-data
                           (mongo/update :companies {:_id id} anything) => true
                           (mongo/update :companies {:_id custom-id} anything) => true
                           (mongo/count :users {:company.id id}) => 2]

                          (fact "Normal user can't change Y but admin can"
                            (com/update-company! {} id {:y "7208863-8" :accountType "custom" :customAccountLimit 1000} admin-caller) => (merge expected {:y "7208863-8"})
                            (provided (mongo/update-by-query :users {:company.id id} anything) => ..irrelevant..)
                            (com/update-company! {} id {:y "7208863-8" :accountType "custom" :customAccountLimit 1000} normal-caller) => unauthorized)

                          (fact "Normal user can't set/change account to/from custom, but admin/company admins can"
                            (com/update-company! {} id {:accountType "custom" :customAccountLimit 1000} normal-caller) => unauthorized
                            (com/update-company! {} id {:accountType "custom" :customAccountLimit 1000} admin-caller) => expected
                            (provided (mongo/update-by-query :users anything anything) => ..irrelevant.. :times 0)

                            (com/update-company! {} custom-id {:accountType "account5"} normal-caller) => unauthorized
                            (com/update-company! {} custom-id {:accountType "account5"} admin-caller) => custom-expected
                            (provided (mongo/update-by-query :users anything anything) => ..irrelevant.. :times 0))

                          (fact "Can't set custom account when no customAccountLimit is given"
                            (com/update-company! {} id {:accountType "custom"} admin-caller) => (err "company.missing.custom-limit"))

                          (fact "customAccountLimit is set to nil when using other than custom account"
                            (com/update-company! {} id {:accountType "account5" :customAccountLimit 123} admin-caller) => (dissoc data :id)
                            (provided (mongo/update-by-query :users anything anything) => ..irrelevant.. :times 0))

                          (fact "customAccountLimit can't be set less than current count of users in company"
                            (com/update-company! {} id {:accountType "custom" :customAccountLimit 1} admin-caller) => (err "company.limit-too-small")
                            (com/update-company! {} id {:accountType "custom" :customAccountLimit 2} admin-caller) => (assoc expected :customAccountLimit 2)
                            (provided (mongo/update-by-query :users anything anything) => ..irrelevant.. :times 0)
                            (com/update-company! {} id {:accountType "custom" :customAccountLimit 3} admin-caller) => (assoc expected :customAccountLimit 3)
                            (provided (mongo/update-by-query :users anything anything) => ..irrelevant.. :times 0))))))

(facts "Company not found"
  (com/find-company {}) => nil
  (com/find-company nil) => nil
  (com/find-company {} [:foo]) => nil
  (com/find-company! {}) => (throws #"company.not-found")
  (com/find-company-by-id nil) => nil
  (com/find-company-by-id " ") => nil
  (com/find-company-by-id! nil) => (throws #"company.not-found"))

(facts "Locked and company auth"
  (fact "Locked"
    (com/locked? {} 123) => false
    (com/locked? {} -123) => false
    (com/locked? {:locked 0} -1) => false
    (com/locked? {:locked nil} 123) => false
    (com/locked? {:locked 100} 123) => true)
  (fact "company auth"
    (com/company->auth {:id "hii" :name "Hii" :y "Not real Y"})
    => {:id        "hii"    :name     "Hii"     :y        "Not real Y"
        :role      "writer" :type     "company" :username "not real y"
        :firstName "Hii"    :lastName ""}
    (com/company->auth {:locked (+ (sc/now) 10000)
                        :id     "hii" :name "Hii" :y "Not real Y"})
    => {:id        "hii"    :name     "Hii"     :y        "Not real Y"
        :role      "writer" :type     "company" :username "not real y"
        :firstName "Hii"    :lastName ""}
    (com/company->auth {:locked (- (sc/now) 10000)
                        :id     "hii" :name "Hii" :y "Not real Y"})
    => nil)
  (fact "company auth with invite"
    (com/company->auth {:id "foo" :name "Foo Ltd." :y "000-0"} :writer)
    => {:id       "foo"     :role     "reader" :company-role :admin
        :y        "000-0"   :name     "Foo Ltd."
        :type     "company" :username "000-0"  :firstName    "Foo Ltd."
        :lastName ""        :invite   {:user    {:id "foo"}
                                       :created 12345
                                       :role    :writer}}
    (provided
      (sc/now) => 12345)))

(def locked-check #'lupapalvelu.company/locked-check)

(def company-nope? (err :company.not-found))

(def company-not-found (sc/fail :company.not-found))

(facts "locked-query"
  (locked-check {} "foo-id" true) => company-nope?
  (locked-check {:created 1} nil true) => company-nope?
  (locked-check {:created 1} " " true) => company-nope?
  (locked-check {:created 12345} " foo-id " true) => nil
  (provided
    (mongo/any? :companies {:_id    "foo-id"
                            :locked {$lt 12345 $gt 0}}) => true)
  (locked-check {:created 12345} " foo-id " false) => nil
  (provided
    (mongo/any? :companies {:_id    "foo-id"
                            :locked {$not {$lt 12345 $gt 0}}}) => true))

(facts "Pre-checkers"
  (let [unauthorized (err :error.unauthorized)]
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
    (let [command {:user    {:company {:id "my-id"}}
                   :data    {:company "other-id"}
                   :created 12345}]
      (facts "company-not-locked"
        (fact "Not locked"
          ((com/company-not-locked :nope) command) => nil?
          ((com/company-not-locked :company) command) => nil?
          (provided
            (#'lupapalvelu.company/locked-check command "other-id" false) => nil))

        (fact "Locked"
          ((com/company-not-locked :company) command)
          => company-not-found
          (provided
            (#'lupapalvelu.company/locked-check command "other-id" false)
            => company-not-found)))
      (facts "user-company vs. locked"
        (fact "No user company"
          (com/user-company-is-locked (assoc command :user {}))
          => company-not-found
          (com/user-company-not-locked (assoc command :user {}))
          => company-not-found)
        (fact "Not locked"
          (com/user-company-is-locked command)
          => company-not-found
          (provided
            (#'lupapalvelu.company/locked-check command "my-id" true)
            => company-not-found)
          (com/user-company-not-locked command) => nil?
          (provided
            (#'lupapalvelu.company/locked-check command "my-id" false)
            => nil))
        (fact "Locked"
          (com/user-company-is-locked command) => nil?
          (provided
            (#'lupapalvelu.company/locked-check command "my-id" true)
            => nil)
          (com/user-company-not-locked command)
          => company-not-found
          (provided
            (#'lupapalvelu.company/locked-check command "my-id" false)
            => company-not-found)))))
  (let [already-invited (err :company.already-invited)]
    (fact "Company already invited and accepted"
      (com/company-not-already-invited {:data        {:company-id "foo"}
                                        :application {:auth [{:id "hei"} {:id "foo"}]}})
      => already-invited)
    (fact "Company already invited but not yet accepted"
      (com/company-not-already-invited {:data        {:company-id "foo"}
                                        :application {:auth [{:id "hei"} {:invite {:user {:id "foo"}}}]}})
      => already-invited)
    (fact "Company not already invited"
      (com/company-not-already-invited {:data        {:company-id "foo"}
                                        :application {:auth [{:id "hei"} {:invite {:user {:id "foobar"}}}]}})
      => nil))
  (facts "check-company-authorized"
    (let [company-not-authorized (err :error.company-has-not-accepted-invite)]
      (fact "Company is not in auth"
        (com/check-company-authorized {:application {:auth []}
                                       :user        {:company {:id "com1"}}})
        => company-not-authorized)

      (fact "Company has not accepted invitation"
        (com/check-company-authorized {:application {:auth [{:id     "com1"
                                                             :invite "not-empty"}]}
                                       :user        {:company {:id "com1"}}})
        => company-not-authorized)

      (fact "Company is authorized" ;; company in auth, but :invite key is not present
        (com/check-company-authorized {:application {:auth [{:id "com1"}]}
                                       :user        {:company {:id "com1"}}})
        => nil?)))
  (facts "validate-existing-subscriber"
    (let [is-member? (com/validate-existing-subscriber true)]
      (fact "given email should be already subscribed"
        (fact "missing param is ok"
          (is-member? {:data {}}) => nil?)
        (fact "unknown email gives error"
          (is-member? {:data {:email "foo@example.com"}}) => (err :error.not-yet-subscribed)
          (provided (com/company-subscribers-emails) => []))
        (fact "known email is ok"
          (is-member? {:data {:email "foo@example.com"}}) => nil?
          (provided (com/company-subscribers-emails) => ["foo@example.com" "bar@example.com"]))))
    (let [is-not-member? (com/validate-existing-subscriber false)]
      (fact "given email should be already subscribed"
        (fact "missing param is ok"
          (is-not-member? {:data {}}) => nil?)
        (fact "unknown email is ok"
          (is-not-member? {:data {:email "foo@example.com"}}) => nil?
          (provided (com/company-subscribers-emails) => []))
        (fact "known email is error"
          (is-not-member? {:data {:email "foo@example.com"}}) => (err :error.already-subscribed)
          (provided (com/company-subscribers-emails) => ["foo@example.com" "bar@example.com"]))))))

(facts "Company allow invitations"
  (let [denying-company  {:id       "denied" :name "Company Oy" :y  "2341528-4" :created          1 :accountType "account5"
                          :address1 "katu"   :zip  "33100"      :po "Tampere"   :invitationDenied true}
        allowing-company {:id       "allow" :name "Another Company" :y  "2341528-4" :created          1 :accountType "account5"
                          :address1 "katu"  :zip  "33100"           :po "Tampere"   :invitationDenied false}
        locked-company   {:id       "locked" :name "Locked Company Oy" :y  "2341528-4" :created          1    :accountType "account5"
                          :address1 "katu"   :zip  "33100"             :po "Tampere"   :invitationDenied true :locked      123}
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

;; Ugly workaround for running tests on QA, where dummy-server is disabled
(when (env/value :email :dummy-server)
  (facts "company change notifications"
    (env/with-feature-value :company-change-notifications true
      (let [id      "012345678901234567890123"
            company (assoc base-data-custom :id id :created 1)
            caller  {:username "test"
                     :email    "admin@example.com"
                     :role     "admin"}
            changes {:name               "New name"
                     :customAccountLimit 13}]
        (dummy/reset-sent-messages)
        (against-background [(com/find-company-by-id! id) => company
                             (mongo/update :companies {:_id id} anything) => true
                             (mongo/count :users {:company.id id}) => 0
                             (mongo/update-by-query :users anything anything) => 0
                             (com/company-subscribers-emails) => ["foo@example.com"
                                                                  "bar@example.com"]]
          (fact "can update company"
            (com/update-company! {} id changes caller) => truthy)
          (let [[{:keys [body subject]} :as messages] (dummy/messages :reset true)]
            (fact "two notifications sent" (count messages) => 2)
            (fact "correct recipient"
              (set (map :to messages)) => #{"foo@example.com" "bar@example.com"})
            (fact "subject refers to new company name and y-number"
              subject => "Lupapiste: Muutoksia yrityksen tiedoissa (New name - 2341528-4)")
            (fact "body identifies target company, user, and changes"
              (ss/includes? body "Yritys: New name - 2341528-4") => truthy
              (ss/includes? body "Käyttäjä: admin@example.com") => truthy
              (ss/includes? body "name from \\\"foo\\\" to \\\"New name\\\"") => truthy
              (ss/includes? body "customAccountLimit from \\\"100\\\" to \\\"13\\\"") => truthy)))))))
