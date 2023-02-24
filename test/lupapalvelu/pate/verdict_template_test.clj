(ns lupapalvelu.pate.verdict-template-test
  (:require [lupapalvelu.pate.schemas :refer [PateSavedTemplate]]
            [lupapalvelu.pate.shared-schemas :as shared-schemas]
            [lupapalvelu.pate.verdict-template :refer :all]
            [lupapalvelu.pate.verdict-template-schemas :as template-schemas]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [schema.core :as sc]))

(testable-privates lupapalvelu.pate.verdict-template
                   sync-repeatings draft-for-publishing template-inclusions
                   pack-dependencies pack-verdict-dates published-settings)

(facts "sync-repeatings"
  (fact "Initially empty"
    (sync-repeatings nil nil nil) => {}
    (sync-repeatings nil {:id1 {:fi "suomi" :sv "svenska" :en "English"}} nil)
    => {:id1 {:text-fi "suomi" :text-sv "svenska" :text-en "English"}})
  (fact "New entry"
    (sync-repeatings nil {:id1 {:fi "suomi" :sv "svenska" :en "English"}
                          :id2 {:fi "suomi2" :sv "svenska2" :en "English2"}}
                     {:id1 {:text-sv "svenska" :foo "hello"}})
    => {:id1 {:text-fi "suomi" :text-sv "svenska" :text-en "English" :foo "hello"}
        :id2 {:text-fi "suomi2" :text-sv "svenska2" :text-en "English2"}})
  (fact "Removed entry"
    (sync-repeatings nil {:id2 {:fi "suomi2" :sv "svenska2" :en "English2"}}
                     {:id1 {:text-fi "suomi" :text-sv "svenska" :text-en "English" :foo "hello"}})
    => {:id2 {:text-fi "suomi2" :text-sv "svenska2" :text-en "English2"}}))

(facts "sync-repeatings: preserve manual sort order"
  (fact "Initially empty"
    (sync-repeatings :index nil nil) => {}
    (sync-repeatings :index {:id1 {:index 0 :fi "suomi" :sv "svenska" :en "English"}} nil)
    => {:id1 {:text-fi "suomi" :text-sv "svenska" :text-en "English" :index 0}})
  (fact "New entry"
    (sync-repeatings :index {:id1 {:fi "suomi" :sv "svenska" :en "English" :index 2}
                             :id2 {:fi "suomi2" :sv "svenska2" :en "English2" :index 1}}
                     {:id1 {:text-sv "svenska" :foo "hello"}})
    => {:id1 {:text-fi "suomi" :text-sv "svenska" :text-en "English" :foo "hello" :index 2}
        :id2 {:text-fi "suomi2" :text-sv "svenska2" :text-en "English2" :index 1}})
  (fact "Removed entry"
    (sync-repeatings :index {:id2 {:fi "suomi2" :sv "svenska2" :en "English2" :index 0}}
                     {:id1 {:text-fi "suomi" :text-sv "svenska" :text-en "English" :foo "hello"}})
    => {:id2 {:text-fi "suomi2" :text-sv "svenska2" :text-en "English2" :index 0}}))

