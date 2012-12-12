(ns lupapalvelu.xml.krysp.rakennuslupa-mapping
  (:use  [lupapalvelu.xml.krysp.yhteiset]
         [clojure.data.xml]
         [clojure.java.io]))

;RakVal
(def tunnus [{:tag :tunnus
              :child [{:tag :valtakunnallinenNumero}
                      {:tag :jarjestysnumero}
                      {:tag :kiinttun}
                      {:tag :rakennusnro}
                      {:tag :aanestysalue}]}])

(def rakennelma (conj [{:tag :kuvaus
                        :child [{:tag :kuvaus}]}]
                      yksilointitieto
                      sijantitieto
                      tunnus))

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
    ;:child (conj rakennuspaikka rakennus rakennelma)}
   :child [{:tag :toimituksenTiedot :child toimituksenTiedot}
           {:tag :rakennusvalvontaAsiatieto
            :child [{:tag :RakennusvalvontaAsia
                     :child [{:tag :kasittelynTilatieto :child [tilamuutos]}
                             {:tag :osapuolettieto
                              :child [{:tag :Osapuolet
                                       :attr {:xmlns "http://www.paikkatietopalvelu.fi/gml/yhteiset"}
                                       :child [{:tag :osapuolitieto
                                                :child [{:tag :Osapuoli
                                                         :child [{:tag :kuntaRooliKoodi}
                                                                 {:tag :VRKrooliKoodi}
                                                                 {:tag :henkilo
                                                                  :child [{:tag :nimi :child [{:tag :etunimi}, {:tag :sukunimi}]}
                                                                          postiosoite
                                                                          {:tag :sahkopostiosoite}
                                                                          {:tag :faksinumero}
                                                                          {:tag :puhelin}
                                                                          {:tag :henkilotunnus}]
                                                                  }]
                                                         }]}]}]}]
                     }]}]
    })
