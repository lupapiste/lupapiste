(ns lupapalvelu.pate.pdf-test
  "PDF testing concentrates mainly on the source value resolution."
  (:require [lupapalvelu.pate.pdf :as pdf]
            [lupapalvelu.pate.pdf-html :as html]
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

(defn party-company
  ([company-name street zip city country contact-first contact-last]
   {:schema-info {:subtype "hakija"}
    :data        {:_selected "yritys"
                  :yritys    {:yritysnimi    company-name
                              :osoite        (address street zip
                                                   city country)
                              :yhteyshenkilo {:henkilotiedot {:etunimi  contact-first
                                                              :sukunimi contact-last}}}}})
  ([company-name street zip city country]
   (party-company company-name street zip city country nil nil)))

(facts "applicants"
  (let [app {:documents [(party-person "  Annie  " "Applicant"
                                       "Road 10" "12345" "Anchorage" "USA")
                         (party-person "Other" "Otter"
                                       "Tori 10" "26820" "Oulu" "FIN")
                         (party-person "" ""
                                       "Ignore Way" "26820" "Missingville"
                                       "FIN")
                         (party-company "Firm" "One Way 8" "12345" "Tampere"
                                        "FIN" "Yrjö " " Yhteyshenkilö")
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
               :address "Guang Hua Lu, 88 Beijing, Kiina"}] :in-any-order)
    (pdf/applicants {:application (assoc app :permitType "YA") :lang "fi"})
    => (just [{:name    "Annie Applicant"
               :address "Road 10, 12345 Anchorage, Yhdysvallat (USA)"}
              {:name    "Other Otter"
               :address "Tori 10, 26820 Oulu"}
              {:name    "Firm"
               :address "One Way 8, 12345 Tampere"
               :contact "Yrjö Yhteyshenkilö"}
              {:name    "Foreign Firm"
               :address "Guang Hua Lu, 88 Beijing, Kiina"}] :in-any-order)))

(facts "site-responsibles"
  (let [responsible (fn [function & args] (-> (apply function args)
                                              (update :schema-info merge {:name    "tyomaastaVastaava"
                                                                          :subtype "tyomaasta-vastaava"})))
        app         {:documents [(responsible party-person "Randy" "Responsible"
                                              "Anchovies 54" "44558" "Toronto" "CAN")
                                 (responsible party-company "Responsibility Inc" "Jack Daniels Drive"
                                              "65" "Anchorage" "USA"
                                              " Corinne " " Contact ")]}]
    (pdf/site-responsibles {:application (assoc app :permitType "YA") :lang "sv"})
    => (just [{:name    "Randy Responsible"
               :address "Anchovies 54, 44558 Toronto, Canada"}
              {:name    "Responsibility Inc"
               :address "Jack Daniels Drive, 65 Anchorage, Förenta Staterna (USA)"
               :contact "Corinne Contact"}])))

(fact "format-party-details"
  (pdf/format-party-details []) => []
  (pdf/format-party-details [{:name "Name"}
                             {:address "Address"}
                             {:contact " Contact "}])
  => ["Name" "\n" "Address" "\n" "Contact"]
  (pdf/format-party-details [{:name "Name" :address "A1"}
                             {:address "Address" :contact "C2"}
                             {:contact " Contact " :name " N3 " :address " A3 "}])
  => ["Name\nA1" "\n" "Address\nC2" "\n" "N3\nA3\nContact"])

(facts "property-id"
  (pdf/property-id {:propertyId "75341600550007"
                    :documents  []})
  => "753-416-55-7"
  (pdf/property-id {:propertyId "75341600550007"
                    :documents  [{:schema-info {:type "location"}
                                  :data        {:kiinteisto {:maaraalaTunnus "9876"}}}]})
  => "753-416-55-7-M9876"
  (pdf/property-id {:propertyId "75341600550007"
                    :documents  [{:schema-info {:type "location"}
                                  :data        {:kiinteisto {:maaraalaTunnus " "}}}]})
  => "753-416-55-7"
  (pdf/property-id {:propertyId "75341600550007"
                    :documents  [{:schema-info {:type "location"}
                                  :data        {:kiinteisto {:maaraalaTunnus " "}}}
                                 {:schema-info {:type "greeting"}
                                  :data        {:kiinteisto {:maaraalaTunnus " hello "}}}
                                 {:schema-info {:type "location"}
                                  :data        {:kiinteisto {:maaraalaTunnus " correct "}}}
                                 {:schema-info {:type "location"}
                                  :data        {:kiinteisto {:maaraalaTunnus "world"}}}]})
  => "753-416-55-7-Mcorrect")

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

;;Head designers and designer are approved iff approved? argument is not false
(defn head-designer [firstname lastname difficulty
                     education-select education-other & [approved?]]
  (util/assoc-when
    {:schema-info {:name    "paasuunnittelija"
                   :subtype "suunnittelija"}
     :data        {:suunnittelutehtavanVaativuusluokka difficulty
                   :henkilotiedot                      {:etunimi  firstname
                                                        :sukunimi lastname}
                   :patevyys                           {:koulutusvalinta education-select
                                                        :koulutus        education-other}}}
    :meta (when-not (false? approved?) {:_approved "approved"})))

(defn designer [firstname lastname role-code other-role difficulty
                education-select education-other & [approved?]]
  (util/assoc-when
    {:schema-info {:name    "suunnittelija"
                   :subtype "suunnittelija"}
     :data        {:kuntaRoolikoodi                    role-code
                   :muuSuunnittelijaRooli              other-role
                   :suunnittelutehtavanVaativuusluokka difficulty
                   :henkilotiedot                      {:etunimi  firstname
                                                        :sukunimi lastname}
                   :patevyys                           {:koulutusvalinta education-select
                                                        :koulutus        education-other}}}
    :meta (when-not (false? approved?) {:_approved "approved"})))

(facts "designers - only approved designers are included"
  (let [not-approved-head (head-designer "Pena" "Designer" "B"
                                         "other" "Hobbyist" false)
        not-approved-des  (designer "Pentti" "Penttinen" "GEO-suunnittelija"
                                    nil "AA" "sisustusarkkitehti" nil false)
        head1             (head-designer "Head" "Designer" "B"
                                         "Insin\u00f6\u00f6ri" nil)
        head2             (head-designer "Second" "Header" "A"
                                         "other" "Hobbyist")
        des1              (designer "Andy" "Aardvark" "GEO-suunnittelija"
                                    nil "AA" "sisustusarkkitehti" nil)
        des2              (designer "Betty" "Bopper" "other" "Builder"
                                    "ei tiedossa" "other" "Maker")
        head3             (designer "Heddy" "Haughty" "p\u00e4\u00e4suunnittelija" "Builder"
                                    "C" "arkkitehti" "Maker")
        des3              (designer "Charlie" "Crooked" "ei tiedossa" "Builder"
                                    "A" "arkkitehtiylioppilas" nil)
        des4              (designer "Diana" "Dreamer" "rakennussuunnittelija" nil
                                    "B" "rakennusarkkitehti" nil)
        head4             (head-designer " " "" "A" "other" "Hobbyist")
        des5              (designer "" " " "rakennussuunnittelija" nil
                                    "B" "rakennusarkkitehti" nil)
        infos             (pdf/designers {:lang        "fi"
                                          :application {:documents [head1 head2 des1
                                                                    des2 head3 des3
                                                                    des4 head4 des5
                                                                    not-approved-head
                                                                    not-approved-des]}})]
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
                              :in-any-order))
    (fact "No ex-designers"
      (pdf/designers {:lang        "fi"
                      :application {:documents [(assoc head1 :disabled true)
                                                head2
                                                (assoc des1 :disabled true)
                                                des2]}})
      => [{:role       "P\u00e4\u00e4suunnittelija"
           :difficulty "A"
           :person     "Second Header, Hobbyist"}
          {:role       "Builder"
           :difficulty "ei tiedossa"
           :person     "Betty Bopper, Maker"}])))

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
        build1  {:kiinteiston-autopaikat       "1"
                 :autopaikat-yhteensa          "3"
                 :tag                          "Foo"
                 :description                  "Foobar"
                 :paloluokka                   "P1"
                 :vss-luokka                   "VSS1"
                 :kokoontumistilanHenkilomaara "5"
                 :show-building                true}
        build2  {:kiinteiston-autopaikat       "2"
                 :autopaikat-yhteensa          "4"
                 :description                  "Burn!"
                 :paloluokka                   "P2"
                 :vss-luokka                   "VSS2"
                 :building-id                  "12345"
                 :kokoontumistilanHenkilomaara "7"
                 :show-building                true}
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
    (fact "Building parking: building 1, only total"
      (pdf/building-parking "fi" (assoc build1 :kiinteiston-autopaikat ""))
      => [{:text [:strong "Foo: Foobar"] :amount ""}
          {:text "Autopaikat yhteens\u00e4" :amount "3"}]
      (pdf/building-parking "fi" (dissoc build1 :kiinteiston-autopaikat))
      => [{:text [:strong "Foo: Foobar"] :amount ""}
          {:text "Autopaikat yhteens\u00e4" :amount "3"}])
    (fact "Building parking: building 2"
      (pdf/building-parking "fi" build2)
      => [{:text [:strong "Burn! \u2013 12345"] :amount ""}
          {:text "Kiinteist\u00f6n autopaikat" :amount "2"}
          {:text "Autopaikat yhteens\u00e4" :amount "4"}])
    (fact "Building parking: building 2: no spots"
      (pdf/building-parking "fi" (assoc build2
                                        :kiinteiston-autopaikat "  "
                                        :autopaikat-yhteensa "  "))
      => nil)
    (fact "Parking: both buildings"
      (pdf/parking [build1 build2] :fi)
      => [{:text [:strong "Foo: Foobar"] :amount ""}
          {:text "Kiinteist\u00f6n autopaikat" :amount "1"}
          {:text "Autopaikat yhteens\u00e4" :amount "3"}
          {:text "" :amount "" ::pdf/styles {:row :pad-before}}
          {:text [:strong "Burn! \u2013 12345"] :amount ""}
          {:text "Kiinteist\u00f6n autopaikat" :amount "2"}
          {:text "Autopaikat yhteens\u00e4" :amount "4"}])
    (fact "Parking: both buildings: no spots in building 1"
      (pdf/parking [(dissoc build1 :kiinteiston-autopaikat :autopaikat-yhteensa)
                    build2] :fi)
      => [{:text [:strong "Burn! \u2013 12345"] :amount ""}
          {:text "Kiinteist\u00f6n autopaikat" :amount "2"}
          {:text "Autopaikat yhteens\u00e4" :amount "4"}])
    (fact "Parking: both buildings: no spots in building 2"
      (pdf/parking [build1
                    (dissoc build2 :kiinteiston-autopaikat :autopaikat-yhteensa)] :fi)
      => [{:text [:strong "Foo: Foobar"] :amount ""}
          {:text "Kiinteist\u00f6n autopaikat" :amount "1"}
          {:text "Autopaikat yhteens\u00e4" :amount "3"}])
    (fact "Parking: both buildings: no spots at all"
      (pdf/parking [(assoc build1 :kiinteiston-autopaikat "" :autopaikat-yhteensa "  ")
                    (dissoc build2 :kiinteiston-autopaikat :autopaikat-yhteensa)] :fi)
      => nil)

    (fact "combine-building-fields: building 1"
      (pdf/combine-building-fields :paloluokka [build1]) => "P1"
      (pdf/combine-building-fields :vss-luokka [build1]) => "VSS1"
      (pdf/combine-building-fields :kokoontumistilanHenkilomaara [build1]) => "5")
    (fact "combine-building-fields: none"
      (pdf/combine-building-fields :bad [build1]) => ""
      (pdf/combine-building-fields :bad [build1 build2]) => ""
      (pdf/combine-building-fields :paloluokka [(assoc build1 :paloluokka "   ")]) => "")
    (fact "combine-building-fields: both buildings"
      (pdf/combine-building-fields :paloluokka [build1 build2]) => "P1 / P2"
      (pdf/combine-building-fields :vss-luokka [build1 build2]) => "VSS1 / VSS2"
      (pdf/combine-building-fields :kokoontumistilanHenkilomaara [build1 build2]) => "5 / 7")
    (fact "combine-building-fields: some or all missing"
      (pdf/combine-building-fields :paloluokka [(dissoc build1 :paloluokka) build2]) => "P2"
      (pdf/combine-building-fields :paloluokka [build1 (dissoc build2 :paloluokka)]) => "P1"
      (pdf/combine-building-fields :paloluokka [{} {:paloluokka "  "}]) => "")
    (fact "combine-building-fields: distinct"
      (pdf/combine-building-fields :paloluokka [{:paloluokka "one"} {:paloluokka "two"} {:paloluokka "three"}])
      => "one / two / three"
      (pdf/combine-building-fields :paloluokka [{:paloluokka "one"} {:paloluokka "one"} {:paloluokka "three"}])
      => "one / three"
      (pdf/combine-building-fields :paloluokka [{:paloluokka "one"} {:paloluokka "one"} {:paloluokka "one"}])
      => "one")))

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
                                :muuMika                                {:value true},
                                :muuMikaValue                           {:value ""}
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

