(ns lupapalvelu.pate.verdict-canonical-test
  (:require [lupapalvelu.pate.schemas :refer [PateVerdict]]
            [lupapalvelu.pate.verdict-canonical :refer :all]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.verdict :refer [Verdict]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.date :as date]
            [sade.util :as util]
            [schema.core :as sc]))

(def verdict {:id             "1a156dd40e40adc8ee064463"
              :schema-version 1
              :category       "r"
              :state          {:_user     "test"
                               :_modified 112233
                               :_value    "draft"}
              :modified       123456
              :data           {:language                "fi"
                               :voimassa                (date/timestamp "23.11.2023")
                               :appeal                  "muutoksenhakuohje - teksti"
                               :julkipano               (date/timestamp "24.11.2017")
                               :bulletin-op-description "julkipano - teksti"
                               :purpose                 "käyttötarkoitus"
                               :verdict-text            "päätös - teksti"
                               :anto                    (date/timestamp "20.11.2017")
                               :complexity              "small"
                               :plans                   ["5a156ddf0e40adc8ee064464"
                                                         "6a156ddf0e40adc8ee064464"]
                               :aloitettava             (date/timestamp "23.11.2022")
                               :muutoksenhaku           (date/timestamp "27.12.2017")
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
                               :neighbors               ""
                               :lainvoimainen           (date/timestamp "27.11.2017")
                               :reviews-included        true
                               :statements              ""
                               :verdict-date            (date/timestamp "23.11.2017")
                               :automatic-verdict-dates true
                               :handler                 "Pate Paattaja"
                               :verdict-section         "99"
                               :buildings               {"5a1561250e40adc8ee064449" {:rakennetut-autopaikat        "1"
                                                                                     :kiinteiston-autopaikat       "2"
                                                                                     :autopaikat-yhteensa          "3"
                                                                                     :vss-luokka                   "4"
                                                                                     :paloluokka                   "5"
                                                                                     :kokoontumistilanHenkilomaara "6"}}
                               :complexity-text         "hankkeen vaativuus"}
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
                                              "annettu-lausunto"]}
              :template       {:giver      "viranhaltija"
                               :inclusions [:language :voimassa :appeal :julkipano :bulletin-op-description
                                            :purpose :verdict-text :anto :complexity :plans :aloitettava
                                            :muutoksenhaku :foremen :verdict-code :collateral :conditions.condition
                                            :rights :plans-included :reviews :foremen-included :neighbors
                                            :lainvoimainen :reviews-included :statements :verdict-date
                                            :automatic-verdict-dates :handler :verdict-section
                                            :buildings.rakennetut-autopaikat :buildings.kiinteiston-autopaikat
                                            :buildings.autopaikat-yhteensa :buildings.vss-luokka
                                            :buildings.paloluokka :buildings.kokoontumistilanHenkilomaara]}})

(sc/validate PateVerdict verdict)

(def p-verdict {:id             "5a8adacba067cd387ff9c00c"
                :schema-version 1
                :category       "p"
                :state          {:_user     "test"
                                 :_modified 112233
                                 :_value    "draft"}
                :modified       1519224505171
                :data           {:voimassa              (date/timestamp "25.2.2020")
                                 :appeal                ""
                                 :julkipano             (date/timestamp "22.2.2018")
                                 :bulletinOpDescription "Hanke on todella vaativa"
                                 :verdict-date          (date/timestamp "21.2.2018")
                                 :purpose               ""
                                 :verdict-section       "9"
                                 :verdict-text          "Annettu"
                                 :muutoksenhaku         (date/timestamp "24.2.2018")
                                 :anto                  (date/timestamp "23.2.2018")
                                 :complexity            "medium"
                                 :attachments           [{:type-group :paatoksenteko :type-id :paatos :amount 6}
                                                         {:type-group :paatoksenteko :type-id :paatosote :amount 1}]
                                 :plans                 []
                                 :foremen               ["erityis-tj"]
                                 :verdict-code          "annettu-lausunto"
                                 :extra-info            ""
                                 :collateral            ""
                                 :conditions            {}
                                 :rights                ""
                                 :plans-included        false
                                 :language              "fi"
                                 :reviews               []
                                 :foremen-included      false
                                 :deviations            ""
                                 :neighbors             ""
                                 :handler               "Pete Paattaja"
                                 :reviews-included      false}
                :references     {:verdict-code ["annettu-lausunto"]
                                 :foremen      ["erityis-tj"]
                                 :date-deltas  {:julkipano     {:delta 1 :unit "days"}
                                                :anto          {:delta 2 :unit "days"}
                                                :muutoksenhaku {:delta 3 :unit "days"}
                                                :lainvoimainen {:delta 4 :unit "days"}
                                                :aloitettava   {:delta 1 :unit "years"}
                                                :voimassa      {:delta 2 :unit "years"}}
                                 :plans        []
                                 :reviews      []}
                :template       {:giver      "viranhaltija"
                                 :inclusions [:statements
                                              :upload
                                              :aloitettava
                                              :lainvoimainen
                                              :verdict-section
                                              :buildings]}})

