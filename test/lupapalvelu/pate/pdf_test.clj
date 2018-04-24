(ns lupapalvelu.pate.pdf-test
  "PDF testing concentrates mainly on the source value resolution."
  (:require [lupapalvelu.application :as app]
            [lupapalvelu.pate.pdf :as pdf]
            [midje.sweet :refer :all]))

(fact "join-non-blanks"
  (pdf/join-non-blanks "-" "foo" " " "bar" nil "baz")
  => "foo-bar-baz"
  (pdf/join-non-blanks "-")
  => ""
  (pdf/join-non-blanks "-" "  " nil "")
  => "")

(fact "loc-non-blank"
  (pdf/loc-non-blank :fi :hii.hoo " ")
  => nil
  (pdf/loc-non-blank :fi :pdf "page")
  => "Sivu")

(fact "loc-fill-non-blank"
  (pdf/loc-fill-non-blank :fi :pdf.not-later-than " ")
  => nil
  (pdf/loc-fill-non-blank :fi :pdf.not-later-than nil)
  => nil
  (pdf/loc-fill-non-blank :fi :pdf.not-later-than "5.2.2018")
  => "viimeist\u00e4\u00e4n 5.2.2018"
  (pdf/loc-fill-non-blank :fi :pdf.voimassa.text "1.1.2000" " ")
  => nil
  (fact "Only values matter, not the placeholders"
    (pdf/loc-fill-non-blank :fi :pdf.voimassa.text "1.1.2000")
    =not=> nil)
  (pdf/loc-fill-non-blank :fi :pdf.voimassa.text nil "1.1.2000")
  => nil
  (pdf/loc-fill-non-blank :fi :pdf.voimassa.text "30.10.1999" "1.1.2000")
  =not=> nil)

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

(fact "pathify"
  (pdf/pathify :hello.world.foo.bar) => [:hello :world :foo :bar]
  (pdf/pathify :hello) => [:hello]
  (pdf/pathify "hello.world") => [:hello :world])

(fact "add-unit"
  (pdf/add-unit :fi :ha " ") => nil
  (pdf/add-unit :fi :ha nil) => nil
  (pdf/add-unit :fi :ha "20") => "20 ha"
  (pdf/add-unit :fi :ha 10) => "10 ha"
  (pdf/add-unit :fi :m2 88) => [:span 88 " m"[:sup 2]]
  (pdf/add-unit :fi :m3 "hello") => [:span "hello" " m"[:sup 3]]
  (pdf/add-unit :fi :kpl 8) => "8 kpl"
  (pdf/add-unit :fi :section 88) => "\u00a788"
  (pdf/add-unit :fi :eur "foo") => "foo\u20ac")

(fact "property-id"
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

(fact "value-or-other"
  (pdf/value-or-other :fi "other" "world" :hii :hoo)
  => "world"
  (pdf/value-or-other :fi "yleinen" "world" :phrase :category)
  => "Yleinen"
  (pdf/value-or-other :fi "Hello" "world")
  => "Hello")


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

(fact "collateral"
  (pdf/collateral {:lang       :fi
                   :dictionary {:collateral-date {:date {}}}
                   :verdict    {:data {:collateral-flag true
                                       :collateral      "20 000"
                                       :collateral-type "shekki"
                                       :collateral-date "5.2.2018"}}})
  => "20 000\u20ac, Shekki, 5.2.2018"
  (pdf/collateral {:lang       :fi
                   :dictionary {}
                   :verdict    {:data {:collateral-flag true
                                       :collateral      "20 000"}}})
  => "20 000\u20ac"
  (pdf/collateral {:lang       :fi
                   :dictionary {}
                   :verdict    {:data {:collateral-flag true}}})
  => ""
  (pdf/collateral {:lang        :fi
                   :dictionary  {:collateral-date {:date {}}}
                   :verdict {:data {:collateral-flag false
                                    :collateral      "20 000"
                                    :collateral-type "shekki"
                                    :collateral-date "5.2.2018"}}})
  => nil
  (pdf/collateral {:lang :fi :data {}}) => nil)

(fact "complexity"
  (pdf/complexity {:lang :fi :verdict {:data {}}})
  => nil
  (pdf/complexity {:lang :fi :verdict {:data {:complexity "large"}}})
  => ["Vaativa"]
  (pdf/complexity {:lang       :fi
                   :dictionary {:complexity-text {:phrase-text {}}}
                   :verdict    {:data {:complexity-text "Tai nan le."}}})
  => '(([:div.markup ([:p {} "Tai nan le." [:br]])]))
  (pdf/complexity {:lang       :fi
                   :dictionary {:complexity-text {:phrase-text {}}}
                   :verdict    {:data {:complexity-text "Tai nan le."
                                       :complexity      "large"}}})
  => ["Vaativa" '([:div.markup ([:p {}"Tai nan le." [:br]])])])

(fact "statements"
  (pdf/statements {:lang    :fi
                   :verdict {:data {}}})
  => nil
  (pdf/statements {:lang    :fi
                   :verdict {:data {:statements [{:given  1518017023967
                                                  :text   "Stakeholder"
                                                  :status "ei-huomautettavaa"}
                                                 {:given  1517930824494
                                                  :text   "Interested"
                                                  :status "ehdollinen"}]}}})
  => ["Stakeholder, 7.2.2018, Ei huomautettavaa"
      "Interested, 6.2.2018, Ehdollinen"])

(fact "handler"
  (pdf/handler {:verdict {:data {}}}) => ""
  (pdf/handler {:verdict {:data {:handler "Hank Handler  "}}})
  => "Hank Handler"
  (pdf/handler {:verdict {:data {:handler " Hank Handler"
                                 :handler-title "Title"}}})
  => "Title Hank Handler"
  (pdf/handler {:verdict {:data {:handler-title " Title  "}}})
  => "Title")

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
    (fact "Primary operation data"
      (pdf/primary-operation-data app)
      => {:foo "bar"})
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
      => [{:text "Grand Design"}
          {:text purkaminen}])))

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
        verdict {:data {:buildings {:op2 build2
                                    :op1 build1}}}]
    (fact "Verdict buildings: no buildings"
      (pdf/verdict-buildings {:application app :verdict {}})
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

(facts "references"
  (let [refs {:reviews [{:id "id1" :fi "Yksi" :sv "Ett" :en "One"}
                        {:id "id2" :fi "Kaksi" :sv "Två" :en "Two"}
                        {:id "id3" :fi "Kolme" :sv "Tre" :en "Three"}
                        {:id "id4" :fi "Neljä" :sv "Fyra" :en "Four"}]}]
    (fact "not included"
      (pdf/references {:lang    :fi
                       :verdict {:data       {:reviews-included false
                                              :reviews          ["id1" "id3"]}
                                 :references refs}} :reviews)
      => nil)
    (fact "Included"
      (pdf/references {:lang    :fi
                       :verdict {:data       {:reviews-included true
                                              :reviews          ["id1" "bad" "id3"]}
                                 :references refs}} :reviews)
      => ["Kolme" "Yksi"])))
