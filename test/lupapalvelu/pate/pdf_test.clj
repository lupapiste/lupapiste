(ns lupapalvelu.pate.pdf-test
  "PDF testing concentrates mainly on the source value resolution."
  (:require [lupapalvelu.pate.pdf :as pdf]
            [lupapalvelu.pate.pdf-html :as html]
            [lupapalvelu.pate.verdict-schemas :as verdict-schemas]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.util :as util]))

(defn address  [street zip city country]
  {:katu                 street
   :postinumero          zip
   :postitoimipaikannimi city
   :maa                  country})

(defn party-person [firstname lastname street zip city country]
  {:schema-info {:subtype "hakija"}
   :data        {:_selected "henkilo"
                 :henkilo   {:henkilotiedot {:etunimi  firstname
                                             :sukunimi lastname}
                             :osoite        (address street zip
                                                     city country)}}})

(defn party-company [company-name street zip city country]
  {:schema-info {:subtype "hakija"}
   :data        {:_selected "yritys"
                 :yritys    {:yritysnimi company-name
                             :osoite     (address street zip
                                                  city country)}}})

(facts "applicants"
  (let [app {:documents [(party-person "  Annie  " "Applicant"
                                       "Road 10" "12345" "Anchorage" "USA")
                         (party-person "Other" "Otter"
                                       "Tori 10" "26820" "Oulu" "FIN")
                         (party-person "" ""
                                       "Ignore Way" "26820" "Missingville"
                                       "FIN")
                         (party-company "Firm" "One Way 8" "12345" "Tampere"
                                        "FIN")
                         (party-company "" "Gone Way 8" "12345" "Tampere" "FIN")
                         (party-company "Foreign Firm" "Guang Hua Lu" "88"
                                        "Beijing" "CHN")]}]
    (pdf/applicants {:application app :lang "fi"})
    => (just [{:name    "Annie Applicant"
               :address "Road 10, 12345 Anchorage, Yhdysvallat (USA)"}
              {:name    "Other Otter"
               :address "Tori 10, 26820 Oulu"}
              {:name    "Firm"
               :address "One Way 8, 12345 Tampere"}
              {:name    "Foreign Firm"
               :address "Guang Hua Lu, 88 Beijing, Kiina"}] :in-any-order)))

(facts "property-id"
  (pdf/property-id {:propertyId "75341600550007"
                    :documents []})
  => "753-416-55-7"
  (pdf/property-id {:propertyId "75341600550007"
                    :documents [{:schema-info {:name "rakennuspaikka"}
                                 :data {:kiinteisto {:maaraalaTunnus "9876"}}}]})
  => "753-416-55-7-M9876"
  (pdf/property-id {:propertyId "75341600550007"
                    :documents [{:schema-info {:name "rakennuspaikka"}
                                 :data {:kiinteisto {:maaraalaTunnus " "}}}]})
  => "753-416-55-7")

(facts "value-or-other"
  (pdf/value-or-other :fi "other" "world" :hii :hoo)
  => "world"
  (pdf/value-or-other :fi "yleinen" "world" :phrase :category)
  => "Yleinen"
  (pdf/value-or-other :fi "Hello" "world")
  => "Hello"
  (pdf/value-or-other :fi nil "world")
  => ""
  (pdf/value-or-other :fi nil "world" :hii :hoo)
  => "")

(defn head-designer [firstname lastname difficulty
                     education-select education-other]
  {:schema-info {:name    "paasuunnittelija"
                 :subtype "suunnittelija"}
   :data        {:suunnittelutehtavanVaativuusluokka difficulty
                 :henkilotiedot                      {:etunimi  firstname
                                                      :sukunimi lastname}
                 :patevyys                           {:koulutusvalinta education-select
                                                      :koulutus        education-other}}})

(defn designer [firstname lastname role-code other-role difficulty
                education-select education-other]
  {:schema-info {:name    "suunnittelija"
                 :subtype "suunnittelija"}
   :data        {:kuntaRoolikoodi                    role-code
                 :muuSuunnittelijaRooli              other-role
                 :suunnittelutehtavanVaativuusluokka difficulty
                 :henkilotiedot                      {:etunimi  firstname
                                                      :sukunimi lastname}
                 :patevyys                           {:koulutusvalinta education-select
                                                      :koulutus        education-other}}})

