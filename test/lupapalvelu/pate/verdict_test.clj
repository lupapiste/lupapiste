(ns lupapalvelu.pate.verdict-test
  (:require [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as krysp]
            [lupapalvelu.inspection-summary :as inspection-summary]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-test-util :refer :all]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.pdf :as pdf]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.schemas :as schemas]
            [lupapalvelu.pate.shared-schemas :as shared-schemas]
            [lupapalvelu.pate.verdict :refer :all]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.pate.verdict-schemas :as verdict-schemas]
            [lupapalvelu.pate.verdict-template-schemas :as template-schemas]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.core :refer [ok? fail!]]
            [sade.date :as date]
            [sade.shared-schemas :refer [object-id-pattern]]
            [sade.util :as util]
            [schema.core :as sc])
  (:import [clojure.lang ExceptionInfo]))

(testable-privates lupapalvelu.pate.verdict
                   next-section verdict->updates
                   panic-update-section
                   application-deviations
                   archive-info application-operation
                   verdict-attachment-items
                   bulletin-data
                   deletable-verdict-attachment-ids
                   ya-matti-integration-enabled?
                   app-documents-having-buildings)

(testable-privates lupapalvelu.pate.verdict-template
                   template-inclusions)

(fact verdict->updates
  (verdict->updates {:data {:foo "hello"}} :data)
  => {:verdict {:data {:foo "hello"}}
      :updates {$set {:pate-verdicts.$.data {:foo "hello"}}}}
  (verdict->updates {:data {:foo "hello"}
                     :template {:inclusions [:one :two]}
                     :published 12345}
                    :template.inclusions :published)
  => {:verdict {:data {:foo "hello"}
                :template {:inclusions [:one :two]}
                :published 12345}
      :updates {$set {:pate-verdicts.$.published 12345
                      :pate-verdicts.$.template.inclusions [:one :two]}}})

(fact "Resolve verdict attachment type"
  (schemas/resolve-verdict-attachment-type {:permitType "R"})
  => {:type-group "paatoksenteko" :type-id "paatos"}
  (schemas/resolve-verdict-attachment-type {:permitType "R"} :ilmoitus)
  => {:type-group "paatoksenteko" :type-id "ilmoitus"}
  (schemas/resolve-verdict-attachment-type {:permitType "R"} :bad)
  => {:type-group "muut" :type-id "muu"}
  (schemas/resolve-verdict-attachment-type {:permitType "R"} :cv)
  => {:type-group "osapuolet" :type-id "cv"}
  (schemas/resolve-verdict-attachment-type {:permitType "BAD"}) => nil
  (schemas/resolve-verdict-attachment-type {}) => nil
  (schemas/resolve-verdict-attachment-type {} nil) => nil
  (schemas/resolve-verdict-attachment-type {:permitType "BAD"} nil) => nil
  (schemas/resolve-verdict-attachment-type {:permitType "YA"})
  => {:type-group "muut" :type-id "paatos"}
  (schemas/resolve-verdict-attachment-type {:permitType "R"} :paatosehdotus)
  => {:type-group "paatoksenteko" :type-id "paatosehdotus"})

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

(facts finalize--section
  (let [args    {:application {:organization "123-T"}
                 :command     {:created 1515151515151 :data {:verdict-id "vid"}}}
        test-fn #(finalize--section (assoc args :verdict %))]
    (fact "section is not set"
      (test-fn {:data     {}
                :template {:giver "test"}})
      => {:updates {"$set" {:pate-verdicts.$.data.verdict-section "2"}}
          :verdict {:data     {:verdict-section "2"}
                    :template {:giver "test"}}}
      (provided
        (lupapalvelu.mongo/get-next-sequence-value "verdict_test_123-T_2018") => 2))

    (fact "section is blank"
      (test-fn {:data     {:verdict-section ""}
                :template {:giver "test"}})
      => {:updates {"$set" {:pate-verdicts.$.data.verdict-section "1"}}
          :verdict {:data     {:verdict-section "1"}
                    :template {:giver "test"}}}
      (provided
        (lupapalvelu.mongo/get-next-sequence-value "verdict_test_123-T_2018") => 1))

    (fact "section already given"
      (test-fn {:data     {:verdict-section "9"}
                :template {:giver "test"}})
      => nil
      (provided
        (lupapalvelu.mongo/get-next-sequence-value irrelevant) => irrelevant :times 0))

    (fact "Board verdict (lautakunta)"
      (test-fn {:data     {}
                :template {:giver "lautakunta"}})
      => nil
      (provided
        (lupapalvelu.mongo/get-next-sequence-value irrelevant) => irrelevant :times 0))

    (fact "Legacy verdict"
      (test-fn {:legacy?  true
                :data     {}
                :template {:giver "test"}})
      => nil
      (provided
        (lupapalvelu.mongo/get-next-sequence-value irrelevant) => irrelevant :times 0))

    (facts "panic-update-section"
      (fact "if section in updates, panic update would update verdict"
        (->> (test-fn {:data {} :template {:giver "test"}})
             :updates
             (panic-update-section (:command args)))
        => "did update"
        (provided
          (lupapalvelu.mongo/get-next-sequence-value "verdict_test_123-T_2018") => 666
          (verdict-update (:command args) {"$set" {:pate-verdicts.$.data.verdict-section "666"}}) => "did update"))

      (fact "if no section, no update"
        (->> (test-fn {:data {:verdict-section "666"} :template {:giver "test"}})
             :updates
             (panic-update-section (:command args)))
        => nil
        (provided
          (lupapalvelu.mongo/get-next-sequence-value "verdict_test_123-T_2018") => 666 :times 0
          (verdict-update anything anything) => nil :times 0)))))

(facts "update automatic dates"
  ;; Calculation is cumulative
  (let [refs {:date-deltas {:julkipano     {:delta 1 :unit "days"}
                            :anto          {:delta 2 :unit "days"}
                            :muutoksenhaku {:delta 3 :unit "days"}
                            :lainvoimainen {:delta 4 :unit "days"}
                            :aloitettava   {:delta 1 :unit "years"}
                            :voimassa      {:delta 2 :unit "years"}}}
        ts   #(date/timestamp %)]
    (fact "All dates included in the verdict"
      (update-automatic-verdict-dates {:category     :r
                                       :references   refs
                                       :template     {:inclusions (helper/verdict-dates :r)}
                                       :verdict-data {:automatic-verdict-dates true
                                                      :verdict-date            (ts "6.4.2018") ;; Friday
                                                      }})
      => {:julkipano     (ts "9.4.2018")
          :anto          (ts "11.4.2018")
          :muutoksenhaku (ts "16.4.2018") ;; Skips weekend
          :lainvoimainen (ts "20.4.2018")
          :aloitettava   (ts "20.4.2019")
          :voimassa      (ts "20.4.2021")})
    (fact "Calculation skips public holiday (Easter)"
      (update-automatic-verdict-dates {:category     :r
                                       :references   refs
                                       :template     {:inclusions (helper/verdict-dates :r)}
                                       :verdict-data {:automatic-verdict-dates true
                                                      :verdict-date            (ts "26.3.2018")}})
      => {:julkipano     (ts "27.3.2018")
          :anto          (ts "29.3.2018")
          :muutoksenhaku (ts "3.4.2018") ;; Skips Easter
          :lainvoimainen (ts "7.4.2018")
          :aloitettava   (ts "7.4.2019")
          :voimassa      (ts "7.4.2021")})
    (fact "Only some dates included, no skips"
      (update-automatic-verdict-dates {:category     :r
                                       :references   refs
                                       :template     {:inclusions [:anto :lainvoimainen :voimassa]}
                                       :verdict-data {:automatic-verdict-dates true
                                                      :verdict-date            (ts "26.3.2018")}})
      => {:anto          (ts "29.3.2018")
          :lainvoimainen (ts "7.4.2018")
          :voimassa      (ts "7.4.2021")})
    (fact "No automatic calculation flag"
      (update-automatic-verdict-dates {:category     :r
                                       :references   refs
                                       :template     {:inclusions (helper/verdict-dates :r)}
                                       :verdict-data {:automatic-verdict-dates false
                                                      :verdict-date            (ts "26.3.2018")}})
      => nil
      (update-automatic-verdict-dates {:category     :r
                                       :references   refs
                                       :template     {:inclusions (helper/verdict-dates :r)}
                                       :verdict-data {:verdict-date (ts "26.3.2018")}})
      => nil)
    (fact "Verdict date is not set"
      (update-automatic-verdict-dates {:category     :r
                                       :references   refs
                                       :template     {:inclusions (helper/verdict-dates :r)}
                                       :verdict-data {:automatic-verdict-dates true
                                                      :verdict-date            ""}})
      => nil
      (update-automatic-verdict-dates {:category     :r
                                       :references   refs
                                       :template     {:inclusions (helper/verdict-dates :r)}
                                       :verdict-data {:automatic-verdict-dates true}})
      => nil)

    (fact "Only dates within schema are included in the calculation"
      (update-automatic-verdict-dates {:category     :tj
                                       :references   refs
                                       :template     {:inclusions (helper/verdict-dates :tj)}
                                       :verdict-data {:automatic-verdict-dates true
                                                      :verdict-date            (ts "22.11.2020")}})
      => {:anto          (ts "24.11.2020")
          :muutoksenhaku (ts "27.11.2020")
          :lainvoimainen (ts "1.12.2020")}
      (update-automatic-verdict-dates {:category     :p
                                       :references   refs
                                       :template     {:inclusions (helper/verdict-dates :p)}
                                       :verdict-data {:automatic-verdict-dates true
                                                      :verdict-date            (ts "6.4.2018")}})
      => {:julkipano     (ts "9.4.2018")
          :anto          (ts "11.4.2018")
          :muutoksenhaku (ts "16.4.2018") ;; Skips weekend
          :lainvoimainen (ts "20.4.2018")
          :voimassa      (ts "20.4.2020")})
    (fact "Missing deltas are consoidered zeroes"
      (update-automatic-verdict-dates {:category     :tj
                                       :references (update refs :date-deltas dissoc :muutoksenhaku)
                                       :template     {:inclusions (helper/verdict-dates :tj)}
                                       :verdict-data {:automatic-verdict-dates true
                                                      :verdict-date            (ts "22.11.2020")}})
      => {:anto          (ts "24.11.2020")
          :muutoksenhaku (ts "24.11.2020")
          :lainvoimainen (ts "28.11.2020")}
      (update-automatic-verdict-dates {:category     :p
                                       :references   (update refs :date-deltas
                                                             dissoc :muutoksenhaku :lainvoimainen)
                                       :template     {:inclusions (helper/verdict-dates :p)}
                                       :verdict-data {:automatic-verdict-dates true
                                                      :verdict-date            (ts "6.4.2018")}})
      => {:julkipano     (ts "9.4.2018")
          :anto          (ts "11.4.2018")
          :muutoksenhaku (ts "11.4.2018")
          :lainvoimainen (ts "11.4.2018")
          :voimassa      (ts "11.4.2020")})))

(def test-verdict
  {:version  1
   :dictionary
   {:one   {:text             {}
            :template-section :t-first}
    :two   {:text             {}
            :template-dict    :t-two}
    :three {:date          {}
            :template-dict :t-three}
    :four  {:text {}}
    :five  {:repeating {:r-one   {:text {}}
                        :r-two   {:toggle {}}
                        :r-three {:repeating {:r-sub-one {:date {}}
                                              :r-sub-two {:text {}}}}}}
    :six   {:toggle {}}
    :seven {:repeating     {:up {:text {}}}
            :template-dict :t-seven}}
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
                                                                                       {:dict :r-sub-two}]]}}]]}}]]}}
              {:id               :fourth
               :template-section :t-fifth
               :grid             {:columns 1
                                  :rows    [[{:grid {:columns   1
                                                     :repeating :seven
                                                     :rows      [[{:dict :up}]]}}]]}}]})

(def mock-template
  {:dictionary {:t-one            {:text {}}
                :t-two            {:toggle {}}
                :t-three          {:date {}}
                :t-seven          {:repeating {:up {:text {}}}}
                :removed-sections {:keymap {:t-first  false
                                            :t-second false
                                            :t-third  false
                                            :t-fourth false
                                            :t-fifth  false
                                            :t-sixth  false}}}
   :sections   [{:id   :t-first
                 :grid {:columns 2
                        :rows    [[{:dict :t-one}]]} }
                {:id   :t-second
                 :grid {:columns 2
                        :rows    [[{:dict :t-two}
                                   {:dict :t-three}]]} }
                {:id   :t-third
                 :grid {:columns 2
                        :rows    [[{:dict :t-three}]]} }
                {:id   :t-sixth
                 :grid {:columns 2
                        :rows    [[{:dict :t-seven}]]} }]})

(facts "Build schemas"
  (fact "Dict missing"
    (schema-util/check-dicts (dissoc (:dictionary test-verdict) :three)
                        (:sections test-verdict))
    => (throws AssertionError))
  (fact "Dict in repeating missing"
    (schema-util/check-dicts (util/dissoc-in (:dictionary test-verdict)
                                        [:five :repeating :r-three :repeating :r-sub-two])
                        (:sections test-verdict))
    => (throws AssertionError))
  (fact "Overlapping dicts"
    (schema-util/check-overlapping-dicts [{:dictionary {:foo {:toggle {}}
                                                        :bar {:text {}}
                                                        :baz {:toggle {}}}}
                                          {:dictionary {:doo {:toggle {}}
                                                        :mar {:text {}}
                                                        :daz {:toggle {}}}}
                                          {:dictionary {:foo {:toggle {}}
                                                        :har {:text {}}
                                                        :baz {:toggle {}}}}])
    => (throws AssertionError))
  (fact "Unique section ids"
    (schema-util/check-unique-section-ids (:sections mock-template))
    => nil)
  (fact "Non-unique section ids"
    (schema-util/check-unique-section-ids (cons {:id :t-second}
                                           (:sections mock-template)))
    => (throws AssertionError))
  (fact "Combine subschemas"
    (schema-util/combine-subschemas {:dictionary {:foo {:toggle {}}
                                                  :bar {:text {}}}
                                     :sections        [{:id   :one
                                                        :grid {:columns 1
                                                               :rows    [[{:dict :foo}]]}}
                                                       {:id   :two
                                                        :grid {:columns 1
                                                               :rows    [[{:dict :bar}]]}}]}
                                    {:dictionary {:baz {:date {}}}
                                     :section    {:id   :three
                                                  :grid {:columns 1
                                                         :rows    [[{:dict :baz}]]}}})
    => {:dictionary {:foo {:toggle {}}
                     :bar {:text {}}
                     :baz {:date {}}}
        :sections   [{:id   :one
                      :grid {:columns 1
                             :rows    [[{:dict :foo}]]}}
                     {:id   :two
                      :grid {:columns 1
                             :rows    [[{:dict :bar}]]}}
                     {:id   :three
                      :grid {:columns 1
                             :rows    [[{:dict :baz}]]}}]})
  (fact "Build verdict template schema"
    (template-schemas/build-verdict-template-schema
      {:dictionary {:foo {:toggle {}}
                    :bar {:text {}}}
       :sections   [{:id   :one
                     :grid {:columns 1
                            :rows    [[{:dict :foo}]]}}
                    {:id   :two
                     :grid {:columns 1
                            :rows    [[{:dict :bar}]]}}]
       :removable? true}
      {:dictionary {:baz {:date {}}}
       :section    {:id   :three
                    :grid {:columns 1
                           :rows    [[{:dict :baz}]]}}
       :removable? true}
      {:dictionary {:dum {:date {}}}
       :section    {:id   :four
                    :grid {:columns 1
                           :rows    [[{:dict :dum}]]}}})
    => {:dictionary {:foo              {:toggle {}}
                     :bar              {:text {}}
                     :baz              {:date {}}
                     :dum              {:date {}}
                     :removed-sections {:keymap {:one   false
                                                 :two   false
                                                 :three false}}}
        :sections   [{:id   :one
                      :grid {:columns 1
                             :rows    [[{:dict :foo}]]}}
                     {:id   :two
                      :grid {:columns 1
                             :rows    [[{:dict :bar}]]}}
                     {:id   :three
                      :grid {:columns 1
                             :rows    [[{:dict :baz}]]}}
                     {:id   :four
                      :grid {:columns 1

                             :rows    [[{:dict :dum}]]}}]}))

(facts "Verdict validation"
  (facts "Schema creation"
    (fact "OK"
      (verdict-schemas/build-verdict-schema :r 1 test-verdict)
      => (contains {:version 1})
      (provided (template-schemas/verdict-template-schema :r)
                =>     (sc/validate shared-schemas/PateVerdictTemplate
                                    mock-template)))
    (fact "Template section t-first missing"
      (verdict-schemas/build-verdict-schema :r 1 test-verdict)
      => (throws AssertionError)
      (provided (template-schemas/verdict-template-schema :r)
                => (sc/validate shared-schemas/PateVerdictTemplate
                                (util/dissoc-in mock-template
                                                [:dictionary :removed-sections :keymap :t-first]))))
    (fact "Template section t-third missing"
      (verdict-schemas/build-verdict-schema :r 1 test-verdict)
      => (throws AssertionError)
      (provided (template-schemas/verdict-template-schema :r)
                => (sc/validate shared-schemas/PateVerdictTemplate
                                (util/dissoc-in mock-template
                                                [:dictionary :removed-sections :keymap :t-third]))))
    (fact "Template dict t-two missing"
      (verdict-schemas/build-verdict-schema :r 1 test-verdict)
      => (throws AssertionError)
      (provided (template-schemas/verdict-template-schema :r)
                => (sc/validate shared-schemas/PateVerdictTemplate
                                (util/dissoc-in mock-template
                                                [:dictionary :t-two]))))))

