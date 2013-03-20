(ns lupapalvelu.xml.krysp.rakennuslupa-mapping
  (:use  [lupapalvelu.xml.krysp.yhteiset]
         [clojure.data.xml]
         [clojure.java.io]
         [lupapalvelu.document.krysp :only [application-to-canonical]]
         [lupapalvelu.xml.emit :only [element-to-xml]]
         [lupapalvelu.xml.krysp.validator :only [validate]])
  (:require [lupapalvelu.env :as env]
            [me.raynes.fs :as fs]))

;RakVal

(def ^:private huoneisto {:tag :huoneisto
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
                                 {:tag :jakokirjain}]}]})


(def rakennelma (conj [{:tag :kuvaus
                        :child [{:tag :kuvaus}]}]
                      sijantitieto
                      {:tag :tunnus :child tunnus-children}))

(def yht-rakennus [{:tag :yksilointitieto :ns "yht"}
                   {:tag :alkuHetki :ns "yht"}
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
                            {:tag :rakentamistapa}
                            {:tag :kantavaRakennusaine :child [{:tag :muuRakennusaine}
                                                               {:tag :rakennusaine}]}
                            {:tag :julkisivu
                             :child [{:tag :muuMateriaali}
                                     {:tag :julkisivumateriaali}]}
                            {:tag :verkostoliittymat :child [{:tag :viemariKytkin}
                                                             {:tag :vesijohtoKytkin}
                                                             {:tag :sahkoKytkin}
                                                             {:tag :maakaasuKytkin}
                                                             {:tag :kaapeliKytkin}]}
                            {:tag :energialuokka}
                            {:tag :energiatehokkuusluku}
                            {:tag :energiatehokkuusluvunYksikko}
                            {:tag :paloluokka}
                                 {:tag :lammitystapa}
                                 {:tag :lammonlahde :child [{:tag :polttoaine}
                                                            {:tag :muu}]}
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
                                 ]}
                   {:tag :omistajatieto
                    :child [{:tag :Omistaja
                             :child [{:tag :kuntaRooliKoodi :ns "yht"}
                                     {:tag :VRKrooliKoodi :ns "yht"}
                                     henkilo
                                     yritys
                                     {:tag :omistajalaji :ns "rakval"
                                      :child [{:tag :muu}
                                              {:tag :omistajalaji}]}]}]}])

(def rakennus {:tag :Rakennus
               :child yht-rakennus})


(def rakennuslupa_to_krysp
  {:tag :Rakennusvalvonta :ns "rakval" :attr {:xsi:schemaLocation "http://www.paikkatietopalvelu.fi/gml/yhteiset http://www.paikkatietopalvelu.fi/gml/yhteiset/2.0.4/yhteiset.xsd http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta/2.0.4/rakennusvalvonta.xsd"
                                        :xmlns:rakval "http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta"
                                        :xmlns:yht "http://www.paikkatietopalvelu.fi/gml/yhteiset"
                                        :xmlns:xlink "http://www.w3.org/1999/xlink"
                                        :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"}
   :child [{:tag :toimituksenTiedot :child toimituksenTiedot}
           {:tag :rakennusvalvontaAsiatieto
            :child [{:tag :RakennusvalvontaAsia
                     :child [{:tag :kasittelynTilatieto :child [tilamuutos]}
                             {:tag :luvanTunnisteTiedot
                              :child [lupatunnus]}
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
                                               {:tag :perusparannus}
                                               {:tag :uudelleenrakentaminen}
                                               {:tag :purkaminen}
                                               {:tag :muuMuutosTyo}
                                               {:tag :kaupunkikuvaToimenpide}
                                               {:tag :rakennustieto
                                                :child [rakennus]}
                                               {:tag :rakennelmatieto}]}]}]}]}]})

(defn get-application-as-krysp [application]
  (let [canonical  (application-to-canonical application)
        xml        (element-to-xml canonical rakennuslupa_to_krysp)
        xml-s      (indent-str xml)
        output-dir (str (:outgoing-directory env/config) "/" (:municipality application) "/rakennus")
        _          (fs/mkdirs output-dir)
        file-name  (str output-dir "/Lupapiste" (:id application))
        tempfile   (file (str file-name ".tmp"))
        outfile    (file (str file-name ".xml"))]
    (validate xml-s)

    (with-open [out-file (writer tempfile)]
      (emit xml out-file)
      )
    ;todoo liitetiedostot
   (.renameTo tempfile outfile)))
