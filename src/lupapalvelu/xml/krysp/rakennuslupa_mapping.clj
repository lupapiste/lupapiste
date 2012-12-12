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
                      yksilointitieto
                      sijantitieto
                      {:tag :tunnus :child tunnus-children}))

(def rakennus [{:tag :Rakennus
                :child [sijantitieto
                        {:tag :rakennuksenTiedot
                         :child [{:tag :rakennustunnus :child tunnus-children}
                                 {:tag :kayttotarkoitus}
                                 {:tag :tilavuus}
                                 {:tag :kokonaisala}
                                 {:tag :kellarinpinta-ala}
                                 {:tag :BIM :child []}
                                 osoite
                                 {:tag :rinnakkaisosoite
                                  :attr {:xmlns "http://www.paikkatietopalvelu.fi/gml/yhteiset"}
                                  :child postiosoite-children}
                                 {:tag :kerrosluku}
                                 {:tag :kerrosala}
                                 {:tag :rakentamistapa :child []}
                                 {:tag :kantavaRakennusaine :child []}
                                 {:tag :julkisivu :child []}
                                 {:tag :verkostoliittymat :child []}
                                 {:tag :energialuokka}
                                 {:tag :paloluokka}
                                 {:tag :lammitystapa :child []}
                                 {:tag :lammonlahde :child []}
                                 {:tag :varusteet :child []}
                                 {:tag :jaahdytysmuoto}
                                 {:tag :asuinhuoneistot :child []}
                                 ]}]}])


(def rakennuslupa_to_krysp
  {:tag :Rakennusvalvonta :attr {:xmlns:xs "http://www.w3.org/2001/XMLSchema"
                                  :xmlns "http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta"
                                  ;:xmlns:gml "http://www.opengis.net/gml"
                                  ;:xmlns:yht "http://www.paikkatietopalvelu.fi/gml/yhteiset"
                                  ; XXX not in schema:
                                  ; :elementFormDefault "qualified"
                                  ; :attributeFormDefault "unqualified"
                                  ; :version "2.0.0"
                                  }
   :child [{:tag :toimituksenTiedot :child toimituksenTiedot}
           {:tag :rakennusvalvontaAsiatieto
            :child [{:tag :RakennusvalvontaAsia
                     :child [{:tag :kasittelynTilatieto :child [tilamuutos]}
                             {:tag :luvanTunnisteTiedot
                              :child [{:tag :LupaTunnus
                                       :attr {:xmlns "http://www.paikkatietopalvelu.fi/gml/yhteiset"}
                                       :child [{:tag :muuTunnus} {:tag :saapumisPvm}]}]}
                             {:tag :osapuolettieto
                              :child [{:tag :Osapuolet
                                       :attr {:xmlns "http://www.paikkatietopalvelu.fi/gml/yhteiset"}
                                       :child [{:tag :osapuolitieto
                                                :child [{:tag :Osapuoli
                                                         :child [{:tag :kuntaRooliKoodi}
                                                                 {:tag :VRKrooliKoodi}
                                                                 henkilo]
                                                         }]}]}]}
                             {:tag :rakennuspaikkatieto}
                             {:tag :toimenpidetieto
                              :child [{:tag :Toimenpide
                                       :child [{:tag :uusi}
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
                                                :child []}
                                               {:tag :rakennelmatieto}]}]
                              }]}
                    ]
            }]}
    )