(facts "section-dicts"
  (fact "Verdict sections"
    (schemas/section-dicts (-> test-verdict :sections first))
    => #{:one}
    (schemas/section-dicts (-> test-verdict :sections second))
    => #{:three :four}
    (schemas/section-dicts (-> test-verdict :sections (nth 2)))
    => #{:two :three :five}
    (schemas/section-dicts (-> test-verdict :sections last))
    => #{:seven})
  (fact "Verdict tmeplate section"
    (schemas/section-dicts (-> mock-template :sections first))
    => #{:t-one}
    (schemas/section-dicts (-> mock-template :sections second))
    => #{:t-two :t-three}
    (schemas/section-dicts (-> mock-template :sections (nth 2)))
    => #{:t-three}
    (schemas/section-dicts (-> mock-template :sections last))
    => #{:t-seven}))

(facts "dict-sections"
  (fact "Verdict"
    (schemas/dict-sections (:sections test-verdict))
    => {:one   #{:first}
        :two   #{:third}
        :three #{:second :third}
        :four  #{:second}
        :five  #{:third}
        :seven #{:fourth}})
  (fact "Template"
    (schemas/dict-sections (:sections mock-template))
    => {:t-one   #{:t-first}
        :t-two   #{:t-second}
        :t-three #{:t-second  :t-third}
        :t-seven #{:t-sixth}}))

(fact "dicts->kw-paths"
  (dicts->kw-paths (:dictionary test-verdict))
  => (just [:one :two :three :four
            :five.r-one :five.r-two
            :five.r-three.r-sub-one :five.r-three.r-sub-two
            :six
            :seven.up] :in-any-order)
  (dicts->kw-paths (:dictionary mock-template))
  => (just [:t-one :t-two :t-three :t-seven.up :removed-sections]
           :in-any-order))

(defn mocker [removed-sections]
  (let [m {:removed-sections removed-sections}]
    {:data       m
     :inclusions (template-inclusions {:category :r
                                       :draft    m})}))
(against-background
 [(verdict-schemas/verdict-schema :r)          => test-verdict
  (template-schemas/verdict-template-schema :r) => mock-template]
 (facts "Inclusions"
   (fact "Every template section included"
     (inclusions :r (mocker {:t-first  false
                             :t-second false
                             :t-third  false
                             :t-fourth false
                             :t-fifth  false}))
     => (just [:one :two :three :four
               :five.r-one :five.r-two
               :five.r-three.r-sub-one :five.r-three.r-sub-two
               :six :seven.up] :in-any-order)
     (inclusions :r (mocker {}))
     => (just [:one :two :three :four
               :five.r-one :five.r-two
               :five.r-three.r-sub-one :five.r-three.r-sub-two
               :six :seven.up] :in-any-order))
   (fact "Template sections t-first and t-second removed"
     (inclusions :r (mocker {:t-first  true
                             :t-second true
                             :t-third  false
                             :t-fourth false
                             :t-fifth  false}))
     => (just [:three ;; Not removed since also in :third
               :four
               :five.r-one :five.r-two
               :five.r-three.r-sub-one :five.r-three.r-sub-two
               :six :seven.up] :in-any-order))
   (fact "Template section t-third removed"
     (inclusions :r (mocker {:t-first  false
                             :t-second false
                             :t-third  true
                             :t-fourth false
                             :t-fifth  false}))
     => (just [:one :two :three ;; Not removed since also in :second
               :five.r-one :five.r-two
               :five.r-three.r-sub-one :five.r-three.r-sub-two
               :six :seven.up] :in-any-order))
   (fact "Template section t-fourth removed"
     (inclusions :r (mocker {:t-first  false
                             :t-second false
                             :t-third  false
                             :t-fourth true
                             :t-fifth  false}))
     => (just [:one
               :three ;; ;; Not removed since also in :second
               :four :six :seven.up] :in-any-order))
   (fact "Template section t-third and t-fourth removed"
     (inclusions :r (mocker {:t-first  false
                             :t-second false
                             :t-third  true
                             :t-fourth true
                             :t-fifth  false}))
     => (just [:one :six :seven.up] :in-any-order))
   (fact "Template section t-third, t-fourth and t-sixth removed"
     (inclusions :r (mocker {:t-first  false
                             :t-second false
                             :t-third  true
                             :t-fourth true
                             :t-fifth  false
                             :t-sixth  true}))
     => (just [:one :six] :in-any-order))
   (fact "Every template section removed"
     (inclusions :r (mocker {:t-first  true
                             :t-second true
                             :t-third  true
                             :t-fourth true
                             :t-fifth  true
                             :t-sixth true}))
     => (just [:six]))))

(defn templater [removed & kvs]
  (let [data             (apply hash-map kvs)
        board?           (:giver data)
        removed-sections (zipmap removed (repeat true))
        draft (assoc data
                     :giver (if board?
                              "lautakunta" "viranhaltija")
                     :removed-sections removed-sections)]
    {:category  "r"
     :published {:data draft
                 :inclusions (template-inclusions {:category :r :draft draft})
                 :settings (cond-> {:verdict-code ["osittain-myonnetty"]}
                             board? (assoc :boardname "Gate is boarding"))}}))

(against-background
 [(verdict-schemas/verdict-schema "r") => test-verdict
  (template-schemas/verdict-template-schema :r) => mock-template]
 (facts "Initialize mock verdict draft"
   (fact "default, no removed sections"
     (default-verdict-draft (templater [] :t-two "Hello"))
     => {:category   "r"
         :schema-version 1
         :template   {:inclusions [:one :two :three :four
                                   :five.r-one :five.r-two
                                   :five.r-three.r-sub-one
                                   :five.r-three.r-sub-two
                                   :six :seven.up]}
         :data       {:two "Hello"}
         :references {:verdict-code ["osittain-myonnetty"]}})
   (fact "default, :t-second removed"
     (default-verdict-draft (templater [:t-second]
                                       :t-two "Hello"
                                       :giver true))
     => {:category   "r"
         :schema-version 1
         :template   {:inclusions [:one :three :four
                                   :five.r-one :five.r-two
                                   :five.r-three.r-sub-one
                                   :five.r-three.r-sub-two
                                   :six :seven.up]}
         :data       {}
         :references {:verdict-code ["osittain-myonnetty"]
                      :boardname    "Gate is boarding"}})
   (fact "default, repeating init"
     (let [draft (default-verdict-draft (templater [:t-first :t-third :t-fourth]
                                                   :t-two "Hello"
                                                   :t-seven [{:up "These"}
                                                             {:up "are"}
                                                             {:up "terms"}]))]
       draft => (contains {:template   {:inclusions [:six :seven.up]}
                           :references {:verdict-code ["osittain-myonnetty"]}})

       (-> draft :data :seven vals) => (just [{:up "These"}
                                              {:up "are"}
                                              {:up "terms"}])
       (-> draft :data :seven keys) => (has every? (partial re-matches #"[0-9a-z]+"))))))

(def mini-verdict-template
  {:dictionary
   {:paatosteksti     {:phrase-text {:category :paatosteksti}}
    :conditions       {:repeating {:condition {:phrase-text {:category :yleinen}}}}
    :removed-sections {:keymap {:foremen     false
                                :reviews     false
                                :plans       false
                                :attachments false
                                :conditions  false
                                :deviations  false
                                :buildings   false
                                :random      false}}}
   :sections [{:id   :verdict
               :grid {:columns 4
                      :rows    [[{:dict :paatostekesti}]]}}
              {:id   :conditions
               :grid {:columns 4
                      :rows    [[{:dict :conditions}]]}}]})

(def mini-verdict
  {:version  1
   :dictionary
   {:julkipano               {:date {}}
    :anto                    {:date {}}
    :muutoksenhaku           {:date {}}
    :lainvoimainen           {:date {}}
    :aloitettava             {:date {}}
    :voimassa                {:date {}}
    :handler                 {:text {}}
    :verdict-section         {:text {}}
    :boardname               {:reference {:path :*ref.boardname}}
    :automatic-verdict-dates {:toggle {}}
    :paatosteksti            {:phrase-text   {:category :paatosteksti}
                              :template-dict :paatosteksti}
    :conditions              {:repeating     {:condition
                                              {:phrase-text {:category :yleinen}}
                                              :remove-condition {:button {:remove :conditions}}}
                              :template-dict :conditions}
    :add-condition           {:button           {:add :conditions}
                              :template-section :conditions}
    :deviations              {:phrase-text      {:category :yleinen}
                              :template-section :deviations}
    :foremen                 {:reference-list {:path :foremen
                                               :type :multi-select}}
    :foremen-included        {:toggle {}}
    :reviews-included        {:toggle {}}
    :reviews                 {:reference-list {:path :reviews
                                               :type :multi-select}}
    :plans                   {:reference-list {:path :plans
                                               :type :multi-select}}
    :plans-included          {:toggle {}}
    :upload                  {:attachments      {}
                              :template-section :attachments}
    :attachments             {:application-attachments {}
                              :template-section        :attachments}
    :buildings               {:repeating        {:rakennetut-autopaikat  {:text {}}
                                                 :kiinteiston-autopaikat {:text {}}
                                                 :autopaikat-yhteensa    {:text {}}
                                                 :vss-luokka             {:text {}}
                                                 :paloluokka             {:text {}}
                                                 :kokoontumistilanHenkilomaara {:text {}}}
                              :template-section :buildings}
    :start-date              {:date             {}
                              :template-section :random}
    :end-date                {:date             {}
                              :template-section :random}}
   :sections []})

(defn draftee [& args]
  (let [t (apply templater args)]
    {:template t
     :draft    (default-verdict-draft t)}))

(defn check-draft [& kvs]
  (let [{:keys [inclusions giver references data]} (apply hash-map kvs)]
    (fn [{draft :draft}]
      (facts "check draft part of initmap"
       (when inclusions
         (fact "inclusions"
           (-> draft :template :inclusions)
           => (just inclusions :in-any-order)))
       (when giver
         (fact "giver"
           (-> draft :template :giver) => giver))
       (when references
         (fact "references"
           (:references draft) => (just references)))
       (when data
         (fact "data"
           (:data draft) => (just data)))))))

(defn mongo-id? [v]
  (re-matches object-id-pattern v))

(against-background
 [(template-schemas/verdict-template-schema "r") => mini-verdict-template
  (template-schemas/verdict-template-schema :r) => mini-verdict-template
  (verdict-schemas/verdict-schema "r")          => mini-verdict]
 (facts "Initialize verdict draft"
   (fact "Minis are valid"
     (sc/validate shared-schemas/PateVerdictTemplate
                  mini-verdict-template)
     => mini-verdict-template
     (sc/validate shared-schemas/PateVerdict
                  mini-verdict)
     => mini-verdict)

   (fact "init foremen: no foremen in template"
     (init--foremen (draftee [:conditions :deviations :attachments
                              :buildings]))
     => (check-draft :inclusions [:julkipano :anto :lainvoimainen
                                  :voimassa :muutoksenhaku :aloitettava
                                  :handler :paatosteksti :plans
                                  :plans-included :reviews :reviews-included
                                  :automatic-verdict-dates :verdict-section
                                  :boardname :start-date :end-date]
                     :references {:verdict-code ["osittain-myonnetty"]}
                     :data {}))
   (fact "init foremen: no foremen included"
     (init--foremen (draftee [:conditions :deviations :attachments
                              :buildings]
                             :vastaava-tj false
                             :vastaava-tj-included false
                             :vv-tj true
                             :iv-tj false
                             :iv-tj-included false))
     => (check-draft :inclusions [:julkipano :anto :lainvoimainen
                                  :voimassa :muutoksenhaku :aloitettava
                                  :handler :paatosteksti :plans
                                  :plans-included :reviews :reviews-included
                                  :automatic-verdict-dates  :start-date
                                  :end-date :verdict-section :boardname]
                     :references {:verdict-code ["osittain-myonnetty"]}
                     :data {}))
   (facts "init foremen: three foremen included, one selected"
     (fact "No order"
       (init--foremen (draftee [:conditions :deviations :attachments
                                :buildings]
                               :vastaava-tj false
                               :vastaava-tj-included true
                               :vv-tj true
                               :vv-tj-included true
                               :iv-tj false
                               :iv-tj-included true
                               :erityis-tj true
                               :erityis-tj-included false
                               :tj false))
       => (check-draft :inclusions [:julkipano :anto :lainvoimainen
                                    :voimassa :muutoksenhaku :aloitettava
                                    :handler :paatosteksti :plans
                                    :plans-included :reviews :reviews-included
                                    :automatic-verdict-dates
                                    :verdict-section :boardname
                                    :foremen :foremen-included
                                    :start-date :end-date]
                       :references {:verdict-code ["osittain-myonnetty"]
                                    :foremen      (just ["vastaava-tj" "vv-tj" "iv-tj"]
                                                        :in-any-order)}
                       :data {:foremen          ["vv-tj"]
                              :foremen-included true}))
     (fact "Order: iv-tj, erityis-tj, vastaava-tj, tj, vv-tj"
       (init--foremen (draftee [:conditions :deviations :attachments
                                :buildings]
                               :vastaava-tj false
                               :vastaava-tj-included true
                               :vv-tj true
                               :vv-tj-included true
                               :iv-tj false
                               :iv-tj-included true
                               :erityis-tj true
                               :erityis-tj-included false
                               :tj false
                               :tj-order ["iv-tj" "erityis-tj" "vastaava-tj" "tj" "vv-tj"]))
       => (check-draft :inclusions [:julkipano :anto :lainvoimainen
                                    :voimassa :muutoksenhaku :aloitettava
                                    :handler :paatosteksti :plans
                                    :plans-included :reviews :reviews-included
                                    :automatic-verdict-dates
                                    :verdict-section :boardname
                                    :foremen :foremen-included
                                    :start-date :end-date]
                       :references {:verdict-code ["osittain-myonnetty"]
                                    :foremen      ["iv-tj" "vastaava-tj" "vv-tj"]}
                       :data {:foremen          ["vv-tj"]
                              :foremen-included true})))
   (fact "init foremen: all foremen included, everyone selected, section removed"
     (fact "No order"
       (init--foremen (draftee [:conditions :deviations :attachments
                                :buildings :foremen]
                               :vastaava-tj true
                               :vastaava-tj-included true
                               :vv-tj true
                               :vv-tj-included true
                               :iv-tj true
                               :iv-tj-included true
                               :erityis-tj true
                               :erityis-tj-included true
                               :tj true
                               :tj-included true))
       => (check-draft :inclusions [:julkipano :anto :lainvoimainen
                                    :voimassa :muutoksenhaku :aloitettava
                                    :handler :paatosteksti :plans
                                    :plans-included :reviews :reviews-included
                                    :automatic-verdict-dates
                                    :verdict-section :boardname
                                    :foremen :foremen-included
                                    :start-date :end-date]
                       :references {:verdict-code ["osittain-myonnetty"]
                                    :foremen      (just ["vastaava-tj" "vv-tj" "iv-tj"
                                                         "erityis-tj" "tj"]
                                                        :in-any-order)}
                       :data {:foremen          (just ["vastaava-tj" "vv-tj" "iv-tj"
                                                       "erityis-tj" "tj"]
                                                      :in-any-order)
                              :foremen-included false}))
     (fact "Order: iv-tj, erityis-tj, vastaava-tj, tj, vv-tj"
       (init--foremen (draftee [:conditions :deviations :attachments
                                :buildings :foremen]
                               :vastaava-tj true
                               :vastaava-tj-included true
                               :vv-tj true
                               :vv-tj-included true
                               :iv-tj true
                               :iv-tj-included true
                               :erityis-tj true
                               :erityis-tj-included true
                               :tj true
                               :tj-included true
                               :tj-order ["iv-tj" "erityis-tj" "vastaava-tj" "tj" "vv-tj"]))
       => (check-draft :inclusions [:julkipano :anto :lainvoimainen
                                    :voimassa :muutoksenhaku :aloitettava
                                    :handler :paatosteksti :plans
                                    :plans-included :reviews :reviews-included
                                    :automatic-verdict-dates
                                    :verdict-section :boardname
                                    :foremen :foremen-included
                                    :start-date :end-date]
                       :references {:verdict-code ["osittain-myonnetty"]
                                    :foremen      ["iv-tj" "erityis-tj" "vastaava-tj"
                                                   "tj" "vv-tj"]}
                       :data {:foremen          (just ["vastaava-tj" "vv-tj" "iv-tj"
                                                       "erityis-tj" "tj"]
                                                      :in-any-order)
                              :foremen-included false})))

   (fact "Init requirements references: no plans"
     (init--requirements-references (draftee [:conditions :deviations :attachments
                                              :buildings])
                                    :plans)
     => (check-draft :inclusions [:julkipano :anto :lainvoimainen
                                  :voimassa :muutoksenhaku :aloitettava
                                  :handler :paatosteksti
                                  :reviews :reviews-included
                                  :foremen :foremen-included
                                  :automatic-verdict-dates
                                  :verdict-section :boardname
                                  :start-date :end-date]
                     :references {:verdict-code ["osittain-myonnetty"]}
                     :data {}))
   (fact "Init requirements references: no reviews"
     (init--requirements-references (draftee [:conditions :deviations :attachments
                                              :buildings])
                                    :reviews)
     => (check-draft :inclusions [:julkipano :anto :lainvoimainen
                                  :voimassa :muutoksenhaku :aloitettava
                                  :handler :paatosteksti
                                  :plans :plans-included
                                  :foremen :foremen-included
                                  :automatic-verdict-dates
                                  :verdict-section :boardname
                                  :start-date :end-date]
                     :references {:verdict-code ["osittain-myonnetty"]}
                     :data {}))
   (fact "Init requirements references: plan, not selected"
     (init--requirements-references (assoc-in (draftee [:conditions :deviations :attachments
                                                        :buildings])
                                              [:template :published :settings :plans]
                                              [{:fi "suomi" :sv "svenska" :en "english"}])
                                    :plans)
     => (check-draft :inclusions [:julkipano :anto :lainvoimainen
                                  :voimassa :muutoksenhaku :aloitettava
                                  :handler :paatosteksti
                                  :reviews :reviews-included
                                  :plans :plans-included
                                  :foremen :foremen-included
                                  :automatic-verdict-dates
                                  :verdict-section :boardname
                                  :start-date :end-date]
                     :references {:verdict-code ["osittain-myonnetty"]
                                  :plans        (just [(just {:fi "suomi"
                                                              :sv "svenska"
                                                              :en "english"
                                                              :id mongo-id?})])}
                     :data {:plans-included true}))
   (fact "Init requirements references: two plans, one selected"
     (let [result  (init--requirements-references (assoc-in (draftee [:conditions :deviations :attachments
                                                                      :buildings])
                                                            [:template :published :settings :plans]
                                                            [{:fi "suomi" :sv "svenska" :en "english" :selected true}
                                                             {:fi "imous" :sv "aksnevs" :en "hsilgne"}])
                                                  :plans)
           find-id #(:id (util/find-by-key :fi % (get-in result [:draft :references :plans])))]
       result => (check-draft :inclusions [:julkipano :anto :lainvoimainen
                                           :voimassa :muutoksenhaku :aloitettava
                                           :handler :paatosteksti
                                           :reviews :reviews-included
                                           :plans :plans-included
                                           :foremen :foremen-included
                                           :automatic-verdict-dates
                                           :verdict-section :boardname
                                           :start-date :end-date]
                              :references {:verdict-code ["osittain-myonnetty"]
                                           :plans        [{:fi "suomi"
                                                           :sv "svenska"
                                                           :en "english"
                                                           :id (find-id "suomi")}
                                                          {:fi "imous"
                                                           :sv "aksnevs"
                                                           :en "hsilgne"
                                                           :id (find-id "imous")}]}
                              :data {:plans-included true
                                     :plans          [(find-id "suomi")]})))
   (fact "Init requirements references: two reviews, both selected, section removed"
     (let [result  (init--requirements-references (assoc-in (draftee [:conditions :deviations :attachments
                                                                      :buildings :reviews])
                                                            [:template :published :settings :reviews]
                                                            [{:fi       "suomi" :sv   "svenska" :en "english"
                                                              :selected true    :type "hello"}
                                                             {:fi       "imous" :sv   "aksnevs" :en "hsilgne"
                                                              :selected true    :type "olleh"}])
                                                  :reviews)
           find-id #(:id (util/find-by-key :fi % (get-in result [:draft :references :reviews])))]
       result => (check-draft :inclusions [:julkipano :anto :lainvoimainen
                                           :voimassa :muutoksenhaku :aloitettava
                                           :handler :paatosteksti
                                           :reviews :reviews-included
                                           :plans :plans-included
                                           :foremen :foremen-included
                                           :automatic-verdict-dates
                                           :verdict-section :boardname
                                           :start-date :end-date]
                              :references {:verdict-code ["osittain-myonnetty"]
                                           :reviews      [{:fi   "suomi"
                                                           :sv   "svenska"
                                                           :en   "english"
                                                           :type "hello"
                                                           :id   (find-id "suomi")}
                                                          {:fi   "imous"
                                                           :sv   "aksnevs"
                                                           :en   "hsilgne"
                                                           :type "olleh"
                                                           :id   (find-id "imous")}]}
                              :data {:reviews-included false
                                     :reviews          (just [(find-id "suomi") (find-id "imous")] :in-any-order)})))

   (fact "init verdict-dates"
     (init--verdict-dates (draftee [:conditions :deviations :attachments
                                    :buildings]
                                   :verdict-dates []))
     => (check-draft :inclusions [:handler :paatosteksti :foremen
                                  :foremen-included :plans
                                  :verdict-section :boardname
                                  :plans-included :reviews :reviews-included
                                   :start-date :end-date]
                     :references {:verdict-code ["osittain-myonnetty"]}
                     :data       {})
     (init--verdict-dates (draftee [:conditions :deviations :attachments
                                    :buildings]
                                   :verdict-dates [:julkipano
                                                   :anto
                                                   :voimassa]))
     => (check-draft :inclusions [:handler :paatosteksti :foremen
                                  :foremen-included :plans
                                  :verdict-section :boardname
                                  :plans-included :reviews :reviews-included
                                  :julkipano :anto :voimassa
                                  :automatic-verdict-dates
                                  :start-date :end-date]
                     :references {:verdict-code ["osittain-myonnetty"]}
                     :data       {}))
   (fact "init upload: upload unchecked"
     (init--upload (draftee []))
     => (check-draft :inclusions [:julkipano :anto :lainvoimainen
                                  :voimassa :muutoksenhaku :aloitettava
                                  :automatic-verdict-dates
                                  :verdict-section :boardname
                                  :handler :paatosteksti :foremen
                                  :foremen-included :plans
                                  :plans-included :reviews :reviews-included
                                  :conditions.condition
                                  :conditions.remove-condition
                                  :add-condition :deviations
                                  :attachments :buildings.rakennetut-autopaikat
                                  :buildings.kiinteiston-autopaikat
                                  :buildings.autopaikat-yhteensa
                                  :buildings.vss-luokka :buildings.paloluokka
                                  :buildings.kokoontumistilanHenkilomaara
                                  :start-date :end-date]
                     :references {:verdict-code ["osittain-myonnetty"]}
                     :data {}))
   (fact "init upload: upload checked"
     (init--upload (draftee [] :upload true))
     => (check-draft :inclusions [:julkipano :anto :lainvoimainen
                                  :voimassa :muutoksenhaku :aloitettava
                                  :handler :paatosteksti :foremen
                                  :foremen-included :plans
                                  :verdict-section :boardname
                                  :automatic-verdict-dates
                                  :plans-included :reviews :reviews-included
                                  :conditions.condition
                                  :conditions.remove-condition
                                  :add-condition :deviations
                                  :attachments :upload
                                  :buildings.rakennetut-autopaikat
                                  :buildings.kiinteiston-autopaikat
                                  :buildings.autopaikat-yhteensa
                                  :buildings.vss-luokka :buildings.paloluokka
                                  :buildings.kokoontumistilanHenkilomaara
                                  :start-date :end-date]
                     :references {:verdict-code ["osittain-myonnetty"]}
                     :data {}))
   (fact "init upload: upload checked, attachments removed"
     (init--upload (draftee [:attachments] :upload true))
     => (check-draft :inclusions [:julkipano :anto :lainvoimainen
                                  :voimassa :muutoksenhaku :aloitettava
                                  :automatic-verdict-dates
                                  :handler :paatosteksti :foremen
                                  :foremen-included :plans
                                  :plans-included :reviews :reviews-included
                                  :conditions.condition
                                  :verdict-section :boardname
                                  :conditions.remove-condition
                                  :add-condition :deviations
                                  :buildings.rakennetut-autopaikat
                                  :buildings.kiinteiston-autopaikat
                                  :buildings.autopaikat-yhteensa
                                  :buildings.vss-luokka :buildings.paloluokka
                                  :buildings.kokoontumistilanHenkilomaara
                                  :start-date :end-date]
                     :references {:verdict-code ["osittain-myonnetty"]}
                     :data {}))
   (fact "init upload: upload unchecked, attachments removed"
     (init--upload (draftee [:attachments] :upload false))
     => (check-draft :inclusions [:julkipano :anto :lainvoimainen
                                  :voimassa :muutoksenhaku :aloitettava
                                  :automatic-verdict-dates
                                  :handler :paatosteksti :foremen
                                  :foremen-included :plans
                                  :plans-included :reviews :reviews-included
                                  :conditions.condition
                                  :conditions.remove-condition
                                  :add-condition :deviations
                                  :verdict-section :boardname
                                  :buildings.rakennetut-autopaikat
                                  :buildings.kiinteiston-autopaikat
                                  :buildings.autopaikat-yhteensa
                                  :buildings.vss-luokka :buildings.paloluokka
                                  :buildings.kokoontumistilanHenkilomaara
                                  :start-date :end-date]
                     :references {:verdict-code ["osittain-myonnetty"]}
                     :data {}))
   (fact "init verdict giver type: viranhaltija"
     (init--verdict-giver-type (draftee [:conditions :deviations
                                         :attachments :buildings]))
     => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                  :lainvoimainen :voimassa :aloitettava
                                  :handler :paatosteksti :foremen
                                  :foremen-included :plans
                                  :automatic-verdict-dates
                                  :plans-included :reviews
                                  :reviews-included :start-date :end-date]
                     :giver "viranhaltija"
                     :references {:verdict-code ["osittain-myonnetty"]}
                     :data {}))
   (fact "init verdict giver type: lautakunta"
     (init--verdict-giver-type (draftee [:conditions :deviations
                                         :attachments :buildings]
                                        :giver true))
     => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                  :lainvoimainen :voimassa :aloitettava
                                  :handler :paatosteksti :foremen
                                  :foremen-included :plans
                                  :verdict-section :boardname
                                  :automatic-verdict-dates
                                  :plans-included :reviews
                                  :reviews-included :start-date :end-date]
                     :giver "lautakunta"
                     :references {:boardname    "Gate is boarding"
                                  :verdict-code ["osittain-myonnetty"]}
                     :data {}))
   (fact "init by application: handler - no handler"
     (init--dict-by-application (assoc (draftee [:conditions :deviations
                                                 :attachments :buildings])
                                       :application {})
                                :handler general-handler)
     => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                  :lainvoimainen :voimassa :aloitettava
                                  :handler :paatosteksti :foremen
                                  :foremen-included :plans
                                  :automatic-verdict-dates
                                  :verdict-section :boardname
                                  :plans-included :reviews
                                  :reviews-included :start-date :end-date]
                     :references {:verdict-code ["osittain-myonnetty"]}
                     :data {:handler ""}))
   (fact "init by application: handler - Bob Builder"
     (init--dict-by-application (assoc (draftee [:conditions :deviations
                                                 :attachments :buildings])
                                       :application {:handlers [{:general   true
                                                                 :firstName "Bob"
                                                                 :lastName  "Builder"}]})
                                :handler general-handler)
     => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                  :lainvoimainen :voimassa :aloitettava
                                  :handler :paatosteksti :foremen
                                  :foremen-included :plans
                                  :automatic-verdict-dates
                                  :verdict-section :boardname
                                  :plans-included :reviews
                                  :reviews-included :start-date :end-date]
                     :references {:verdict-code ["osittain-myonnetty"]}
                     :data {:handler "Bob Builder"}))
   (fact "init by application: deviations - no deviations"
     (init--dict-by-application (assoc (draftee [:conditions :attachments
                                                 :buildings])
                                       :application {:documents []})
                                :deviations application-deviations)
     => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                  :lainvoimainen :voimassa :aloitettava
                                  :handler :paatosteksti :foremen
                                  :foremen-included :plans
                                  :automatic-verdict-dates
                                  :verdict-section :boardname
                                  :plans-included :reviews
                                  :reviews-included :deviations
                                  :start-date :end-date]
                     :references {:verdict-code ["osittain-myonnetty"]}
                     :data {:deviations ""}))
      (fact "init by application: deviations - Cannot live by your rules, man!"
        (init--dict-by-application (assoc (draftee [:conditions :attachments :buildings])
                                          :application
                                          {:documents
                                           [{:schema-info {:name "hankkeen-kuvaus"}
                                             :data        {:poikkeamat {:value "Cannot live by your rules, man!"}}}]})
                                   :deviations application-deviations)
        => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                     :lainvoimainen :voimassa :aloitettava
                                     :handler :paatosteksti :foremen
                                     :foremen-included :plans
                                     :automatic-verdict-dates
                                     :plans-included :reviews
                                     :verdict-section :boardname
                                     :reviews-included :deviations
                                     :start-date :end-date]
                        :references {:verdict-code ["osittain-myonnetty"]}
                        :data {:deviations "Cannot live by your rules, man!"}))
      (fact "init by application: deviations - template section removed"
        (init--dict-by-application (assoc (draftee [:conditions :attachments
                                                    :deviations :buildings])
                                          :application
                                          {:documents
                                           [{:schema-info {:name "hankkeen-kuvaus"}
                                             :data        {:poikkeamat {:value "Cannot live by your rules, man!"}}}]})
                                   :deviations application-deviations)
        => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                     :lainvoimainen :voimassa :aloitettava
                                     :handler :paatosteksti :foremen
                                     :foremen-included :plans
                                     :verdict-section :boardname
                                     :automatic-verdict-dates
                                     :plans-included :reviews
                                     :reviews-included :start-date :end-date]
                        :references {:verdict-code ["osittain-myonnetty"]}
                        :data {}))
      (fact "init by application: operation - dict not included"
        (init--dict-by-application (assoc (draftee [:conditions :attachments
                                                    :deviations :buildings])
                                          :application
                                          {:documents
                                           [{:schema-info {:name    "hankkeen-kuvaus"
                                                           :subtype "hankkeen-kuvaus"}
                                             :data        {:kuvaus {:value "Co-operation"}}}]})
                                   :operation application-operation)
        => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                     :lainvoimainen :voimassa :aloitettava
                                     :handler :paatosteksti :foremen
                                     :foremen-included :plans
                                     :verdict-section :boardname
                                     :automatic-verdict-dates
                                     :plans-included :reviews
                                     :reviews-included :start-date :end-date]
                        :references {:verdict-code ["osittain-myonnetty"]}
                        :data {}))
      (fact "init by application: operation - dict included, R application"
        (init--dict-by-application (assoc (draftee [:conditions :attachments
                                                    :deviations :buildings])
                                          :application
                                          {:documents
                                           [{:schema-info {:name    "hankkeen-kuvaus"
                                                           :subtype "hankkeen-kuvaus"}
                                             :data        {:kuvaus {:value "Co-operation"}}}]})
                                   :handler application-operation)
        => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                     :lainvoimainen :voimassa :aloitettava
                                     :handler :paatosteksti :foremen
                                     :foremen-included :plans
                                     :verdict-section :boardname
                                     :automatic-verdict-dates
                                     :plans-included :reviews
                                     :reviews-included :start-date :end-date]
                        :references {:verdict-code ["osittain-myonnetty"]}
                        :data {:handler "Co-operation"}))
      (fact "init by application: operation - dict included, YA application"
        (init--dict-by-application (assoc (draftee [:conditions :attachments
                                                    :deviations :buildings])
                                          :application
                                          {:documents
                                           [{:schema-info {:name    "hankkeen-kuvaus-ya"
                                                           :subtype "hankkeen-kuvaus"}
                                             :data        {:kayttotarkoitus {:value "Co-operation"}}}]})
                                   :handler application-operation)
        => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                     :lainvoimainen :voimassa :aloitettava
                                     :handler :paatosteksti :foremen
                                     :foremen-included :plans
                                     :verdict-section :boardname
                                     :automatic-verdict-dates
                                     :plans-included :reviews
                                     :reviews-included :start-date :end-date]
                        :references {:verdict-code ["osittain-myonnetty"]}
                        :data {:handler "Co-operation"}))
      (fact "init by application: operation - dict included, no document"
        (init--dict-by-application (assoc (draftee [:conditions :attachments
                                                    :deviations :buildings])
                                          :application
                                          {:documents
                                           [{:schema-info {:name "some-document"}
                                             :data        {:kayttotarkoitus {:value "Co-operation"}}}]})
                                   :handler application-operation)
        => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                     :lainvoimainen :voimassa :aloitettava
                                     :handler :paatosteksti :foremen
                                     :foremen-included :plans
                                     :verdict-section :boardname
                                     :automatic-verdict-dates
                                     :plans-included :reviews
                                     :reviews-included :start-date :end-date]
                        :references {:verdict-code ["osittain-myonnetty"]}
                        :data {:handler ""}))
      (fact "init buildings: no buildings, no template data"
        (init--buildings (draftee [:conditions :deviations :attachments :buildings]))
        => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                     :lainvoimainen :voimassa
                                     :automatic-verdict-dates :aloitettava
                                     :handler :paatosteksti :foremen
                                     :foremen-included :plans
                                     :plans-included :reviews
                                     :verdict-section :boardname
                                     :reviews-included :start-date :end-date]
                        :references {:verdict-code ["osittain-myonnetty"]}
                        :data {}))
      (fact "init buildings: no buildings, template data"
        (init--buildings (draftee [:conditions :deviations :attachments :buildings]
                                  :autopaikat true))
        => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                     :automatic-verdict-dates
                                     :lainvoimainen :voimassa :aloitettava
                                     :handler :paatosteksti :foremen
                                     :foremen-included :plans
                                     :verdict-section :boardname
                                     :plans-included :reviews
                                     :reviews-included :start-date :end-date]
                        :references {:verdict-code ["osittain-myonnetty"]}
                        :data {}))
      (fact "init buildings: buildings, no template data"
        (init--buildings (draftee [:conditions :deviations :attachments]))
        => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                     :lainvoimainen :voimassa :aloitettava
                                     :handler :paatosteksti :foremen
                                     :foremen-included :plans
                                     :verdict-section :boardname
                                     :automatic-verdict-dates
                                     :plans-included :reviews
                                     :reviews-included :start-date :end-date]
                        :references {:verdict-code ["osittain-myonnetty"]}
                        :data {}))
      (fact "init buildings: buildings, autopaikat"
        (init--buildings (draftee [:conditions :deviations :attachments]
                                  :autopaikat true))
        => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                     :lainvoimainen :voimassa :aloitettava
                                     :handler :paatosteksti :foremen
                                     :foremen-included :plans
                                     :plans-included :reviews
                                     :reviews-included
                                     :verdict-section :boardname
                                     :automatic-verdict-dates
                                     :buildings.rakennetut-autopaikat
                                     :buildings.kiinteiston-autopaikat
                                     :buildings.autopaikat-yhteensa
                                     :start-date :end-date]
                        :references {:verdict-code ["osittain-myonnetty"]}
                        :data {}))
      (fact "init buildings: buildings, vss-luokka and paloluokka"
        (init--buildings (draftee [:conditions :deviations :attachments]
                                  :vss-luokka true
                                  :paloluokka true))
        => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                     :lainvoimainen :voimassa :aloitettava
                                     :handler :paatosteksti :foremen
                                     :foremen-included :plans
                                     :verdict-section :boardname
                                     :plans-included :reviews
                                     :reviews-included
                                     :automatic-verdict-dates
                                     :buildings.vss-luokka
                                     :buildings.paloluokka
                                     :start-date :end-date]
                        :references {:verdict-code ["osittain-myonnetty"]}
                        :data {}))
      (fact "init permit period: no template section"
        (init--permit-period (assoc (draftee [:conditions :deviations :attachments
                                              :buildings :random])
                                    :application {:documents [{:schema-info {:name "tyoaika"}
                                                               :data        {:tyoaika-alkaa-ms   {:value 12345}
                                                                             :tyoaika-paattyy-ms {:value 54321}}}]}))
        => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                     :lainvoimainen :voimassa :aloitettava
                                     :handler :paatosteksti :foremen
                                     :foremen-included :plans
                                     :verdict-section :boardname
                                     :automatic-verdict-dates
                                     :plans-included :reviews
                                     :reviews-included]
                        :references {:verdict-code ["osittain-myonnetty"]}
                        :data {}))
      (fact "init permit period: all OK"
        (init--permit-period (assoc (draftee [:conditions :deviations :attachments
                                              :buildings])
                                    :application {:documents [{:schema-info {:name "tyoaika"}
                                                               :data        {:tyoaika-alkaa-ms   {:value 12345}
                                                                             :tyoaika-paattyy-ms {:value 54321}}}]}))
        => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                     :lainvoimainen :voimassa :aloitettava
                                     :handler :paatosteksti :foremen
                                     :foremen-included :plans
                                     :verdict-section :boardname
                                     :automatic-verdict-dates
                                     :plans-included :reviews
                                     :reviews-included
                                     :start-date :end-date]
                        :references {:verdict-code ["osittain-myonnetty"]}
                        :data {:start-date 12345 :end-date 54321}))
      (fact "init permit period: not integer :tyoaika-alkaa-ms"
        (init--permit-period (assoc (draftee [:conditions :deviations :attachments
                                              :buildings])
                                    :application {:documents [{:schema-info {:name "tyoaika"}
                                                               :data        {:tyoaika-alkaa-ms   {:value ""}
                                                                             :tyoaika-paattyy-ms {:value 54321}}}]}))
        => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                     :lainvoimainen :voimassa :aloitettava
                                     :handler :paatosteksti :foremen
                                     :foremen-included :plans
                                     :verdict-section :boardname
                                     :automatic-verdict-dates
                                     :plans-included :reviews
                                     :reviews-included
                                     :start-date :end-date]
                        :references {:verdict-code ["osittain-myonnetty"]}
                        :data {:end-date 54321}))
      (fact "init permit period: not-integer :tyoaika-paattyy-ms"
        (init--permit-period (assoc (draftee [:conditions :deviations :attachments
                                              :buildings])
                                    :application {:documents [{:schema-info {:name "tyoaika"}
                                                               :data        {:tyoaika-alkaa-ms {:value 12345}}}]}))
        => (check-draft :inclusions [:julkipano :anto :muutoksenhaku
                                     :lainvoimainen :voimassa :aloitettava
                                     :handler :paatosteksti :foremen
                                     :foremen-included :plans
                                     :verdict-section :boardname
                                     :automatic-verdict-dates
                                     :plans-included :reviews
                                     :reviews-included
                                     :start-date :end-date]
                        :references {:verdict-code ["osittain-myonnetty"]}
                        :data {:start-date 12345}))
      (fact "initialize-verdict-draft"
        (let [init (initialize-verdict-draft
                    (assoc (assoc-in (draftee [:foremen]
                                              :paatosteksti "This is verdict."
                                              :conditions [{:condition "Stay calm"}
                                                           {:condition "Carry on"}]
                                              :verdict-dates ["anto" "voimassa"]
                                              :vastaava-tj true
                                              :vastaava-tj-included true
                                              :tj false
                                              :tj-included true
                                              :vss-luokka true
                                              :autopaikat true)
                                     [:template :published :settings :reviews]
                                     [{:fi "foo" :sv "foo" :en "foo" :selected true}
                                      {:fi "bar" :sv "bar" :en "bar"}])
                           :application
                           {:handlers [{:general   true
                                        :firstName "Bob"
                                        :lastName  "Builder"}]
                            :documents
                            [{:schema-info {:name "hankkeen-kuvaus"}
                              :data        {:poikkeamat {:value "Cannot live by your rules, man!"}}}]}))]
          init => (check-draft :inclusions [:anto :voimassa :paatosteksti :foremen
                                            :foremen-included
                                            :automatic-verdict-dates
                                            :reviews :reviews-included :handler
                                            :deviations :conditions.condition
                                            :add-condition :conditions.remove-condition
                                            :attachments
                                            :buildings.rakennetut-autopaikat
                                            :buildings.kiinteiston-autopaikat
                                            :buildings.autopaikat-yhteensa
                                            :buildings.vss-luokka
                                            :start-date :end-date]
                               :giver       "viranhaltija"
                               :references {:verdict-code ["osittain-myonnetty"]
                                            :foremen      (just ["vastaava-tj" "tj"] :in-any-order)
                                            :reviews      (just [(just {:id mongo-id?
                                                                        :fi "foo"
                                                                        :sv "foo"
                                                                        :en "foo"})
                                                                 (just {:id mongo-id?
                                                                        :fi "bar"
                                                                        :sv "bar"
                                                                        :en "bar"})]
                                                                :in-any-order)})
          (-> init :draft :data
              :conditions vec flatten) => (just [mongo-id? {:condition "Stay calm"}
                                                 mongo-id? {:condition "Carry on"}])
          (-> init :draft :data
              (dissoc :conditions)) => (just {:foremen-included false
                                              :foremen          ["vastaava-tj"]
                                              :reviews-included true
                                              :reviews          (just [mongo-id?])
                                              :deviations       "Cannot live by your rules, man!"
                                              :handler          "Bob Builder"
                                              :paatosteksti     "This is verdict."})
          (fact "select-inclusions"
            ;; Tested here since this is Pate specific functionality
            (vc/select-inclusions (:dictionary mini-verdict)
                               [:deviations :buildings.paloluokka :foremen-included])
            => {:deviations       {:phrase-text      {:category :yleinen}
                                   :template-section :deviations}
                :buildings        {:repeating        {:paloluokka {:text {}}}
                                   :template-section :buildings}
                :foremen-included {:toggle {}}})))))