(facts "designers"
  (let [head1 (head-designer "Head" "Designer" "B"
                             "Insin\u00f6\u00f6ri" nil)
        head2 (head-designer "Second" "Header" "A"
                             "other" "Hobbyist")
        des1  (designer "Andy" "Aardvark" "GEO-suunnittelija"
                        nil "AA" "sisustusarkkitehti" nil)
        des2  (designer "Betty" "Bopper" "other" "Builder"
                        "ei tiedossa" "other" "Maker")
        head3 (designer "Heddy" "Haughty" "p\u00e4\u00e4suunnittelija" "Builder"
                        "C" "arkkitehti" "Maker")
        des3  (designer "Charlie" "Crooked" "ei tiedossa" "Builder"
                        "A" "arkkitehtiylioppilas" nil)
        des4  (designer "Diana" "Dreamer" "rakennussuunnittelija" nil
                        "B" "rakennusarkkitehti" nil)
        head4 (head-designer " " "" "A" "other" "Hobbyist")
        des5  (designer "" " " "rakennussuunnittelija" nil
                        "B" "rakennusarkkitehti" nil)
        infos (pdf/designers {:lang        "fi"
                              :application {:documents [head1 head2 des1
                                                        des2 head3 des3
                                                        des4 head4 des5]}})]
    (fact "No empty designers"
      (count infos) => 7)
    (fact "Head designers are first"
      (take 3 infos) => (just [{:role       "P\u00e4\u00e4suunnittelija"
                                :difficulty "B"
                                :person     "Head Designer, Insin\u00f6\u00f6ri"}
                               {:role       "P\u00e4\u00e4suunnittelija"
                                :difficulty "A"
                                :person     "Second Header, Hobbyist"}
                               {:role       "P\u00e4\u00e4suunnittelija"
                                :difficulty "C"
                                :person     "Heddy Haughty, arkkitehti"}]
                             :in-any-order))
    (fact "Regular designers"
      (drop 3 infos) => (just [{:role       "GEO-suunnittelija"
                                :difficulty "AA"
                                :person     "Andy Aardvark, sisustusarkkitehti"}
                               {:role       "Builder"
                                :difficulty "ei tiedossa"
                                :person     "Betty Bopper, Maker"}
                               {:role       "Ei tiedossa"
                                :difficulty "A"
                                :person     "Charlie Crooked, arkkitehtiylioppilas"}
                               {:role       "Rakennussuunnittelija"
                                :difficulty "B"
                                :person     "Diana Dreamer, rakennusarkkitehti"}]
                       :in-any-order))))

(facts "Application operations"
  (let [app             {:documents           [{:schema-info {:op {:id   "op2"
                                                                   :name "purkaminen"}}}
                                               {:schema-info {:op {:id   "op1"
                                                                   :name "sisatila-muutos"}}
                                                :data        {:foo "bar"}}]
                         :primaryOperation    {:id "op1"}
                         :secondaryOperations [{:id "op2" :created 1}]}
        sisatila-muutos "Rakennuksen sis\u00e4tilojen muutos (k\u00e4ytt\u00f6tarkoitus ja/tai muu merkitt\u00e4v\u00e4 sis\u00e4muutos)"
        purkaminen      "Rakennuksen purkaminen"]
    (fact "Operation infos"
      (pdf/operation-infos app)
      => [{:id   "op1"
           :name "sisatila-muutos"}
          {:id   "op2"
           :name "purkaminen"}])
    (fact "operations: no operation dict"
      (pdf/operations {:lang "fi" :verdict {} :application app})
      => [{:text sisatila-muutos}
          {:text purkaminen}])
    (fact "operations: blank operation dict"
      (pdf/operations {:lang "fi" :verdict {:data {:operation "   "}} :application app})
      => [{:text sisatila-muutos}
          {:text purkaminen}])
    (fact "operations: operation dict"
      (pdf/operations {:lang "fi" :verdict {:data {:operation "  Grand Design   "}} :application app})
      => [{:text "Grand Design"}])
    (fact "operations: bulletin description"
      (pdf/operations {:lang "fi"
                       :verdict {:data {:bulletin-desc-as-operation true
                                        :bulletin-op-description "   This is bulletin description  "}}
                       :application app})
      => [{:text "This is bulletin description"}])))

