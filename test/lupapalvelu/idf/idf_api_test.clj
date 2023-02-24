(ns lupapalvelu.idf.idf-api-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.idf.idf-api]))

(testable-privates lupapalvelu.idf.idf-api invalid-boolean)

(facts
  (invalid-boolean nil) => falsey
  (invalid-boolean "") => falsey
  (invalid-boolean "true") => falsey
  (invalid-boolean "false") => falsey
  (invalid-boolean "TRUE") => truthy
  (invalid-boolean "1") => truthy
  (invalid-boolean "FALSE") => truthy
  (invalid-boolean "0") => truthy)