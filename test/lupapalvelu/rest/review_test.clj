(ns lupapalvelu.rest.review-test
  (:require [clojure.test.check.generators :as gen]
            [midje.sweet :refer [facts fact]]
            [midje.experimental :refer [for-all]]
            [sade.core :refer [def-]]
            [sade.strings :as ss]
            [lupapalvelu.rest.review :as rest.review]))

(def- gen-uuid-string (gen/fmap str gen/uuid))

(comment (gen/sample gen-uuid-string))

(facts "canonical-uuid"
  (fact "returns nil for non-UUID strings"
    (for-all
      [s gen/string]
      (#'rest.review/canonical-uuid s) => nil))

  (fact "returns canonical UUID strings as-is"
    (for-all
      [s gen-uuid-string]
      (#'rest.review/canonical-uuid s) => s))

  (fact "lowercases uppercased UUID strings"
    (for-all
      [s gen-uuid-string]
      (#'rest.review/canonical-uuid (ss/upper-case s)) => s)))

(facts "read-review-id"
  (fact "raises an error if the request's review ID is not a valid UUID"
    (for-all
      [id gen/string]
      (let [context {:request {:review-id id}}]
        (#'rest.review/read-review-id
         context) => (assoc context
                            :review-id id
                            :error :invalid-review-id))))

  (fact "canonicalizes the review ID"
    (for-all
      [id gen-uuid-string]
      (let [context {:request {:review-id (.toUpperCase id)}}]
        (#'rest.review/read-review-id
         context) => (assoc context
                            :review-id id)))))
