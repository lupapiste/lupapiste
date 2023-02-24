(ns lupapalvelu.verdict-robot-test
  (:require [lupapalvelu.pate.schemas :refer [PateVerdict]]
            [lupapalvelu.verdict-robot.core :as robot]
            [lupapalvelu.verdict-robot.schemas :refer [Sanoma]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.util :as util]
            [sade.date :refer [timestamp]]
            [schema.core :as sc]))

(def verdict {:id             "1a156dd40e40adc8ee064463"
              :schema-version 1
              :category       "r"
              :state          {:_user     "test"
                               :_modified 112233
                               :_value    "draft"}
              :modified       123456
              :data           {:language                "fi"
                               :voimassa                (timestamp "23.11.2023")
                               :appeal                  "muutoksenhakuohje - teksti"
                               :giver                   "  Pate Päättäjä  "
                               :giver-title             " Lupaguru  "
                               :julkipano               (timestamp "24.11.2017")
                               :bulletin-op-description "julkipano - teksti"
                               :purpose                 "k\u00e4ytt\u00f6tarkoitus"
                               :verdict-text            "p\u00e4\u00e4t\u00f6s - teksti"
                               :anto                    (timestamp "20.11.2017")
                               :complexity              "small"
                               :plans                   ["5a156ddf0e40adc8ee064464"
                                                         "6a156ddf0e40adc8ee064464"]
                               :aloitettava             (timestamp "23.11.2022")
                               :muutoksenhaku           (timestamp "27.12.2017")
                               :foremen                 ["erityis-tj"
                                                         "iv-tj"
                                                         "vastaava-tj"
                                                         "vv-tj"]
                               :verdict-code            "myonnetty"
                               :collateral              "vakuus - teksti"
                               :conditions              {:id1 {:condition "muut lupaehdot - teksti"}
                                                         :id2 {:condition "toinen teksti"}}
                               :rights                  "rakennusoikeus"
                               :plans-included          true
                               :reviews                 ["5a156dd40e40adc8ee064463"
                                                         "6a156dd40e40adc8ee064463"]
                               :foremen-included        true
                               :neighbors               "  "
                               :lainvoimainen           (timestamp "27.11.2017")
                               :reviews-included        true
                               :statements              ""
                               :verdict-date            (timestamp "23.11.2017")
                               :automatic-verdict-dates true
                               :handler                 "  Kaino Käsittelijä  "
                               :handler-title           " Lupaduunari "
                               :verdict-section         "99"
                               :buildings               {"5a1561250e40adc8ee064449" {:rakennetut-autopaikat  "1"
                                                                                     :kiinteiston-autopaikat "2"
                                                                                     :autopaikat-yhteensa    "3"
                                                                                     :vss-luokka             "4"
                                                                                     :paloluokka             "5"}}
                               :complexity-text         "  hankkeen vaativuus  "}
              :references     {:foremen      ["erityis-tj" "iv-tj" "vastaava-tj" "vv-tj" "tj"]
                               :plans        [{:id "5a156ddf0e40adc8ee064464"
                                               :fi "Suunnitelmat"
                                               :sv "Planer"
                                               :en "Plans"}
                                              {:id "6a156ddf0e40adc8ee064464"
                                               :fi "Suunnitelmat2"
                                               :sv "Planer2"
                                               :en "Plans2"}]
                               :reviews      [{:id   "5a156dd40e40adc8ee064463"
                                               :fi   "Katselmus"
                                               :sv   "Syn"
                                               :en   "Review"
                                               :type "muu-katselmus"}
                                              {:id   "6a156dd40e40adc8ee064463"
                                               :fi   "Katselmus2"
                                               :sv   "Syn2"
                                               :en   "Review2"
                                               :type "paikan-merkitseminen"}]
                               :date-deltas  {:julkipano     {:delta 1 :unit "days"}
                                              :anto          {:delta 2 :unit "days"}
                                              :muutoksenhaku {:delta 3 :unit "days"}
                                              :lainvoimainen {:delta 4 :unit "days"}
                                              :aloitettava   {:delta 1 :unit "years"}
                                              :voimassa      {:delta 2 :unit "years"}}
                               :verdict-code ["pysytti-evattyna",
                                              "myonnetty",
                                              "konversio",
                                              "ilmoitus-tiedoksi"
                                              "ei-tutkittu-3"
                                              "annettu-lausunto"]
                               :boardname    " Lupapoppoo "}
              :template       {:giver      "viranhaltija"
                               :inclusions [:language :voimassa :appeal :julkipano :bulletin-op-description
                                            :purpose :verdict-text :anto :complexity :plans :aloitettava
                                            :valitus :foremen :verdict-code :collateral :conditions.condition
                                            :rights :plans-included :reviews :foremen-included :neighbors
                                            :lainvoimainen :reviews-included :statements :verdict-date
                                            :automatic-verdict-dates :handler :verdict-section
                                            :buildings.rakennetut-autopaikat :buildings.kiinteiston-autopaikat
                                            :buildings.autopaikat-yhteensa :buildings.vss-luokka
                                            :buildings.paloluokka]}})