(sc/validate PateVerdict p-verdict)

(def legacy-verdict
  {:category  "r"
   :data      {:anto                    (date/timestamp "20.11.2017")
               :attachments             [{:amount     1
                                          :type-group "paatoksenteko"
                                          :type-id    "paatosote"}]
               :bulletin-op-description "Hello world!"
               :conditions              {:63738886f3ad115b1ff3c7a5 {:name "Lippu salkoon"}}
               :foremen                 {:6373887bf3ad115b1ff3c7a3 {:role "erityisalojen työnjohtaja"}
                                         :6373887ff3ad115b1ff3c7a4 {:role "vastaava työnjohtaja"}}
               :handler                 "Annie Authority"
               :julkipano               (date/timestamp "24.11.2017")
               :kuntalupatunnus         "omatunnus"
               :lainvoimainen           (date/timestamp "27.11.2017")
               :reviews                 {:6373883ef3ad115b1ff3c7a0 {:name "Aloituskokous"
                                                                    :type "aloituskokous"}
                                         :63738851f3ad115b1ff3c7a1 {:name "Loppukatselmus"
                                                                    :type "loppukatselmus"}
                                         :63738861f3ad115b1ff3c7a2 {:name "Onpahan vaan"
                                                                    :type "muu-katselmus"}}
               :verdict-code            "22"
               :verdict-section         "20"
               :verdict-text            "Lorem ipsum et al."}
   :id        "6373880af3ad115b1ff3c79f"
   :legacy?   true
   :modified  1668516008445
   :published {:attachment-id "637388aaf3ad115b1ff3c7ae"
               :published     1668516008445
               :tags          "<div>verdict as html</div>"}
   :state     {:_modified 1668516008445
               :_user     "rakennustarkastaja@jarvenpaa.fi"
               :_value    "published"}
   :template  {:inclusions
               ["foreman-label" "conditions-title" "julkipano"
                "foremen-title" "bulletin-op-description"
                "kuntalupatunnus" "verdict-section" "verdict-text"
                "anto" "attachments" "foremen.role" "foremen.remove"
                "verdict-code" "conditions.name" "conditions.remove"
                "reviews-title" "type-label" "reviews.name"
                "reviews.type" "reviews.remove" "add-review"
                "name-label" "condition-label" "lainvoimainen"
                "handler" "add-foreman" "upload" "add-condition"]}})

(sc/validate PateVerdict legacy-verdict)

