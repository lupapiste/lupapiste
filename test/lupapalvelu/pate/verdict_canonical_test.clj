(ns lupapalvelu.pate.verdict-canonical-test
  (:require [lupapalvelu.pate.verdict-canonical :refer :all]
            [lupapalvelu.document.attachments-canonical :refer [verdict-attachment-canonical]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.util :as util]))

(defn timestamp [date]
  (+ (* 1000 3600 12) (util/to-millis-from-local-date-string date)))

(def verdict {:id         "1a156dd40e40adc8ee064463"
              :data       {:voimassa                (timestamp "23.11.2023")
                           :appeal                  "muutoksenhakuohje - teksti"
                           :julkipano               (timestamp "24.11.2017")
                           :bulletin-op-description "julkipano - teksti"
                           :purpose                 "k\u00e4ytt\u00f6tarkoitus"
                           :verdict-text            "p\u00e4\u00e4t\u00f6s - teksti"
                           :anto                    (timestamp "20.11.2017")
                           :complexity              "small"
                           :plans                   ["5a156ddf0e40adc8ee064464"
                                                     "6a156ddf0e40adc8ee064464"]
                           :aloitettava             (timestamp "23.11.2022")
                           :valitus                 (timestamp "27.12.2017")
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
                           :lainvoimainen           (timestamp "27.11.2017")
                           :reviews-included        true
                           :statements              ""
                           :verdict-date            (timestamp "23.11.2017")
                           :automatic-verdict-dates true
                           :handler                 "Pate Paattaja"
                           :verdict-section         "99"
                           :buildings               {"5a1561250e40adc8ee064449" {:rakennetut-autopaikat  "1"
                                                                                 :kiinteiston-autopaikat "2"
                                                                                 :autopaikat-yhteensa    "3"
                                                                                 :vss-luokka             "4"
                                                                                 :paloluokka             "5"}}
                           :complexity-text         "hankkeen vaativuus"}
              :references {:plans   [{:id   "5a156ddf0e40adc8ee064464"
                                      :name {:fi "Suunnitelmat"
                                             :sv "Planer"
                                             :en "Plans"}}
                                     {:id   "6a156ddf0e40adc8ee064464"
                                      :name {:fi "Suunnitelmat2"
                                             :sv "Planer2"
                                             :en "Plans2"}}]
                           :reviews [{:id   "5a156dd40e40adc8ee064463"
                                      :name {:fi "Katselmus"
                                             :sv "Syn"
                                             :en "Review"}
                                      :type "muu-katselmus"}
                                     {:id   "6a156dd40e40adc8ee064463"
                                      :name {:fi "Katselmus2"
                                             :sv "Syn2"
                                             :en "Review2"}
                                      :type "paikan-merkitseminen"}]}
              :template   {:giver "viranhaltija"}
              :verdict-attachment {:type           {:type-id    :paatos
                                                    :type-group :paatoksenteko}
                                   :created        1521628178984
                                   :size           22959
                                   :filename       "LP-753-2018-90012 P\u00e4\u00e4t\u00f6s 21.03.2018 12:29.pdf"
                                   :originalFileId "5ab234139997d70535b952b2"
                                   :id             "5ab234139997d70535b952b5"
                                   :autoConversion true
                                   :contentType    "application/pdf"
                                   :archivable     true
                                   :version        {:major 0 :minor 1}
                                   :stamped        false
                                   :user           {:id        "777777777777777777000023"
                                                    :username  "sonja"
                                                    :firstName "Sonja"
                                                    :lastName  "Sibbo"
                                                    :role      "authority"}
                                   :fileId         "5ab234149997d70535b952b6"}})