(sc/validate PateVerdict verdict)

(facts "dict-value"
  (robot/dict-value {:verdict verdict} :rights) => "rakennusoikeus"
  (robot/dict-value {:verdict verdict} :neighbors) => nil
  (robot/dict-value {:verdict verdict} :handler) => "Kaino Käsittelijä"
  (robot/dict-value {:verdict verdict} :verdict-date) => "2017-11-23"
  (robot/dict-value {:verdict verdict} :automatic-verdict-dates) => true
  (robot/dict-value {:verdict verdict} :valitus) => (throws AssertionError))

(facts "->Paivamaarat"
  (robot/->Paivamaarat verdict) => {:paatosPvm        "2017-11-23"
                                    :voimassaPvm      "2023-11-23"
                                    :antoPvm          "2017-11-20"
                                    :julkipanoPvm     "2017-11-24"
                                    :aloitettavaPvm   "2022-11-23"
                                    :muutoksenhakuPvm "2017-12-27"
                                    :lainvoimainenPvm "2017-11-27"}
  (robot/->Paivamaarat (update verdict :data dissoc :voimassa :anto :aloitettava))
  => {:paatosPvm        "2017-11-23"
      :julkipanoPvm     "2017-11-24"
      :muutoksenhakuPvm "2017-12-27"
      :lainvoimainenPvm "2017-11-27"}
  (robot/->Paivamaarat (update verdict :data dissoc :voimassa :anto :aloitettava
                               :julkipano :muutoksenhaku :lainvoimainen))
  => {:paatosPvm        "2017-11-23"}
  (robot/->Paivamaarat (update verdict :data dissoc :verdict-date))
  => (throws Exception)
  (robot/->Paivamaarat (update verdict :data assoc :voimassa nil :anto "  "
                               :aloitettava "hello" :julkipano""))
  => {:paatosPvm        "2017-11-23"
      :muutoksenhakuPvm "2017-12-27"
      :lainvoimainenPvm "2017-11-27"})

(defn make-task [task-id taskname tasktype & {:keys [subtype source laji]}]
  (util/assoc-when {:id          task-id
                    :taskname    taskname
                    :schema-info (util/assoc-when {:name tasktype}
                                                  :subtype subtype)
                    :data        (cond-> {}
                                   laji (assoc-in [:katselmuksenLaji :value] laji))}
                   :source source))

(defn make-options [tasks]
  {:verdict     verdict
   :application {:tasks tasks}})

