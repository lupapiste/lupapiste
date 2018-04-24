(ns lupapalvelu.pate.verdict-test
  (:require [clj-time.coerce :as time-coerce]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.date :as date]
            [lupapalvelu.pate.schemas :as schemas]
            [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.pate.shared-schemas :as shared-schemas]
            [lupapalvelu.pate.verdict :refer :all]
            [lupapalvelu.pate.verdict-template :as template]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.shared-schemas :refer [object-id-pattern]]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(testable-privates lupapalvelu.pate.verdict
                   next-section insert-section
                   general-handler application-deviations
                   archive-info application-operation)

(testable-privates lupapalvelu.pate.verdict-template
                   template-inclusions)

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
        :template {:giver "test"
                   :inclusions [:verdict-section]}}

    (provided (lupapalvelu.mongo/get-next-sequence-value "verdict_test_123-T_2018") => 2))

  (fact "section is blank"
    (insert-section "123-T" 1515151515151 {:data     {:verdict-section ""}
                                           :template {:giver "test"}})
    => {:data     {:verdict-section "1"}
        :template {:giver "test"
                   :inclusions [:verdict-section]}}

    (provided (lupapalvelu.mongo/get-next-sequence-value "verdict_test_123-T_2018") => 1))

  (fact "Distinct inclusions"
    (insert-section "123-T" 1515151515151 {:data     {:verdict-section ""}
                                           :template {:giver "test"
                                                      :inclusions [:hello :verdict-section :foo]}})
    => {:data     {:verdict-section "1"}
        :template {:giver "test"
                   :inclusions [:hello :verdict-section :foo]}}

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

(facts "update automatic dates"
  ;; Calculation is cumulative
  (let [refs {:date-deltas {:julkipano     {:delta 1 :unit "days"}
                            :anto          {:delta 2 :unit "days"}
                            :muutoksenhaku {:delta 3 :unit "days"}
                            :lainvoimainen {:delta 4 :unit "days"}
                            :aloitettava   {:delta 1 :unit "years"}
                            :voimassa      {:delta 2 :unit "years"}}}
        ts #(+ (* 1000 3600 12) (util/to-millis-from-local-date-string %))]
    (fact "All dates included in the verdict"
      (update-automatic-verdict-dates {:references   refs
                                       :template     {:inclusions shared/verdict-dates}
                                       :verdict-data {:automatic-verdict-dates true
                                                      :verdict-date            (ts "6.4.2018") ;; Friday
                                                      }})
      => {:julkipano     (ts "9.4.2018")
          :anto          (ts "11.4.2018")
          :muutoksenhaku (ts "16.4.2018") ;; Skips weekend
          :lainvoimainen (ts "20.4.2018")
          :aloitettava   (ts "23.4.2019")
          :voimassa      (ts "23.4.2021")})
    (fact "Calculation skips public holiday (Easter)"
      (update-automatic-verdict-dates {:references   refs
                                       :template     {:inclusions shared/verdict-dates}
                                       :verdict-data {:automatic-verdict-dates true
                                                      :verdict-date            (ts "26.3.2018")}})
      => {:julkipano     (ts "27.3.2018")
          :anto          (ts "29.3.2018")
          :muutoksenhaku (ts "3.4.2018") ;; Skips Easter
          :lainvoimainen (ts "9.4.2018") ;; Skips weekend
          :aloitettava   (ts "9.4.2019")
          :voimassa      (ts "9.4.2021")})
    (fact "Only some dates included"
      (update-automatic-verdict-dates {:references   refs
                                       :template     {:inclusions [:anto :lainvoimainen :voimassa]}
                                       :verdict-data {:automatic-verdict-dates true
                                                      :verdict-date            (ts "26.3.2018")}})
      => { :anto          (ts "29.3.2018")
          :lainvoimainen (ts "9.4.2018") ;; Skips weekend
          :voimassa      (ts "9.4.2021")})
    (fact "No automatic calculation flag"
      (update-automatic-verdict-dates {:references   refs
                                       :template     {:inclusions shared/verdict-dates}
                                       :verdict-data {:automatic-verdict-dates false
                                                      :verdict-date            (ts "26.3.2018")}})
      => nil
      (update-automatic-verdict-dates {:references   refs
                                       :template     {:inclusions shared/verdict-dates}
                                       :verdict-data {:verdict-date            (ts "26.3.2018")}})
      => nil)
    (fact "Verdict date is not set"
      (update-automatic-verdict-dates {:references   refs
                                       :template     {:inclusions shared/verdict-dates}
                                       :verdict-data {:automatic-verdict-dates true
                                                      :verdict-date ""}})
      => nil
      (fact "Verdict date is not set"
        (update-automatic-verdict-dates {:references   refs
                                         :template     {:inclusions shared/verdict-dates}
                                         :verdict-data {:automatic-verdict-dates true}})
        => nil))))

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
    (shared/check-dicts (dissoc (:dictionary test-verdict) :three)
                        (:sections test-verdict))
    => (throws AssertionError))
  (fact "Dict in repeating missing"
    (shared/check-dicts (util/dissoc-in (:dictionary test-verdict)
                                        [:five :repeating :r-three :repeating :r-sub-two])
                        (:sections test-verdict))
    => (throws AssertionError))
  (fact "Overlapping dicts"
    (shared/check-overlapping-dicts [{:dictionary {:foo {:toggle {}}
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
    (shared/check-unique-section-ids (:sections mock-template))
    => nil)
  (fact "Non-unique section ids"
    (shared/check-unique-section-ids (cons {:id :t-second}
                                           (:sections mock-template)))
    => (throws AssertionError))
  (fact "Combine subschemas"
    (shared/combine-subschemas {:dictionary {:foo {:toggle {}}
                                             :bar {:text {}}}
                                :sections   [{:id   :one
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
    (shared/build-verdict-template-schema
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
    => {:dictionary {:foo                       {:toggle {}}
                     :bar                       {:text {}}
                     :baz                       {:date {}}
                     :dum                       {:date {}}
                     :removed-sections          {:keymap {:one   false
                                                          :two   false
                                                          :three false}}
                     :link-to-settings          {:link {:text-loc :pate.settings-link
                                                        :click    :open-settings}}
                     :link-to-settings-no-label {:link {:text-loc :pate.settings-link
                                                        :label?   false
                                                        :click    :open-settings}}}
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
    => {:t-one #{:t-first}
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
 [(shared/verdict-schema :r)          => test-verdict
  (shared/verdict-template-schema :r) => mock-template]
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
 [(shared/verdict-schema "r") => test-verdict
  (shared/verdict-template-schema :r) => mock-template]
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
                                                 :paloluokka             {:text {}}}
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
 [(shared/verdict-template-schema "r") => mini-verdict-template
  (shared/verdict-template-schema :r) => mini-verdict-template
  (shared/verdict-schema "r")          => mini-verdict]
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
   (fact "init foremen: three foremen included, one selected"
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
   (fact "init foremen: all foremen included, everyone selected, section removed"
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
                                           :plans        (just [{:fi "suomi"
                                                                 :sv "svenska"
                                                                 :en "english"
                                                                 :id (find-id "suomi")}
                                                                {:fi "imous"
                                                                 :sv "aksnevs"
                                                                 :en "hsilgne"
                                                                 :id (find-id "imous")}] :in-any-order)}
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
                                           :reviews      (just [{:fi   "suomi"
                                                                 :sv   "svenska"
                                                                 :en   "english"
                                                                 :type "hello"
                                                                 :id   (find-id "suomi")}
                                                                {:fi   "imous"
                                                                 :sv   "aksnevs"
                                                                 :en   "hsilgne"
                                                                 :type "olleh"
                                                                 :id   (find-id "imous")}] :in-any-order)}
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
            (select-inclusions (:dictionary mini-verdict)
                               [:deviations :buildings.paloluokka :foremen-included])
            => {:deviations       {:phrase-text      {:category :yleinen}
                                   :template-section :deviations}
                :buildings        {:repeating        {:paloluokka {:text {}}}
                                   :template-section :buildings}
                :foremen-included {:toggle {}}})))))

(facts "archive-info"
  (let [verdict {:id             "5ac78d3e791c066eef7198a2"
                 :category       "r"
                 :schema-version 1
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