(def wrap (partial metadata/wrap "user" 12345))

(facts "archive-info"
  (let [verdict {:id             "5ac78d3e791c066eef7198a2"
                 :category       "r"
                 :schema-version 1
                 :state          (wrap "draft")
                 :modified       12345
                 :data           {:handler       "Hank Handler"
                                  :verdict-date  8765432
                                  :handler-title "Bossman"}
                 :references     {:boardname    "The Board"
                                  :verdict-code ["myonnetty"]
                                  :date-deltas  {:julkipano     {:delta 1 :unit "days"}
                                                 :anto          {:delta 2 :unit "days"}
                                                 :muutoksenhaku {:delta 3 :unit "days"}
                                                 :lainvoimainen {:delta 4 :unit "days"}
                                                 :aloitettava   {:delta 1 :unit "years"}
                                                 :voimassa      {:delta 2 :unit "years"}}}
                 :template       {:inclusions []}}]
    (fact "Board verdict"
      (let [v (assoc-in verdict [:template :giver] "lautakunta")]
        (fact "Valid according to schema"
          (sc/check schemas/PateVerdict v) => nil)
        (fact "archive info (without lainvoimainen)"
          (let [archive (archive-info v)]
            archive => {:verdict-date  8765432
                        :verdict-giver "The Board"}
            (fact "Archive schema validation"
              (sc/check schemas/PateVerdict (assoc v :archive archive))
              => nil)))
        (fact "archive info (with lainvoimainen)"
          (let [archive (archive-info (assoc-in v [:data :lainvoimainen] 99999))]
            archive => {:verdict-date  8765432
                        :lainvoimainen 99999
                        :verdict-giver "The Board"}
           (fact "Archive schema validation"
             (sc/check schemas/PateVerdict (assoc v :archive archive))
             => nil)))))
    (fact "Authority verdict"
      (let [v (assoc-in verdict [:template :giver] "viranhaltija")]
        (fact "Valid according to schema"
          (sc/check schemas/PateVerdict v) => nil)
        (fact "archive info (without lainvoimainen)"
          (archive-info v)
          => {:verdict-date  8765432
              :verdict-giver "Bossman Hank Handler"})
        (fact "archive info (with lainvoimainen)"
          (archive-info (assoc-in v [:data :lainvoimainen] 99999))
          => {:verdict-date  8765432
              :lainvoimainen 99999
              :verdict-giver "Bossman Hank Handler"})
        (fact "archive info (no title)"
          (archive-info (-> v
                            (assoc-in [:data :lainvoimainen] 99999)
                            (assoc-in [:data :handler-title] "")))
          => {:verdict-date  8765432
              :lainvoimainen 99999
              :verdict-giver "Hank Handler"}
          (archive-info (assoc-in v [:data :handler-title] nil))
          => {:verdict-date  8765432
              :verdict-giver "Hank Handler"})))))