(def backing-system-verdict
  {:id              "637262a05d2b4812838a1125"
   :kuntalupatunnus "13-0185-R"
   :paatokset       [{:id             "637262a05d2b4812838a1127"
                      :lupamaaraykset {:autopaikkojaEnintaan      10
                                       :autopaikkojaKiinteistolla 7
                                       :autopaikkojaRakennettava  2
                                       :autopaikkojaRakennettu    0
                                       :autopaikkojaUlkopuolella  3
                                       :autopaikkojaVahintaan     1
                                       :kerrosala                 "100"
                                       :kokonaisala               "110"
                                       :maaraykset                [{:maaraysaika   1377637200000
                                                                    :sisalto       "Radontekninen suunnitelma"
                                                                    :toteutusHetki 1377723600000}
                                                                   {:maaraysaika   1377810000000
                                                                    :sisalto       "Ilmanvaihtosuunnitelma"
                                                                    :toteutusHetki 1377896400000}]
                                       :vaaditutKatselmukset      [{:katselmuksenLaji                "aloituskokous"
                                                                    :tarkastuksenTaiKatselmuksenNimi "Aloituskokous"}
                                                                   {:katselmuksenLaji                "muu tarkastus"
                                                                    :tarkastuksenTaiKatselmuksenNimi "Käyttöönottotarkastus"}]
                                       :vaaditutTyonjohtajat
                                       "Vastaava työnjohtaja, Vastaava IV-työnjohtaja, Työnjohtaja"}
                      :paivamaarat    {:aloitettava      (date/timestamp "23.11.2022")
                                       :anto             (date/timestamp "20.11.2017")
                                       :julkipano        (date/timestamp "24.11.2017")
                                       :lainvoimainen    (date/timestamp "27.11.2017")
                                       :raukeamis        (date/timestamp "23.11.2024")
                                       :viimeinenValitus (date/timestamp "27.12.2017")
                                       :voimassaHetki    (date/timestamp "23.11.2023")}
                      :poytakirjat    [{:paatoksentekija "Mölli Keinonen"
                                        :paatos          "Mitä muuta on tekeillä?"
                                        :paatoskoodi     "ehdollinen"
                                        :paatospvm       (date/timestamp "23.11.2017")
                                        :pykala          "2"
                                        :status          "6"
                                        :urlHash         "b55ae9c30533428bd9965a84106fb163611c1a7d"}]}]
   :timestamp 12345})

(sc/validate Verdict backing-system-verdict)


(testable-privates lupapalvelu.pate.verdict-canonical
                   vaadittu-katselmus-canonical
                   maarays-seq-canonical
                   vaadittu-erityissuunnitelma-canonical
                   vaadittu-tyonjohtaja-canonical
                   lupamaaraykset-type-canonical
                   paivamaarat-type-canonical
                   paatoksentekija
                   paatospoytakirja-type-canonical
                   verdict-reviews
                   status->kuntagml
                   verdict-code)

(facts vaadittu-katselmus-canonical
  (fact "muu-katselmus / Finnish"
    (vaadittu-katselmus-canonical "fi"
                                  {:id      "5a156dd40e40adc8ee064463"
                                   :fi      "Katselmus"
                                   :sv      "Syn"
                                   :en      "Review"
                                   :type    "muu katselmus"
                                   :task-id "5d1aeebc2079b50ac3fc582c"})
    => {:Katselmus {:katselmuksenLaji "muu katselmus", :tarkastuksenTaiKatselmuksenNimi "Katselmus",
                    :muuTunnustieto   [{:MuuTunnus {:tunnus "5d1aeebc2079b50ac3fc582c" :sovellus "Lupapiste"}}]}})

  (fact "muu-katselmus / English"
    (vaadittu-katselmus-canonical "en"
                                  {:id      "5a156dd40e40adc8ee064463"
                                   :fi      "Katselmus"
                                   :sv      "Syn"
                                   :en      "Review"
                                   :type    "muu katselmus"
                                   :task-id "5d1aeebc2079b50ac3fc582c"})
    => {:Katselmus {:katselmuksenLaji "muu katselmus", :tarkastuksenTaiKatselmuksenNimi "Review",
                    :muuTunnustieto   [{:MuuTunnus {:tunnus "5d1aeebc2079b50ac3fc582c" :sovellus "Lupapiste"}}]}})

  (fact "paikan-merkisteminen / Swedish"
    (vaadittu-katselmus-canonical "sv"
                                  {:id      "6a156dd40e40adc8ee064463"
                                   :fi      "Katselmus2"
                                   :sv      "Syn2"
                                   :en      "Review2"
                                   :type    "rakennuksen paikan merkitseminen"
                                   :task-id "5d1aeebc2079b50ac3fc582c"})
    => {:Katselmus {:katselmuksenLaji "rakennuksen paikan merkitseminen", :tarkastuksenTaiKatselmuksenNimi "Syn2",
                    :muuTunnustieto   [{:MuuTunnus {:tunnus "5d1aeebc2079b50ac3fc582c" :sovellus "Lupapiste"}}]}}))