(def p-verdict {:data     {:voimassa              (timestamp "25.2.2020")
                           :appeal                ""
                           :julkipano             (timestamp "22.2.2018")
                           :bulletinOpDescription "Hanke on todella vaativa"
                           :verdict-date          (timestamp "21.2.2018")
                           :purpose               ""
                           :verdict-section       "9"
                           :verdict-text          "Annettu"
                           :muutoksenhaku         (timestamp "24.2.2018")
                           :anto                  (timestamp "23.2.2018")
                           :complexity            "medium"
                           :attachments
                           ({:type-group :paatoksenteko, :type-id :paatos, :amount 6}
                            {:type-group :paatoksenteko, :type-id :paatosote, :amount 1})
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
                :references
                {:verdict-code ["annettu-lausunto"]
                 :foremen      ["erityis-tj"]
                 :date-deltas
                 {:julkipano     1
                  :anto          11
                  :muutoksenhaku 1
                  :lainvoimainen 1
                  :aloitettava   1
                  :voimassa      2}
                 :plans        []
                 :reviews      []}
                :template
                {:giver "viranhaltija"
                 :exclusions
                 {:statements      true
                  :upload          true
                  :aloitettava     true
                  :lainvoimainen   true
                  :verdict-section true
                  :buildings       true}}
                :id       "5a8adacba067cd387ff9c00c"
                :modified 1519224505171
                :category :p})


(testable-privates lupapalvelu.pate.verdict-canonical
                   vaadittu-katselmus-canonical
                   maarays-seq-canonical
                   vaadittu-erityissuunnitelma-canonical
                   vaadittu-tyonjohtaja-canonical
                   lupamaaraykset-type-canonical
                   paivamaarat-type-canonical
                   paatoksentekija
                   paatospoytakirja-type-canonical)

(facts vaadittu-katselmus-canonical
  (fact "muu-katselmus / Finnish"
    (vaadittu-katselmus-canonical "fi" verdict "5a156dd40e40adc8ee064463")
    => {:Katselmus {:katselmuksenLaji "muu katselmus", :tarkastuksenTaiKatselmuksenNimi "Katselmus", :muuTunnustieto []}})

  (fact "muu-katselmus / English"
    (vaadittu-katselmus-canonical "en" verdict "5a156dd40e40adc8ee064463")
    => {:Katselmus {:katselmuksenLaji "muu katselmus", :tarkastuksenTaiKatselmuksenNimi "Review", :muuTunnustieto []}})

  (fact "paikan-merkisteminen / Swedish"
    (vaadittu-katselmus-canonical "sv" verdict "6a156dd40e40adc8ee064463")
    => {:Katselmus {:katselmuksenLaji "rakennuksen paikan merkitseminen", :tarkastuksenTaiKatselmuksenNimi "Syn2", :muuTunnustieto []}}))

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
    (vaadittu-erityissuunnitelma-canonical "fi" verdict "5a156ddf0e40adc8ee064464")
    => {:VaadittuErityissuunnitelma {:vaadittuErityissuunnitelma "Suunnitelmat", :toteutumisPvm nil}})

  (fact "suunnitelmat / Swedish"
    (vaadittu-erityissuunnitelma-canonical "sv" verdict "5a156ddf0e40adc8ee064464")
    => {:VaadittuErityissuunnitelma {:vaadittuErityissuunnitelma "Planer", :toteutumisPvm nil}})

  (fact "suunnitelmat / English"
    (vaadittu-erityissuunnitelma-canonical "en" verdict "6a156ddf0e40adc8ee064464")
    => {:VaadittuErityissuunnitelma {:vaadittuErityissuunnitelma "Plans2", :toteutumisPvm nil}}))

(fact vaadittu-tyonjohtaja-canonical
  (fact "vv-tj"
    (vaadittu-tyonjohtaja-canonical "vv-tj")
    => {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "KVV-ty\u00f6njohtaja"}})

  (fact "tj"
    (vaadittu-tyonjohtaja-canonical "tj")
    => {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "ty\u00f6njohtaja"}})

  (fact "nil"
    (vaadittu-tyonjohtaja-canonical nil)
    => {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "ei tiedossa"}})

  (fact "foo"
    (vaadittu-tyonjohtaja-canonical "foo")
    => {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi "ei tiedossa"}}))

(facts lupamaaraykset-type-canonical
  (let [canonical (lupamaaraykset-type-canonical "fi" verdict)]

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
                                  :vaadittuTyonjohtajatieto}))

    (fact "not nil fields"
      (keys (filter val canonical)) => (just #{:autopaikkojaRakennettava
                                               :autopaikkojaRakennettu
                                               :autopaikkojaKiinteistolla
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
      => ["erityisalojen ty\u00f6njohtaja" "IV-ty\u00f6njohtaja" "vastaava ty\u00f6njohtaja" "KVV-ty\u00f6njohtaja"])))

