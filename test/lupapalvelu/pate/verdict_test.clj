(ns lupapalvelu.pate.verdict-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clj-time.coerce :as time-coerce]
            [lupapalvelu.pate.verdict :refer :all]))

(testable-privates lupapalvelu.pate.verdict next-section insert-section)

(facts next-section
  (fact "all arguments given"
    (next-section "123-T" 1515151515151 :test) => "1"
    (provided (lupapalvelu.mongo/get-next-sequence-value "verdict_test_123-T_2018") => 1))

  (fact "all arguments given - year is determined by local time zone (UTC+2))"
    (next-section "123-T" 1514760000000 "test") => "99"
    (provided (lupapalvelu.mongo/get-next-sequence-value "verdict_test_123-T_2018") => 99))

  (fact "org-id is nil"
    (next-section nil 1515151515151 :test) => nil)

  (fact "org-id is blank"
    (next-section "" 1515151515151 :test) => nil)

  (fact "created is nil"
    (next-section "123-T" nil :test) => nil)

  (fact "verdict-giver is nil"
    (next-section "123-T" 1515151515151 nil) => nil)

  (fact "verdict-giver is blank"
    (next-section "123-T" 1515151515151 "") => nil))

(facts insert-section
  (fact "section is not set"
    (insert-section "123-T" 1515151515151 {:data {:giver "test"}})
    => {:data {:giver "test" :verdict-section "2"}}

    (provided (lupapalvelu.mongo/get-next-sequence-value "verdict_test_123-T_2018") => 2))

  (fact "section is blank"
    (insert-section "123-T" 1515151515151 {:data {:giver "test" :verdict-section ""}})
    => {:data {:giver "test" :verdict-section "1"}}

    (provided (lupapalvelu.mongo/get-next-sequence-value "verdict_test_123-T_2018") => 1))

  (fact "section already given"
    (insert-section "123-T" 1515151515151 {:data {:giver "test" :verdict-section "9"}})

    => {:data {:giver "test" :verdict-section "9"}}

    (provided (lupapalvelu.mongo/get-next-sequence-value irrelevant) => irrelevant :times 0)))
