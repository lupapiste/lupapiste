(ns lupapalvelu.pate.pdf-test
  "PDF testing concentrates mainly on the source value resolution."
  (:require [lupapalvelu.document.schemas :as doc-schemas]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pate-test-util :as ptu]
            [lupapalvelu.pate.columns :as cols]
            [lupapalvelu.pate.pdf :as pdf]
            [lupapalvelu.pate.pdf-html :as html]
            [lupapalvelu.pate.verdict-schemas :as vs]
            [lupapalvelu.statistics.core :as stats]
            [lupapalvelu.statistics.db :as stats-db]
            [lupapalvelu.statistics.schemas :as stats-sc]
            [lupapalvelu.statistics.tags :as stats-tags]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.util :as util]))

(testable-privates lupapalvelu.pate.pdf
                   update-sum-field update-sum-map double->str
                   operation-infos)

(facts "applicants"
  (let [app {:documents [(ptu/party-person "  Annie  " "Applicant"
                                           " Road 10 " " 12345 "
                                           " Anchorage " " USA ")
                         (ptu/party-person "Other" "Otter"
                                           "Tori 10" "26820" "Oulu" "FIN")
                         (ptu/party-person "" ""
                                           "Ignore Way" "26820" "Missingville"
                                           "FIN")
                         (ptu/party-company " Firm " " One Way 8 " " 12345 "
                                            " Tampere " " FIN " " Yrjö " " Yhteyshenkilö ")
                         (ptu/party-company "" "Gone Way 8" "12345" "Tampere" "FIN")
                         (ptu/party-company "Foreign Firm" "Guang Hua Lu" "88"
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
        app         {:documents [(responsible ptu/party-person "Randy" "Responsible"
                                              "Anchovies 54" "44558" "Toronto" "CAN")
                                 (responsible ptu/party-company "Responsibility Inc" "Jack Daniels Drive"
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

(defn vm
  ([v m] (util/strip-nils {:value v :modified m}))
  ([v] (vm v nil)))

;;Head designers and designer are approved iff approved? argument is not false
(defn head-designer [firstname lastname difficulty
                     education-select education-other & [approved? ts]]
  (util/assoc-when
    {:schema-info {:name    "paasuunnittelija"
                   :subtype "suunnittelija"}
     :data        {:suunnittelutehtavanVaativuusluokka (vm difficulty ts)
                   :henkilotiedot                      {:etunimi  (vm firstname)
                                                        :sukunimi (vm lastname)}
                   :patevyys                           {:koulutusvalinta (vm education-select)
                                                        :koulutus        (vm education-other)}}}
    :meta (when-not (false? approved?) {:_approved {:value     "approved"
                                                    :timestamp 10}})))

(defn designer [firstname lastname role-code other-role difficulty
                education-select education-other & [approved? ts]]
  (util/assoc-when
    {:schema-info {:name    "suunnittelija"
                   :subtype "suunnittelija"}
     :data        {:kuntaRoolikoodi                    (vm role-code)
                   :muuSuunnittelijaRooli              (vm other-role)
                   :suunnittelutehtavanVaativuusluokka (vm difficulty ts)
                   :henkilotiedot                      {:etunimi  (vm firstname)
                                                        :sukunimi (vm lastname)}
                   :patevyys                           {:koulutusvalinta (vm education-select)
                                                        :koulutus        (vm education-other)}}}
    :meta (when-not (false? approved?) {:_approved {:value     "approved"
                                                    :timestamp 10}})))

(facts "designers - only approved designers are included"
  (let [not-approved-head (head-designer "Pena" "Designer" "B"
                                         "other" "Hobbyist" false)
        not-approved-des  (designer "Pentti" "Penttinen" "GEO-suunnittelija"
                                    nil "AA" "sisustusarkkitehti" nil nil 20)
        head1             (head-designer "Head" "Designer" "B"
                                         "Insinööri" nil)
        head2             (head-designer "Second" "Header" "A"
                                         "other" "Hobbyist")
        des1              (designer "Andy" "Aardvark" "GEO-suunnittelija"
                                    nil "AA" "sisustusarkkitehti" nil)
        des2              (designer "Betty" "Bopper" "other" "Builder"
                                    "ei tiedossa" "other" "Maker")
        head3             (designer "Heddy" "Haughty" "pääsuunnittelija" "Builder"
                                    "C" "arkkitehti" "Maker")
        des3              (designer "Charlie" "Crooked" "ei tiedossa" "Builder"
                                    "A" "arkkitehtiylioppilas" nil)
        des4              (designer "Diana" "Dreamer" "rakennussuunnittelija" nil
                                    "B" "rakennusarkkitehti" nil)
        head4             (head-designer " " "" "A" "other" "Hobbyist")
        des5              (designer "" " " "rakennussuunnittelija" nil
                                    "B" "rakennusarkkitehti" nil)
        infos             (pdf/designers pdf/format-designer-details
                                         {:lang        "fi"
                                          :original-application {:documents [head1 head2 des1
                                                                             des2 head3 des3
                                                                             des4 head4 des5
                                                                             not-approved-head
                                                                             not-approved-des]}})]
    (fact "No empty designers"
      (count infos) => 7)
    (fact "Head designers are first"
      (take 3 infos) => (just [{:role       "Pääsuunnittelija"
                                :difficulty "B"
                                :person     "Head Designer, Insinööri"}
                               {:role       "Pääsuunnittelija"
                                :difficulty "A"
                                :person     "Second Header, Hobbyist"}
                               {:role       "Pääsuunnittelija"
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
      (pdf/designers pdf/format-designer-details
                     {:lang        "fi"
                      :original-application {:documents [(assoc head1 :disabled true)
                                                         head2
                                                         (assoc des1 :disabled true)
                                                         des2]}})
      => [{:role       "Pääsuunnittelija"
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
                                                :data        {:foo                    "bar"
                                                              :valtakunnallinenNumero "123-PRT"}}]
                         :primaryOperation    {:id "op1"}
                         :secondaryOperations [{:id "op2" :created 1}]}
        sisatila-muutos "Rakennuksen sisätilojen muutos (käyttötarkoitus ja/tai muu merkittävä sisämuutos) \u2013 123-PRT"
        purkaminen      "Purkamislupa (rakennuksen purkaminen)"]
    (fact "Operation infos"
      (operation-infos app)
      => [{:id      "op1"
           :name    "sisatila-muutos"
           :vtj-prt "123-PRT"}
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
      (pdf/operations {:lang        "fi"
                       :verdict     {:data {:bulletin-desc-as-operation true
                                            :bulletin-op-description    "   This is bulletin description  "}}
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
                 :autopaikat-vahintaan         "3"
                 :tag                          "Foo"
                 :description                  "Foobar"
                 :paloluokka                   "P1"
                 :vss-luokka                   "VSS1"
                 :kokoontumistilanHenkilomaara "5"
                 :show-building                true}
        build2  {:kiinteiston-autopaikat       "2"
                 :autopaikat-vahintaan         "4"
                 :description                  "Burn!"
                 :paloluokka                   "P2"
                 :vss-luokka                   "VSS2"
                 :building-id                  "12345"
                 :kokoontumistilanHenkilomaara "7"
                 :show-building                true}
        build3  (->> vs/autopaikat
                     (map-indexed (fn [idx k]
                                    [k (str (inc idx))]))
                     (into {:description   "All fields"
                            :tag           "TAG"
                            :vss-luokka    "VSS3"
                            :paloluokka    "P3"
                            :building-id   "333"
                            :show-building true}))
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
          {:text "Kiinteistön autopaikat" :amount "1"}
          {:text "Autopaikat vähintään" :amount "3"}])
    (fact "Building parking: building 1, only total"
      (pdf/building-parking "fi" (assoc build1 :kiinteiston-autopaikat ""))
      => [{:text [:strong "Foo: Foobar"] :amount ""}
          {:text "Autopaikat vähintään" :amount "3"}]
      (pdf/building-parking "fi" (dissoc build1 :kiinteiston-autopaikat))
      => [{:text [:strong "Foo: Foobar"] :amount ""}
          {:text "Autopaikat vähintään" :amount "3"}])
    (fact "Building parking: building 2"
      (pdf/building-parking "fi" build2)
      => [{:text [:strong "Burn! \u2013 12345"] :amount ""}
          {:text "Kiinteistön autopaikat" :amount "2"}
          {:text "Autopaikat vähintään" :amount "4"}])
    (fact "Building parking: building 2: no spots"
      (pdf/building-parking "fi" (assoc build2
                                        :kiinteiston-autopaikat "  "
                                        :autopaikat-vahintaan "  "))
      => nil)
    (fact "Parking: both buildings"
      (pdf/parking [build1 build2] :fi)
      => [{:text [:strong "Foo: Foobar"] :amount ""}
          {:text "Kiinteistön autopaikat" :amount "1"}
          {:text "Autopaikat vähintään" :amount "3"}
          {:text "" :amount "" ::pdf/styles {:row :pad-before}}
          {:text [:strong "Burn! \u2013 12345"] :amount ""}
          {:text "Kiinteistön autopaikat" :amount "2"}
          {:text "Autopaikat vähintään" :amount "4"}])
    (fact "Parking: both buildings: no spots in building 1"
      (pdf/parking [(dissoc build1 :kiinteiston-autopaikat :autopaikat-vahintaan)
                    build2] :fi)
      => [{:text [:strong "Burn! \u2013 12345"] :amount ""}
          {:text "Kiinteistön autopaikat" :amount "2"}
          {:text "Autopaikat vähintään" :amount "4"}])
    (fact "Parking: both buildings: no spots in building 2"
      (pdf/parking [build1
                    (dissoc build2 :kiinteiston-autopaikat :autopaikat-vahintaan)] :fi)
      => [{:text [:strong "Foo: Foobar"] :amount ""}
          {:text "Kiinteistön autopaikat" :amount "1"}
          {:text "Autopaikat vähintään" :amount "3"}])
    (fact "Parking: both buildings: no spots at all"
      (pdf/parking [(assoc build1 :kiinteiston-autopaikat "" :autopaikat-vahintaan "")
                    (dissoc build2 :kiinteiston-autopaikat :autopaikat-vahintaan)] :fi)
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
      => "one")
    (fact "all parking spot fields present"
      (pdf/parking [build3] :fi)
      => [{:amount "" :text [:strong "TAG: All fields \u2013 333"]}
          {:amount "1" :text "Kiinteistön autopaikat"}
          {:amount "2" :text "Kiinteistön ulkopuoliset autopaikat"}
          {:amount "3" :text "Rakennetut autopaikat (yhteensä)"}
          {:amount "4" :text "Autopaikat vähintään"}
          {:amount "5" :text "Autopaikat enintään"}
          {:amount "6" :text "Rakennettava"}])))

(fact "tj-vastattavat-tyot"
  (pdf/tj-vastattavat-tyot
    {:documents [{:schema-info {:name "tyonjohtaja-v2"}
                  :data        {:kuntaRoolikoodi {:value "KVV-työnjohtaja"}
                                :vastattavatTyotehtavat
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
  => ["Ulkopuolinen KVV-työ" "Sisäpuolinen KVV-työ"])

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

(defn mitat [kala rakala koala tila raktil]
  {:mitat (util/assoc-when {}
                           :kerrosala kala
                           :rakennusoikeudellinenKerrosala rakala
                           :kokonaisala koala
                           :tilavuus tila
                           :rakennusoikeusKuutiometreina raktil)})

(facts "Dimensions"
  (fact "double->str"
    (double->str nil) => nil
    (double->str 0) => nil
    (double->str 0.04) => nil
    (double->str 0.05) => "0.1"
    (double->str 3.04) => "3"
    (double->str 12.0500) => "12.1")
  (fact "double->str for negatives"
    (double->str -0) => nil
    (double->str -0.04) => nil
    (double->str -0.05) => "-0.1"
    (double->str -3.04) => "-3"
    (double->str -12.0500) => "-12.1")
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
                      :data        (mitat 1 2 3 4 nil)}
          new-build2 {:schema-info {:name "uusi-rakennus-ei-huoneistoa"}
                      :data        (mitat 10 12 13 14 nil)}
          extend1    {:schema-info {:name "rakennuksen-laajentaminen"}
                      :data        (assoc (mitat 1 2 201 4 nil)
                                          :laajennuksen-tiedot (mitat 101 102 103 104 nil))}
          extend2    {:schema-info {:name "rakennuksen-laajentaminen-ei-huoneistoja"}
                      :data        (assoc (mitat 1 2 202 4 nil)
                                          :laajennuksen-tiedot (mitat 201 202 203 204 nil))}
          skip       {:schema-info {:name "onpahanVaanRakennus"}
                      :data        (mitat 1 2 3 4 nil)}]
      (pdf/dimensions {:documents [new-build1 new-build2 extend1 extend2 skip]})
      => {:kerrosala                      "313" ; (+ 1 10 101 201)
          :rakennusoikeudellinenKerrosala "318" ; (+ 2 12 102 202)
          :tilavuus                       "326" ; (+ 4 14 104 204)
          :laajennettavaKokonaisala       "306" ; (+ 103 203)
          :rakennettavaKokonaisala        "16"  ; (+ 13 3)
          :uusiKokonaisala                "419"  ; (+ 201 202 16)
          }
      (pdf/dimensions {:documents [new-build1 new-build2 extend1
                                   extend2 skip new-build1 new-build2
                                   extend1 extend2 skip]})
      => {:kerrosala                      "626"
          :rakennusoikeudellinenKerrosala "636"
          :laajennettavaKokonaisala       "612"
          :rakennettavaKokonaisala        "32"
          :uusiKokonaisala                "838"
          :tilavuus                       "652"}
      (pdf/dimensions {:documents skip}) => {})))

(fact "missing dimensions"
  (let [new-build1 {:schema-info {:name "uusiRakennus"}
                    :data        (mitat nil 2 3 4 nil)}
        new-build2 {:schema-info {:name "uusi-rakennus-ei-huoneistoa"}
                    :data        (mitat 10 nil 13 14 nil)}
        extend1    {:schema-info {:name "rakennuksen-laajentaminen"}
                    :data        (assoc (mitat 1 2 204 4 nil)
                                        :laajennuksen-tiedot (mitat 101 102 nil 104 nil))}
        extend2    {:schema-info {:name "rakennuksen-laajentaminen-ei-huoneistoja"}
                    :data        (assoc (mitat 1 2 203 4 nil)
                                        :laajennuksen-tiedot (mitat 201 202 203 nil nil))}]
    (pdf/dimensions {:documents [new-build1 new-build2 extend1 extend2]})
    => {:kerrosala                      "312"
        :rakennusoikeudellinenKerrosala "306"
        :rakennettavaKokonaisala        "16"
        :laajennettavaKokonaisala       "203"
        :uusiKokonaisala                "423"
        :tilavuus                       "122"}))

(fact "bad dimensions"
  (let [new-build1 {:schema-info {:name "uusiRakennus"}
                    :data        (mitat "Bad" 2 3 4 nil)}
        new-build2 {:schema-info {:name "uusi-rakennus-ei-huoneistoa"}
                    :data        (mitat 10 "to" 13 14 nil)}
        extend1    {:schema-info {:name "rakennuksen-laajentaminen"}
                    :data        (assoc (mitat 1 2 3 4 nil)
                                        :laajennuksen-tiedot (mitat 101 102 "the" 104 nil))}
        extend2    {:schema-info {:name "rakennuksen-laajentaminen-ei-huoneistoja"}
                    :data        (assoc (mitat 1 2 3 4 nil)
                                        :laajennuksen-tiedot (mitat 201 202 203 "bone" nil))}]
    (pdf/dimensions {:documents [new-build1 new-build2 extend1 extend2]})
    => {:kerrosala                      "312"
        :rakennusoikeudellinenKerrosala "306"
        :rakennettavaKokonaisala        "16"
        :laajennettavaKokonaisala       "203"
        :uusiKokonaisala                "22"
        :tilavuus                       "122"}))

(fact "new 2.2.6 dimensions"
  (fact "just rakennusoikeusKuutiometreina"
    (let [new-build1 {:schema-info {:name "uusiRakennus"}
                      :data        (mitat 1 nil 3 4 50)}
          new-build2 {:schema-info {:name "uusi-rakennus-ei-huoneistoa"}
                      :data        (mitat 10 12 13 14 nil)}
          extend1    {:schema-info {:name "rakennuksen-laajentaminen"}
                      :data        (assoc (mitat 1 2 3 4 nil)
                                          :laajennuksen-tiedot (mitat 101 nil 103 104 3))}
          extend2    {:schema-info {:name "rakennuksen-laajentaminen-ei-huoneistoja"}
                      :data        (assoc (mitat 1 2 3 4 nil)
                                          :laajennuksen-tiedot (mitat 201 202 203 204 nil))}]
      (pdf/dimensions {:documents [new-build1 new-build2 extend1 extend2]})
      => {:kerrosala                      "313"
          :rakennusoikeudellinenKerrosala "214"
          :rakennusoikeusKuutiometreina   "53" ; 50 + 3
          :rakennettavaKokonaisala        "16"
          :laajennettavaKokonaisala       "306"
          :tilavuus                       "326"
          :uusiKokonaisala                "22"})))

(fact "negative dimensions"
  (let [extend {:schema-info {:name "rakennuksen-laajentaminen"}
                :data        (assoc (mitat 200 100 3 4 nil)
                                    :laajennuksen-tiedot (mitat -50 100 200 5 nil))}]
    (pdf/dimensions {:documents [extend]})
    => {:kerrosala                      "-50"
        :rakennusoikeudellinenKerrosala "100"
        :laajennettavaKokonaisala       "200"
        :tilavuus                       "5"
        :uusiKokonaisala                "3"}))

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

(facts "kuntaroolikoodi translations for pdf"
  (let [roles (->> doc-schemas/kuntaroolikoodi
                   first
                   :body
                   (map :name)
                   (into #{}))]
    (fact "sanity check number of roles"
      (count roles) => 24)
    (doseq [lang [:fi :sv]]
      (doseq [role roles]
        (let [kw (util/kw-path :pdf :kuntaRoolikoodi role)]
          (fact {:midje/description (format "Have %s translation for %s"
                                            lang kw)}
            (i18n/has-term? lang kw) => true))))))

(facts "Trim verdict"
  (let [verdict      {:data       {:verdict-section "123"
                                   :verdict-text    "Decree"
                                   :verdict-code    "ok"
                                   :hello           "world"}
                      :category   :r
                      :references {:boardname "Monopoly"}
                      :state      "draft"}
        trimmed-data {:hello "world"}]
    (fact "Proposal draft"
      (pdf/trim-verdict verdict)
      => (assoc verdict :data trimmed-data))
    (fact "Regular draft"
      (pdf/trim-verdict (dissoc verdict :references))
      => (dissoc verdict :references))
    (fact "Proposal"
      (-> (assoc verdict :state "proposal")
          pdf/trim-verdict :data)
      => trimmed-data)
    (fact "Proposal draft with flag set"
      (let [verdict (assoc-in verdict [:data :verdict-flag] true)]
        (pdf/trim-verdict verdict) => verdict))
    (fact "Proposal with flag set"
      (let [verdict (-> verdict
                        (assoc :state "proposal")
                        (assoc-in [:data :verdict-flag] true))]
        (pdf/trim-verdict verdict) => verdict))))

(facts "Ryhti 2026 Dimensions"
  (fact "Basic new building"
    (let [new-build {:schema-info {:name "uusiRakennuskohde"}
                     :data        {:rakennuskohteen-osa {:ulkokuorentiedot {:kerrosala                      1
                                                                            :rakennusoikeudellinenKerrosala 2
                                                                            :kokonaisala                    3
                                                                            :tilavuus                       4}}}}]
      (pdf/dimensions {:ryhti-data-2026 true
                       :documents       [new-build]})
      => {:kerrosala                      "1"
          :rakennusoikeudellinenKerrosala "2"
          :tilavuus                       "4"
          :rakennettavaKokonaisala        "3"}))

  (fact "Multiple new buildings"
    (let [new-build1 {:schema-info {:name "uusiRakennuskohde"}
                      :data        {:rakennuskohteen-osa {:ulkokuorentiedot {:kerrosala                      1
                                                                             :rakennusoikeudellinenKerrosala 2
                                                                             :kokonaisala                    3
                                                                             :tilavuus                       4}}}}
          new-build2 {:schema-info {:name "uusiRakennuskohde"}
                      :data        {:rakennuskohteen-osa {:ulkokuorentiedot {:kerrosala                      10
                                                                             :rakennusoikeudellinenKerrosala 12
                                                                             :kokonaisala                    13
                                                                             :tilavuus                       14}}}}]
      (pdf/dimensions {:ryhti-data-2026 true
                       :documents       [new-build1 new-build2]})
      => {:kerrosala                      "11"
          :rakennusoikeudellinenKerrosala "14"
          :tilavuus                       "18"
          :rakennettavaKokonaisala        "16"}))

  (fact "Extension only"
    (let [extend {:schema-info {:name "rakennuskohteenLaajentaminen"}
                  :data        (merge {:rakennuskohteen-osa {:ulkokuorentiedot {:kerrosala                      100
                                                                                :rakennusoikeudellinenKerrosala 200
                                                                                :kokonaisala                    300
                                                                                :tilavuus                       400}}}
                                      {:laajennuksen-tiedot {:mitat {:kerrosala                      10
                                                                     :rakennusoikeudellinenKerrosala 20
                                                                     :kokonaisala                    30
                                                                     :tilavuus                       40}}})}]
      (pdf/dimensions {:ryhti-data-2026 true
                       :documents       [extend]})
      => {:kerrosala                      "10"
          :rakennusoikeudellinenKerrosala "20"
          :tilavuus                       "40"
          :laajennettavaKokonaisala       "30"
          :uusiKokonaisala                "300"}))

  (fact "Non-matching schema returns empty map"
    (let [skip {:schema-info {:name "onpahanVaanRakennus"}
                :data        {:rakennuskohteen-osa {:ulkokuorentiedot {:kerrosala                      1
                                                                       :rakennusoikeudellinenKerrosala 2
                                                                       :kokonaisala                    3
                                                                       :tilavuus                       4}}}}]
      (pdf/dimensions {:ryhti-data-2026 true
                       :documents       skip}) => {}
      (pdf/dimensions {:ryhti-data-2026 true
                       :documents       [skip]}) => {})))

(facts "processing"
  (let [app     {:permitType       "R"
                 :state            "submitted"
                 :submitted        12345
                 :primaryOperation {:name "pientalo"}}
        verdict {:template {:inclusions [:processing]}}
        options {:application app
                 :lang        "fi"
                 :verdict     verdict}]

    (fact "Returns nil when verdict has no template inclusions"
      (pdf/processing (assoc options :verdict {})) => nil)

    (fact "Returns nil when :processing is not in inclusions"
      (pdf/processing (assoc options :verdict {:template {:inclusions [:dates]}})) => nil)

    (fact "Returns nil when application is not a processing application"
      (pdf/processing options) => nil
      (provided
        (stats/processing-application? app) => false))

    (fact "Returns nil when read-processing returns nil (no data stored)"
      (pdf/processing options) => nil
      (provided
        (stats/processing-application? app) => true
        (stats-db/read-processing app) => nil))

    (fact "Returns nil when complexity is nil (no-complexity status)"
      (pdf/processing options) => nil
      (provided
        (stats/processing-application? app) => true
        (stats-db/read-processing app) => {:complexity  nil
                                           :used-period {:y 0 :m 1 :d 5}}))

    (fact "Returns nil when complexity->months returns zero"
      (pdf/processing options) => nil
      (provided
        (stats/processing-application? app) => true
        (stats-db/read-processing app) => {:complexity  :special
                                           :used-period {:y 0 :m 0 :d 10}}
        (stats-sc/complexity->months :special) => 0))

    (fact "Returns map with :months, :used and :text on happy path"
      (pdf/processing options) => {:months "3 kk"
                                   :used   "1 kk 5 pv"
                                   :text   "Käsittelyaika"}
      (provided
        (stats/processing-application? app) => true
        (stats-db/read-processing app) => {:complexity  :small
                                           :used-period {:y 0 :m 1 :d 5}}
        (stats-sc/complexity->months :small) => 3
        (i18n/localize-and-fill "fi" :statistics.period.m 3) => "3 kk"
        (stats-tags/period->text anything {:y 0 :m 1 :d 5}) => "1 kk 5 pv"
        (cols/dict-value options :processing) => "Käsittelyaika"))

    (fact "Works with different language"
      (let [sv-options (assoc options :lang "sv")]
        (pdf/processing sv-options) => {:months "3 mån"
                                        :used   "1 mån 5 dgr"
                                        :text   "Handläggningstid"}
        (provided
          (stats/processing-application? app) => true
          (stats-db/read-processing app) => {:complexity  :medium
                                             :used-period {:y 0 :m 1 :d 5}}
          (stats-sc/complexity->months :medium) => 3
          (i18n/localize-and-fill "sv" :statistics.period.m 3) => "3 mån"
          (stats-tags/period->text anything {:y 0 :m 1 :d 5}) => "1 mån 5 dgr"
          (cols/dict-value sv-options :processing) => "Handläggningstid")))

    (fact "Large complexity with longer period"
      (pdf/processing options) => {:months "6 kk"
                                   :used   "1 v 2 kk 10 pv"
                                   :text   "Käsittelyaika"}
      (provided
        (stats/processing-application? app) => true
        (stats-db/read-processing app) => {:complexity  :very-large
                                           :used-period {:y 1 :m 2 :d 10}}
        (stats-sc/complexity->months :very-large) => 6
        (i18n/localize-and-fill "fi" :statistics.period.m 6) => "6 kk"
        (stats-tags/period->text anything {:y 1 :m 2 :d 10}) => "1 v 2 kk 10 pv"
        (cols/dict-value options :processing) => "Käsittelyaika"))

    (fact "Inclusions with :processing as string still works"
      (let [str-options (assoc options :verdict {:template {:inclusions ["processing"]}})]
        (pdf/processing str-options) => {:months "3 kk"
                                         :used   "1 kk 5 pv"
                                         :text   "Käsittelyaika"}
        (provided
          (stats/processing-application? app) => true
          (stats-db/read-processing app) => {:complexity  :small
                                             :used-period {:y 0 :m 1 :d 5}}
          (stats-sc/complexity->months :small) => 3
          (i18n/localize-and-fill "fi" :statistics.period.m 3) => "3 kk"
          (stats-tags/period->text anything {:y 0 :m 1 :d 5}) => "1 kk 5 pv"
          (cols/dict-value str-options :processing) => "Käsittelyaika")))))