(facts "continuation verdict"
  (fact "accepted-verdict?" ; TODO Which verdict codes are accepted??
    (accepted-verdict? {:data {:verdict-code "hyvaksytty"}}) => :hyvaksytty
    (accepted-verdict? {:data {:verdict-code "myonnetty"}}) => :myonnetty
    (accepted-verdict? {:data {:verdict-code "evatty"}}) => nil
    (accepted-verdict? {:data {}}) => nil
    (accepted-verdict? nil) => nil))

(defn publish [verdict ts]
  (-> verdict
      (assoc :state (metadata/wrap "publisher" ts "published"))
      (assoc-in [:published :published] ts)))

(facts "Verdict handling helpers"
  (fact "Find latest published pate verdict"
    (vif/latest-published-pate-verdict {:application {}})
    => nil
    (vif/latest-published-pate-verdict {:application
                                        {:pate-verdicts
                                         [(publish test-verdict 1525336290167)]}})
    => (metadata/unwrap-all (publish test-verdict 1525336290167))
    (vif/latest-published-pate-verdict {:application
                                        {:pate-verdicts
                                         [test-verdict
                                          (publish test-verdict 1525336290167)
                                          (publish test-verdict 1425330000000)
                                          (publish test-verdict 1525336290000)]}})
    => (metadata/unwrap-all (publish test-verdict 1525336290167))))





(def rakennusjateselvitys
  {:id "12345bb8e7f60415804219af"
   :created 1514376120409
   :schema-info {:name "rakennusjateselvitys"
                 :order 201
                 :editable-in-states ["archived"
                                      "closed"
                                      "foremanVerdictGiven"
                                      "constructionStarted"
                                      "agreementPrepared"
                                      "appealed"
                                      "extinct"
                                      "inUse"
                                      "final"
                                      "finished"
                                      "agreementSigned"
                                      "acknowledged"
                                      "onHold"]
                 :blacklist ["neighbor"]
                 :version 1}
   :data {:rakennusJaPurkujate {:suunniteltuJate {:0 {:jatetyyppi {:value "puu"
                                                                   :modified nil}
                                                      :suunniteltuMaara {:value "2"
                                                                         :modified nil}
                                                      :yksikko {:value "m3"
                                                                :modified nil}
                                                      :painoT {:value "05"
                                                               :modified nil}}
                                                  :1 {:jatetyyppi {:value "betoni"
                                                                   :modified nil}
                                                      :painoT {:value "02"
                                                               :modified nil}
                                                      :yksikko {:value "m3"
                                                                :modified nil}
                                                      :suunniteltuMaara {:value "005"
                                                                         :modified nil}}
                                                   :2 {:jatetyyppi {:value "muovi"
                                                                    :modified nil}
                                                       :painoT {:value "005"
                                                                :modified nil}
                                                       :suunniteltuMaara {:value "001"
                                                                          :modified nil}
                                                       :yksikko {:value "m3"
                                                                 :modified nil}}}
                                :suunnittelematonJate {:0 {:jatetyyppi {:value nil}
                                                           :toteutunutMaara {:value ""}
                                                           :yksikko {:value nil}
                                                           :painoT {:value ""}
                                                           :jatteenToimituspaikka {:value ""}}}}
          :vaarallisetAineet {:suunniteltuJate {}
                              :suunnittelematonJate {:0 {:vaarallinenainetyyppi {:value nil}
                                                         :toteutunutMaara {:value ""}
                                                         :yksikko {:value nil}
                                                         :painoT {:value ""}
                                                         :jatteenToimituspaikka {:value ""}}}}
          :contact {:name {:value ""}
                    :phone {:value ""}
                    :email {:value ""}}
          :availableMaterials {:0 {:aines {:value ""}
                                   :maara {:value ""}
                                   :yksikko {:value nil}
                                   :saatavilla {:value nil}
                                   :kuvaus {:value ""}}}}})