(let [verdict-source {:id   (:id verdict)
                      :type "verdict"}
      r1             (make-task "r1" "Review One" "task-katselmus"
                                :subtype "review" :source verdict-source
                                :laji "aloituskokous")
      r2             (make-task "r2" "Review Two" :task-katselmus
                                :subtype :review :source verdict-source
                                :laji "rakennekatselmus")
      tj1            (make-task "tj1" "Foreman One" "task-vaadittu-tyonjohtaja"
                                :subtype "foreman" :source verdict-source)
      p1             (make-task "p1" "Plan One" "task-lupamaarays"
                                :source verdict-source)]
  (facts "->Katselmukset"
    (robot/->Katselmukset (make-options [r1])) => [{:tunnus "r1"
                                                    :laji   "aloituskokous"
                                                    :nimi   "Review One"}]
    (robot/->Katselmukset (make-options [tj1 r1 p1])) => [{:tunnus "r1"
                                                           :laji   "aloituskokous"
                                                           :nimi   "Review One"}]
    (robot/->Katselmukset (make-options [r1 tj1 r2 p1]))
    => (just [{:tunnus "r1" :laji "aloituskokous" :nimi "Review One"}
              {:tunnus "r2" :laji "rakennekatselmus" :nimi "Review Two"}]
             :in-any-order)
    (robot/->Katselmukset (make-options [(make-task "bad" "Bad type" "task-foobar"
                                                    :subtype "review" :laji "pohjakatselmus"
                                                    :source verdict-source)])) => nil
    (robot/->Katselmukset (make-options [(make-task "bad" "Bad subtype" "task-katselmus"
                                                    :subtype "" :laji "pohjakatselmus"
                                                    :source verdict-source)])) => nil
    (robot/->Katselmukset (make-options [(make-task "bad" "Bad laji" "task-katselmus"
                                                    :subtype "review" :laji "bad"
                                                    :source verdict-source)]))
    => (throws Exception)
    (robot/->Katselmukset (make-options [(make-task "bad" "Bad source type" "task-katselmus"
                                                    :subtype "review" :laji "pohjakatselmus"
                                                    :source (assoc verdict-source :type "bad"))])) => nil
    (robot/->Katselmukset (make-options [(make-task "bad" "Bad source id" "task-katselmus"
                                                    :subtype "review" :laji "pohjakatselmus"
                                                    :source (assoc verdict-source :id "bad"))])) => nil
    (robot/->Katselmukset (make-options [(make-task "good" "All good" "task-katselmus"
                                                    :subtype "review" :laji "pohjakatselmus"
                                                    :source verdict-source)]))
    => [{:nimi   "All good"
         :tunnus "good"
         :laji   "pohjakatselmus"}]))

(facts "->HankkeenVaativuus"
  (robot/->HankkeenVaativuus {:verdict verdict}) => {:vaativuus "vähäinen"
                                                     :selite    "hankkeen vaativuus"}
  (robot/->HankkeenVaativuus {:verdict (assoc-in verdict [:data :complexity-text] "  ")})
  => {:vaativuus "vähäinen"}
  (robot/->HankkeenVaativuus {:verdict (assoc-in verdict [:data :complexity-text] nil)})
  => {:vaativuus "vähäinen"}
  (robot/->HankkeenVaativuus {:verdict (assoc-in verdict [:data :complexity] nil)})
  => {:selite "hankkeen vaativuus"}
  (robot/->HankkeenVaativuus {:verdict (assoc-in verdict [:data :complexity] "bad")})
  => {:selite "hankkeen vaativuus"}
  (robot/->HankkeenVaativuus {:verdict (update verdict :data dissoc :complexity :complexity-text)})
  => nil)

(defn make-collateral-options [flag amount type date]
  {:verdict (update verdict :data assoc
                    :collateral-flag flag
                    :collateral amount
                    :collateral-type type
                    :collateral-date date)})

