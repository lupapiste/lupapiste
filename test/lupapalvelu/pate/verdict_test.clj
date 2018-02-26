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
    (next-section nil 1515151515151 :test) => nil
    (provided (lupapalvelu.mongo/get-next-sequence-value irrelevant) => irrelevant :times 0))

  (fact "org-id is blank"
    (next-section "" 1515151515151 :test) => nil
    (provided (lupapalvelu.mongo/get-next-sequence-value irrelevant) => irrelevant :times 0))

  (fact "created is nil"
    (next-section "123-T" nil :test) => nil
    (provided (lupapalvelu.mongo/get-next-sequence-value irrelevant) => irrelevant :times 0))

  (fact "verdict-giver is nil"
    (next-section "123-T" 1515151515151 nil) => nil
    (provided (lupapalvelu.mongo/get-next-sequence-value irrelevant) => irrelevant :times 0))

  (fact "verdict-giver is blank"
    (next-section "123-T" 1515151515151 "") => nil
    (provided (lupapalvelu.mongo/get-next-sequence-value irrelevant) => irrelevant :times 0)))

(facts insert-section
  (fact "section is not set"
    (insert-section "123-T" 1515151515151 {:data     {}
                                           :template {:giver "test"}})
    => {:data     {:verdict-section "2"}
        :template {:giver "test"}}

    (provided (lupapalvelu.mongo/get-next-sequence-value "verdict_test_123-T_2018") => 2))

  (fact "section is blank"
    (insert-section "123-T" 1515151515151 {:data     {:verdict-section ""}
                                           :template {:giver "test"}})
    => {:data     {:verdict-section "1"}
        :template {:giver "test"}}

    (provided (lupapalvelu.mongo/get-next-sequence-value "verdict_test_123-T_2018") => 1))

  (fact "section already given"
    (insert-section "123-T" 1515151515151 {:data     {:verdict-section "9"}
                                           :template {:giver "test"}})

    => {:data     {:verdict-section "9"}
        :template {:giver "test"}}

    (provided (lupapalvelu.mongo/get-next-sequence-value irrelevant) => irrelevant :times 0))

  (fact "Board verdict (lautakunta)"
    (insert-section "123-T" 1515151515151 {:data     {}
                                           :template {:giver "lautakunta"}})

    => {:data     {}
        :template {:giver "lautakunta"}}

    (provided (lupapalvelu.mongo/get-next-sequence-value irrelevant) => irrelevant :times 0)))

(facts "update automatic dates"                             ; TODO test "update-automatic-verdict-dates" function
  ; this should be continued done at some point,
  ; update-automatic-verdict-dates is given the "verdict" (result of 'command->verdict')
  ; which has keys 'category' 'verdict-data', 'references' 'template'
  #_(-> (set/rename-keys (:verdict verdict-draft) {:data :verdict-data})
       (assoc :category :r)
       (assoc-in [:verdict-data :automatic-verdict-dates] true)
       (assoc-in [:verdict-data :verdict-date] "1.3.2018")
       (lupapalvelu.pate.verdict/update-automatic-verdict-dates)))
