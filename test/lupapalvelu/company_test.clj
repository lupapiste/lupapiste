(ns lupapalvelu.company-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.company :as c]
            [lupapalvelu.mongo :as mongo]
            [sade.core :as core]))

(facts create-company
  (fact
    (c/create-company {}) => (throws clojure.lang.ExceptionInfo))
  (fact
    (c/create-company {:name "foo" :y "2341528-4" :accountType "account5"
                       :address1 "katu" :zip "33100" :po "Tampere"})
    => {:name "foo"
        :y "2341528-4"
        :id "012345678901234567890123"
        :accountType "account5"
        :address1 "katu"
        :zip "33100"
        :po "Tampere"
        :created 1}
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
                                :accountType "account5"}) => true)))

(let [id       "012345678901234567890123"
      data     {:id id :name "foo" :y "2341528-4" :created 1 :accountType "account15"
                :address1 "katu" :zip "33100" :po "Tampere"}
      expected (-> data (dissoc :id) (assoc :name "bar"))]
  (against-background [(c/find-company-by-id! id) => data
                       (mongo/update :companies {:_id id} anything) => true]
    (fact "Can change company name"
      (c/update-company! id {:name "bar"}) => expected)
    (fact "Extra keys are not persisted"
       (c/update-company! id {:name "bar" :bozo ..irrelevant..}) => (throws clojure.lang.ExceptionInfo))
    (fact "Can't change Y"
      (c/update-company! id {:name "bar" :y ..irrelevant..}) => (throws clojure.lang.ExceptionInfo))
    (fact "Cant downgrade account type"
      (c/update-company! id {:accountType "account5"}) => (throws clojure.lang.ExceptionInfo))))