(facts "->Vakuus"
  (robot/->Vakuus (make-collateral-options true " 12345 " "  Shekki  " (timestamp "20.1.2019")))
  => {:pvm   "2019-01-20"
      :summa "12345"
      :laji  "Shekki"}
  (robot/->Vakuus (make-collateral-options false "12345" "Shekki" (timestamp "20.1.2019")))
  => nil
  (robot/->Vakuus (make-collateral-options nil "12345" "Shekki" (timestamp "20.1.2019")))
  => nil
  (robot/->Vakuus (make-collateral-options true "  " "Shekki" (timestamp "20.1.2019")))
  => {:pvm  "2019-01-20"
      :laji "Shekki"}
  (robot/->Vakuus (make-collateral-options true "" "Shekki" (timestamp "20.1.2019")))
  => {:pvm  "2019-01-20"
      :laji "Shekki"}
  (robot/->Vakuus (make-collateral-options true nil "Shekki" (timestamp "20.1.2019")))
  => {:pvm  "2019-01-20"
      :laji "Shekki"}
  (robot/->Vakuus (make-collateral-options true "12345" " " (timestamp "20.1.2019")))
  => {:pvm   "2019-01-20"
      :summa "12345"}
  (robot/->Vakuus (make-collateral-options true "12345" "" (timestamp "20.1.2019")))
  => {:pvm   "2019-01-20"
      :summa "12345"}
  (robot/->Vakuus (make-collateral-options true "12345" nil (timestamp "20.1.2019")))
  => {:pvm   "2019-01-20"
      :summa "12345"}
  (robot/->Vakuus (make-collateral-options true "12345" "Shekki" "bad"))
  => {:summa "12345"
      :laji  "Shekki"}
  (robot/->Vakuus (make-collateral-options true "12345" "Shekki" nil))
  => {:summa "12345"
      :laji  "Shekki"}
  (robot/->Vakuus (make-collateral-options true "" "" "")) => nil
  (robot/->Vakuus (make-collateral-options true "" "" "")) => nil)

(let [app          {:documents           [{:schema-info {:op {:id   "op2"
                                                              :name "purkaminen"}}}
                                          {:schema-info {:op {:id   "op1"
                                                              :name "pientalo"}}
                                           :data        {:foo "bar"}}]
                    :primaryOperation    {:id "op1"}
                    :secondaryOperations [{:id "op2" :created 1}]}
      ;; Buildings after the verdict enrichment
      build1       {:kiinteiston-autopaikat " 1 "
                    :autopaikat-yhteensa    " 3 "
                    :tag                    " Foo "
                    :description            "  Foobar  "
                    :paloluokka             "  P1  "
                    :vss-luokka             "  VSS1   "
                    :show-building          true}
      build2       {:kiinteiston-autopaikat "2"
                    :autopaikat-yhteensa    "4"
                    :description            "Burn!"
                    :paloluokka             "P2"
                    :rakennetut-autopaikat  "8"
                    :vss-luokka             "VSS2"
                    :building-id            "123456001M"
                    :show-building          true}
      make-options (fn [buildings-map]
                     {:verdict     {:category :r
                                    :data     {:buildings buildings-map}}
                      :application app})]
  (facts "->Rakennukset"
    (robot/->Rakennukset (make-options {:op1 build1 :op2 build2}))
    => (just [{:tunniste              "Foo"
               :selite                "Foobar"
               :paloluokka            "P1"
               :vssLuokka             "VSS1"
               :kiinteistonAutopaikat "1"
               :autopaikatYhteensa    "3"}
              {:tunnus                "123456001M"
               :selite                "Burn!"
               :paloluokka            "P2"
               :vssLuokka             "VSS2"
               :rakennetutAutopaikat  "8"
               :kiinteistonAutopaikat "2"
               :autopaikatYhteensa    "4"}])
    (robot/->Rakennukset (make-options {:op1 (dissoc build1 :show-building)
                                        :op2 build2}))
    => (just [{:tunnus                "123456001M"
               :selite                "Burn!"
               :paloluokka            "P2"
               :vssLuokka             "VSS2"
               :rakennetutAutopaikat  "8"
               :kiinteistonAutopaikat "2"
               :autopaikatYhteensa    "4"}])
    (robot/->Rakennukset (make-options {:op1 (update build1 :show-building not)
                                        :op2 build2}))
    => (just [{:tunnus                "123456001M"
               :selite                "Burn!"
               :paloluokka            "P2"
               :vssLuokka             "VSS2"
               :rakennetutAutopaikat  "8"
               :kiinteistonAutopaikat "2"
               :autopaikatYhteensa    "4"}])
    (robot/->Rakennukset (make-options {:op1 (update build1 :show-building not)}))
    => nil
    (robot/->Rakennukset (make-options {:op-bad build1}))
    => nil
    (robot/->Rakennukset (make-options {:op1 {:kiinteiston-autopaikat "  "
                                              :show-building true
                                              :paloluokka ""
                                              :description "   "}}))
    => nil
    (robot/->Rakennukset (make-options nil)) => nil
    (robot/->Rakennukset (make-options {})) => nil))