(defn header [[org board center right]
              [section date]]
  [:div.header
   [:div.section.header
    [:div.row.pad-after
     [:div.cell.cell--40
      org (when board [:div board])]
     [:div.cell.cell--20.center [:div center]]
     [:div.cell.cell--40.right
      [:div.permit right]]]
    [:div.row
     [:div.cell.cell--40 section]
     [:div.cell.cell--20.center [:div date]]
     [:div.cell.cell--40.right.page-number "Sivu" " " [:span.pageNumber]]]]])

(defn check-header-and-title [lang application verdict header-result title-result]
  (fact "Verdict header"
    (html/verdict-header lang application verdict)
    => (apply header header-result))
  (fact {:midje/description (str "Title: " title-result)}
    (html/pdf-title lang application verdict) => title-result))

(def runeberg 1580860800000) ; 5.2.2020
(def flagday  1591254570278) ; 4.6.2020

(against-background
  [(lupapalvelu.organization/get-organization-name anything anything) => "Organization name"]
  (facts "verdict-header and pdf-title"
    (check-header-and-title :fi {:id "LP-123"} {:category "r"}
                            [["Organization name" nil "Päätös" "Lupapäätös"]
                             [nil nil]]
                            "LP-123 - Lupapäätös - Organization name")
    (check-header-and-title :fi {:id "LP-123"}
                            {:category "r"
                             :preview? true}
                            [["Organization name" nil [:span.preview "Esikatselu"] "Lupapäätös"]
                             [nil nil]]
                            "LP-123 - Lupapäätös - Organization name")

    (check-header-and-title :fi {:id "LP-123"}
                            {:category   "r"
                             :published  {:published flagday}
                             :data       {:verdict-section "12"
                                          :verdict-date    runeberg}
                             :template   {:giver "lautakunta"}
                             :references {:boardname "Board name"}}
                            [["Organization name" "Board name" "Päätös" "Lupapäätös"]
                             ["§12" "5.2.2020"]]
                            "LP-123 - Lupapäätös - 5.2.2020 - Organization name, Board name")

    (check-header-and-title :fi {:id "LP-123"}
                            {:category  "r"
                             :state     {:_user     "Bob"
                                         :_modified flagday
                                         :_value    "proposal"}
                             :published {:published flagday}
                             :data      {:verdict-section "12"
                                         :verdict-date    runeberg}
                             :template  {:giver    "viranhaltija"
                                         :title    "Title"
                                         :subtitle "Subtitle"}}
                            [["Organization name" nil "Päätösehdotus" "Subtitle"]
                             ["§12" "5.2.2020"]]
                            "LP-123 - Subtitle - 5.2.2020 - Organization name")

    (check-header-and-title :fi {:id "LP-123"}
                            {:category   "r"
                             :state      {:_user     "Bob"
                                          :_modified flagday
                                          :_value    "published"}
                             :published  {:published flagday}
                             :data       {:verdict-section "12"
                                          :verdict-date    runeberg}
                             :template   {:giver    "viranhaltija"
                                          :title    "Title"
                                          :subtitle "Subtitle"}
                             :references {:organization-name "New name"}}
                            [["New name" nil "Title" "Subtitle"]
                             ["§12" "5.2.2020"]]
                            "LP-123 - Subtitle - 5.2.2020 - New name")

    (check-header-and-title :fi
                            {:id               "LP-123"
                             :primaryOperation {:name "onpahan-vaan-kayttolupa"}}
                            {:category   "ya"
                             :state      {:_user     "Bob"
                                          :_modified flagday
                                          :_value    "published"}
                             :published  {:published flagday}
                             :data       {:verdict-section "12"
                                          :verdict-type    "katulupa"
                                          :verdict-date    runeberg}
                             :template   {:giver "viranhaltija"}
                             :references {:organization-name "New name"}}
                            [["New name" nil "Päätös" "Katulupa"]
                             ["§12" "5.2.2020"]]
                            "LP-123 - Katulupa - 5.2.2020 - New name")

    (check-header-and-title :fi
                            {:id               "LP-123"
                             :primaryOperation {:name "onpahan-vaan-kayttolupa"}}
                            {:category   "ya"
                             :legacy?    true
                             :state      {:_user     "Bob"
                                          :_modified flagday
                                          :_value    "published"}
                             :published  {:published flagday}
                             :data       {:verdict-section "12"}
                             :template   {:giver "viranhaltija"}
                             :references {:organization-name "New name"}}
                            [["New name" nil "Päätös" "Käyttölupa"]
                             ["§12" nil]]
                            "LP-123 - Käyttölupa - New name")

    (check-header-and-title :fi
                            {:id "LP-123" :permitSubtype "poikkeamislupa"}
                            {:category   "p"
                             :legacy?    true
                             :state      {:_user     "Bob"
                                          :_modified flagday
                                          :_value    "published"}
                             :published  {:published flagday}
                             :data       {:verdict-section "12"}
                             :template   {:giver "viranhaltija"}
                             :references {:organization-name "New name"}}
                            [["New name" nil "Päätös" "Poikkeamispäätös"]
                             ["§12" nil]]
                            "LP-123 - Poikkeamispäätös - New name")

    (check-header-and-title :fi
                            {:id            "LP-123"
                             :permitSubtype "suunnittelutarveratkaisu"}
                            {:category   "p"
                             :state      {:_user     "Bob"
                                          :_modified flagday
                                          :_value    "published"}
                             :published  {:published flagday}
                             :data       {:verdict-section "12"
                                          :verdict-date    runeberg}
                             :template   {:giver "viranhaltija"}
                             :references {:organization-name "New name"}}
                            [["New name" nil "" "Suunnittelutarveratkaisu"]
                             ["§12" "5.2.2020"]]
                            "LP-123 - Suunnittelutarveratkaisu - 5.2.2020 - New name")

    (check-header-and-title :fi
                            {:id "LP-123"}
                            {:category   "contract"
                             :state      {:_user     "Bob"
                                          :_modified flagday
                                          :_value    "published"}
                             :published  {:published flagday}
                             :data       {:verdict-section "12"
                                          :verdict-date    runeberg}
                             :template   {:giver "viranhaltija"}
                             :references {:organization-name "New name"}}
                            [["New name" nil nil "Sopimus"]
                             ["§12" "5.2.2020"]]
                            "LP-123 - Sopimus - 5.2.2020 - New name")

    (check-header-and-title :fi
                            {:id "LP-123"}
                            {:category   "migration-contract"
                             :legacy?    true
                             :state      {:_user     "Bob"
                                          :_modified flagday
                                          :_value    "published"}
                             :published  {:published flagday}
                             :data       {:verdict-section "12"}
                             :template   {:giver "viranhaltija"}
                             :references {:organization-name "New name"}}
                            [["New name" nil nil "Päätös / Sopimus"]
                             ["§12" nil]]
                            "LP-123 - Päätös / Sopimus - New name")

    (check-header-and-title :fi
                            {:id "LP-123"}
                            {:category   "kt"
                             :legacy?    true
                             :state      {:_user     "Bob"
                                          :_modified flagday
                                          :_value    "published"}
                             :published  {:published flagday}
                             :data       {:verdict-section "12"}
                             :template   {:giver "viranhaltija"}
                             :references {:organization-name "New name"}}
                            [["New name" nil nil "Päätös"]
                             ["§12" nil]]
                            "LP-123 - Päätös - New name")

    (check-header-and-title :fi
                            {:id "LP-123"}
                            {:category   "ymp"
                             :legacy?    true
                             :state      {:_user     "Bob"
                                          :_modified flagday
                                          :_value    "published"}
                             :published  {:published flagday}
                             :data       {}
                             :template   {:giver "viranhaltija"}
                             :references {:organization-name "New name"}}
                            [["New name" nil nil "Päätös"]
                             [nil nil]]
                            "LP-123 - Päätös - New name")

    (check-header-and-title :fi
                            {:id "LP-123"}
                            {:category   "ya"
                             :state      {:_user     "Bob"
                                          :_modified flagday
                                          :_value    "proposal"}
                             :data       {:verdict-type "katulupa"
                                          :verdict-date runeberg}
                             :template   {:giver "lautakunta"}
                             :references {:organization-name "New name"
                                          :boardname         "Bored"}}
                            [["New name" "Bored" "Päätösehdotus" "Katulupa"]
                             [nil "5.2.2020"]]
                            "LP-123 - Katulupa - 5.2.2020 - New name, Bored")))

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


