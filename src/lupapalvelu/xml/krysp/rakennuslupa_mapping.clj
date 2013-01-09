(ns lupapalvelu.xml.krysp.rakennuslupa-mapping
  (:use  [lupapalvelu.xml.krysp.yhteiset]
         [clojure.data.xml]
         [clojure.java.io]))

;RakVal
(def tunnus-children [{:tag :valtakunnallinenNumero}
                      {:tag :jarjestysnumero}
                      {:tag :kiinttun}
                      {:tag :rakennusnro}
                      {:tag :aanestysalue}])

(def rakennelma (conj [{:tag :kuvaus
                        :child [{:tag :kuvaus}]}]
                      sijantitieto
                      {:tag :tunnus :child tunnus-children}))

(def huoneisto {:tag :huoneisto
                :child [{:tag :huoneluku}
                        {:tag :keittionTyyppi}
                        {:tag :huoneistoala}
                        {:tag :varusteet
                         :child [{:tag :WCKytkin}
                                 {:tag :ammeTaiSuihkuKytkin}
                                 {:tag :saunaKytkin}
                                 {:tag :parvekeTaiTerassiKytkin}
                                 {:tag :lamminvesiKytkin}]}
                        {:tag :huoneistonTyyppi}
                        {:tag :huoneistotunnus
                         :child [{:tag :porras}
                                 {:tag :huoneistonumero}
                                 {:tag :jakokirjain}
                                 ]}
                        ]})

(def rakennus {:tag :Rakennus
                :child [{:tag :yksilointitieto :attr {:xmlns "http://www.paikkatietopalvelu.fi/gml/yhteiset"}}
                        {:tag :alkuHetki :attr {:xmlns "http://www.paikkatietopalvelu.fi/gml/yhteiset"}}
                        sijantitieto
                        {:tag :rakennuksenTiedot
                         :child [{:tag :rakennustunnus :child tunnus-children}
                                 {:tag :kayttotarkoitus}
                                 {:tag :tilavuus}
                                 {:tag :kokonaisala}
                                 {:tag :kellarinpinta-ala}
                                 {:tag :BIM :child []}
                                 {:tag :kerrosluku}
                                 {:tag :kerrosala}
                                 {:tag :rakentamistapa :child []}
                                 {:tag :kantavaRakennusaine :child []}
                                 {:tag :julkisivu
                                  :child [{:tag :muuMateriaali}
                                          {:tag :julkisivumateriaali}]}
                                 {:tag :verkostoliittymat :child []}
                                 {:tag :energialuokka}
                                 {:tag :paloluokka}
                                 {:tag :lammitystapa :child []}
                                 {:tag :lammonlahde :child []}
                                 {:tag :varusteet
                                  :child [{:tag :sahkoKytkin}
                                          {:tag :kaasuKytkin}
                                          {:tag :viemariKytkin}
                                          {:tag :vesijohtoKytkin}
                                          {:tag :lamminvesiKytkin}
                                          {:tag :aurinkopaneeliKytkin}
                                          {:tag :hissiKytkin}
                                          {:tag :koneellinenilmastointiKytkin}
                                          {:tag :saunoja}
                                          {:tag :uima-altaita}
                                          {:tag :vaestonsuoja}]}
                                 {:tag :jaahdytysmuoto}
                                 {:tag :asuinhuoneistot :child [huoneisto]}
                                 ]}]})


(def rakennuslupa_to_krysp
  {:tag :Rakennusvalvonta :attr {:xmlns:xs "http://www.w3.org/2001/XMLSchema"
                                  :xmlns "http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta"}
   :child [{:tag :toimituksenTiedot :child toimituksenTiedot}
           {:tag :rakennusvalvontaAsiatieto
            :child [{:tag :RakennusvalvontaAsia
                     :child [{:tag :kasittelynTilatieto :child [tilamuutos]}
                             {:tag :luvanTunnisteTiedot
                              :child [{:tag :LupaTunnus
                                       :attr {:xmlns "http://www.paikkatietopalvelu.fi/gml/yhteiset"}
                                       :child [{:tag :muuTunnus} {:tag :saapumisPvm}]}]}
                             {:tag :osapuolettieto
                              :child [osapuolet]}
                             {:tag :rakennuspaikkatieto
                              :child [rakennuspaikka]}
                             {:tag :toimenpidetieto
                              :child [{:tag :Toimenpide
                                       :child [{:tag :uusi
                                                :child [{:tag :huoneistoala}
                                                        {:tag :kuvaus}]}
                                               {:tag :laajennus}
                                               {:tag :kayttotarkoitusmuutos}
                                               {:tag :perustus}
                                               {:tag :perusparannus}
                                               {:tag :uudelleenrakentaminen}
                                               {:tag :purkaminen}
                                               {:tag :muuMuutosTyo}
                                               {:tag :kaupunkikuvaToimenpide}
                                               {:tag :katselmustieto}
                                               {:tag :rakennustieto
                                                :child [rakennus]}
                                               {:tag :rakennelmatieto}]}]}]}]}]})