(facts "->Toimija"
  (robot/->Toimija  verdict :giver-title :giver)
  => {:nimike "Lupaguru" :nimi "Pate Päättäjä"}
  (robot/->Toimija (util/dissoc-in verdict [:data :giver-title]) :giver-title :giver)
  => {:nimi "Pate Päättäjä"}
  (robot/->Toimija (assoc-in verdict [:data :giver-title] "  ") :giver-title :giver)
  => {:nimi "Pate Päättäjä"}
  (robot/->Toimija (util/dissoc-in verdict [:data :giver]) :giver-title :giver)
  => {:nimike "Lupaguru"}
  (robot/->Toimija (assoc-in verdict [:data :giver] "  ") :giver-title :giver)
  => {:nimike "Lupaguru"}
  (robot/->Toimija (dissoc verdict :data) :giver-title :giver) => nil
  (robot/->Toimija (update verdict :data merge {:giver "  " :giver-title "   "})
                   :giver-title :giver) => nil)

(facts "verdict-giver"
  (robot/verdict-giver verdict) => {:nimi "Pate Päättäjä" :nimike "Lupaguru"}
  (robot/verdict-giver (assoc-in verdict [:template :giver] "lautakunta"))
  => {:nimi "Lupapoppoo"}
  (robot/verdict-giver (-> verdict
                           (assoc-in [:template :giver] "lautakunta")
                           (assoc-in [:references :boardname] "")))
  => nil
  (robot/verdict-giver (-> verdict
                           (assoc-in [:template :giver] "lautakunta")
                           (util/dissoc-in [:references :boardname])))
  => nil)