(against-background
  [(lupapalvelu.application-meta-fields/enrich-with-link-permit-data anything) => {:linkPermitData "link"}]
  (facts "link permits"
    (fact "Pate: kerrostalo-rivitalo"
      (pdf/link-permits {:verdict     {:category "r"}
                         :application {:primaryOperation {:name "kerrostalo-rivitalo"}}}) => nil)
    (fact "Pate: tj"
      (pdf/link-permits {:verdict     {:category "tj"}
                         :application {:primaryOperation {:name "kerrostalo-rivitalo"}}}) => "link")
    (fact "Pate: ya"
      (pdf/link-permits {:verdict     {:category "ya"}
                         :application {:primaryOperation {:name "kerrostalo-rivitalo"}}}) => "link")
    (fact "Raktyo-aloit-loppuunsaat"
      (pdf/link-permits {:verdict     {:category "r"}
                         :application {:primaryOperation {:name "raktyo-aloit-loppuunsaat"}}}) => "link"
      (pdf/link-permits {:verdict     {}
                         :application {:primaryOperation {:name "raktyo-aloit-loppuunsaat"}}}) => "link")
    (fact "Muutoslupa"
      (pdf/link-permits {:verdict     {:category "r"}
                         :application {:primaryOperation {:name "kerrostalo-rivitalo"}
                                       :permitSubtype    "muutoslupa"}}) => "link"
      (pdf/link-permits {:verdict     {:category "r"}
                         :application {:primaryOperation {:name "kerrostalo-rivitalo"}
                                       :permitSubtype    "muutoslupa"}}) => "link")))