(facts "Buildings"
  (let [app     {:documents           [{:schema-info {:op {:id   "op2"
                                                           :name "purkaminen"}}}
                                       {:schema-info {:op {:id   "op1"
                                                           :name "pientalo"}}
                                        :data        {:foo "bar"}}]
                 :primaryOperation    {:id "op1"}
                 :secondaryOperations [{:id "op2" :created 1}]}
        build1  {:kiinteiston-autopaikat "1"
                 :autopaikat-yhteensa    "3"
                 :tag                    "Foo"
                 :description            "Foobar"
                 :show-building          true}
        build2  {:kiinteiston-autopaikat "2"
                 :autopaikat-yhteensa    "4"
                 :description            "Burn!"
                 :building-id            "12345"
                 :show-building          true}
        verdict {:category :r
                 :data     {:buildings {:op2 build2
                                        :op1 build1}}}]
    (fact "Verdict buildings: no buildings"
      (pdf/verdict-buildings {:application app :verdict {:category :r}})
      => [])
    (fact "Verdict buildings: two buildings"
      (pdf/verdict-buildings {:application app :verdict verdict})
      => [build1 build2])
    (fact "Verdict buildings: other building deselected"
      (pdf/verdict-buildings {:application app
                              :verdict     (update-in verdict
                                                      [:data :buildings :op2 :show-building]
                                                      not)})
      => [build1])
    (fact "Building parking: building 1"
      (pdf/building-parking "fi" build1)
      => [{:text [:strong "Foo: Foobar"] :amount ""}
          {:text "Kiinteist\u00f6n autopaikat" :amount "1"}
          {:text "Autopaikat yhteens\u00e4" :amount "3"}])
    (fact "Building parking: building 2"
      (pdf/building-parking "fi" build2)
      => [{:text [:strong "Burn! \u2013 12345"] :amount ""}
          {:text "Kiinteist\u00f6n autopaikat" :amount "2"}
          {:text "Autopaikat yhteens\u00e4" :amount "4"}])
    (fact "Parking section: both buildings"
      (pdf/parking-section :fi [build1 build2])
      => [:div.section
          [[{:text [:strong "Foo: Foobar"] :amount ""}
            {:text "Kiinteist\u00f6n autopaikat" :amount "1"}
            {:text "Autopaikat yhteens\u00e4" :amount "3"}]
           [{:text [:strong "Burn! \u2013 12345"] :amount ""}
            {:text "Kiinteist\u00f6n autopaikat" :amount "2"}
            {:text "Autopaikat yhteens\u00e4" :amount "4"}]]])))

(fact "tj-vastattavat-tyot"
  (pdf/tj-vastattavat-tyot
   {:documents [{:schema-info {:name "tyonjohtaja-v2"}
                 :data        {:vastattavatTyotehtavat
                               {:rakennuksenPurkaminen                  {:value false},
                                :ivLaitoksenKorjausJaMuutostyo          {:value false},
                                :uudisrakennustyoIlmanMaanrakennustoita {:value false},
                                :maanrakennustyot                       {:value true},
                                :uudisrakennustyoMaanrakennustoineen    {:value false},
                                :ulkopuolinenKvvTyo                     {:value true, :modified 1527251435900},
                                :rakennuksenMuutosJaKorjaustyo          {:value false},
                                :linjasaneeraus                         {:value false},
                                :ivLaitoksenAsennustyo                  {:value false},
                                :muuMika                                {:value ""},
                                :sisapuolinenKvvTyo                     {:value true, :modified 1527251434831}}}}]}
   "fi")
  => ["Maanrakennusty\u00f6t" "Ulkopuolinen KVV-ty\u00f6" "Sis\u00e4puolinen KVV-ty\u00f6"])

(let [organization {:name {:fi "Sipoon rakennusvalvonta"}}
      app          {:organization "753-R"}
      verdict      {:references {:organization-name "Special name"}}]
  (fact "organization-name"
    (html/organization-name :fi app) => "Sipoon rakennusvalvonta"
    (provided (lupapalvelu.organization/get-organization "753-R") => organization)
    (html/organization-name :fi app {}) => "Sipoon rakennusvalvonta"
    (provided (lupapalvelu.organization/get-organization "753-R") => organization)
    (html/organization-name :fi app verdict) => "Special name"))

(testable-privates lupapalvelu.pate.pdf
                   update-sum-field update-sum-map double->str)

(defn mitat [kala rakala koala tila]
  {:mitat (util/assoc-when {}
                           :kerrosala kala
                           :rakennusoikeudellinenKerrosala rakala
                           :kokonaisala koala
                           :tilavuus tila)})

