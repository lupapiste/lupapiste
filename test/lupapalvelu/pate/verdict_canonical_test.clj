(ns lupapalvelu.pate.verdict-canonical-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.pate.verdict-canonical :refer :all]))

(def verdict {:id "1a156dd40e40adc8ee064463"
              :data {:voimassa "23.11.2023"
                     :appeal "muutoksenhakuohje - teksti"
                     :julkipano "24.11.2017"
                     :bulletin-op-description "julkipano - teksti"
                     :purpose "k\u00e4ytt\u00f6tarkoitus"
                     :verdict-text "p\u00e4\u00e4t\u00f6s - teksti"
                     :anto "20.11.2017"
                     :giver "viranhaltija"
                     :complexity "small"
                     :plans ["5a156ddf0e40adc8ee064464"
                             "6a156ddf0e40adc8ee064464"]
                     :aloitettava "23.11.2022"
                     :valitus "27.12.2017"
                     :foremen ["erityis-tj"
                               "iv-tj"
                               "vastaava-tj"
                               "vv-tj"]
                     :verdict-code "myonnetty"
                     :collateral "vakuus - teksti"
                     :conditions "muut lupaehdot - teksti"
                     :rights "rakennusoikeus"
                     :plans-included true
                     :reviews ["5a156dd40e40adc8ee064463"
                               "6a156dd40e40adc8ee064463"]
                     :foremen-included true
                     :neighbors ""
                     :lainvoimainen "27.11.2017"
                     :reviews-included true
                     :statements ""
                     :verdict-date "23.11.2017"
                     :automatic-verdict-dates true
                     :contact "Pate Paattaja"
                     :verdict-section "99"
                     :buildings {"5a1561250e40adc8ee064449" {:rakennetut-autopaikat "1"
                                                             :kiinteiston-autopaikat "2"
                                                             :autopaikat-yhteensa "3"
                                                             :vss-luokka "4"
                                                             :paloluokka "5"}}
                     :complexity-text "hankkeen vaativuus"}
              :references {:plans [{:id "5a156ddf0e40adc8ee064464"
                                    :name {:fi "Suunnitelmat"
                                           :sv "Planer"
                                           :en "Plans"}}
                                   {:id "6a156ddf0e40adc8ee064464"
                                    :name {:fi "Suunnitelmat2"
                                           :sv "Planer2"
                                           :en "Plans2"}}]
                           :reviews [{:id "5a156dd40e40adc8ee064463"
                                      :name {:fi "Katselmus"
                                             :sv "Syn"
                                             :en "Review"}
                                      :type "muu-katselmus"}
                                     {:id "6a156dd40e40adc8ee064463"
                                      :name {:fi "Katselmus2"
                                             :sv "Syn2"
                                             :en "Review2"}
                                      :type "paikan-merkitseminen"}]}})

(testable-privates lupapalvelu.pate.verdict-canonical
                   vaadittu-katselmus-canonical
                   maarays-canonical
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

(fact maarays-canonical
  (maarays-canonical "fi" verdict)
  => {:Maarays {:sisalto "muut lupaehdot - teksti", :maaraysPvm "2017-11-23", :toteutusHetki nil}})

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
      => ["erityisalojen työnjohtaja" "IV-työnjohtaja" "vastaava työnjohtaja" "KVV-työnjohtaja"])))

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
    (paatoksentekija "fi" {:data {:giver "" :contact ""}}) => "")

  (fact "nil fields"
    (paatoksentekija "fi" {:data {:giver nil :contact nil}}) => nil)

  (fact "emtpy giver"
    (paatoksentekija "fi" {:data {:giver "" :contact "contact text"}}) => "contact text")

  (fact "emtpy contact - viranhaltija - Finnish"
    (paatoksentekija "fi" {:data {:giver "viranhaltija" :contact ""}}) => "Viranhaltija")

  (fact "emtpy contact - lautakunta - Swedish"
    (paatoksentekija "sv" {:data {:giver "lautakunta" :contact ""}}) => "N\u00e4mnd")

  (fact "both fields set - English"
    (paatoksentekija "en" verdict) => "Pate Paattaja (Office-holder)"))

(fact paatospoytakirja-type-canonical
  (paatospoytakirja-type-canonical "fi" verdict)
  => {:paatos "p\u00e4\u00e4t\u00f6s - teksti"
      :paatoskoodi "my\u00f6nnetty"
      :paatoksentekija "Pate Paattaja (Viranhaltija)"
      :paatospvm "2017-11-23"
      :pykala "99"
      :liite nil})