(facts maarays-seq-canonical
  (fact "Two items"
    (maarays-seq-canonical verdict)
    => (just [{:Maarays {:sisalto "muut lupaehdot - teksti"}}
              {:Maarays {:sisalto "toinen teksti"}}] :in-any-order))
  (fact "without text nil"
    (maarays-seq-canonical (assoc-in verdict [:data :conditions] {:foo {:condition ""}})) => nil
    (maarays-seq-canonical (assoc-in verdict [:data :conditions] {:foo {:condition "    "}})) => nil
    (maarays-seq-canonical (assoc-in verdict [:data :conditions] {:foo {}})) => nil
    (maarays-seq-canonical (assoc-in verdict [:data :conditions] {:foo nil})) => nil
    (maarays-seq-canonical (assoc-in verdict [:data :conditions] {})) => nil
    (maarays-seq-canonical (util/dissoc-in verdict [:data :conditions])) => nil))

(facts vaadittu-erityissuunnitelma-canonical
  (fact "suunnitelmat / Finnish"
    (vaadittu-erityissuunnitelma-canonical "fi"
                                           {:id "5a156ddf0e40adc8ee064464"
                                            :fi "Suunnitelmat"
                                            :sv "Planer"
                                            :en "Plans"})
    => {:VaadittuErityissuunnitelma {:vaadittuErityissuunnitelma "Suunnitelmat", :toteutumisPvm nil}}))

(fact vaadittu-tyonjohtaja-canonical
  (fact "Pate"
    (vaadittu-tyonjohtaja-canonical {:category "r"} "KVV-työnjohtaja")
    => {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "KVV-työnjohtaja"}}
    (vaadittu-tyonjohtaja-canonical {:category "r"} "What?")
    => {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "What?"}}
    (vaadittu-tyonjohtaja-canonical {:category "r"} "  ") => nil)
  (fact "Backing system"
    (vaadittu-tyonjohtaja-canonical {} "KVV-työnjohtaja")
    => {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "KVV-työnjohtaja"}}
    (vaadittu-tyonjohtaja-canonical {} " EriTyISaloJEn TyÖnJoHTaJa ")
    => {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "erityisalojen työnjohtaja"}}
    (vaadittu-tyonjohtaja-canonical {} "EI TIEDOSSA")
    => {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "ei tiedossa"}}
    (vaadittu-tyonjohtaja-canonical {} "What?")
    => {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "ei tiedossa"}}
    (vaadittu-tyonjohtaja-canonical {} " ") => nil
    (vaadittu-tyonjohtaja-canonical {} "Vesi- ja viemärityönjohtaja")
    => {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "KVV-työnjohtaja"}}))