(def application-r
  {:address "Latokuja 3",
   :primaryOperation {:id "5b34a9d2cea1d0f410db2403",
                      :name "sisatila-muutos",
                      :description nil,
                      :created 1530178002437},
   :buildings [],
   :comments [],
   :secondaryOperations [],
   :attachments [],
   :history [{:state "sent",
              :ts 1200,
              :user {:id "777777777777777777000023",
                     :username "sonja",
                     :firstName "Sonja",
                     :lastName "Sibbo",
                     :role "authority"}}],
   :operation-name "sisatila-muutos",
   :state "sent",
   :municipality "753"
   :permitType "R",
   :organization "753-R",
   :modified 1530179094764,
   :documents '({:id "5b34a9d3cea1d0f410db2404",
                 :schema-info {:name "rakennuksen-muuttaminen",
                               :op {:id "5b34a9d2cea1d0f410db2403",
                                    :name "sisatila-muutos",
                                    },
                               },
                 :data {:buildingId {:value "199887766E",
                                     :source nil,
                                     :sourceValue "199887766E",
                                     :modified 1530179094764},
                        :muutostyolaji {:value "rakennukse p\u00E4\u00E4asiallinen k\u00E4ytt\u00F6tarkoitusmuutos",
                                        :modified 1530179094764,
                                        :source nil,
                                        :sourceValue "rakennukse p\u00E4\u00E4asiallinen k\u00E4ytt\u00F6tarkoitusmuutos"},
                        :rakennuksenOmistajat {:0 {:_selected {:value "yritys",
                                                               :source "krysp",
                                                               :sourceValue "yritys",
                                                               :modified 1530179094764},
                                                   :henkilo {:userId {:value nil,
                                                                      :source "krysp",
                                                                      :sourceValue nil,
                                                                      :modified 1530179094764},
                                                             :henkilotiedot {:etunimi {:value "",
                                                                                       :modified 1530179094764,
                                                                                       :sourceValue "",
                                                                                       :source "krysp"},
                                                                             :sukunimi {:value "",
                                                                                        :sourceValue "",
                                                                                        :source "krysp",
                                                                                        :modified 1530179094764},
                                                                             :hetu {:value nil,
                                                                                    :sourceValue nil,
                                                                                    :modified 1530179094764,
                                                                                    :source "krysp"},
                                                                             :turvakieltoKytkin {:value false,
                                                                                                 :modified 1530179094764,
                                                                                                 :source "krysp",
                                                                                                 :sourceValue false}},
                                                             :osoite {:katu {:value "",
                                                                             :sourceValue "",
                                                                             :modified 1530179094764,
                                                                             :source "krysp"},
                                                                      :postinumero {:value "",
                                                                                    :modified 1530179094764,
                                                                                    :sourceValue "",
                                                                                    :source "krysp"},
                                                                      :postitoimipaikannimi {:value "",
                                                                                             :sourceValue "",
                                                                                             :source "krysp",
                                                                                             :modified 1530179094764},
                                                                      :maa {:value "FIN",
                                                                            :sourceValue "FIN",
                                                                            :source "krysp",
                                                                            :modified 1530179094764}},
                                                             :yhteystiedot {:puhelin {:value "",
                                                                                      :sourceValue "",
                                                                                      :source "krysp",
                                                                                      :modified 1530179094764},
                                                                            :email {:value "",
                                                                                    :modified 1530179094764,
                                                                                    :sourceValue "",
                                                                                    :source "krysp"}},
                                                             :kytkimet {:suoramarkkinointilupa {:value false,
                                                                                                :sourceValue false,
                                                                                                :modified 1530179094764,
                                                                                                :source "krysp"}}},
                                                   :yritys {:companyId {:value nil,
                                                                        :sourceValue nil,
                                                                        :modified 1530179094764,
                                                                        :source "krysp"},
                                                            :yritysnimi {:value "Testiyritys 9242",
                                                                         :source "krysp",
                                                                         :modified 1530179094764,
                                                                         :sourceValue "Testiyritys 9242"},
                                                            :liikeJaYhteisoTunnus {:value "1234567-1",
                                                                                   :modified 1530179094764,
                                                                                   :source "krysp",
                                                                                   :sourceValue "1234567-1"},
                                                            :osoite {:katu {:value "Testikatu 1 A 9242",
                                                                            :modified 1530179094764,
                                                                            :sourceValue "Testikatu 1 A 9242",
                                                                            :source "krysp"},
                                                                     :postinumero {:value "00380",
                                                                                   :sourceValue "00380",
                                                                                   :source "krysp",
                                                                                   :modified 1530179094764},
                                                                     :postitoimipaikannimi {:value "HELSINKI",
                                                                                            :sourceValue "HELSINKI",
                                                                                            :source "krysp",
                                                                                            :modified 1530179094764},
                                                                     :maa {:value "FIN",
                                                                           :modified 1530179094764,
                                                                           :source "krysp",
                                                                           :sourceValue "FIN"}},
                                                            :yhteyshenkilo {:henkilotiedot {:etunimi {:value "",
                                                                                                      :source "krysp",
                                                                                                      :sourceValue "",
                                                                                                      :modified 1530179094764},
                                                                                            :sukunimi {:value "",
                                                                                                       :source "krysp",
                                                                                                       :modified 1530179094764,
                                                                                                       :sourceValue ""},
                                                                                            :turvakieltoKytkin {:value false,
                                                                                                                :sourceValue false,
                                                                                                                :source "krysp",
                                                                                                                :modified 1530179094764}},
                                                                            :yhteystiedot {:puhelin {:value "",
                                                                                                     :sourceValue "",
                                                                                                     :modified 1530179094764,
                                                                                                     :source "krysp"},
                                                                                           :email {:value "",
                                                                                                   :sourceValue "",
                                                                                                   :modified 1530179094764,
                                                                                                   :source "krysp"}}}},
                                                   :omistajalaji {:value nil,
                                                                  :sourceValue nil,
                                                                  :modified 1530179094764,
                                                                  :source "krysp"},
                                                   :muu-omistajalaji {:value "",
                                                                      :modified 1530179094764,
                                                                      :source "krysp",
                                                                      :sourceValue ""}},
                                               },
                        :varusteet {:viemariKytkin {:value true,
                                                    :sourceValue true,
                                                    :modified 1530179094764,
                                                    :source "krysp"},
                                    :saunoja {:value "",
                                              :modified 1530179094764,
                                              :sourceValue "",
                                              :source "krysp"},
                                    :vesijohtoKytkin {:value true,
                                                      :source "krysp",
                                                      :sourceValue true,
                                                      :modified 1530179094764},
                                    :hissiKytkin {:value false,
                                                  :source "krysp",
                                                  :modified 1530179094764,
                                                  :sourceValue false},
                                    :vaestonsuoja {:value "",
                                                   :source "krysp",
                                                   :modified 1530179094764,
                                                   :sourceValue ""},
                                    :kaasuKytkin {:value false,
                                                  :sourceValue false,
                                                  :source "krysp",
                                                  :modified 1530179094764},
                                    :aurinkopaneeliKytkin {:value false,
                                                           :modified 1530179094764,
                                                           :sourceValue false,
                                                           :source "krysp"},
                                    :liitettyJatevesijarjestelmaanKytkin {:value false,
                                                                          :modified 1530179094764,
                                                                          :source "krysp",
                                                                          :sourceValue false},
                                    :koneellinenilmastointiKytkin {:value true,
                                                                   :source "krysp",
                                                                   :sourceValue true,
                                                                   :modified 1530179094764},
                                    :sahkoKytkin {:value true,
                                                  :sourceValue true,
                                                  :source "krysp",
                                                  :modified 1530179094764},
                                    :lamminvesiKytkin {:value true,
                                                       :modified 1530179094764,
                                                       :source "krysp",
                                                       :sourceValue true}},
                        :rakennusnro {:value "002",
                                      :modified 1530179094764,
                                      :source "krysp",
                                      :sourceValue "002"},
                        :verkostoliittymat {:viemariKytkin {:value true,
                                                            :modified 1530179094764,
                                                            :sourceValue true,
                                                            :source "krysp"},
                                            :vesijohtoKytkin {:value true,
                                                              :source "krysp",
                                                              :modified 1530179094764,
                                                              :sourceValue true},
                                            :sahkoKytkin {:value true,
                                                          :source "krysp",
                                                          :sourceValue true,
                                                          :modified 1530179094764},
                                            :maakaasuKytkin {:value false,
                                                             :source "krysp",
                                                             :modified 1530179094764,
                                                             :sourceValue false},
                                            :kaapeliKytkin {:value false,
                                                            :modified 1530179094764,
                                                            :source "krysp",
                                                            :sourceValue false}},
                        :kaytto {:rakentajaTyyppi {:value nil,
                                                   :source "krysp",
                                                   :sourceValue nil,
                                                   :modified 1530179094764},
                                 :kayttotarkoitus {:value "021 rivitalot",
                                                   :source "krysp",
                                                   :sourceValue "021 rivitalot",
                                                   :modified 1530179094764}},
                        :huoneistot { :0 {:WCKytkin {:value true,
                                                     :sourceValue true,
                                                     :source "krysp",
                                                     :modified 1530179094764},
                                          :huoneistoTyyppi {:value "asuinhuoneisto",
                                                            :source "krysp",
                                                            :modified 1530179094764,
                                                            :sourceValue "asuinhuoneisto"},
                                          :keittionTyyppi {:value "keittio",
                                                           :modified 1530179094764,
                                                           :sourceValue "keittio",
                                                           :source "krysp"},
                                          :huoneistoala {:value "108",
                                                         :source "krysp",
                                                         :sourceValue "108",
                                                         :modified 1530179094764},
                                          :huoneluku {:value "4",
                                                      :sourceValue "4",
                                                      :source "krysp",
                                                      :modified 1530179094764},
                                          :jakokirjain {:value "",
                                                        :modified 1530179094764,
                                                        :sourceValue "",
                                                        :source "krysp"},
                                          :ammeTaiSuihkuKytkin {:value true,
                                                                :modified 1530179094764,
                                                                :source "krysp",
                                                                :sourceValue true},
                                          :saunaKytkin {:value true,
                                                        :sourceValue true,
                                                        :modified 1530179094764,
                                                        :source "krysp"},
                                          :huoneistonumero {:value "001",
                                                            :source "krysp",
                                                            :sourceValue "001",
                                                            :modified 1530179094764},
                                          :porras {:value "A",
                                                   :sourceValue "A",
                                                   :modified 1530179094764,
                                                   :source "krysp"},
                                          :muutostapa {:value nil,
                                                       :source nil,
                                                       :modified 1530179094764,
                                                       :sourceValue nil},
                                          :lamminvesiKytkin {:value true,
                                                             :sourceValue true,
                                                             :modified 1530179094764,
                                                             :source "krysp"},
                                          :parvekeTaiTerassiKytkin {:value true,
                                                                    :source "krysp",
                                                                    :modified 1530179094764,
                                                                    :sourceValue true}},
                                     :1 {:WCKytkin {:modified 1530179094764,
                                                    :source "krysp",
                                                    :sourceValue true,
                                                    :value true},
                                         :huoneistoTyyppi {:value "asuinhuoneisto",
                                                           :sourceValue "asuinhuoneisto",
                                                           :modified 1530179094764,
                                                           :source "krysp"},
                                         :keittionTyyppi {:sourceValue "keittio",
                                                          :source "krysp",
                                                          :modified 1530179094764,
                                                          :value "keittio"},
                                         :huoneistoala {:modified 1530179094764,
                                                        :value "106",
                                                        :source "krysp",
                                                        :sourceValue "106"},
                                         :huoneluku {:source "krysp",
                                                     :modified 1530179094764,
                                                     :sourceValue "4",
                                                     :value "4"},
                                         :ammeTaiSuihkuKytkin {:modified 1530179094764,
                                                               :sourceValue true,
                                                               :source "krysp",
                                                               :value true},
                                         :saunaKytkin {:modified 1530179094764,
                                                       :source "krysp",
                                                       :sourceValue true,
                                                       :value true},
                                         :huoneistonumero {:sourceValue "002",
                                                           :value "002",
                                                           :modified 1530179094764,
                                                           :source "krysp"},
                                         :porras {:sourceValue "A",
                                                  :source "krysp",
                                                  :value "A",
                                                  :modified 1530179094764},
                                         :lamminvesiKytkin {:value true,
                                                            :sourceValue true,
                                                            :modified 1530179094764,
                                                            :source "krysp"},
                                         :parvekeTaiTerassiKytkin {:modified 1530179094764,
                                                                   :sourceValue true,
                                                                   :source "krysp",
                                                                   :value true}},
                                     },
                        :lammitys {:lammitystapa {:value "vesikeskus",
                                                  :modified 1530179094764,
                                                  :source "krysp",
                                                  :sourceValue "vesikeskus"},
                                   :lammonlahde {:value "kevyt poltto\u00F6ljy",
                                                 :source "krysp",
                                                 :modified 1530179094764,
                                                 :sourceValue "kevyt poltto\u00F6ljy"},
                                   :muu-lammonlahde {:value "",
                                                     :modified 1530179094764,
                                                     :source "krysp",
                                                     :sourceValue ""}},
                        :kunnanSisainenPysyvaRakennusnumero {:value "",
                                                             :sourceValue "",
                                                             :source "krysp",
                                                             :modified 1530179094764},
                        :perusparannuskytkin {:value false,
                                              :sourceValue false,
                                              :source nil,
                                              :modified 1530179094764},
                        :rakennustietojaEimuutetaKytkin {:value false,
                                                         :modified 1530179094764,
                                                         :source nil,
                                                         :sourceValue false},
                        :rakenne {:rakentamistapa {:value "paikalla",
                                                   :modified 1530179094764,
                                                   :source "krysp",
                                                   :sourceValue "paikalla"},
                                  :kantavaRakennusaine {:value "tiili",
                                                        :sourceValue "tiili",
                                                        :source "krysp",
                                                        :modified 1530179094764},
                                  :muuRakennusaine {:value "",
                                                    :sourceValue "",
                                                    :source "krysp",
                                                    :modified 1530179094764},
                                  :julkisivu {:value "tiili",
                                              :sourceValue "tiili",
                                              :source "krysp",
                                              :modified 1530179094764},
                                  :muuMateriaali {:value "",
                                                  :sourceValue "",
                                                  :source "krysp",
                                                  :modified 1530179094764}},
                        :osoite {:osoitenumero2 {:value "5",
                                                 :source "krysp",
                                                 :modified 1530179094764,
                                                 :sourceValue "5"},
                                 :huoneisto {:value "",
                                             :modified 1530179094764,
                                             :sourceValue "",
                                             :source "krysp"},
                                 :jakokirjain {:value "",
                                               :sourceValue "",
                                               :source "krysp",
                                               :modified 1530179094764},
                                 :kunta {:value "245",
                                         :sourceValue "245",
                                         :source "krysp",
                                         :modified 1530179094764},
                                 :jakokirjain2 {:value "",
                                                :sourceValue "",
                                                :source "krysp",
                                                :modified 1530179094764},
                                 :postinumero {:value "04200",
                                               :source "krysp",
                                               :modified 1530179094764,
                                               :sourceValue "04200"},
                                 :porras {:value "",
                                          :source "krysp",
                                          :modified 1530179094764,
                                          :sourceValue ""},
                                 :osoitenumero {:value "3",
                                                :modified 1530179094764,
                                                :source "krysp",
                                                :sourceValue "3"},
                                 :postitoimipaikannimi {:value "KERAVA",
                                                        :source "krysp",
                                                        :modified 1530179094764,
                                                        :sourceValue "KERAVA"},
                                 :maa {:value "FIN",
                                       :sourceValue "FIN",
                                       :modified 1530179094764,
                                       :source "krysp"},
                                 :lahiosoite {:value "Kyllikintie",
                                              :modified 1530179094764,
                                              :sourceValue "Kyllikintie",
                                              :source "krysp"}},
                        :mitat {:tilavuus {:value "837",
                                           :source "krysp",
                                           :modified 1530179094764,
                                           :sourceValue "837"},
                                :kerrosala {:value "281",
                                            :source "krysp",
                                            :modified 1530179094764,
                                            :sourceValue "281"},
                                :rakennusoikeudellinenKerrosala {:value "",
                                                                 :modified 1530179094764,
                                                                 :source "krysp",
                                                                 :sourceValue ""},
                                :kokonaisala {:value "281",
                                              :modified 1530179094764,
                                              :source "krysp",
                                              :sourceValue "281"},
                                :kerrosluku {:value "2",
                                             :source "krysp",
                                             :sourceValue "2",
                                             :modified 1530179094764},
                                :kellarinpinta-ala {:value "",
                                                    :modified 1530179094764,
                                                    :source "krysp",
                                                    :sourceValue ""}},
                        :manuaalinen_rakennusnro {:value "",
                                                  :sourceValue "",
                                                  :source "krysp",
                                                  :modified 1530179094764},
                        :luokitus {:energialuokka {:value nil,
                                                   :modified 1530179094764,
                                                   :sourceValue nil,
                                                   :source "krysp"},
                                   :energiatehokkuusluku {:value "",
                                                          :source "krysp",
                                                          :modified 1530179094764,
                                                          :sourceValue ""},
                                   :energiatehokkuusluvunYksikko {:value "kWh/m2",
                                                                  :modified 1530179094764,
                                                                  :sourceValue "kWh/m2",
                                                                  :source "krysp"},
                                   :paloluokka {:value nil,
                                                :modified 1530179094764,
                                                :sourceValue nil,
                                                :source "krysp"}},
                        :valtakunnallinenNumero {:value "199887766E",
                                                 :modified 1530179094764,
                                                 :source "krysp",
                                                 :sourceValue "199887766E"}}}

                {:id "5b34a9d3cea1d0f410db2409",
                 :schema-info {:name "rakennuspaikka",
                               :version 1,
                               :type :location,
                               :approvable true,
                               :order 2,
                               :copy-action :clear},
                :created 1530178002437,
                 :data {:kiinteisto {:maaraalaTunnus {:value nil},
                                     :tilanNimi {:value ""},
                                     :rekisterointipvm {:value ""},
                                     :maapintaala {:value ""},
                                     :vesipintaala {:value ""}},
                        :hallintaperuste {:value nil},
                        :kaavanaste {:value nil},
                        :kaavatilanne {:value nil},
                        :hankkeestaIlmoitettu {:hankkeestaIlmoitettuPvm {:value nil}}}}


               {:id "5b34a9d3cea1d0f410db240c",
                :schema-info {:name "rakennusjatesuunnitelma",
                              :order 200,
                              :blacklist [:neighbor]},
                :created 1530178002437,
                :data {:rakennusJaPurkujate {:0 {:jatetyyppi {:value "kipsi",
                                                              :modified 1530178832555},
                                                 :suunniteltuMaara {:value "1",
                                                                    :modified 1530178835793},
                                                 :yksikko {:value "tonni",
                                                           :modified 1530178840071},
                                                 :painoT {:value "1",
                                                          :modified 1530178844422}},
                                             :1 {:jatetyyppi {:value "lasi",
                                                              :modified 1530178850654},
                                                 :suunniteltuMaara {:value "20",
                                                                    :modified 1530178853738},
                                                 :yksikko {:value "kg",
                                                           :modified 1530178855943},
                                                 :painoT {:value "0",
                                                          :modified 1530178860517}}},
                       :vaarallisetAineet {:0 {:vaarallinenainetyyppi {:value "aerosolipullot",
                                                                       :modified 1530178864753},
                                               :suunniteltuMaara {:value "10",
                                                                  :modified 1530178867910},
                                               :yksikko {:value "kg",
                                                         :modified 1530178872036},
                                               :painoT {:value "0",
                                                        :modified 1530178891502}}}}}),
   :location-wgs84 [25.266 60.36938]
   :id "LP-753-2018-90008"
   :propertyId "75341600550007"
   :location [404369.304 6693806.957]
   :inspection-summaries []
   :schema-version 1})