(facts "->Paatos"
  (robot/->Paatos {:verdict verdict :application {}})
  => {:tunnus                      "1a156dd40e40adc8ee064463"
      :hankkeenVaativuus           {:selite    "hankkeen vaativuus"
                                    :vaativuus "vähäinen"}
      :kaavanKayttotarkoitus       "käyttötarkoitus"
      :kieli                       "fi"
      :muutLupaehdot               ["muut lupaehdot - teksti" "toinen teksti"]
      :paatoksentekija             {:nimike "Lupaguru" :nimi "Pate Päättäjä"}
      :kasittelija                 {:nimike "Lupaduunari" :nimi "Kaino Käsittelijä"}
      :paatosteksti                "päätös - teksti"
      :paatostieto                 "myönnetty"
      :paatostyyppi                "viranhaltija"
      :paivamaarat                 {:aloitettavaPvm   "2022-11-23"
                                    :antoPvm          "2017-11-20"
                                    :julkipanoPvm     "2017-11-24"
                                    :lainvoimainenPvm "2017-11-27"
                                    :muutoksenhakuPvm "2017-12-27"
                                    :paatosPvm        "2017-11-23"
                                    :voimassaPvm      "2023-11-23"}
      :pykala                      "99"
      :rakennusoikeus              "rakennusoikeus"
      :toimenpidetekstiJulkipanoon "julkipano - teksti"
      :vaaditutErityissuunnitelmat ["Suunnitelmat" "Suunnitelmat2"]
      :vaaditutTyonjohtajat        ["erityisalojen työnjohtaja" "IV-työnjohtaja"
                                    "vastaava työnjohtaja" "KVV-työnjohtaja"]}
  (robot/->Paatos {:verdict     (-> verdict
                                    (update :data assoc
                                            :plans-included false
                                            :foremen-included false
                                            :neighbors "Howdy!"
                                            :bulletin-op-description "  "
                                            :operation "Operational"
                                            :rationale "Behold!"
                                            :legalese "Law"
                                            :conditions {}
                                            :extra-info "  Bonus "
                                            :deviations " Shortcuts "
                                            :purpose "Show me the money!"
                                            :address "Dawang Lu")
                                    (update :data dissoc :verdict-text
                                            :handler :handler-title :giver-title)
                                    (assoc-in [:replacement :replaces] "oldie"))
                   :application {}})
  => {:tunnus                      "1a156dd40e40adc8ee064463"
      :korvaaPaatoksen             "oldie"
      :hankkeenVaativuus           {:selite    "hankkeen vaativuus"
                                    :vaativuus "vähäinen"}
      :kaavanKayttotarkoitus       "Show me the money!"
      :kieli                       "fi"
      :lisaselvitykset             "Bonus"
      :naapurienKuuleminen         "Howdy!"
      :osoite                      "Dawang Lu"
      :paatoksentekija             {:nimi "Pate Päättäjä"}
      :paatostieto                 "myönnetty"
      :paatostyyppi                "viranhaltija"
      :paivamaarat                 {:aloitettavaPvm   "2022-11-23"
                                    :antoPvm          "2017-11-20"
                                    :julkipanoPvm     "2017-11-24"
                                    :lainvoimainenPvm "2017-11-27"
                                    :muutoksenhakuPvm "2017-12-27"
                                    :paatosPvm        "2017-11-23"
                                    :voimassaPvm      "2023-11-23"}
      :perustelut                  "Behold!"
      :poikkeamiset                "Shortcuts"
      :pykala                      "99"
      :rakennushanke               "Operational"
      :rakennusoikeus              "rakennusoikeus"
      :sovelletutOikeusohjeet      "Law"
      :toimenpidetekstiJulkipanoon "Operational"})

(facts "->Naapuri"
  (robot/->Naapuri {:propertyId "75341600380014"
                    :status [{:created (timestamp "1.2.2020")
                              :state "open"}
                             {:created (timestamp "3.2.2020")
                              :state "mark-done"}]})
  => {:kiinteistotunnus "75341600380014"
      :pvm "2020-02-01"
      :kuultu true}
  (robot/->Naapuri {:propertyId "75341600380014"
                    :status [{:created (timestamp "1.2.2020")
                              :state "open"}]})
  => {:kiinteistotunnus "75341600380014"
      :pvm "2020-02-01"
      :kuultu false})

(facts "->Lausunto"
  (robot/->Lausunto {:given  (timestamp "1.1.2020")
                     :person {:name "  Stan States  "
                              :text " Squirrel  "}
                     :status "puollettu"})
  => {:lausunnonantaja "Squirrel Stan States"
      :pvm             "2020-01-01"
      :lausuntotieto   "puollettu"}
  (robot/->Lausunto {:given  (timestamp "1.1.2020")
                     :person {:name "  Stan States  "}
                     :status "puollettu"})
  => {:lausunnonantaja "Stan States"
      :pvm             "2020-01-01"
      :lausuntotieto   "puollettu"}
  (robot/->Lausunto {:person {:name "  Stan States  "}
                     :status "puollettu"})
  => nil)

