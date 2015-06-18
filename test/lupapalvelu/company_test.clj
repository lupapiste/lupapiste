(ns lupapalvelu.company-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.company :as c]
            [lupapalvelu.mongo :as mongo]
            [sade.core :as core]))

(facts create-company
  (fact
    (c/create-company {}) => (throws clojure.lang.ExceptionInfo))
  (fact
    (c/create-company {:name "foo" :y "2341528-4" :accountType "account5" :customAccountLimit nil
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
  (against-background [(c/find-company-by-id! id) => data
                       (mongo/update :companies {:_id id} anything) => true]
    (fact "Can change company name"
      (c/update-company! id {:name "bar"} false) => expected)
    (fact "Extra keys are not persisted"
       (c/update-company! id {:name "bar" :bozo ..irrelevant..} false) => (throws clojure.lang.ExceptionInfo))
    (fact "Can't change Y"
      (c/update-company! id {:name "bar" :y ..irrelevant..} false) => (throws clojure.lang.ExceptionInfo))
    (fact "Cant downgrade account type"
      (c/update-company! id {:accountType "account5"} false) => (throws clojure.lang.ExceptionInfo))))

(facts "Custom account"
 (let [id "0987654321"
       custom-id "123456789"
       data {:id id :name "normal" :y "2341528-4" :created 1 :accountType "account5"
             :address1 "katu" :zip "33100" :po "Tampere" :customAccountLimit nil}
       custom-data {:id custom-id :name "custom" :y "2341528-4" :created 1 :accountType "custom"
                    :address1 "katu" :zip "33100" :po "Tampere" :customAccountLimit 100}
       expected (-> (assoc data :accountType "custom" :customAccountLimit 1000)
                  (dissoc :id))
       custom-expected (-> (assoc custom-data :accountType "account5" :customAccountLimit nil)
                         (dissoc :id))]
     (against-background [(c/find-company-by-id! id) => data
                          (c/find-company-by-id! custom-id) => custom-data
                          (mongo/update :companies {:_id id} anything) => true
                          (mongo/update :companies {:_id custom-id} anything) => true
                          (mongo/count :users {:company.id id}) => 2]

       (fact "Normal user can't set/change account to/from custom, but admin can"
         (c/update-company! id {:accountType "custom" :customAccountLimit "1000"} false) => (throws clojure.lang.ExceptionInfo #"unauthorized")
         (c/update-company! id {:accountType "custom" :customAccountLimit "1000"} true) => expected

         (c/update-company! custom-id {:accountType "account5"} false) => (throws clojure.lang.ExceptionInfo #"unauthorized")
         (c/update-company! custom-id {:accountType "account5"} true) => custom-expected)

       (fact "Can't set custom account when no customAccountLimit is given"
         (c/update-company! id {:accountType "custom"} true) => (throws clojure.lang.ExceptionInfo #"company.missing.custom-limit"))

       (fact "customAccountLimit is set to nil when using other than custom account"
         (c/update-company! id {:accountType "account5" :customAccountLimit "123"} true) => (dissoc data :id))

       (fact "customAccountLimit can't be set less than current count of users in company"
         (c/update-company! id {:accountType "custom" :customAccountLimit "1"} true) => (throws clojure.lang.ExceptionInfo #"company.limit-too-small")
         (c/update-company! id {:accountType "custom" :customAccountLimit "2"} true) => (assoc expected :customAccountLimit 2)
         (c/update-company! id {:accountType "custom" :customAccountLimit "3"} true) => (assoc expected :customAccountLimit 3)))))


