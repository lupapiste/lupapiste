(ns lupapalvelu.pate.verdict-template-test
  (:require [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.pate.shared-schemas :as shared-schemas]
            [lupapalvelu.pate.verdict-template :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [schema.core :as sc]))

(testable-privates lupapalvelu.pate.verdict-template
                   sync-repeatings draft-for-publishing
                   template-inclusions)

(facts "sync-repeatings"
  (fact "Initially empty"
    (sync-repeatings :fi nil nil) => {}
    (sync-repeatings :fi {:id1 {:fi "suomi" :sv "svenska" :en "English"}} nil)
    => {:id1 {:text "suomi"}})
  (fact "New entry"
    (sync-repeatings :fi
                     {:id1 {:fi "suomi" :sv "svenska" :en "English"}
                      :id2 {:fi "suomi2" :sv "svenska2" :en "English2"}}
                     {:id1 {:text "svenska" :foo "hello"}})
    => {:id1 {:text "suomi" :foo "hello"}
        :id2 {:text "suomi2"}})
  (fact "Removed entry"
    (sync-repeatings :en
                     {:id2 {:fi "suomi2" :sv "svenska2" :en "English2"}}
                     {:id1 {:text "svenska" :foo "hello"}})
    => {:id2 {:text "English2"}}))


(def test-template
  {:dictionary {:one              {:toggle {}}
                :two              {:date {}}
                :three            {:text {}}
                :four             {:repeating {:four-one {:toggle {}}
                                               :four-two {:text {}}}}
                :five             {:text {}}
                :removed-sections {:keymap {:first  false
                                            :second false
                                            :third  false}}}
   :sections   [{:id   :first
                 :grid {:columns 4
                        :rows    [[{:dict :one}]]}}
                {:id               :second
                 :always-included? false
                 :grid             {:columns 4
                                    :rows    [[{:dict :one} {:dict :two}]]}}
                {:id               :third
                 :always-included? true
                 :grid             {:columns 4
                                    :rows    [[{:dict :three}]
                                              [{:grid {:columns   2
                                                       :repeating :four
                                                       :rows      [[{:dict :four-one}
                                                                    {:dict :four-two}]]}}]]}}]})

(fact "Test template is valid"
  (sc/validate shared-schemas/PateVerdictTemplate test-template)
  => test-template)

(fact "draft-for-publishing"
  (draft-for-publishing {:category :r :draft {:one   true
                                              :three "hello"}})
  => {:one   true
      :three "hello"}
  (provided (shared/verdict-template-schema :r) => test-template)
  (draft-for-publishing {:category :r :draft {:one              true
                                              :two              nil
                                              :three            ""
                                              :removed-sections {:first  true
                                                                 :second false}
                                              :four             {:id1 {:four-one false
                                                                       :four-two "Yeah"}
                                                                 :id2 {:four-two "haeY"}}}})
  => {:one              true
      :removed-sections {:first  true
                         :second false}
      :four             [{:four-one false :four-two "Yeah"}
                         {:four-two "haeY"}]}
  (provided (shared/verdict-template-schema :r) => test-template))

(facts "Template inclusions"
  (fact "Every section included"
    (template-inclusions {:category :r :draft {}})
    => (just ["one" "two" "three" "four" "five"] :in-any-order)
    (provided (shared/verdict-template-schema :r) => test-template))
  (fact "First section removed"
    (template-inclusions {:category :r :draft {:removed-sections {:first true}}})
    => (just ["one" "two" "three" "four" "five"] :in-any-order)
    (provided (shared/verdict-template-schema :r) => test-template))
  (fact "Second section removed"
    (template-inclusions {:category :r :draft {:removed-sections {:second true}}})
    => (just ["one" "three" "four" "five"] :in-any-order)
    (provided (shared/verdict-template-schema :r) => test-template))
  (fact "Third section removed"
    (template-inclusions {:category :r :draft {:removed-sections {:third true}}})
    => (just ["one" "two" "three" "four" "five"] :in-any-order)
    (provided (shared/verdict-template-schema :r) => test-template))
  (fact "Every section removed"
    (template-inclusions {:category :r :draft {:removed-sections {:first  true
                                                                  :second true
                                                                  :third  true}}})
    => (just ["three" "four" "five"] :in-any-order)
    (provided (shared/verdict-template-schema :r) => test-template)))

(facts "Operation->category"
  (fact "R" (operation->category "pientalo") => :r)
  (fact "YA" (operation->category "ya-katulupa-kaapelityot") => :ya)
  (fact "P" (operation->category "poikkeamis") => :p)
  (fact "YI" (operation->category "meluilmoitus") => :ymp)
  (fact "YM" (operation->category "koeluontoinen-toiminta") => :ymp)
  (fact "YL" (operation->category "pima") => :ymp)
  (fact "MAL" (operation->category "maa-aineslupa") => :ymp)
  (fact "VVVL" (operation->category "vvvl-vesijohdosta") => :ymp)
  (fact "KT keyword" (operation->category :tonttijako) => :kt)
  (fact "MM" (operation->category "asemakaava") => :kt)
  (fact "Foreman" (operation->category "tyonjohtajan-nimeaminen-v2") => :tj))