(facts lupamaaraykset-type-canonical
  (let [canonical (lupamaaraykset-type-canonical "fi" verdict {})]

    (fact "fields"
      (keys canonical) => (just #{:autopaikkojaEnintaan
                                  :autopaikkojaVahintaan
                                  :autopaikkojaRakennettava
                                  :autopaikkojaRakennettu
                                  :kokoontumistilanHenkilomaara
                                  :autopaikkojaKiinteistolla
                                  :autopaikkojaUlkopuolella
                                  :kerrosala
                                  :kokonaisala
                                  :rakennusoikeudellinenKerrosala
                                  :vaaditutKatselmukset
                                  :maaraystieto
                                  :vaadittuErityissuunnitelmatieto
                                  :vaadittuTyonjohtajatieto}))

    (fact "not nil fields"
      (keys (filter val canonical)) => (just #{:autopaikkojaRakennettava
                                               :autopaikkojaRakennettu
                                               :autopaikkojaKiinteistolla
                                               :kokoontumistilanHenkilomaara
                                               :vaaditutKatselmukset
                                               :maaraystieto
                                               :vaadittuErityissuunnitelmatieto
                                               :vaadittuTyonjohtajatieto}))

    (fact "autopaikkojarakennettava"
      (:autopaikkojaRakennettava canonical) => 3)

    (fact "autopaikkojarakennettu"
      (:autopaikkojaRakennettu canonical) => 1)

    (fact "autopaikkojakiinteistolla"
      (:autopaikkojaKiinteistolla canonical) => 2)

    (fact "two reviews"
      (map (comp :katselmuksenLaji :Katselmus) (:vaaditutKatselmukset canonical))
      =>  ["muu katselmus" "rakennuksen paikan merkitseminen"])

    (fact "two plans"
      (map (comp :vaadittuErityissuunnitelma :VaadittuErityissuunnitelma) (:vaadittuErityissuunnitelmatieto canonical))
      => ["Suunnitelmat" "Suunnitelmat2"])

    (fact "four foremen"
      (map (comp :tyonjohtajaRooliKoodi :VaadittuTyonjohtaja) (:vaadittuTyonjohtajatieto canonical))
      => ["erityisalojen työnjohtaja" "IV-työnjohtaja" "vastaava työnjohtaja" "KVV-työnjohtaja"]))
  (facts "No reviews, plans or foremen included"
    (let [canonical (lupamaaraykset-type-canonical "fi"
                                                   (update verdict :data dissoc
                                                           :reviews-included :plans-included
                                                           :foremen-included)
                                                   {})]
      (fact "fields"
      (keys canonical) => (just #{:autopaikkojaEnintaan
                                  :autopaikkojaVahintaan
                                  :autopaikkojaRakennettava
                                  :autopaikkojaRakennettu
                                  :autopaikkojaKiinteistolla
                                  :autopaikkojaUlkopuolella
                                  :kerrosala
                                  :kokonaisala
                                  :rakennusoikeudellinenKerrosala
                                  :vaaditutKatselmukset
                                  :maaraystieto
                                  :vaadittuErityissuunnitelmatieto
                                  :kokoontumistilanHenkilomaara
                                  :vaadittuTyonjohtajatieto}))

    (fact "not nil fields"
      (keys (filter val canonical)) => (just #{:autopaikkojaRakennettava
                                               :autopaikkojaRakennettu
                                               :autopaikkojaKiinteistolla
                                               :kokoontumistilanHenkilomaara
                                               :maaraystieto})))))

(facts paivamaarat-type-canonical
  (fact "all dates defined"
    (paivamaarat-type-canonical verdict)
    => {:aloitettavaPvm      (date/xml-date "2022-11-23")
        :lainvoimainenPvm    (date/xml-date "2017-11-27")
        :voimassaHetkiPvm    (date/xml-date "2023-11-23")
        :raukeamisPvm        nil
        :antoPvm             (date/xml-date "2017-11-20")
        :viimeinenValitusPvm (date/xml-date "2017-12-27")
        :julkipanoPvm        (date/xml-date "2017-11-24")})

  (fact "undefined fields defaults to nil"
    (paivamaarat-type-canonical (update verdict :data dissoc :aloitettava :lainvoimainen :voimassa))
    => {:aloitettavaPvm      nil
        :lainvoimainenPvm    nil
        :voimassaHetkiPvm    nil
        :raukeamisPvm        nil
        :antoPvm             (date/xml-date "2017-11-20")
        :viimeinenValitusPvm (date/xml-date "2017-12-27")
        :julkipanoPvm        (date/xml-date "2017-11-24")}))

(fact paatospoytakirja-type-canonical
  (paatospoytakirja-type-canonical verdict)
  => {:paatos          "päätös - teksti"
      :paatoskoodi     "myönnetty"
      :paatoksentekija "Pate Paattaja"
      :paatospvm       (date/xml-date "2017-11-23")
      :pykala          "99"}
  (paatospoytakirja-type-canonical (util/dissoc-in verdict [:data :verdict-text]))
  => {:paatos          nil
      :paatoskoodi     "myönnetty"
      :paatoksentekija "Pate Paattaja"
      :paatospvm       (date/xml-date "2017-11-23")
      :pykala          "99"})