(facts "Dimensions"
  (fact "double->str"
    (double->str nil) => nil
    (double->str 0) => nil
    (double->str 0.04) => nil
    (double->str 0.05) => "0.1"
    (double->str 3.04) => "3"
    (double->str 12.0500) => "12.1")
  (fact "update-sum-field"
    (update-sum-field {} {} :foo) => {:foo 0.0}
    (update-sum-field nil nil :foo) => {:foo 0.0}
    (update-sum-field nil {:foo "hello"} :foo) => {:foo 0.0}
    (update-sum-field {:foo 10} {:foo "hello"} :foo) => {:foo 10.0}
    (update-sum-field {:bar 4} {} :foo) => {:bar 4 :foo 0.0}
    (update-sum-field {:bar 4} {:foo 9} :foo) => {:bar 4 :foo 9.0}
    (update-sum-field {:bar 4 :foo 2} {:foo 9.21} :foo) => {:bar 4 :foo 11.21})
  (fact "update-sum-map"
    (update-sum-map {} {} [:foo :bar :baz]) => {:foo 0.0 :bar 0.0 :baz 0.0}
    (update-sum-map {} nil [:foo :bar :baz]) => {:foo 0.0 :bar 0.0 :baz 0.0}
    (update-sum-map nil nil [:foo :bar :baz]) => {:foo 0.0 :bar 0.0 :baz 0.0}
    (update-sum-map {:hii 8} {:foo 1 :bar 2} [:foo :bar :baz])
    => {:hii 8 :foo 1.0 :bar 2.0 :baz 0.0}
    (update-sum-map {:hii 8 :foo 3 :baz 9} {:foo 1 :bar 2} [:foo :bar :baz])
    => {:hii 8 :foo 4.0 :bar 2.0 :baz 9.0})
  (fact "dimensions"
    (let [new-build1 {:schema-info {:name "uusiRakennus"}
                      :data        (mitat 1 2 3 4)}
          new-build2 {:schema-info {:name "uusi-rakennus-ei-huoneistoa"}
                      :data        (mitat 10 12 13 14)}
          extend1    {:schema-info {:name "rakennuksen-laajentaminen"}
                      :data        (assoc (mitat 1 2 3 4)
                                          :laajennuksen-tiedot (mitat 101 102 103 104))}
          extend2    {:schema-info {:name "rakennuksen-laajentaminen-ei-huoneistoja"}
                      :data        (assoc (mitat 1 2 3 4)
                                          :laajennuksen-tiedot (mitat 201 202 203 204))}
          skip       {:schema-info {:name "onpahanVaanRakennus"}
                      :data        (mitat 1 2 3 4)}]
      (pdf/dimensions {:documents [new-build1 new-build2 extend1 extend2 skip]})
      => {:kerrosala                      "313" ; (+ 1 10 101 201)
          :rakennusoikeudellinenKerrosala "318" ; (+ 2 12 102 202)
          :kokonaisala                    "322" ; (+ 3 13 103 203)
          :tilavuus                       "326" ; (+ 4 14 104 204)
          }
      (pdf/dimensions {:documents [new-build1 new-build2 extend1
                                   extend2 skip new-build1 new-build2
                                   extend1 extend2 skip]})
      => {:kerrosala                      "626"
          :rakennusoikeudellinenKerrosala "636"
          :kokonaisala                    "644"
          :tilavuus                       "652"}
      (pdf/dimensions {:documents skip}) => {})))

(fact "missing dimensions"
    (let [new-build1 {:schema-info {:name "uusiRakennus"}
                      :data        (mitat nil 2 3 4)}
          new-build2 {:schema-info {:name "uusi-rakennus-ei-huoneistoa"}
                      :data        (mitat 10 nil 13 14)}
          extend1    {:schema-info {:name "rakennuksen-laajentaminen"}
                      :data        (assoc (mitat 1 2 3 4)
                                          :laajennuksen-tiedot (mitat 101 102 nil 104))}
          extend2    {:schema-info {:name "rakennuksen-laajentaminen-ei-huoneistoja"}
                      :data        (assoc (mitat 1 2 3 4)
                                          :laajennuksen-tiedot (mitat 201 202 203 nil))}]
      (pdf/dimensions {:documents [new-build1 new-build2 extend1 extend2]})
      => {:kerrosala                      "312"
          :rakennusoikeudellinenKerrosala "306"
          :kokonaisala                    "219"
          :tilavuus                       "122"}))

(fact "bad dimensions"
    (let [new-build1 {:schema-info {:name "uusiRakennus"}
                      :data        (mitat "Bad" 2 3 4)}
          new-build2 {:schema-info {:name "uusi-rakennus-ei-huoneistoa"}
                      :data        (mitat 10 "to" 13 14)}
          extend1    {:schema-info {:name "rakennuksen-laajentaminen"}
                      :data        (assoc (mitat 1 2 3 4)
                                          :laajennuksen-tiedot (mitat 101 102 "the" 104))}
          extend2    {:schema-info {:name "rakennuksen-laajentaminen-ei-huoneistoja"}
                      :data        (assoc (mitat 1 2 3 4)
                                          :laajennuksen-tiedot (mitat 201 202 203 "bone"))}]
      (pdf/dimensions {:documents [new-build1 new-build2 extend1 extend2]})
      => {:kerrosala                      "312"
          :rakennusoikeudellinenKerrosala "306"
          :kokonaisala                    "219"
          :tilavuus                       "122"}))