(facts "Finalize verdict"
  (let [command {:user        {:id        "user-id" :username "user-email"
                               :firstName "Hello"   :lastName "World"}
                 :created     12345
                 :application application-r}
        verdict {:id             "vid"
                 :schema-version 1
                 :category       "r"
                 :state          "draft"
                 :data           {:language     "fi"
                                  :verdict-code "myonnetty"
                                  :handler      "Foo Bar"
                                  :verdict-date 876543}
                 :modified       12300
                 :template       {:inclusions ["verdict-code"
                                               "handler"
                                               "verdict-date"]
                                  :giver      "viranhaltija"}
                 :references     {:boardname "Broad board abroad"}}
        c-v-a   (hash-map :command command
                          :verdict verdict
                          :application application-r)]

    (facts "finalize pipeline"
           (fact "clear-publishing-state-for-verdict is called"
             (let [command (assoc command :data {:verdict-id "123"}
                                  :application {:pate-verdicts [{:id "123" :state {:_value "draft"}}]})]
               (process-finalize-pipeline {:command     command
                                           :application application-r
                                           :verdict     verdict}
                                              finalize--verdict)
                   => (throws ExceptionInfo #"Value does not match schema")
                   (provided
                     (command->verdict command true) => {:state "publishing-verdict"}
                     (update-verdict-state command "123" {:_value "draft"}) => "ok")))

           (fact "returns ok when pipeline is fine"
             (let [vid     (mongo/create-id)
                   verdict (assoc verdict :id vid)
                   command (assoc command :data {:verdict-id vid}
                                  :application {:pate-verdicts [{:id vid :state {:_value "draft"}}]})]
               (process-finalize-pipeline {:command     command
                                           :application application-r
                                           :verdict     verdict}
                                              finalize--verdict
                                              finalize--pdf-tags)
                   => ok?
                   (provided
                     (pdf/verdict-tags anything anything) => [:html [:h1 "testi"]]
                     (create-verdict-pdf anything) => {:updates "jee"}
                     (verdict-update command "jee") => nil
                     (clear-publishing-state-for-verdict anything) => nil)))

           (fact "returns fail when pdf creations throws"
             (let [vid     (mongo/create-id)
                   verdict (assoc verdict :id vid)
                   command (assoc command :data {:verdict-id vid}
                                  :application {:pate-verdicts [{:id vid :state {:_value "draft"}}]})]
               (process-finalize-pipeline {:command     command
                                           :application application-r
                                           :verdict     verdict}
                                              finalize--verdict
                                              finalize--pdf-tags)
               => {:ok false :text "error.attachment-pdf-generation-failed"}
                   (provided
                     (pdf/verdict-tags anything anything) => [:html [:h1 "testi"]]
                     (create-verdict-pdf anything) => (fail! :pate.pdf-verdict-error)
                     (clear-publishing-state-for-verdict anything) => nil))))


    (fact finalize--verdict
      (finalize--verdict c-v-a)
      => {:verdict (util/deep-merge verdict
                                    {:archive   {:verdict-date  876543
                                                 :verdict-giver "Foo Bar"}
                                     :published {:published 12345}
                                     :state     {:_value    "published"
                                                 :_user     "user-email"
                                                 :_modified 12345}})
          :updates {$set {:pate-verdicts.$.archive             {:verdict-date  876543
                                                                :verdict-giver "Foo Bar"}
                          :pate-verdicts.$.data.handler        "Foo Bar"
                          :pate-verdicts.$.data.language       "fi"
                          :pate-verdicts.$.data.verdict-code   "myonnetty"
                          :pate-verdicts.$.data.verdict-date   876543
                          :pate-verdicts.$.published.published 12345
                          :pate-verdicts.$.template.inclusions ["verdict-code"
                                                                "handler"
                                                                "verdict-date"]
                          :pate-verdicts.$.state               {:_value    "published"
                                                                :_user     "user-email"
                                                                :_modified 12345}}}})

    (fact "finalize--application-state: waste plan - If there is a waste plan but a waste report does not exist, it is added to documents"
          (-> application-r :documents count) => 3
          (let [{:keys [application updates commit-fn]} (finalize--application-state c-v-a)]
            application => (merge application {:state :verdictGiven})
            updates => {$push {:history {:state :verdictGiven
                                         :ts    12345
                                         :user  {:firstName "Hello"
                                                 :id        "user-id"
                                                 :lastName  "World"
                                                 :username  "user-email"}}}
                        $set  {:modified 12345
                               :state    :verdictGiven}}
        ;; assoc-in is used just to avoid using provided, which did
        ;; not like having facts inside the let binding
        (assoc-in (commit-fn (assoc c-v-a :application application) :dry-run)
                  [:mongo-updates $set "documents.3" :id]  "static-id")
        => {:mongo-updates
            {$set {"documents.3" {:id          "static-id"
                                  :created     12345
                                  :data        {:availableMaterials  {:0 {:aines      {:value ""}
                                                                          :kuvaus     {:value ""}
                                                                          :maara      {:value ""}
                                                                          :saatavilla {:value nil}
                                                                          :yksikko    {:value nil}}}
                                                :contact             {:email {:value ""}
                                                                      :name  {:value ""}
                                                                      :phone {:value ""}}
                                                :rakennusJaPurkujate {:suunniteltuJate      {:0 {:jatetyyppi       {:modified nil
                                                                                                                    :value    "kipsi"}
                                                                                                 :painoT           {:modified nil
                                                                                                                    :value    "1"}
                                                                                                 :suunniteltuMaara {:modified nil
                                                                                                                    :value    "1"}
                                                                                                 :yksikko          {:modified nil
                                                                                                                    :value    "tonni"}}
                                                                                             :1 {:jatetyyppi       {:modified nil
                                                                                                                    :value    "lasi"}
                                                                                                 :painoT           {:modified nil
                                                                                                                    :value    "0"}
                                                                                                 :suunniteltuMaara {:modified nil
                                                                                                                    :value    "20"}
                                                                                                 :yksikko          {:modified nil
                                                                                                                    :value    "kg"}}}
                                                                      :suunnittelematonJate {:0 {:jatetyyppi            {:value nil}
                                                                                                 :jatteenToimituspaikka {:value ""}
                                                                                                 :painoT                {:value ""}
                                                                                                 :toteutunutMaara       {:value ""}
                                                                                                 :yksikko               {:value nil}}}}
                                                :vaarallisetAineet   {:suunniteltuJate      {:0 {:painoT                {:modified nil
                                                                                                                         :value    "0"}
                                                                                                 :suunniteltuMaara      {:modified nil
                                                                                                                         :value    "10"}
                                                                                                 :vaarallinenainetyyppi {:modified nil
                                                                                                                         :value    "aerosolipullot"}
                                                                                                 :yksikko               {:modified nil
                                                                                                                         :value    "kg"}}}
                                                                      :suunnittelematonJate {:0 {:jatteenToimituspaikka {:value ""}
                                                                                                 :painoT                {:value ""}
                                                                                                 :toteutunutMaara       {:value ""}
                                                                                                 :vaarallinenainetyyppi {:value nil}
                                                                                                 :yksikko               {:value nil}}}}}
                                  :schema-info {:name    "rakennusjateselvitys"
                                                :version 1}}}}}))

    (fact "finalize--application-state: waste report - If a waste report document exists, it is updated"
      (let [c-v-a                                   (update-in c-v-a [:application :documents]
                                                               #(conj % rakennusjateselvitys))
            {:keys [application updates commit-fn]} (finalize--application-state c-v-a)
            waste-report-id                         (:id rakennusjateselvitys)]
        (commit-fn (assoc c-v-a :application application) :dry-run)
        => {:mongo-query   {:documents {$elemMatch {:id waste-report-id}}}
            :mongo-updates {$set   {"documents.$.data.rakennusJaPurkujate.suunniteltuJate.0.jatetyyppi.modified"          12345
                                    "documents.$.data.rakennusJaPurkujate.suunniteltuJate.0.jatetyyppi.value"             "kipsi"
                                    "documents.$.data.rakennusJaPurkujate.suunniteltuJate.0.painoT.modified"              12345
                                    "documents.$.data.rakennusJaPurkujate.suunniteltuJate.0.painoT.value"                 "1"
                                    "documents.$.data.rakennusJaPurkujate.suunniteltuJate.0.suunniteltuMaara.modified"    12345
                                    "documents.$.data.rakennusJaPurkujate.suunniteltuJate.0.suunniteltuMaara.value"       "1"
                                    "documents.$.data.rakennusJaPurkujate.suunniteltuJate.0.yksikko.modified"             12345
                                    "documents.$.data.rakennusJaPurkujate.suunniteltuJate.0.yksikko.value"                "tonni"
                                    "documents.$.data.rakennusJaPurkujate.suunniteltuJate.1.jatetyyppi.modified"          12345
                                    "documents.$.data.rakennusJaPurkujate.suunniteltuJate.1.jatetyyppi.value"             "lasi"
                                    "documents.$.data.rakennusJaPurkujate.suunniteltuJate.1.painoT.modified"              12345
                                    "documents.$.data.rakennusJaPurkujate.suunniteltuJate.1.painoT.value"                 "0"
                                    "documents.$.data.rakennusJaPurkujate.suunniteltuJate.1.suunniteltuMaara.modified"    12345
                                    "documents.$.data.rakennusJaPurkujate.suunniteltuJate.1.suunniteltuMaara.value"       "20"
                                    "documents.$.data.rakennusJaPurkujate.suunniteltuJate.1.yksikko.modified"             12345
                                    "documents.$.data.rakennusJaPurkujate.suunniteltuJate.1.yksikko.value"                "kg"
                                    "documents.$.data.vaarallisetAineet.suunniteltuJate.0.painoT.modified"                12345
                                    "documents.$.data.vaarallisetAineet.suunniteltuJate.0.painoT.value"                   "0"
                                    "documents.$.data.vaarallisetAineet.suunniteltuJate.0.suunniteltuMaara.modified"      12345
                                    "documents.$.data.vaarallisetAineet.suunniteltuJate.0.suunniteltuMaara.value"         "10"
                                    "documents.$.data.vaarallisetAineet.suunniteltuJate.0.vaarallinenainetyyppi.modified" 12345
                                    "documents.$.data.vaarallisetAineet.suunniteltuJate.0.vaarallinenainetyyppi.value"    "aerosolipullot"
                                    "documents.$.data.vaarallisetAineet.suunniteltuJate.0.yksikko.modified"               12345
                                    "documents.$.data.vaarallisetAineet.suunniteltuJate.0.yksikko.value"                  "kg"
                                    :modified                                                                             12345}
                            $unset {"documents.$.data.rakennusJaPurkujate.suunniteltuJate.2" ""
                                    "documents.$.meta.rakennusJaPurkujate.suunniteltuJate.2" ""}}
            :post-results  []}))

    (fact "finalize--application-state: no waste plan - If there is no waste plan, waste report document is not added or updated"
      (let [application                             (update application-r :documents drop-last)
            {:keys [application updates commit-fn]} (finalize--application-state (assoc c-v-a :application application))]
        application => (merge application
                              {:state :verdictGiven})
        updates => {$push {:history {:state :verdictGiven
                                     :ts    12345
                                     :user  {:firstName "Hello"
                                             :id        "user-id"
                                             :lastName  "World"
                                             :username  "user-email"}}}
                    $set  {:modified 12345
                           :state    :verdictGiven}}
        (commit-fn (assoc c-v-a :application application) :dry-run) => nil))

    (let [verdict (-> verdict
                      (assoc-in [:data :reviews] ["5a156dd40e40adc8ee064463"
                                                  "6a156dd40e40adc8ee064463"])
                      (assoc-in [:data :reviews-included] true)
                      (assoc-in [:data :foremen] ["erityis-tj"
                                                  "iv-tj"
                                                  "vastaava-tj"
                                                  "vv-tj"])
                      (assoc-in [:data :foremen-included] true)
                      (assoc-in [:data :plans] ["5a156ddf0e40adc8ee064464"
                                                "6a156ddf0e40adc8ee064464"])
                      (assoc-in [:data :plans-included] true)
                      (assoc :references {:foremen ["erityis-tj" "iv-tj" "vastaava-tj" "vv-tj" "tj"]
                                          :plans   [{:id "5a156ddf0e40adc8ee064464"
                                                     :fi "Suunnitelmat"
                                                     :sv "Planer"
                                                     :en "Plans"}
                                                    {:id "6a156ddf0e40adc8ee064464"
                                                     :fi "Suunnitelmat2"
                                                     :sv "Planer2"
                                                     :en "Plans2"}]
                                          :reviews [{:id   "5a156dd40e40adc8ee064463"
                                                     :fi   "Katselmus"
                                                     :sv   "Syn"
                                                     :en   "Review"
                                                     :type "muu-katselmus"}
                                                    {:id   "6a156dd40e40adc8ee064463"
                                                     :fi   "Katselmus2"
                                                     :sv   "Syn2"
                                                     :en   "Review2"
                                                     :type "paikan-merkitseminen"}]})
                      (update-in [:template :inclusions] concat [:reviews :reviews-included
                                                                 :foremen :foremen-included
                                                                 :plans :plans-included]))
          review1 {:assignee    {}
                   :closed      nil
                   :created     12345
                   :data        {:katselmuksenLaji   {:modified 12345
                                                      :value    "muu katselmus"}
                                 :katselmus          {:huomautukset {:kuvaus        {:value ""}
                                                                     :maaraAika     {:value nil}
                                                                     :toteaja       {:value ""}
                                                                     :toteamisHetki {:value nil}}
                                                      :lasnaolijat  {:value ""}
                                                      :pitaja       {:value nil}
                                                      :pitoPvm      {:value nil}
                                                      :poikkeamat   {:value ""}
                                                      :tiedoksianto {:value false}
                                                      :tila         {:value nil}}
                                 :muuTunnus          {:value ""}
                                 :muuTunnusSovellus  {:value ""}
                                 :rakennus           {:0 {:rakennus {:jarjestysnumero                    {:modified 12345
                                                                                                          :value    "1"}
                                                                     :kiinttun                           {:modified 12345
                                                                                                          :value    "75341600550007"}
                                                                     :kunnanSisainenPysyvaRakennusnumero {:modified 12345
                                                                                                          :value    nil}
                                                                     :rakennusnro                        {:modified 12345
                                                                                                          :value    "002"}
                                                                     :valtakunnallinenNumero             {:modified 12345
                                                                                                          :value    "199887766E"}}
                                                          :tila     {:kayttoonottava {:modified 12345
                                                                                      :value    false}
                                                                     :tila           {:modified 12345
                                                                                      :value    ""}}}}
                                 :vaadittuLupaehtona {:modified 12345 :value true}}
                   :duedate     nil
                   :id          "id"
                   :schema-info {:name    "task-katselmus"
                                 :subtype :review
                                 :type    :task
                                 :version 1}
                   :source      {:id "vid" :type "verdict"}
                   :state       :requires_user_action
                   :taskname    "Katselmus"}
          review2 {:assignee    {}
                   :closed      nil
                   :created     12345
                   :data        {:katselmuksenLaji   {:modified 12345
                                                      :value    "rakennuksen paikan merkitseminen"}
                                 :katselmus          {:huomautukset {:kuvaus        {:value ""}
                                                                     :maaraAika     {:value nil}
                                                                     :toteaja       {:value ""}
                                                                     :toteamisHetki {:value nil}}
                                                      :lasnaolijat  {:value ""}
                                                      :pitaja       {:value nil}
                                                      :pitoPvm      {:value nil}
                                                      :poikkeamat   {:value ""}
                                                      :tiedoksianto {:value false}
                                                      :tila         {:value nil}}
                                 :muuTunnus          {:value ""}
                                 :muuTunnusSovellus  {:value ""}
                                 :rakennus           {:0 {:rakennus {:jarjestysnumero                    {:modified 12345
                                                                                                          :value    "1"}
                                                                     :kiinttun                           {:modified 12345
                                                                                                          :value    "75341600550007"}
                                                                     :kunnanSisainenPysyvaRakennusnumero {:modified 12345
                                                                                                          :value    nil}
                                                                     :rakennusnro                        {:modified 12345
                                                                                                          :value    "002"}
                                                                     :valtakunnallinenNumero             {:modified 12345
                                                                                                          :value    "199887766E"}}
                                                          :tila     {:kayttoonottava {:modified 12345
                                                                                      :value    false}
                                                                     :tila           {:modified 12345
                                                                                      :value    ""}}}}
                                 :vaadittuLupaehtona {:modified 12345 :value true}}
                   :duedate     nil
                   :id          "id"
                   :schema-info {:name    "task-katselmus"
                                 :subtype :review
                                 :type    :task
                                 :version 1}
                   :source      {:id "vid" :type "verdict"}
                   :state       :requires_user_action
                   :taskname    "Katselmus2"}
          plan1   {:assignee    {}
                   :closed      nil
                   :created     12345
                   :data        {:kuvaus                      {:value ""}
                                 :maarays                     {:value    "Suunnitelmat"
                                                               :modified 12345}
                                 :vaaditutErityissuunnitelmat {:value ""}}
                   :duedate     nil
                   :id          "id"
                   :schema-info {:name    "task-lupamaarays"
                                 :type    :task
                                 :version 1}
                   :source      {:id "vid" :type "verdict"}
                   :state       :requires_user_action
                   :taskname    "Suunnitelmat"}
          plan2   {:assignee    {}
                   :closed      nil
                   :created     12345
                   :data        {:kuvaus                      {:value ""}
                                 :maarays                     {:value    "Suunnitelmat2"
                                                               :modified 12345}
                                 :vaaditutErityissuunnitelmat {:value ""}}
                   :duedate     nil
                   :id          "id"
                   :schema-info {:name    "task-lupamaarays"
                                 :type    :task
                                 :version 1}
                   :source      {:id "vid" :type "verdict"}
                   :state       :requires_user_action
                   :taskname    "Suunnitelmat2"}
          tj1     {:assignee    {}
                   :closed      nil
                   :created     12345
                   :data        {:asiointitunnus  {:value ""}
                                 :kuntaRoolikoodi {:value    "erityisalojen ty\u00F6njohtaja"
                                                   :modified 12345}
                                 :osapuolena      {:value false}}
                   :duedate     nil
                   :id          "id"
                   :schema-info {:name    "task-vaadittu-tyonjohtaja"
                                 :subtype :foreman
                                 :type    :task
                                 :version 1}
                   :source      {:id "vid" :type "verdict"}
                   :state       :requires_user_action
                   :taskname    "Erityisalojen ty\u00F6njohtaja"}
          tj2     {:assignee    {}
                   :closed      nil
                   :created     12345
                   :data        {:asiointitunnus  {:value ""}
                                 :kuntaRoolikoodi {:value    "IV-ty\u00F6njohtaja"
                                                   :modified 12345}
                                 :osapuolena      {:value false}}
                   :duedate     nil
                   :id          "id"
                   :schema-info {:name    "task-vaadittu-tyonjohtaja"
                                 :subtype :foreman
                                 :type    :task
                                 :version 1}
                   :source      {:id "vid" :type "verdict"}
                   :state       :requires_user_action
                   :taskname    "Ilmanvaihtoty\u00F6njohtaja"}
          tj3     {:assignee    {}
                   :closed      nil
                   :created     12345
                   :data        {:asiointitunnus  {:value ""}
                                 :kuntaRoolikoodi {:value    "vastaava ty\u00F6njohtaja"
                                                   :modified 12345}
                                 :osapuolena      {:value false}}
                   :duedate     nil
                   :id          "id"
                   :schema-info {:name    "task-vaadittu-tyonjohtaja"
                                 :subtype :foreman
                                 :type    :task
                                 :version 1}
                   :source      {:id "vid" :type "verdict"}
                   :state       :requires_user_action
                   :taskname    "Vastaava ty\u00F6njohtaja"}
          tj4     {:assignee    {}
                   :closed      nil
                   :created     12345
                   :data        {:asiointitunnus  {:value ""}
                                 :kuntaRoolikoodi {:value    "KVV-ty\u00F6njohtaja"
                                                   :modified 12345}
                                 :osapuolena      {:value false}}
                   :duedate     nil
                   :id          "id"
                   :schema-info {:name    "task-vaadittu-tyonjohtaja"
                                 :subtype :foreman
                                 :type    :task
                                 :version 1}
                   :source      {:id "vid" :type "verdict"}
                   :state       :requires_user_action
                   :taskname    "Vesi- ja viem\u00E4rity\u00F6njohtaja"}
          house   {:area           "281"
                   :buildingId     "199887766E"
                   :description    ""
                   :index          "1"
                   :localShortId   "002"
                   :location       nil
                   :location-wgs84 nil
                   :nationalId     "199887766E"
                   :operationId    "5b34a9d2cea1d0f410db2403"
                   :propertyId     "75341600550007"
                   :usage          "021 rivitalot"
                   :building-type  ""
                   :apartments     nil}]
      (fact "finalize--building-and-tasks: tasks and buildings"
        (finalize--buildings-and-tasks (assoc c-v-a :verdict verdict))
        => {:application (assoc application-r
                                :buildings [house]
                                :tasks [review1 review2
                                        plan1 plan2
                                        tj1 tj2 tj3 tj4])
            :updates     {$push {:tasks {$each [review1 review2
                                                plan1 plan2
                                                tj1 tj2 tj3 tj4]}}
                          $set  {:buildings [house]}}}
        (provided (lupapalvelu.mongo/create-id) => "id"))
      (fact "finalize--building-and-tasks: tasks, no buildings"
        (let [application (update application-r :documents rest)
              review1     (assoc-in review1 [:data :rakennus] {})
              review2     (assoc-in review2 [:data :rakennus] {})]
          (finalize--buildings-and-tasks (assoc c-v-a
                                                :application application
                                                :verdict verdict))
          => {:application (assoc application
                                  :tasks [review1 review2
                                          plan1 plan2
                                          tj1 tj2 tj3 tj4])
              :updates     {$push {:tasks {$each [review1 review2
                                                  plan1 plan2
                                                  tj1 tj2 tj3 tj4]}}
                            $set  {:buildings []}}}
          (provided (lupapalvelu.mongo/create-id) => "id")))
      (fact "finalize--building-and-tasks: reviews not included, plans, foremen, buildings"
        (finalize--buildings-and-tasks (assoc c-v-a
                                              :verdict (assoc-in verdict [:data :reviews-included] false)))
        => {:application (assoc application-r
                                :buildings [house]
                                :tasks [plan1 plan2 tj1 tj2 tj3 tj4])
            :updates     {$push {:tasks {$each [plan1 plan2 tj1 tj2 tj3 tj4]}}
                          $set  {:buildings [house]}}}
        (provided (lupapalvelu.mongo/create-id) => "id"))

      (let [new-app (assoc-in application-r [:primaryOperation :description] "Hello world!")]
        (fact "finalize--building-and-tasks: buildings, no tasks"
          (let [house (assoc house :description "Hello world!")]
            (finalize--buildings-and-tasks (assoc c-v-a
                                                  :verdict (-> verdict
                                                               (assoc-in [:data :reviews] [])
                                                               (assoc-in [:data :plans-included] false)
                                                               (assoc-in [:data :foremen-included] false))
                                                  :application new-app))
            => {:application (assoc new-app
                                    :buildings [house])
                :updates     {$set {:buildings [house]}}})))
      (fact "finalize--building-and-tasks: buildings, reviews, plans, conditions, foremen"
        (let [c1 (-> plan1
                     (assoc-in [:data :maarays :value] "Hello")
                     (assoc :taskname "Hello"))
              c2 (-> plan1
                     (assoc-in [:data :maarays :value] "World")
                     (assoc :taskname "World"))]
          (finalize--buildings-and-tasks (assoc c-v-a
                                                :verdict (assoc-in verdict [:data :conditions]
                                                                   {:c1 {:condition "Hello"}
                                                                    :c2 {:condition "World"}})))
          => {:application (assoc application-r
                                  :buildings [house]
                                  :tasks [review1 review2 plan1 plan2 c1 c2 tj1 tj2 tj3 tj4])
              :updates     {$push {:tasks {$each [review1 review2 plan1 plan2 c1 c2 tj1 tj2 tj3 tj4]}}
                            $set  {:buildings [house]}}}
          (provided (lupapalvelu.mongo/create-id) => "id"))))

    (fact "finalize--inspection-summary: inspection summaries disabled"
      (inspection-summary/finalize--inspection-summary c-v-a) => nil
      (provided (lupapalvelu.organization/get-organization "753-R")
                => {:inspection-summaries-enabled false}))
    (fact "finalize--inspection-summary: inspection summaries enabled"
      (inspection-summary/finalize--inspection-summary c-v-a)
      => {:application (assoc
                         application-r
                         :inspection-summaries [{:id      "id"
                                                 :name    "Inspector Template"
                                                 :op      {:description nil
                                                           :id          "5b34a9d2cea1d0f410db2403"
                                                           :name        "sisatila-muutos"}
                                                 :targets '({:finished    false
                                                             :id          "id"
                                                             :target-name "First item"}
                                                            {:finished    false
                                                             :id          "id"
                                                             :target-name "Second item"})}])
          :updates     {$push {:inspection-summaries {:id      "id"
                                                      :name    "Inspector Template"
                                                      :op      {:description nil
                                                                :id          "5b34a9d2cea1d0f410db2403"
                                                                :name        "sisatila-muutos"}
                                                      :targets '({:finished    false
                                                                  :id          "id"
                                                                  :target-name "First item"}
                                                                 {:finished    false
                                                                  :id          "id"
                                                                  :target-name "Second item"})}}}}
      (provided
        (lupapalvelu.mongo/create-id) => "id"
        (lupapalvelu.organization/get-organization "753-R")
        => {:inspection-summaries-enabled true
            :inspection-summary           {:operations-templates
                                           {:sisatila-muutos "5b35d34ecea1d0863491149c"}
                                           :templates [{:name     "Inspector Template",
                                                        :modified 1530254158347,
                                                        :id       "5b35d34ecea1d0863491149c",
                                                        :items    ["First item" "Second item"]}]}}))

    (let [att-paatosote {:id            "att1"
                         :type          {:type-id    "paatosote"
                                         :type-group "paatoksenteko"}
                         :target        {:type "verdict"
                                         :id   (:id verdict)}
                         :latestVersion {:fileId "a"}}
          att-ilmoitus  {:id            "att2"
                         :type          {:type-id    "ilmoitus"
                                         :type-group "paatoksenteko"}
                         :latestVersion {:fileId "b"}}
          att-empty     {:id   "att3"
                         :type {:type-id    "ilmoitus"
                                :type-group "paatoksenteko"}}
          att-ilmoitus2 {:id            "att4"
                         :type          {:type-id    "ilmoitus"
                                         :type-group "paatoksenteko"}
                         :target        {:type "verdict"
                                         :id   (:id verdict)}
                         :latestVersion {:fileId "c"}}
          att-ilmoitus3 {:id            "att5"
                         :type          {:type-id    "ilmoitus"
                                         :type-group "paatoksenteko"}
                         :latestVersion {:fileId "d"}}
          att-paatos    {:id            "att6"
                         :type          {:type-id    "paatos"
                                         :type-group "paatoksenteko"}
                         :latestVersion {:fileId "e"}}
          att-paatos2   {:id            "att7"
                         :type          {:type-id    "paatos"
                                         :type-group "paatoksenteko"}
                         :target        {:type "verdict"
                                         :id   (:id verdict)}
                         :latestVersion {:fileId "f"}}]
      (fact "finalize--attachments: no attachments"
        (finalize--attachments c-v-a)
        => (contains {:application (assoc application-r :attachments [])
                      :updates     {$set {:pate-verdicts.$.data.attachments          []
                                          :pate-verdicts.$.data.selected-attachments []}}
                      :verdict     (-> verdict
                                       (assoc-in [:data :attachments] [])
                                       (assoc-in [:data :selected-attachments] []))})
        (provided
          (lupapalvelu.attachment/attachment-array-updates
            "LP-753-2018-90008" anything
            :metadata.nakyvyys "julkinen"
            :metadata.draftTarget false
            :target {:type "verdict" :id "vid"}
            :readOnly true :locked true)
          => nil))
      (fact "verdict-attachment-items"
        (verdict-attachment-items {:application {:attachments [att-paatosote att-ilmoitus att-empty]}}
                                  {:id   (:id verdict)
                                   :data {:atts ["att2" "att3"]}}
                                  :atts)
        => (just [{:id "att1" :type-group "paatoksenteko" :type-id "paatosote"}
                  {:id "att2" :type-group "paatoksenteko" :type-id "ilmoitus"}]
                 :in-any-order))
      (fact "attachment-items"
        (let [{:keys [update-fn]} (attachment-items
                                    {:application {:attachments [att-paatosote att-ilmoitus att-empty
                                                                 att-ilmoitus2 att-ilmoitus3 att-paatos
                                                                 att-paatos2]
                                                   :permitType  "R"}}
                                    {:id   (:id verdict)
                                     :data {:attachments ["att2" "att3" "gone" "att5" "att6"]}})]
          (update-fn {:foo 8})
          => (just {:foo         8
                    :attachments (just [{:type-group "paatoksenteko" :type-id "paatosote" :amount 1}
                                        {:type-group "paatoksenteko" :type-id "ilmoitus" :amount 3}
                                        {:type-group "paatoksenteko" :type-id "paatos" :amount 2}]
                                       :in-any-order)})))
      (fact "finalize--attachments: new, selected and empty"
        (finalize--attachments (-> c-v-a
                                   (assoc-in [:command :application :attachments]
                                             [att-paatosote att-ilmoitus att-empty
                                              att-ilmoitus2 att-ilmoitus3 att-paatos
                                              att-paatos2])
                                   (assoc-in [:verdict :data :attachments]
                                             ["att2" "att3"  "gone" "att5" "att6"])))
        => (contains {:updates (just {$set (just {:pate-verdicts.$.data.attachments
                                                  (just [{:amount     2
                                                          :type-group "paatoksenteko"
                                                          :type-id    "paatos"}
                                                         {:amount     1
                                                          :type-group "paatoksenteko"
                                                          :type-id    "paatosote"}
                                                         {:amount     3
                                                          :type-group "paatoksenteko"
                                                          :type-id    "ilmoitus"}]
                                                        :in-any-order)
                                                  :pate-verdicts.$.data.selected-attachments
                                                  ["att2" "att3"  "gone" "att5" "att6"]})})})
        (provided
          (lupapalvelu.attachment/attachment-array-updates
            "LP-753-2018-90008" anything
            :metadata.nakyvyys "julkinen"
            :metadata.draftTarget false :target {:type "verdict" :id "vid"}
            :readOnly true :locked true)
          => nil)))

    (let [att-ref1  {:id "att-ref1" :target {:type "verdict-ref" :id "vid"}}
          ;; Non-sensical
          att-ref2  {:id "att-ref2" :target {:type "verdict-ref" :id "vid"} :metadata {:draftTarget true}}
          att-src   {:id "att-src" :source {:type "verdicts" :id "vid"}}
          att-draft {:id "att-draft" :target {:type "verdict" :id "vid"} :metadata {:draftTarget true}}
          att-trg   {:id "att-trg" :target {:type "verdict" :id "vid"} :metadata {:draftTarget false}}
          att-nod1  {:id "att-nod1" :target {:type "verdict" :id "vid"}}
          att-nod2  {:id "att-nod2" :target {:type "verdict" :id "vid"} :metadata {:hello "world"}}
          att-oth1  {:id "att-oth1" :source {:type "verdicts" :id "oth"}}
          att-oth2  {:id "att-oth2" :target {:type "verdict" :id "oth"} :metadata {:draftTarget false}}
          att-oth3  {:id "att-oth3" :target {:type "verdict" :id "oth"}}
          att-oth4  {:id "att-oth4" :target {:type "verdict-ref" :id "oth"} :metadata {:draftTarget false}}
          app       {:attachments [att-ref1 att-ref2 att-src att-draft att-trg
                                   att-nod1 att-nod2 att-oth1 att-oth2 att-oth3 att-oth4]}]
      (fact "Deletable verdict attachment ids"
        (deletable-verdict-attachment-ids app "vid")
        => (just ["att-src" "att-draft" "att-trg"] :in-any-order)
        (deletable-verdict-attachment-ids app "oth")
        => (just ["att-oth1" "att-oth2"] :in-any-order)))

    (fact "finalize--proposal-pdf-tags"
      (get-in (finalize--pdf-tags c-v-a) [:updates $set :pate-verdicts.$.published.tags])
      => string?
      (provided (lupapalvelu.organization/get-organization-name "753-R" "fi") => "Sipoon rakennusvalvonta"))


    (fact "create-verdict-pdf called with bad args (no tags)"
      (create-verdict-pdf c-v-a) => (throws Exception))

    (against-background [(html-pdf/html->pdf anything anything) => {:ok true}
                         (pdf/create-verdict-attachment anything anything anything) => {:id "A98765"}]
      (fact "create-verdict-pdf"
        (let [result (create-verdict-pdf (assoc-in c-v-a [:verdict :published :tags] "{:body \"very nice hiccup\"}"))]
          result => map?
          (:updates result) => {"$set" {:pate-verdicts.$.published.attachment-id "A98765"}}
          (-> result :verdict :published :attachment-id) => "A98765"
          (:verdict-attachment-id result) => "A98765"
          (select-keys result [:command :application]) => (select-keys c-v-a [:command :application])))

      (fact "create-verdict-pdf-version when html->pdf returns fail"
        (create-verdict-pdf (assoc-in c-v-a [:verdict :published :tags] "{:body \"very nice hiccup\"}"))
        => (throws Exception)
        (provided
          (html-pdf/html->pdf anything anything) => {:ok false})))

    (facts "finalize--signatures"
      (let [contract {:id             "cid"
                      :schema-version 1
                      :category       "contract"
                      :state          "draft"
                      :data           {:handler      "Handler"
                                       :giver        "Signer"
                                       :verdict-date 22334455}}
            c-c-a    {:command     command
                      :verdict     contract
                      :application application-r}]
        (fact "Verdicts are not signed"
          (finalize--signatures c-v-a) => nil)
        (fact "Pate contract: Signed by giver"
          (finalize--signatures c-c-a)
          => {:updates {"$set" {:pate-verdicts.$.signatures [{:date    22334455
                                                              :name    "Signer"
                                                              :user-id "user-id"}]}}
              :verdict (assoc contract :signatures [{:date    22334455
                                                     :name    "Signer"
                                                     :user-id "user-id"}])})
        (fact "Pate contract: No giver, no signature"
          (finalize--signatures (assoc-in c-c-a [:verdict :data :giver] "    "))
          => nil
          (finalize--signatures (assoc-in c-c-a [:verdict :data :giver] nil))
          => nil
          (finalize--signatures (util/dissoc-in c-c-a [:verdict :data :giver]))
          => nil)

        (fact "Legacy contract: Signed by handler"
          (let [contract (assoc contract :legacy? true)
                c-c-a    (assoc c-c-a :verdict contract)]
            (finalize--signatures c-c-a)
            => {:updates {"$set" {:pate-verdicts.$.signatures [{:date    22334455
                                                                :name    "Handler"
                                                                :user-id "user-id"}]}}
                :verdict (assoc contract :signatures [{:date    22334455
                                                       :name    "Handler"
                                                       :user-id "user-id"}])}))))))

