(ns lupapalvelu.pate.verdict-test
  (:require [clj-time.coerce :as time-coerce]
            [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.pate.shared-schemas :as shared-schemas]
            [lupapalvelu.pate.verdict :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.util :as util]
            [schema.core :as sc]))

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


(def test-verdict
  {:dictionary
   {:one   {:text             {}
            :template-section :t-first}
    :two   {:toggle           {}
            :template-dict    :t-two
            :template-section :t-second}
    :three {:date          {}
            :template-dict :t-three}
    :four  {:text {}}
    :five  {:repeating {:r-one   {:text {}}
                        :r-two   {:toggle {}}
                        :r-three {:repeating {:r-sub-one {:date {}}
                                              :r-sub-two {:text {}}}}}}
    :six   {:toggle {}}}
   :sections [{:id   :first
               :grid {:columns 4
                      :rows    [[{:dict :one}]]}}
              {:id               :second
               :template-section :t-third
               :grid             {:columns 4
                                  :rows    [[{:dict :three}
                                             {:dict :four}]]}}
              {:id               :third
               :template-section :t-fourth
               :grid             {:columns 4
                                  :rows    [[{:dict :two}
                                             {:dict :three}]
                                            [{:grid {:columns   3
                                                     :repeating :five
                                                     :rows      [[{:dict :r-one}
                                                                  {:dict :r-two}
                                                                  {:grid {:columns   2
                                                                          :repeating :r-three
                                                                          :rows      [[{:dict :r-sub-one}
                                                                                       {:dict :r-sub-two}]]}}]]}}]]}}]})

(def mock-template
  {:dictionary {:t-two            {:toggle {}}
                :t-three          {:date {}}
                :removed-sections {:keymap {:t-first  false
                                            :t-second false
                                            :t-third  false
                                            :t-fourth false}}}
   :sections [{:id :t-first
               :grid {:columns 2
                      :rows [[{:dict :t-two}
                              {:dict :t-three}]]} }]})

(facts "Verdict validation"
  (facts "Schema creation"
    (fact "OK"
      (shared/build-verdict-schema :r 1 test-verdict)
      => (contains {:version 1})
      (provided (shared/verdict-template-schema :r)
                =>     (sc/validate shared-schemas/PateVerdictTemplate
                                    mock-template)))
    (fact "Template section t-first missing"
      (shared/build-verdict-schema :r 1 test-verdict)
      => (throws AssertionError)
      (provided (shared/verdict-template-schema :r)
                => (sc/validate shared-schemas/PateVerdictTemplate
                                (util/dissoc-in mock-template
                                                [:dictionary :removed-sections :keymap :t-first]))))
    (fact "Template section t-third missing"
      (shared/build-verdict-schema :r 1 test-verdict)
      => (throws AssertionError)
      (provided (shared/verdict-template-schema :r)
                => (sc/validate shared-schemas/PateVerdictTemplate
                                (util/dissoc-in mock-template
                                                [:dictionary :removed-sections :keymap :t-third]))))
    (fact "Template dict t-two missing"
      (shared/build-verdict-schema :r 1 test-verdict)
      => (throws AssertionError)
      (provided (shared/verdict-template-schema :r)
                => (sc/validate shared-schemas/PateVerdictTemplate
                                (util/dissoc-in mock-template
                                                [:dictionary :t-two]))))))

(facts "section-dicts"
  (fact "Verdict sections"
    (section-dicts (-> test-verdict :sections first))
    => #{:one}
    (section-dicts (-> test-verdict :sections second))
    => #{:three :four}
    (section-dicts (-> test-verdict :sections last))
    => #{:two :three :five})
  (fact "Verdict tmeplate section"
    (section-dicts (-> mock-template :sections first))
    => #{:t-two :t-three}))

(facts "dict-sections"
  (fact "Verdict"
    (dict-sections (:sections test-verdict))
    => {:one   #{:first}
        :two   #{:third}
        :three #{:second :third}
        :four  #{:second}
        :five  #{:third}})
  (fact "Template"
    (dict-sections (:sections mock-template))
    => {:t-two   #{:t-first}
        :t-three #{:t-first}}))

(fact "dicts->kw-paths"
  (dicts->kw-paths (:dictionary test-verdict))
  => (just [:one :two :three :four
            :five.r-one :five.r-two
            :five.r-three.r-sub-one :five.r-three.r-sub-two
            :six] :in-any-order)
  (dicts->kw-paths (:dictionary mock-template))
  => [:t-two :t-three :removed-sections] :in-any-order)

(facts "Inclusions"
  (fact "Every template section included"
    (inclusions :r {:data {:removed-sections {:t-first  false
                                              :t-second false
                                              :t-third  false
                                              :t-fourth false}}})
    => (just [:one :two :three :four
              :five.r-one :five.r-two
              :five.r-three.r-sub-one :five.r-three.r-sub-two
              :six] :in-any-order)
    (provided (shared/verdict-schema :r) => test-verdict))
  (fact "Template sections t-first and t-second removed"
    (inclusions :r {:data {:removed-sections {:t-first  true
                                              :t-second true
                                              :t-third  false
                                              :t-fourth false}}})
    => (just [:three ;; Not removed since also in :third
              :four
              :five.r-one :five.r-two
              :five.r-three.r-sub-one :five.r-three.r-sub-two
              :six] :in-any-order)
    (provided (shared/verdict-schema :r) => test-verdict))
    (fact "Template section t-third removed"
      (inclusions :r {:data {:removed-sections {:t-first  false
                                                :t-second false
                                                :t-third  true
                                                :t-fourth false}}})
    => (just [:one :two :three ;; Not removed since also in :second
              :five.r-one :five.r-two
              :five.r-three.r-sub-one :five.r-three.r-sub-two
              :six] :in-any-order)
    (provided (shared/verdict-schema :r) => test-verdict))
    (fact "Template section t-fourth removed"
      (inclusions :r {:data {:removed-sections {:t-first  false
                                                :t-second false
                                                :t-third  false
                                                :t-fourth true}}})
      => (just [:one :two ;; Not removed since dict's template-section overrides section's
                :three ;; ;; Not removed since also in :second
                :four :six] :in-any-order)
      (provided (shared/verdict-schema :r) => test-verdict))
    (fact "Template section t-third and t-fourth removed"
      (inclusions :r {:data {:removed-sections {:t-first  false
                                                :t-second false
                                                :t-third  true
                                                :t-fourth true}}})
      => (just [:one :two ;; Not removed since dict's template-section overrides section's
                :six] :in-any-order)
      (provided (shared/verdict-schema :r) => test-verdict))
    (fact "Every template section removed"
      (inclusions :r {:data {:removed-sections {:t-first  true
                                                :t-second true
                                                :t-third  true
                                                :t-fourth true}}})
      => (just [:six])
      (provided (shared/verdict-schema :r) => test-verdict)))
