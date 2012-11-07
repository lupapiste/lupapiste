(ns lupapalvelu.xml.krysp.rakennuslupa-mapping
  (:use  [lupapalvelu.xml.emit]
         [clojure.data.xml]
         [clojure.java.io]))

;RakVal
(def tunnus [{:tag :tunnus 
              :child [{:tag :valtakunnallinenNumero}
                      {:tag :jarjestysnumero}
                      {:tag :kiinttun}
                      {:tag :rakennusnro}
                      {:tag :aanestysalue}]}])

;YHTEISET
(def piste [{:tag :piste 
             :child [{:tag :Point 
                      :child [{:tag :pos}]}]}])

(def postiosoite [{:tag :osoite 
                   :child [{:tag :kunta}
                           {:tag :osoitenimi 
                            :attr {:xmlns "http://www.paikkatietopalvelu.fi/gml/yhteiset"} 
                            :child [{:tag :teksti}]}
                           {:tag :postinumero}
                           {:tag :postitoimipaikannimi}]}])
 
(def sijantitieto [{:tag :sijaintitieto
                    :child [{:tag :Sijainti
                             :child (conj postiosoite piste [{:tag :sijaintiepavarmuus}
                                                             {:tag :luontitapa}])}]}])

(def rakennusoikeudet [:tag :rakennusoikeudet
                       :child [{:tag :kayttotarkoitus
                                :child [{:tag :pintaAla}
                                        {:tag :kayttotarkoitusKoodi}]}]])

(def kiinteisto [{:tag :kiinteisto 
                  :child (conj [{:tag :kiinteisto 
                                 :child [{:tag :kylanimi}
                                         {:tag :tilannimi}
                                         {:tag :kiinteistotunnus}
                                         {:tag :maaraAlaTunnus}]}
                                {:tag :palsta}
                                {:tag :kokotilaKytkin}
                                {:tag :hallintaperuste}
                                {:tag :vuokraAluetunnus}
                                {:tag :kaavanaste}
                                {:tag :kerrosala}
                                {:tag :tasosijainti}
                                {:tag :rakennusoikeusYhteensa}
                                {:tag :uusiKytkin}]
                               postiosoite
                               sijantitieto
                               rakennusoikeudet
                               )}])

(def yksilointitieto [{:tag :yksilointitieto}
                      {:tag :alkuHetki}
                      {:tag :loppuHetki}]) 

(def rakennus [{:tag :Rakennuspaikka :child [(conj [] 
                                                   yksilointitieto
                                                   kiinteisto)]}])

(def rakennuspaikka  (conj [{:tag :Rakennuspaikka}
                            {:tag :kiinteisto 
                             :child [{:tag :kiinteisto 
                                      :child [{:tag :kylanimi}
                                              {:tag :tilannimi}
                                              {:tag :kiinteistotunnus}
                                              {:tag :maaraAlaTunnus}]}
                                     {:tag :palsta}
                                     {:tag :kokotilaKytkin}
                                     {:tag :hallintaperuste}
                                     {:tag :vuokraAluetunnus}]}
                            {:tag :kaavanaste}
                            {:tag :kerrosala}
                            {:tag :tasosijainti}
                            {:tag :rakennusoikeudet 
                             :child [{:tag :kayttotarkoitus 
                                      :child [{:tag :pintaAla}
                                              {:tag :kayttotarkoitusKoodi}]}]}
                            {:tag :rakennusoikeusYhteensa}
                            {:tag :uusiKytkin}] 
                           yksilointitieto 
                           sijantitieto 
                           postiosoite))

(def rakennelma (conj [{:tag :kuvaus 
                        :child [{:tag :kuvaus}]}] 
                      yksilointitieto
                      sijantitieto
                      tunnus))

(def rakennuslupa_to_krysp
  [{
    :tag :Rakennusvalvonta :attr { :xmlns:xs "http://www.w3.org/2001/XMLSchema"
                                  :xmlns:rakval "http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta"
                                  :xmlns:gml "http://www.opengis.net/gml"
                                  :xmlns:yht "http://www.paikkatietopalvelu.fi/gml/yhteiset"
                                  :targetNamespace "http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta"
                                  :elementFormDefault "qualified" 
                                  :attributeFormDefault "unqualified" 
                                  :version "2.0.0"}
    :child (conj rakennuspaikka rakennus rakennelma)}
   ])