(facts "verdict-exists"
  (let [c-d-a             {:application {:permitType   "R"
                                         :municipality "753"
                                         :pate-verdicts [{:id "123" :category "r" :state {:_value "draft" :_user "simo"
                                                                                     :_modified 1571923455904}}]}
                           :data        {:verdict-id "123"}}
        publishing-c-d-a (assoc-in c-d-a [:application :pate-verdicts 0 :state :_value] "publishing-verdict")]
    ((verdict-exists :not-publishing? :editable?) c-d-a) => nil
    ((verdict-exists :editable?) publishing-c-d-a) => (just {:ok false :text string?})
    ((verdict-exists :not-publishing?) publishing-c-d-a) => (just {:ok false :text string?})))

(facts "finalize--kuntagml"
  (let [organization {:krysp {:R {:ftpUser "haidian"
                                  :url     "http://old.example.org"
                                  :version "2.2.2"
                                  :http    {:enabled true
                                            :url     "http://new.example.org"}}}}
        c-v-a        {:application {:permitType   "R"
                                    :municipality "753"}
                      :verdict     {:category "r"}
                      :command     {:organization (delay organization)}}]
    (against-background
      [(krysp/verdict-as-kuntagml anything anything anything) => "ok"]
      (fact "commit-fn"
          (finalize--kuntagml c-v-a) => (just {:commit-fn fn?}))
      (fact "legacy verdict"
        (finalize--kuntagml (assoc-in c-v-a [:verdict :legacy?] true)) => nil)
      (fact "contract"
        (finalize--kuntagml (assoc-in c-v-a [:verdict :category] :contract)) => nil)
      (fact "No krysp integration"
        (finalize--kuntagml (assoc-in c-v-a [:command :organization]
                                      (delay (util/dissoc-in organization
                                                             [:krysp :R :version]))))
        => nil)
      (fact "No http configuration"
        (finalize--kuntagml (assoc-in c-v-a [:command :organization]
                                      (delay (util/dissoc-in organization
                                                             [:krysp :R :http]))))
        => nil)
      (fact "Http configuration disabled"
        (finalize--kuntagml (assoc-in c-v-a [:command :organization]
                                      (delay (assoc-in organization
                                                       [:krysp :R :http :enabled]
                                                       false))))
        => nil)

      (fact "Pate SFTP"
          (finalize--kuntagml (assoc-in c-v-a [:command :organization]
                                        (-> organization
                                            (util/dissoc-in [:krysp :R :http])
                                            (assoc :scope [{:permitType   "R"
                                                            :municipality "753"
                                                            :pate         {:sftp true}}])
                                            delay)))
          => (just {:commit-fn fn?})))))

