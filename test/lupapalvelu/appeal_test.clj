(ns lupapalvelu.appeal-test
  "Unit tests for lupapalvelu.appeal, lupapalvelu.appeal-verdict, lupapalvelu.appeal-api."
  (:require [lupapalvelu.appeal :refer :all]
            [lupapalvelu.appeal-verdict :as appeal-verdict]
            [lupapalvelu.itest-util :refer [expected-failure?]]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]))

(fact "Invalid data results in nil"
  (appeal-data-for-upsert "123" "appeal" "Test User" (now) nil) => nil ; id wrong format
  (appeal-data-for-upsert (mongo/create-id) "wrong-type" "Test User" (now) nil) => nil ; type wrong
  (appeal-data-for-upsert (mongo/create-id) "rectification" "User" "18.3.2016" "footext") => nil ; date not timestamp
  (appeal-data-for-upsert nil nil nil nil nil) => nil)

(fact "Appeal input validator"
  (let [valid {:verdictId       (mongo/create-id)
               :type           "appeal"
               :appellant      "Me"
               :datestamp           (now)
               :text           "Some information"}
        invalid {:verdictId       (mongo/create-id)
                 :type           "foobar"
                 :appellant      "Me"
                 :datestamp           "18.3.2016"
                 :text           "Some information"}]

    (input-validator {:data valid}) => nil
    (input-validator {:data invalid}) => (partial expected-failure? :error.invalid-appeal)))

(fact "target-verdict is not updated"
  (let [current-id (mongo/create-id)
        verdict-id (mongo/create-id)]
    (appeal-data-for-upsert verdict-id "appeal" "Test User" 123 nil current-id) => {:type "appeal"
                                                                                    :appellant "Test User"
                                                                                    :datestamp 123}
    (appeal-verdict/appeal-verdict-data-for-upsert
      verdict-id "Foo" 123 nil current-id) => {:giver "Foo"
                                               :datestamp 123}))