(facts "->PaatosSanoma"
  (robot/->PaatosSanoma {:command     {:organization (delay {:id   "ORG-ID"
                                                             :tags [{:id "t1" :label " One "}
                                                                    {:id "t2" :label "  Two  "}
                                                                    {:id "t3" :label "   Three   "}]})}
                         :application {:id          "LP-753-2020-90002"
                                       :propertyId  "75341600550007"
                                       :address     "  Dongzhimen  "
                                       :tosFunction "10 03 00 03"
                                       :tags        ["t1" "t3"]
                                       :neighbors   [{:propertyId "75341600380014"
                                                      :status     [{:created (timestamp "1.2.2020")
                                                                    :state   "open"}
                                                                   {:created (timestamp "3.2.2020")
                                                                    :state   "mark-done"}]}]
                                       :statements  [{:given  (timestamp "1.1.2020")
                                                      :person {:name "  Stan States  "}
                                                      :status "puollettu"}
                                                     {}]}
                         :verdict     verdict})
  => (just {:versio           2
            :asiointitunnus   "LP-753-2020-90002"
            :kiinteistotunnus "75341600550007"
            :osoite           "Dongzhimen"
            :paatos           truthy
            :naapurit         [{:kiinteistotunnus "75341600380014"
                                :pvm              "2020-02-01"
                                :kuultu           true}]
            :lausunnot        [{:lausunnonantaja "Stan States"
                                :pvm             "2020-01-01"
                                :lausuntotieto   "puollettu"}]
            :menettely        "Maisematyölupamenettely"
            :avainsanat       (just ["One" "Three"] :in-any-order)})
  (provided (lupapalvelu.tiedonohjaus/tos-function-with-name "10 03 00 03" "ORG-ID")
            => {:code "10 03 00 03", :name "Maisematyölupamenettely"})

  (robot/->PaatosSanoma {:command     {:organization (delay {})}
                         :application {:id          "LP-753-2020-90002"
                                       :propertyId  "75341600550007"
                                       :address     "  Dongzhimen  "
                                       :tosFunction "  "
                                       :tags        []
                                       :neighbors   []
                                       :statements  [{}]}
                         :verdict     verdict})
  => (just {:versio           2
            :asiointitunnus   "LP-753-2020-90002"
            :kiinteistotunnus "75341600550007"
            :osoite           "Dongzhimen"
            :paatos           truthy}))

(facts "->PoistoSanoma"
  (robot/->PoistoSanoma {:id         "LP-753-2020-90002"
                         :propertyId "75341600550007"
                         :address    "  Dongzhimen  "}
                        "verdict-id")
  => {:versio           2
      :asiointitunnus   "LP-753-2020-90002"
      :kiinteistotunnus "75341600550007"
      :osoite           "Dongzhimen"
      :poistettuPaatos  "verdict-id"})

(facts "Sanoma"
  (sc/check Sanoma
            (assoc (robot/->PaatosSanoma {:command     {:organization (delay {})}
                                          :application {:id          "LP-753-2020-90002"
                                                        :propertyId  "75341600550007"
                                                        :address     "  Dongzhimen  "
                                                        :tosFunction "  "
                                                        :tags        []
                                                        :neighbors   []
                                                        :statements  [{}]}
                                          :verdict     verdict})
                   :sanomatunnus "hello")) => nil
  (sc/check Sanoma
            (assoc (robot/->PoistoSanoma {:id         "LP-753-2020-90002"
                                    :propertyId "75341600550007"
                                    :address    "  Dongzhimen  "}
                                         "verdict-id")
                   :sanomatunnus "world")) => nil)

(testable-privates lupapalvelu.verdict-robot.core pate-robot?)


(let [command {:application  {:municipality "178"
                              :permitType   "R"}
               :organization (delay {:scope [{:permitType   "R"
                                              :municipality "178"
                                              :pate         {:robot true}}
                                             {:permitType   "P"
                                              :municipality "178"
                                              :pate         {:robot false}}]})}]
  (facts "pate-robot"
    (pate-robot? command) => true
    (pate-robot? (assoc-in command [:application :municipality] "321")) => false
    (pate-robot? (assoc-in command [:application :permitType] "YA")) => false
    (pate-robot? (assoc-in command [:application :permitType] "P")) => false)
  (facts "robot-integration"
    (robot/robot-integration? {:command command
                               :verdict {:category "r"}}) => true
    (robot/robot-integration? {:command command
                               :verdict {:category "p"}}) => false
    (robot/robot-integration? {:command command
                               :verdict {:category "r"
                                         :legacy?  true}}) => false
    (robot/robot-integration? {:command command
                               :verdict {:category "r"}}) => false
    (provided (#'lupapalvelu.verdict-robot.core/pate-robot? anything) => false)))