(def test-template
  {:dictionary {:one              {:toggle {}}
                :two              {:date {}}
                :three            {:text {}}
                :four             {:repeating {:four-one   {:toggle {}}
                                               :four-two   {:text {}}
                                               :four-three {:excluded? true
                                                            :date      {}}}}
                :five             {:text {}}
                :six              {:excluded? true
                                   :toggle    {}}
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
  (provided (template-schemas/verdict-template-schema :r) => test-template)
  (draft-for-publishing {:category :r :draft {:one              true
                                              :two              nil
                                              :three            ""
                                              :removed-sections {:first  true
                                                                 :second false}
                                              :four             {:id1 {:four-one false
                                                                       :four-two "Yeah"}
                                                                 :id2 {:four-two   "haeY"
                                                                       :four-three 12345}}
                                              :six              true}})
  => {:one              true
      :removed-sections {:first  true
                         :second false}
      :four             [{:four-one false :four-two "Yeah"}
                         {:four-two "haeY"}]}
  (provided (template-schemas/verdict-template-schema :r) => test-template))

(facts "Template inclusions"
  (fact "Every section included"
    (template-inclusions {:category :r :draft {}})
    => (just ["one" "two" "three" "four" "five"] :in-any-order)
    (provided (template-schemas/verdict-template-schema :r) => test-template))
  (fact "First section removed"
    (template-inclusions {:category :r :draft {:removed-sections {:first true}}})
    => (just ["one" "two" "three" "four" "five"] :in-any-order)
    (provided (template-schemas/verdict-template-schema :r) => test-template))
  (fact "Second section removed"
    (template-inclusions {:category :r :draft {:removed-sections {:second true}}})
    => (just ["one" "three" "four" "five"] :in-any-order)
    (provided (template-schemas/verdict-template-schema :r) => test-template))
  (fact "Third section removed"
    (template-inclusions {:category :r :draft {:removed-sections {:third true}}})
    => (just ["one" "two" "three" "four" "five"] :in-any-order)
    (provided (template-schemas/verdict-template-schema :r) => test-template))
  (fact "Every section removed"
    (template-inclusions {:category :r :draft {:removed-sections {:first  true
                                                                  :second true
                                                                  :third  true}}})
    => (just ["three" "four" "five"] :in-any-order)
    (provided (template-schemas/verdict-template-schema :r) => test-template))
  (fact "Excluded?"
    (template-inclusions {:category :r :draft {}})
    => (just ["one" "two" "three" "four" "five" "six"] :in-any-order)
    (provided (template-schemas/verdict-template-schema :r)
              => (assoc-in test-template [:dictionary :six :excluded?] false))))

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

(defn make-template [category deleted? title subtitle]
  (sc/validate PateSavedTemplate
               (merge {:id        "5eb26fd57560453adbb29d8f"
                       :name      "Name"
                       :category  category
                       :deleted   {:_value    deleted?
                                   :_user     "user"
                                   :_modified 12345}
                       :modified  12345
                       :published {:published  {:_value    12345
                                                :_user     "user"
                                                :_modified 12345}
                                   :data       {:title    title
                                                :subtitle subtitle}
                                   :inclusions []
                                   :settings   {}}})))

(facts "pack-dependencies"
  (fact "Default sorting"
    (pack-dependencies :r
                       {:reviews {:rid1 {:fi "K1" :sv "S1" :en "R1"}
                                  :rid2 {:fi "K2" :sv "S2" :en "R2"}
                                  :rid3 {:fi "K3" :sv "S3" :en "R3"}}}
                       :reviews
                       {:reviews {:rid1 {:included true :selected true}
                                  :rid3 {:included true}}})
    => [{:fi "K1" :sv "S1" :en "R1" :selected true}
        {:fi "K3" :sv "S3" :en "R3" :selected false}]
    (provided (lupapalvelu.pate.settings-schemas/settings-schema :r)
              => {}))
  (fact "Manual sorting"
    (pack-dependencies :r
                       {:reviews {:rid1 {:fi "K1" :sv "S1" :en "R1" :index 2}
                                  :rid2 {:fi "K2" :sv "S2" :en "R2" :index 1}
                                  :rid3 {:fi "K3" :sv "S3" :en "R3" :index 0}}}
                       :reviews
                       {:reviews {:rid1 {:included true :selected true}
                                  :rid3 {:included true}}})
    => [{:fi "K3" :sv "S3" :en "R3" :selected false}
        {:fi "K1" :sv "S1" :en "R1" :selected true}]
    (provided (lupapalvelu.pate.settings-schemas/settings-schema :r)
              => {:dictionary {:reviews {:sort-by {:manual :index}}}})))

(facts "pack-verdict-dates"
  (pack-verdict-dates :r {:julkipano     1 :anto        2 :muutoksenhaku 3
                          :lainvoimainen 4 :aloitettava 5 :voimassa      6} false)
  => {:aloitettava   {:delta 5 :unit "years"}
      :anto          {:delta 2 :unit "days"}
      :julkipano     {:delta 1 :unit "days"}
      :lainvoimainen {:delta 4 :unit "days"}
      :muutoksenhaku {:delta 3 :unit "days"}
      :voimassa      {:delta 6 :unit "years"}}
  (pack-verdict-dates :r {:julkipano     1 :muutoksenhaku 3
                          :lainvoimainen 4 :voimassa      6} false)
  => {:aloitettava   {:delta 0 :unit "years"}
      :anto          {:delta 0 :unit "days"}
      :julkipano     {:delta 1 :unit "days"}
      :lainvoimainen {:delta 4 :unit "days"}
      :muutoksenhaku {:delta 3 :unit "days"}
      :voimassa      {:delta 6 :unit "years"}}
  (pack-verdict-dates :r {:julkipano                1 :anto        2 :muutoksenhaku 3
                          :lainvoimainen            4 :aloitettava 5 :voimassa      6
                          :lautakunta-muutoksenhaku 10} true)
  => {:aloitettava   {:delta 5 :unit "years"}
      :anto          {:delta 2 :unit "days"}
      :julkipano     {:delta 1 :unit "days"}
      :lainvoimainen {:delta 4 :unit "days"}
      :muutoksenhaku {:delta 10 :unit "days"}
      :voimassa      {:delta 6 :unit "years"}})

(let [settings {:draft {:organization-name        "Firm"
                        :verdict-code             ["one" "two" "three"]
                        :plans                    {:p1 {:fi "S1" :sv "P1" :en "P1"}
                                                   :p2 {:fi "S2" :sv "P2" :en "P2"}}
                        :reviews                  {:r1 {:fi "K1" :sv "S1" :en "R1" :type "hello"}
                                                   :r2 {:fi "K2" :sv "S2" :en "R2" :type "world"}}
                        :handler-titles           {:h1 {:fi "K1" :sv "H1" :en "H1"}
                                                   :h2 {:fi "K2" :sv "H2" :en "H2"}}
                        :julkipano                1 :anto        2 :muutoksenhaku 3
                        :lainvoimainen            4 :aloitettava 5 :voimassa      6
                        :lautakunta-muutoksenhaku 10
                        :boardname                "Board"}}]
 (facts "published-settings"
   (published-settings {} :r {})
   => {:verdict-code      ["one" "two" "three"]
       :organization-name "Firm"
       :date-deltas       {:aloitettava   {:delta 5 :unit "years"}
                           :anto          {:delta 2 :unit "days"}
                           :julkipano     {:delta 1 :unit "days"}
                           :lainvoimainen {:delta 4 :unit "days"}
                           :muutoksenhaku {:delta 3 :unit "days"}
                           :voimassa      {:delta 6 :unit "years"}}
       :plans             []
       :reviews           []
       :handler-titles    []}
   (provided (lupapalvelu.pate.verdict-template/settings anything) => settings)

   (published-settings {} :r {:giver          "lautakunta"
                              :reviews        {:r1 {:included true :selected true}}
                              :plans          {:p2 {:included true}}
                              :handler-titles {:h2 {:selected true}}})
   => {:verdict-code      ["one" "two" "three"]
       :organization-name "Firm"
       :date-deltas       {:aloitettava   {:delta 5 :unit "years"}
                           :anto          {:delta 2 :unit "days"}
                           :julkipano     {:delta 1 :unit "days"}
                           :lainvoimainen {:delta 4 :unit "days"}
                           :muutoksenhaku {:delta 10 :unit "days"}
                           :voimassa      {:delta 6 :unit "years"}}
       :boardname         "Board"
       :plans             [{:fi "S2" :sv "P2" :en "P2" :selected false}]
       :reviews           [{:fi "K1" :sv "S1" :en "R1" :type "hello" :selected true}]
       :handler-titles    []}
   (provided (lupapalvelu.pate.verdict-template/settings anything) => settings)

   (published-settings {} :r {:giver              "viranhaltija"
                              :reviews            {:r1 {:included true :selected true}}
                              :plans              {:p2 {:included true}}
                              :handler-titles     {:h2 {:included true :selected true}}
                              :julkipano          11 :anto        12 :muutoksenhaku 13
                              :lainvoimainen      14 :aloitettava 15 :voimassa      16
                              :custom-date-deltas true})
   => {:verdict-code      ["one" "two" "three"]
       :organization-name "Firm"
       :date-deltas       {:aloitettava   {:delta 15 :unit "years"}
                           :anto          {:delta 12 :unit "days"}
                           :julkipano     {:delta 11 :unit "days"}
                           :lainvoimainen {:delta 14 :unit "days"}
                           :muutoksenhaku {:delta 13 :unit "days"}
                           :voimassa      {:delta 16 :unit "years"}}
       :plans             [{:fi "S2" :sv "P2" :en "P2" :selected false}]
       :reviews           [{:fi "K1" :sv "S1" :en "R1" :type "hello" :selected true}]
       :handler-titles    [{:fi "K2" :sv "H2" :en "H2" :selected true}]}
   (provided (lupapalvelu.pate.verdict-template/settings anything) => settings)))