(facts "Canonical for P verdict"

  (facts paivamaarat-type-canonical
    (fact "all dates defined"
      (paivamaarat-type-canonical p-verdict)
      => {:aloitettavaPvm      nil
          :lainvoimainenPvm    nil
          :voimassaHetkiPvm    (date/xml-date "2020-02-25")
          :raukeamisPvm        nil
          :antoPvm             (date/xml-date "2018-02-23")
          :viimeinenValitusPvm (date/xml-date "2018-02-24")
          :julkipanoPvm        (date/xml-date "2018-02-22")})
    (fact "undefined fields defaults to nil"
      (paivamaarat-type-canonical (update p-verdict :data dissoc :lainvoimainen :voimassa))
      => {:aloitettavaPvm      nil
          :lainvoimainenPvm    nil
          :voimassaHetkiPvm    nil
          :raukeamisPvm        nil
          :antoPvm             (date/xml-date "2018-02-23")
          :viimeinenValitusPvm (date/xml-date "2018-02-24")
          :julkipanoPvm        (date/xml-date "2018-02-22")}))

  (fact paatospoytakirja-type-canonical
    (paatospoytakirja-type-canonical p-verdict)
    => {:paatos          "Annettu"
        :paatoskoodi     "annettu lausunto"
        :paatoksentekija "Pete Paattaja"
        :paatospvm       (date/xml-date "2018-02-21")
        :pykala          "9"}))


(defn make-task [id schema-name source-id review-type]
  {:id          id
   :schema-info {:name schema-name}
   :source      {:id source-id}
   :data        {:katselmuksenLaji {:value review-type}}})

