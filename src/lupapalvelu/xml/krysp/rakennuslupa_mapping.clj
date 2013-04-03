(ns lupapalvelu.xml.krysp.rakennuslupa-mapping
  (:use  [lupapalvelu.xml.krysp.yhteiset]
         [clojure.data.xml]
         [clojure.java.io]
         [lupapalvelu.document.krysp :only [application-to-canonical]]
         [lupapalvelu.xml.emit :only [element-to-xml]]
         [lupapalvelu.xml.krysp.validator :only [validate]]
         [lupapalvelu.document.krysp :only [to-xml-datetime]]
         [lupapalvelu.mongo :only [download]]
         [lupapalvelu.attachment :only [encode-filename]])
  (:require [sade.env :as env]
            [me.raynes.fs :as fs])
  )

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
                                               {:tag :rakennelmatieto}]}]}
                             {:tag :lisatiedot
                              :child [{:tag :Lisatiedot
                                       :child [{:tag :salassapitotietoKytkin}
                                      {:tag :asioimiskieli}
                                      {:tag :suoramarkkinointikieltoKytkin}]}]}
                             {:tag :liitetieto
                              :child [{:tag :Liite
                                       :child [{:tag :kuvaus :ns "yht"}
                                               {:tag :linkkiliitteeseen :ns "yht"}
                                               {:tag :muokkausHetki :ns "yht"}
                                               {:tag :versionumero :ns "yht"}
                                               {:tag :tekija :ns "yht"
                                                :child [{:tag :kuntaRooliKoodi}
                                                        {:tag :VRKrooliKoodi}
                                                        henkilo
                                                        yritys]}
                                               {:tag :tyyppi :ns "yht"}]}]}
                             {:tag :kayttotapaus}
                             {:tag :asianTiedot
                              :child [{:tag :Asiantiedot
                                       :child [{:tag :vahainenPoikkeaminen}
                                                {:tag :rakennusvalvontaasianKuvaus}]}]}]}]}]})

(defn- get-file-name-on-server [file-id file-name]
  (str file-id "_" (encode-filename file-name)))

(defn- get-attachments-as-canonical [application dynamic-part-of-outgoing-directory]
  (let [attachments (:attachments application)
        fileserver-address (:fileserver-address env/config)
        fileserver-root-directory (:fileserver-root-directory env/config)
        begin-of-link (str fileserver-address "/" fileserver-root-directory "/" dynamic-part-of-outgoing-directory "/")
        canonical-attachments (for [attachment attachments
                                    :when (:latestVersion attachment)
                                    :let [type (get-in attachment [:type :type-id] )
                                          title (str (:title application) " : " type)
                                          file-id (get-in attachment [:latestVersion :fileId])
                                          attachment-file-name (get-file-name-on-server file-id (get-in attachment [:latestVersion :filename]))

                                          link (str begin-of-link attachment-file-name)]]
                                 {:Liite
                                  {:kuvaus title
                                   :linkkiliitteeseen link
                                   :muokkausHetki (to-xml-datetime (:modified attachment))
                                   :versionumero 1
                                   :tyyppi type
                                   :fileId file-id}})]
    (when (not-empty canonical-attachments)
      canonical-attachments)))

(defn- write-file [content attachment-file-name]
  (let [outFile (file attachment-file-name)]
  (with-open [out (output-stream outFile)]
      (copy (content) out))
  ))

(defn- write-attachments [attachments output-dir]
  (println "===========attachments=======================")
  (clojure.pprint/pprint attachments)
  (println (count attachments))

  (for [attachment attachments
          :let [file-id (get-in attachment [:Liite :fileId])
                file (download file-id)
                content (:content file)
                attachment-file-name (str output-dir "/" (get-file-name-on-server file-id (:file-name file)))]]
      (write-file content attachment-file-name)))

(defn get-application-as-krysp [application]
  (let [municipality-code (:municipality application)
        rakennusvalvonta-directory "/rakennus"
        dynamic-part-of-outgoing-directory (str municipality-code rakennusvalvonta-directory)
        output-dir (str (:outgoing-directory env/config) "/" dynamic-part-of-outgoing-directory )
        _          (fs/mkdirs output-dir)
        file-name  (str output-dir "/Lupapiste" (:id application))
        tempfile   (file (str file-name ".tmp"))
        outfile    (file (str file-name ".xml"))
        canonical-without-attachments  (application-to-canonical application)
        attachments (get-attachments-as-canonical application dynamic-part-of-outgoing-directory)
        canonical (assoc-in canonical-without-attachments [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto] attachments)
        xml        (element-to-xml canonical rakennuslupa_to_krysp)]
    (validate (indent-str xml))
    (print (write-attachments attachments output-dir))
    (with-open [out-file (writer tempfile)]
      (emit xml out-file))

    (when (fs/exists? outfile) (fs/delete outfile))
    (fs/rename tempfile outfile)))