(facts "finalize--bulletin"
  (let [organization {:scope [{:permitType   "R"
                               :municipality "753"
                               :bulletins    {:enabled true}}]}
        c-v-a        {:application {:permitType   "R"
                                    :municipality "753"}
                      :verdict     {:category "r"
                                    :data     {:julkipano 12345}}
                      :command     {:organization (delay organization)}}]
    (fact "commit-fn"
      (finalize--bulletin c-v-a) => (just {:commit-fn fn?}))
    (fact "Bulletins disabled in scope"
      (finalize--bulletin (assoc-in c-v-a
                                    [:command :organization]
                                    (delay (update-in organization
                                                      [:scope 0 :bulletins :enabled]
                                                      not)))) => nil
      (finalize--bulletin (assoc-in c-v-a
                                    [:command :organization]
                                    (delay (util/dissoc-in organization
                                                           [:scope 0 :bulletins]))))
      => nil)
    (fact "Scope mismatch"
      (finalize--bulletin (assoc-in c-v-a
                                    [:application :permitType]
                                    "P")) => nil
      (finalize--bulletin (assoc-in c-v-a
                                    [:application :municipality]
                                    "123")) => nil)
    (fact "No julkipano field"
      (finalize--bulletin (util/dissoc-in c-v-a
                                          [:verdict :data :julkipano]))
      => nil)
    (facts "bulletin-data"
      (let [data   {:julkipano               11111
                    :anto                    22222
                    :bulletin-op-description "  Bulletin description  \n"
                    :operation               "    Operation  "
                    :muutoksenhaku           33333}
            result {:verdictGivenAt        22222
                    :bulletinOpDescription "Bulletin description"
                    :appealPeriodStartsAt  11111
                    :appealPeriodEndsAt    33333}]
        (fact "bulletin-op-description"
          (bulletin-data data) => (assoc result :markup? true))
        (fact "operation"
          (bulletin-data (dissoc data :bulletin-op-description))
          => (assoc result :bulletinOpDescription "Operation")
          (bulletin-data (assoc data :bulletin-op-description "  "))
          => (assoc result :bulletinOpDescription "Operation"))
        (fact "Both empty"
          (bulletin-data (dissoc data :bulletin-op-description :operation))
          => (assoc result :bulletinOpDescription nil)
          (bulletin-data (assoc data
                                :bulletin-op-description "    "
                                :operation "    "))
          => (assoc result :bulletinOpDescription nil))
        (fact "Appeal period fallback"
          (bulletin-data (dissoc data :muutoksenhaku))
          => (assoc result
                    :markup? true
                    :appealPeriodEndsAt 1209622222))))))

(facts "Copy old verdict as base of replacement verdict"
  (let [verdictId (mongo/create-id)
        verdict (make-verdict :id verdictId :code "myonnetty" :section "123")]

    (copy-verdict-draft {:application (assoc application-r :pate-verdicts [verdict])
                         :created 123456789
                         :user {:id (mongo/create-id) :username "sonja"}}
                        verdictId) => string?
    (provided
      (lupapalvelu.action/update-application anything anything) => nil)))

(facts "Signature requests"
  (let [verdictId   (mongo/create-id)
        verdict     (make-verdict :id verdictId :code "myonnetty" :section "123")
        verdict     (assoc verdict :signatures [{:user-id "123"
                                                 :name    "Signer one"
                                                 :date    1536138000000}])
        verdict     (assoc verdict :signature-requests [{:user-id "111"
                                                         :name    "Signer four"
                                                         :date    1536138000000}])
        application (assoc application-r :auth [{:id        "123"    :username "user1"
                                                 :firstName "signer" :lastName "one"
                                                 :role      "writer"}
                                                {:id        "456"    :username "user2"
                                                 :firstName "signer" :lastName "two"
                                                 :role      "writer"}
                                                {:id        "789"    :username "user3"
                                                 :firstName "signer" :lastName "three"
                                                 :role      "reader" :invite   {:role "writer"}}
                                                {:id        "111"    :username "user4"
                                                 :firstName "signer" :lastName "four"
                                                 :role      "reader" :invite   {:role "writer"}}
                                                {:id        "222"    :username "user5"
                                                 :firstName "signer" :lastName "five"
                                                 :role      "writer" :type     "company"}
                                                {:id        "313"    :username "donald"
                                                 :firstName "Donald" :lastName "Duck"
                                                 :role      "guest"}])
        application (assoc application :pate-verdicts [verdict])]

    (fact "Should find correct parties into selection"
      (signature-request-parties {:application application :data {:verdict-id verdictId}})
      => [{:text "signer two" :value "456"}
          {:text "signer three" :value "789"}])))

(facts "Verdict proposal"
  (let [proposal-verdict (-> (make-verdict :id "proposal-1" :code "myonnetty" :section "1")
                             (assoc-in [:template :giver] "lautakunta")
                             (assoc :state "proposal"))
        c-v-a            (hash-map :command {:user        {:id        "user-id" :username "user-email"
                                                           :firstName "Hello"   :lastName "World"}
                                             :created     12345
                                             :application application-r}
                                   :verdict proposal-verdict
                                   :application application-r)]
    (fact "proposal?"
      (vc/proposal? proposal-verdict) => true
      (vc/proposal? (dissoc proposal-verdict :category)) => false
      (vc/proposal? (assoc proposal-verdict :state "draft")) => false)

    (fact "proposal-filled? - verdict not"
      (let [proposal (util/dissoc-in proposal-verdict [:data :verdict-code])]
        (proposal-filled? {:data        {:verdict-id "proposal-1"}
                           :application {:pate-verdicts [proposal]}}) => true
        (verdict-filled? {:data        {:verdict-id "proposal-1"}
                          :application {:pate-verdicts [proposal]}}) => false))

    (fact "finalize--proposal"
      (finalize--proposal c-v-a) => {:updates {"$set" {:pate-verdicts.$.data.handler "Foo Bar"
                                                       :pate-verdicts.$.data.verdict-code "myonnetty"
                                                       :pate-verdicts.$.data.verdict-date 876543
                                                       :pate-verdicts.$.data.verdict-section "1"
                                                       :pate-verdicts.$.proposal.proposed 12345
                                                       :pate-verdicts.$.state {:_modified 12345
                                                                               :_user "user-email"
                                                                               :_value "proposal"}}}
                                     :verdict {:category "r"
                                               :data {:handler "Foo Bar"
                                                      :verdict-code "myonnetty"
                                                      :verdict-date 876543
                                                      :verdict-section "1"}
                                               :id "proposal-1"
                                               :modified 1
                                               :proposal {:proposed 12345}
                                               :published nil
                                               :references {:boardname "Broad board abroad"}
                                               :replacement nil
                                               :schema-version 1
                                               :state {:_modified 12345 :_user "user-email" :_value "proposal"}
                                               :template {:giver "lautakunta"
                                                          :inclusions ["verdict-code"
                                                                       "handler"
                                                                       "verdict-date"
                                                                       "verdict-section"
                                                                       "verdict-text"]}}})

    (fact "finalize--proposal-pdf-tags"
      (get-in (finalize--proposal-pdf-tags c-v-a) [:updates $set :pate-verdicts.$.proposal.tags])
      => string?
      (provided (lupapalvelu.organization/get-organization-name "753-R" nil) => "Sipoon rakennusvalvonta"))))

(facts "buildings"
  (let [op1  {:id "op1" :name "op-one" :description "desc-one"}
        op2  {:id "op2" :name "op-two" :description "desc-two"}
        op3  {:id "op3" :name "op-three" :description "desc-three"}
        doc1 {:id          "doc1"
              :schema-info {:op {:id "op1"}}
              :data        {:valtakunnallinenNumero {:value "national1"}
                            :tunnus                 {:value "tag1"}}}
        doc2 {:id          "doc2"
              :schema-info {:op {:id "op2"}}
              :data        {:valtakunnallinenNumero  " "
                            :manuaalinen_rakennusnro "manual2"
                            :tunnus                  "tag2"}}
        doc3 {:id          "doc3"
              :schema-info {:op {:id          "op3"
                                 :description "doc3-description"}}
              :data        {}}
        doc4 {:id          "doc4"
              :schema-info {}
              :data        {:valtakunnallinenNumero  "national4"
                            :manuaalinen_rakennusnro "manual4"}}]

    (fact "Primary operation only"
      (buildings {:primaryOperation op1 :documents [doc1 doc2 doc3 doc4]})
      => {:op1 {:operation   "op-one"
                :description "desc-one"
                :building-id "national1"
                :tag         "tag1"
                :order       "0"}})
    (fact "Primary and secondary operations"
      (buildings {:primaryOperation    op2
                  :secondaryOperations [op1 op3]
                  :documents           [doc1 doc2 doc3 doc4]})
      => {:op1 {:operation   "op-one"
                :description "desc-one"
                :building-id "national1"
                :tag         "tag1"
                :order       "1"}
          :op2 {:operation   "op-two"
                :description "desc-two"
                :building-id "manual2"
                :tag         "tag2"
                :order       "0"}})
    (fact "Both building numbers"
      (buildings {:primaryOperation op1
                  :documents        [(assoc-in doc4
                                               [:schema-info :op :id]
                                               "op1")]})
      => {:op1 {:operation   "op-one"
                :description "desc-one"
                :building-id "national4"
                :tag         ""
                :order       "0"}})

    (facts "Documents having buildings"
      (app-documents-having-buildings {:primaryOperation    op2
                                       :secondaryOperations [op1 op3]
                                       :documents           [doc1 doc2 doc3 doc4]})
      => [{:data        {:manuaalinen_rakennusnro "manual2"
                         :tunnus                  "tag2"
                         :valtakunnallinenNumero  " "}
           :id          "doc2"
           :schema-info {:op {:description "desc-two"
                              :id          "op2"
                              :name        "op-two"}}}
          {:data        {:tunnus                 "tag1"
                         :valtakunnallinenNumero "national1"}
           :id          "doc1"
           :schema-info {:op {:description "desc-one"
                              :id          "op1"
                              :name        "op-one"}}}])))


(facts "ya-matti-integration-enabled?"
       (let [application-ya {:id "LP-092-2019-90001"
                             :municipality "092"
                             :permitType "YA"
                             :organization "092-YA"}
             organization   {:id "092-YA"
                             :scope [{:municipality "092"
                                      :permitType "YA"
                                      :pate {:enabled true}                                    ;; required
                                      }]
                             :krysp {:YA {:version "2.2.4"                                     ;; required
                                          ;:ftpUser nil
                                          ;:url "url"
                                          :backend-system "matti"                              ;; required
                                          :http {:enabled true                                 ;; required
                                                 ;:auth-type "x-header"
                                                 :partner "matti"                              ;; NOT required
                                                 :path {:application "YleistenAlueidenPaatos"
                                                        :verdict     "YleistenAlueidenPaatos"} ;; required
                                                 :url "<target-url>"                           ;; required
                                                 ;:headers [{:key "x-vault" :value "XXX"}]
                                                 ;:username "<username>"
                                                 ;:password "<password>"
                                                 ;:crypto-iv "<crypto-iv>"
                                                 }}}}]
         (against-background
           [(lupapalvelu.organization/pate-scope? anything) => true]

           (fact "basic requirements"
                 (ya-matti-integration-enabled? application-ya organization)
                 => true)

           (fact "No krysp integration"
                 (ya-matti-integration-enabled? application-ya
                                                (util/dissoc-in organization [:krysp :YA :version]))
                 => false)

           (fact "No http configuration"
                 (ya-matti-integration-enabled? application-ya
                                                (util/dissoc-in organization [:krysp :YA :http]))
                 => false)

           (fact "Http configuration disabled"
                 (ya-matti-integration-enabled? application-ya
                                                (assoc-in organization [:krysp :YA :http :enabled] false))
                 => false)

           #_(fact "Application path missing in http configuration"
                   (ya-matti-integration-enabled? application-ya
                                                  (util/dissoc-in organization [:krysp :YA :http :path :application]))
                   => false)

           (fact "Verdict path missing in http configuration"
                 (ya-matti-integration-enabled? application-ya
                                                (util/dissoc-in organization [:krysp :YA :http :path :verdict]))
                 => false)

           (fact "The partner marked in http configuration does not affect"
                 (ya-matti-integration-enabled? application-ya
                                                (assoc-in organization [:krysp :YA :http :partner] "watti"))
                 => true)

           (fact "Wrong backend-system"
                 (ya-matti-integration-enabled? application-ya
                                                (assoc-in organization [:krysp :YA :backend-system] "watti"))
                 => false)

           (fact "Extra SFTP settings do not affect"
                 (ya-matti-integration-enabled? application-ya
                                                (update-in organization [:krysp :YA] merge {:ftpUser "<sftp-user>"
                                                                                            :url "<url>"}))
             => true)

           (fact "pate-scope fails"
             (ya-matti-integration-enabled? application-ya organization)
             => false
             (provided
               (lupapalvelu.organization/pate-scope? application-ya) => false)))

         (fact "R app does not pass"
           (ya-matti-integration-enabled? application-r organization)
           => false)))

(facts "scheduled publish"
  (against-background [(lupapalvelu.organization/bulletins-enabled? anything anything anything) => false
                       (lupapalvelu.verdict-robot.core/robot-integration? anything) => false
                       (clear-publishing-state-for-verdict anything) => nil
                       (verdict-update anything {$set {:pate-verdicts.$.data.verdict-section "6666"}}) => nil
                       (lupapalvelu.organization/get-organization-name anything anything) => "TestiORG"
                       (lupapalvelu.domain/get-application-no-access-checking anything {:attachments true}) => {:attachments []}
                       (pate-verdict->html anything anything) => {:ok true :pdf-file-stream nil}]
    (let [vid (mongo/create-id)
          verdict {:id             vid
                   :category       "r"
                   :state          (wrap "draft")
                   :schema-version 1
                   :data           {:language     "fi"
                                    :verdict-code "myonnetty"
                                    :handler      "Foo Bar"
                                    :verdict-date 123321123}
                   :modified       12300
                   :template       {:inclusions ["verdict-code"
                                                 "handler"
                                                 "verdict-date"]
                                    :giver      "viranhaltija"}
                   :references     {:boardname "Broad board abroad"}}
          command {:user        {:id        "user-id" :username "user-email"
                                 :firstName "Hello"   :lastName "World"}
                   :created     12345
                   :data {:verdict-id vid}
                   :organization (delay nil)
                   :application (assoc application-r :pate-verdicts [verdict])}]

      (fact "schedule successfully"
        (scheduled-publish command) => {:ok true :state :scheduled}
        (provided
          (lupapalvelu.mongo/get-next-sequence-value anything) => nil :times 0
          (update-verdict-state command vid {:_value :scheduled :_user "user-email" :_modified 12345})
          => "SUCCESS"))
      (fact "schedule unsuccessful if PDF fails"
        (scheduled-publish command) => {:ok false :text "u-failed"}
        (provided
          (lupapalvelu.mongo/get-next-sequence-value anything) => nil :times 0
          (pate-verdict->html anything anything) => (sade.core/fail :u-failed))))))

(facts attachment-not-in-published-verdict
  (let [draft       {:category "r"
                     :data     {:selected-attachments ["att-draft1" "att-draft2"]}}
        published   {:category  "r"
                     :data      {:selected-attachments ["att-v1" "att-v2"]}
                     :published {:published 12345}}
        application {:pate-verdicts [draft published]}]
    (vc/draft? draft) => true
    (vc/published? published) => true
    (fact "No verdicts"
      (attachment-not-in-published-verdict {:application {}
                                            :data        {:attachmentId "att1"}})
      => nil)
    (fact "Not a verdict attachment"
      (attachment-not-in-published-verdict {:application application
                                            :data        {:attachmentId "att1"}})
      => nil)
    (fact "Draft verdict attachment"
      (attachment-not-in-published-verdict {:application application
                                            :data        {:attachmentId "att-draft2"}})
      => nil)
    (fact "Published verdict attachment"
      (attachment-not-in-published-verdict {:application application
                                            :data        {:attachmentId "att-v2"}})
      => {:ok false :text "error.attachment-in-published-verdict"})))