(facts paivamaarat-type-canonical
  (fact "all dates defined"
    (paivamaarat-type-canonical "fi" verdict)
    => {:aloitettavaPvm "2022-11-23"
        :lainvoimainenPvm "2017-11-27"
        :voimassaHetkiPvm "2023-11-23"
        :raukeamisPvm nil
        :antoPvm "2017-11-20"
        :viimeinenValitusPvm "2017-12-27"
        :julkipanoPvm "2017-11-24"})

  (fact "undefined fields defaults to nil"
    (paivamaarat-type-canonical "fi" (update verdict :data dissoc :aloitettava :lainvoimainen :voimassa))
    => {:aloitettavaPvm nil
        :lainvoimainenPvm nil
        :voimassaHetkiPvm nil
        :raukeamisPvm nil
        :antoPvm "2017-11-20"
        :viimeinenValitusPvm "2017-12-27"
        :julkipanoPvm "2017-11-24"}))

(facts paatoksentekija
  (fact "empty fields"
    (paatoksentekija "fi" {:data {:handler ""}
                           :template {:giver ""}}) => "")

  (fact "nil fields"
    (paatoksentekija "fi" {:data {:handler nil}
                           :template {:giver nil}}) => "")

  (fact "empty giver"
    (paatoksentekija "fi" {:data {:handler "handler text"}
                           :template {:giver ""}}) => "handler text")

  (fact "empty contact - viranhaltija - Finnish"
    (paatoksentekija "fi" {:data {:handler ""}
                           :template {:giver "viranhaltija"}})
    => "(Viranhaltija)")

  (fact "empty contact - lautakunta - Swedish"
    (paatoksentekija "sv" {:data {:handler ""}
                           :template {:giver "lautakunta"}})
    => "(N\u00e4mnd)")

  (fact "both fields set - English"
    (paatoksentekija "en" verdict) => "Pate Paattaja (Authority)"))

(fact paatospoytakirja-type-canonical
  (paatospoytakirja-type-canonical "fi" verdict)
  => {:paatos "p\u00e4\u00e4t\u00f6s - teksti"
      :paatoskoodi "my\u00f6nnetty"
      :paatoksentekija "Pate Paattaja (Viranhaltija)"
      :paatospvm "2017-11-23"
      :pykala "99"})

(facts "Canonical for P verdict"

  (facts paivamaarat-type-canonical
    (fact "all dates defined"
      (paivamaarat-type-canonical "fi" p-verdict)
      => {:aloitettavaPvm nil
          :lainvoimainenPvm nil
          :voimassaHetkiPvm "2020-02-25"
          :raukeamisPvm nil
          :antoPvm "2018-02-23"
          :viimeinenValitusPvm nil
          :julkipanoPvm "2018-02-22"})
    (fact "undefined fields defaults to nil"
      (paivamaarat-type-canonical "fi" (update p-verdict :data dissoc :lainvoimainen :voimassa))
      => {:aloitettavaPvm nil
          :lainvoimainenPvm nil
          :voimassaHetkiPvm nil
          :raukeamisPvm nil
          :antoPvm "2018-02-23"
          :viimeinenValitusPvm nil
          :julkipanoPvm "2018-02-22"}))

  (fact paatospoytakirja-type-canonical
    (paatospoytakirja-type-canonical "fi" p-verdict)
    => {:paatos "Annettu"
        :paatoskoodi "annettu lausunto"
        :paatoksentekija "Pete Paattaja (Viranhaltija)"
        :paatospvm "2018-02-21"
        :pykala "9"}))

(facts "Canonical for verdict attachment"

  (fact verdict-attachment-canonical
    (verdict-attachment-canonical "fi" verdict "http://localhost:8000/api/raw/")
    => {:kuvaus "P\u00e4\u00e4t\u00f6s"
        :linkkiliitteeseen "http://localhost:8000/api/raw/latest-attachment-version?attachment-id=5ab234139997d70535b952b5"
        :muokkausHetki "2018-03-21T10:29:38"
        :versionumero "0.1"
        :tyyppi "paatos"
        :metatietotieto nil
        :rakennustunnustieto nil
        :fileId "5ab234149997d70535b952b6"
        :filename "LP-753-2018-90012 P\u00e4\u00e4t\u00f6s 21.03.2018 12:29.pdf"}))
