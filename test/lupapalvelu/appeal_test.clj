(ns lupapalvelu.appeal-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer [now]]
            [lupapalvelu.itest-util :refer [expected-failure?]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.appeal :refer :all]
            [lupapalvelu.appeal-api :refer [appeal-verdicts-after-appeal?]]
            [lupapalvelu.appeal-verdict :as appeal-verdict]))

(fact "Invalid data results in nil"
  (appeal-data-for-upsert "123" "appeal" "Test User" (now) nil) => nil ; id wrong format
  (appeal-data-for-upsert (mongo/create-id) "wrong-type" "Test User" (now) nil) => nil ; type wrong
  (appeal-data-for-upsert (mongo/create-id) "rectification" "User" "18.3.2016" "footext") => nil ; date not timestamp
  (appeal-data-for-upsert nil nil nil nil nil) => nil)

(fact "Appeal input validator"
  (let [valid {:targetId       (mongo/create-id)
               :type           "appeal"
               :appellant      "Me"
               :made           (now)
               :text           "Some information"}
        invalid {:targetId       (mongo/create-id)
                 :type           "foobar"
                 :appellant      "Me"
                 :made           "18.3.2016"
                 :text           "Some information"}]

    (input-validator {:data valid}) => nil
    (input-validator {:data invalid}) => (partial expected-failure? :error.invalid-appeal)))

(fact "target-verdict is not updated"
  (let [current-id (mongo/create-id)
        verdict-id (mongo/create-id)]
    (appeal-data-for-upsert verdict-id "appeal" "Test User" 123 nil current-id) => {:type "appeal"
                                                                                    :appellant "Test User"
                                                                                    :made 123}
    (appeal-verdict/appeal-verdict-data-for-upsert
      verdict-id "Foo" 123 nil current-id) => {:giver "Foo"
                                               :made 123}))

(fact "appeal-verdicts-after-appeal?"
  (let [appeal {:made 2}
        verdicts [{:made 1}]]
    (appeal-verdicts-after-appeal? appeal verdicts) => false
    (appeal-verdicts-after-appeal? appeal (conj verdicts {:made 0})) => false
    (appeal-verdicts-after-appeal? appeal (conj verdicts {:made 3})) => true
    (appeal-verdicts-after-appeal? appeal (conj verdicts {:made 2})) => true ; verdict and appeal on same day
    (appeal-verdicts-after-appeal? appeal [])                        => false ; no verdicts

    (appeal-verdicts-after-appeal? nil (conj verdicts {:made 0})) => (throws AssertionError)
    (appeal-verdicts-after-appeal? appeal nil)                    => (throws AssertionError)))