(facts verdict-reviews
  (let [[r1 r2] (->> verdict :references :reviews
                    (map (partial #'vc/pate->required-review :r)))]
    (facts "Pate"
     (verdict-reviews verdict {}) => [r1 r2]
     (verdict-reviews (update-in verdict [:data :reviews] rest)
                      {:tasks []}) => [r2]
     (verdict-reviews verdict {:tasks [(make-task "hello" "task-katselmus"
                                                  (:id verdict)
                                                  "muu katselmus")]})
     => [(assoc r1 :task-id "hello") r2]
     (verdict-reviews (update-in verdict [:data :reviews] rest)
                      {:tasks [(make-task "hello" "task-katselmus"
                                          (:id verdict)
                                          "muu katselmus")]})
     => [r2]
     (verdict-reviews verdict {:tasks [(make-task "hello" "task-lupamaarays"
                                                  (:id verdict)
                                                  "muu katselmus")]})
     => [r1 r2]
     (verdict-reviews verdict {:tasks [(make-task "hello" "task-katselmus"
                                                  "foo"
                                                  "muu katselmus")]})
     => [r1 r2]
     (verdict-reviews verdict {:tasks [(make-task "hello" "task-katselmus"
                                                  (:id verdict)
                                                  "aloituskokous")]})
     => [r1 r2]
     (verdict-reviews verdict {:tasks [(make-task "hello" "task-katselmus"
                                                  (:id verdict)
                                                  "rakennuksen paikan merkitseminen")
                                       (make-task "world" "task-katselmus"
                                                  (:id verdict)
                                                  "muu katselmus")]})
     => [(assoc r1 :task-id "world") (assoc r2 :task-id "hello")]))
  (let [[r1 r2 r3] (->> legacy-verdict :data :reviews
                        (map (partial #'vc/legacy->required-review :r)))]
    (facts "Legacy"
      (verdict-reviews legacy-verdict {}) => [r1 r2 r3]
      (verdict-reviews (update-in legacy-verdict [:data :reviews] rest)
                       {:tasks []}) => [r2 r3]
      (verdict-reviews legacy-verdict {:tasks [(make-task "one" "task-katselmus"
                                                          (:id legacy-verdict)
                                                          "muu katselmus")]})
      => [r1 r2 (assoc r3 :task-id "one")]
      (verdict-reviews legacy-verdict {:tasks [(make-task "one" "task-katselmus"
                                                          (:id legacy-verdict)
                                                          "muu katselmus")
                                               (make-task "two" "task-katselmus"
                                                          (:id legacy-verdict)
                                                          "aloituskokous")
                                               (make-task "three" "task-katselmus"
                                                          (:id legacy-verdict)
                                                          "loppukatselmus")
                                               (make-task "four" "task-katselmus"
                                                          (:id legacy-verdict)
                                                          "osittainen loppukatselmus")]})
      => (map #(assoc %1 :task-id %2) [r1 r2 r3] ["two" "three" "one"]))))

(facts verdict-code
  (verdict-code {}) => "ei tiedossa"
  (verdict-code verdict) => "myönnetty"
  (verdict-code p-verdict) => "annettu lausunto"
  (verdict-code legacy-verdict)
  => "ei tutkittu (oikaisuvaatimus tai lupa pysyy evättynä)"
  (verdict-code {:category "r"
                 :legacy?  true
                 :data     {:verdict-code "10"}})
  => "pysytti määräyksen tai päätöksen"
  (verdict-code {:paatokset [{:poytakirjat [{:status " 25 "}]}]})
  => "valituksesta on luovuttu (oikaisuvaatimus tai lupa pysyy evättynä)")

(facts verdict-canonical
  (fact "Pate R"
    (util/strip-blanks (verdict-canonical :fi verdict
                                          {:tasks [(make-task "one-id" "task-katselmus"
                                                              (:id verdict)
                                                              "rakennuksen paikan merkitseminen")]}))
    => {:Paatos
        {:lupamaaraykset
         {:autopaikkojaKiinteistolla       2
          :autopaikkojaRakennettava        3
          :autopaikkojaRakennettu          1
          :kokoontumistilanHenkilomaara    6
          :maaraystieto                    [{:Maarays {:sisalto "muut lupaehdot - teksti"}}
                                            {:Maarays {:sisalto "toinen teksti"}}]
          :vaadittuErityissuunnitelmatieto [{:VaadittuErityissuunnitelma {:vaadittuErityissuunnitelma "Suunnitelmat"}}
                                            {:VaadittuErityissuunnitelma {:vaadittuErityissuunnitelma "Suunnitelmat2"}}]
          :vaadittuTyonjohtajatieto        [{:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "erityisalojen työnjohtaja"}}
                                            {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "IV-työnjohtaja"}}
                                            {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "vastaava työnjohtaja"}}
                                            {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "KVV-työnjohtaja"}}]
          :vaaditutKatselmukset            [{:Katselmus {:katselmuksenLaji                "muu katselmus"
                                                         :muuTunnustieto                  [{:MuuTunnus {:sovellus "Lupapiste"}}]
                                                         :tarkastuksenTaiKatselmuksenNimi "Katselmus"}}
                                            {:Katselmus {:katselmuksenLaji                "rakennuksen paikan merkitseminen"
                                                         :muuTunnustieto                  [{:MuuTunnus {:sovellus "Lupapiste"
                                                                                                        :tunnus   "one-id"}}]
                                                         :tarkastuksenTaiKatselmuksenNimi "Katselmus2"}}]}
         :paatosdokumentinPvm "2017-11-23+02:00"
         :paivamaarat         {:aloitettavaPvm      "2022-11-23+02:00"
                               :antoPvm             "2017-11-20+02:00"
                               :julkipanoPvm        "2017-11-24+02:00"
                               :lainvoimainenPvm    "2017-11-27+02:00"
                               :viimeinenValitusPvm "2017-12-27+02:00"
                               :voimassaHetkiPvm    "2023-11-23+02:00"}
         :poytakirja          {:paatoksentekija "Pate Paattaja"
                               :paatos          "päätös - teksti"
                               :paatoskoodi     "myönnetty"
                               :paatospvm       "2017-11-23+02:00"
                               :pykala          "99"}}})
  (fact "Pate P"
    (util/strip-blanks (verdict-canonical :fi p-verdict {}))
    => {:Paatos {:lupamaaraykset      {}
                 :paatosdokumentinPvm "2018-02-21+02:00"
                 :paivamaarat         {:antoPvm             "2018-02-23+02:00"
                                       :julkipanoPvm        "2018-02-22+02:00"
                                       :viimeinenValitusPvm "2018-02-24+02:00"
                                       :voimassaHetkiPvm    "2020-02-25+02:00"}
                 :poytakirja          {:paatoksentekija "Pete Paattaja"
                                       :paatos          "Annettu"
                                       :paatoskoodi     "annettu lausunto"
                                       :paatospvm       "2018-02-21+02:00"
                                       :pykala          "9"}}})
  (fact "Legacy"
    (util/strip-blanks (verdict-canonical :fi legacy-verdict
                                          {:tasks [(make-task "one-id" "task-katselmus"
                                                              (:id legacy-verdict)
                                                              "aloituskokous")
                                                   (make-task "two-id" "task-katselmus"
                                                              (:id legacy-verdict)
                                                              "loppukatselmus")]}))
    => {:Paatos
        {:lupamaaraykset
         {:maaraystieto             [{:Maarays {:sisalto "Lippu salkoon"}}]
          :vaadittuTyonjohtajatieto [{:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "erityisalojen työnjohtaja"}}
                                     {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "vastaava työnjohtaja"}}]
          :vaaditutKatselmukset
          [{:Katselmus {:katselmuksenLaji                "aloituskokous"
                        :muuTunnustieto                  [{:MuuTunnus {:sovellus "Lupapiste"
                                                                       :tunnus   "one-id"}}]
                        :tarkastuksenTaiKatselmuksenNimi "Aloituskokous"}}
           {:Katselmus {:katselmuksenLaji                "loppukatselmus"
                        :muuTunnustieto                  [{:MuuTunnus {:sovellus "Lupapiste"
                                                                       :tunnus   "two-id"}}]
                        :tarkastuksenTaiKatselmuksenNimi "Loppukatselmus"}}
           {:Katselmus {:katselmuksenLaji                "muu katselmus"
                        :muuTunnustieto                  [{:MuuTunnus {:sovellus "Lupapiste"}}]
                        :tarkastuksenTaiKatselmuksenNimi "Onpahan vaan"}}]}
         :paatosdokumentinPvm "2017-11-20+02:00"
         :paivamaarat         {:antoPvm          "2017-11-20+02:00"
                               :julkipanoPvm     "2017-11-24+02:00"
                               :lainvoimainenPvm "2017-11-27+02:00"}
         :poytakirja          {:paatoksentekija "Annie Authority"
                               :paatos          "Lorem ipsum et al."
                               :paatoskoodi     "ei tutkittu (oikaisuvaatimus tai lupa pysyy evättynä)"
                               :paatospvm       "2017-11-20+02:00"
                               :pykala          "20"}}})
  (fact "Backing system"
    (util/strip-blanks (verdict-canonical :fi backing-system-verdict
                                          {:tasks [(make-task "one-id" "task-katselmus"
                                                              (:id backing-system-verdict)
                                                              "aloituskokous")
                                                   (make-task "two-id" "task-katselmus"
                                                              (:id backing-system-verdict)
                                                              "loppukatselmus")]}))
    => {:Paatos
        {:lupamaaraykset
         {:autopaikkojaEnintaan      10
          :autopaikkojaKiinteistolla 7
          :autopaikkojaRakennettava  2
          :autopaikkojaRakennettu    0
          :autopaikkojaUlkopuolella  3
          :autopaikkojaVahintaan     1
          :kerrosala                 100
          :kokonaisala               110
          :maaraystieto              [{:Maarays {:sisalto "Radontekninen suunnitelma"}}
                                      {:Maarays {:sisalto "Ilmanvaihtosuunnitelma"}}]
          :vaadittuTyonjohtajatieto  [{:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "vastaava työnjohtaja"}}
                                      {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "ei tiedossa"}}
                                      {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "työnjohtaja"}}]
          :vaaditutKatselmukset      [{:Katselmus {:katselmuksenLaji                "aloituskokous"
                                                   :muuTunnustieto                  [{:MuuTunnus {:sovellus "Lupapiste"
                                                                                                  :tunnus   "one-id"}}]
                                                   :tarkastuksenTaiKatselmuksenNimi "Aloituskokous"}}
                                      {:Katselmus {:katselmuksenLaji                "muu tarkastus"
                                                   :muuTunnustieto                  [{:MuuTunnus {:sovellus "Lupapiste"}}]
                                                   :tarkastuksenTaiKatselmuksenNimi "Käyttöönottotarkastus"}}]}
         :paatosdokumentinPvm "2017-11-23+02:00"
         :paivamaarat         {:aloitettavaPvm   "2022-11-23+02:00"
                               :antoPvm          "2017-11-20+02:00"
                               :julkipanoPvm     "2017-11-24+02:00"
                               :lainvoimainenPvm "2017-11-27+02:00"
                               :raukeamisPvm     "2024-11-23+02:00"
                               :voimassaHetkiPvm "2023-11-23+02:00"}
         :poytakirja          {:paatoksentekija "Mölli Keinonen"
                               :paatos          "Mitä muuta on tekeillä?"
                               :paatoskoodi     "ehdollinen"
                               :paatospvm       "2017-11-23+02:00"
                               :pykala          "2"}}}))
