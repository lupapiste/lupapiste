(ns lupapalvelu.xml.krysp.rakennuslupa-mapping
  (:use  [lupapalvelu.xml.krysp.yhteiset]
         [clojure.data.xml]
         [clojure.java.io]
         [lupapalvelu.document.rakennuslupa_canonical :only [application-to-canonical to-xml-datetime]]
         [lupapalvelu.xml.emit :only [element-to-xml]]
         [lupapalvelu.xml.krysp.validator :only [validate]]
         [lupapalvelu.attachment :only [encode-filename]]
         [clojure.java.io :as io])
  (:require [sade.env :as env]
            [me.raynes.fs :as fs]
            [lupapalvelu.ke6666 :as ke6666]
            [lupapalvelu.mongo :as mongo])
  (:import [java.util.zip ZipOutputStream ZipEntry]))

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
                    :child [{:tag :rakennustunnus :child [{:tag :jarjestysnumero}
                                                          {:tag :kiinttun}]}
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
                   {:tag :rakentajatyyppi}
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
  {:tag :Rakennusvalvonta :ns "rakval" :attr {:xsi:schemaLocation "http://www.paikkatietopalvelu.fi/gml/yhteiset http://www.paikkatietopalvelu.fi/gml/yhteiset/2.0.7/yhteiset.xsd http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta/2.0.5/rakennusvalvonta.xsd"
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
                                       :child [{:tag :uusi :child [{:tag :kuvaus}]}
                                               {:tag :laajennus :child [{:tag :laajennuksentiedot :child[{:tag :tilavuus}
                                                                                                        {:tag :kerrosala}
                                                                                                        {:tag :kokonaisala}
                                                                                                        {:tag :huoneistoala :child [{:tag :pintaAla :ns "yht"}
                                                                                                                                    {:tag :kayttotarkoitusKoodi :ns "yht"}]}]}
                                                                         {:tag :kuvaus}
                                                                         {:tag :perusparannusKytkin}]}
                                               {:tag :perusparannus}
                                               {:tag :uudelleenrakentaminen}
                                               {:tag :purkaminen :child [{:tag :kuvaus}
                                                                        {:tag :purkamisenSyy}
                                                                        {:tag :poistumaPvm }]}
                                               {:tag :muuMuutosTyo :child [{:tag :muutostyonLaji}
                                                                           {:tag :kuvaus}
                                                                           {:tag :perusparannusKytkin}]}
                                               {:tag :kaupunkikuvaToimenpide :child [{:tag :kuvaus}]}
                                               {:tag :rakennustieto
                                                :child [rakennus]}
                                               {:tag :rakennelmatieto :child [{:tag :Rakennelma :child [{:tag :yksilointitieto :ns "yht"}
                                                                                                        {:tag :alkuHetki :ns "yht"}
                                                                                                        sijantitieto
                                                                                                        {:tag :kuvaus :child [{:tag :kuvaus}]}]}]}
                                               ]}]}
                             {:tag :lausuntotieto :child [{:tag :Lausunto :ns "yht" :child [{:tag :pyydetty :child [{:tag :viranomainen}
                                                                                                          {:tag :pyyntoPvm}]}
                                                                                  {:tag :lausunto :child [{:tag :viranomainen}
                                                                                                          {:tag :lausuntoPvm}
                                                                                                          {:tag :lausunto :child [{:tag :lausunto}
                                                                                                                                  {:tag :liite
                                                                                                                                   :child [{:tag :kuvaus}
                                                                                                                                           {:tag :linkkiliitteeseen}
                                                                                                                                           {:tag :muokkausHetki}
                                                                                                                                           {:tag :versionumero}
                                                                                                                                           {:tag :tekija
                                                                                                                                            :child [{:tag :kuntaRooliKoodi}
                                                                                                                                                    {:tag :VRKrooliKoodi}
                                                                                                                                                    henkilo
                                                                                                                                                    yritys]}
                                                                                                                                           {:tag :tyyppi}]}]}
                                                                                                          {:tag :puoltotieto :child [{:tag :puolto}]}]}]}] }

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

(defn- get-submitted-filename [application-id]
  (str application-id "_submitted_application.pdf"))

(defn- get-current-filename [application-id]
  (str application-id "_current_application.pdf"))

(defn- get-Liite [title link attachment type file-id]
   {:kuvaus title
    :linkkiliitteeseen link
    :muokkausHetki (to-xml-datetime (:modified attachment))
    :versionumero 1
    :tyyppi type
    :fileId file-id})

(defn- get-liite-for-lausunto [attachment application begin-of-link]
  (let [type "Lausunto"
        title (str (:title application) ": " type "-" (:id attachment))
        file-id (get-in attachment [:latestVersion :fileId])
        attachment-file-name (get-file-name-on-server file-id (get-in attachment [:latestVersion :filename]))
        link (str begin-of-link attachment-file-name)]
    {:kuvaus title
     :linkkiliitteeseen link
     :muokkausHetki (to-xml-datetime (:modified attachment))
     :versionumero 1
     :tyyppi type
     :fileId file-id}))

(defn- get-liite-zipped [id-and-attachments application begin-of-link]
  (let [attachment (first (last id-and-attachments))
        type "Lausunto"
        title (str (:title application) ": " type "-" (:id attachment))
        file-id (str "L-" (first id-and-attachments))
        attachment-file-name (str (first id-and-attachments) ".zip")
        link (str begin-of-link attachment-file-name)]
    {:kuvaus title
     :linkkiliitteeseen link
     :muokkausHetki (to-xml-datetime (:modified attachment))
     :versionumero 1
     :tyyppi type
     :files (for [attachment (last id-and-attachments)]
              (:id attachment))}))

(defn- get-statement-attachments-as-canonical [application begin-of-link ]
  (let [statement-attachments-by-id (group-by #(keyword (get-in % [:target :id]))  (filter #(= "statement" (-> % :target :type)) (:attachments application)))
        canonical-attachments (for [attachment-tuple statement-attachments-by-id]
                                (if (= 1 (count (last attachment-tuple)))
                                  nil
                                  ; kommentoitu pois kryspiun choicen vuoksi{(first attachment-tuple) {:liite (for-lausunto (first (last attachment-tuple)) application begin-of-link)}}
                                  ; Ei tueta useampaa liitetta toistaiseksi. krysp menee uusiksi{(first attachment-tuple) {:liite (get-liite-zipped attachment-tuple application begin-of-link)}}
                                  ))]
    (not-empty canonical-attachments)))

(defn- get-attachments-as-canonical [application begin-of-link ]
  (let [attachments (:attachments application)
        canonical-attachments (for [attachment attachments
                                    :when (and (:latestVersion attachment) (not (= "statement" (-> attachment :target :type))))
                                    :let [type (get-in attachment [:type :type-id] )
                                          title (str (:title application) ": " type "-" (:id attachment))
                                          file-id (get-in attachment [:latestVersion :fileId])
                                          attachment-file-name (get-file-name-on-server file-id (get-in attachment [:latestVersion :filename]))
                                          link (str begin-of-link attachment-file-name)]]
                                {:Liite (get-Liite title link attachment type file-id)})]
    (not-empty canonical-attachments)))

(defn- write-attachments [attachments output-dir]
  (doseq [attachment attachments]
    (let [file-id (get-in attachment [:Liite :fileId])
          attachment-file (mongo/download file-id)
          content (:content attachment-file)
          attachment-file-name (str output-dir "/" (get-file-name-on-server file-id (:file-name attachment-file)))
          attachment-file (file attachment-file-name)]
      (with-open [out (output-stream attachment-file)
                  in (content)]
        (copy in out)))))

(defn- append-gridfs-file [zip file-name file-id]
  (when file-id
    (.putNextEntry zip (ZipEntry. (encode-filename file-name)))
    (with-open [in ((:content (mongo/download file-id)))]
      (io/copy in zip))))

(defn- write-statement-attachments [attachments output-dir]
  (let [single-files (filter #(nil? (:files %)) attachments)
        multiple-files (filter #(:files %) attachments)]
    ;(println "single-files")
    ;(clojure.pprint/pprint single-files)
    ;(println "multiple-files")
    ;(clojure.pprint/pprint multiple-files)
    (doseq [file-tuple single-files]
      (write-attachments (map (fn [m] {:Liite (:liite m)}) (vals file-tuple)) output-dir))
    (for [statement-attachments multiple-files]
      (let [tempfile (file (str (:kuvaus statement-attachments) ".zip.tmp"))
            outfile (file (str (:kuvaus statement-attachments) ".zip"))]
        (with-open [out (io/output-stream tempfile)]
          (let [zip (ZipOutputStream. out)]
            ; Add all attachments:
            (doseq [file (:files statement-attachments)]
              (append-gridfs-file zip (:filename file) (:fileId file))
              (.finish zip))
            (fs/rename tempfile outfile)
          ))))))

(defn- write-application-pdf-versions [output-dir application submitted-application lang]
  (let [id (:id application)
        submitted-file (file (str output-dir "/" (get-submitted-filename id)))
        current-file (file (str output-dir "/"  (get-current-filename id)))]
    (ke6666/generate submitted-application lang submitted-file)
    (ke6666/generate application lang current-file)))

(defn- add-statement-attchments [canonical statement-attachments]
  (reduce (fn [c a]
            (let [lausuntotieto (get-in canonical [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :lausuntotieto])
                  paivitettava-lausunto (some #(if (= (get-in % [:Lausunto :id])) %) lausuntotieto)
                  index-of-paivitettava (.indexOf lausuntotieto paivitettava-lausunto)
                  paivitetty-lausunto (assoc-in paivitettava-lausunto [:Lausunto :lausunto :lausunto :liite] (:liite (last (last a))))
                  paivitetty (assoc lausuntotieto index-of-paivitettava paivitetty-lausunto)]
              (assoc-in c [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :lausuntotieto] paivitetty))
            ) canonical statement-attachments))

(defn get-application-as-krysp [application lang submitted-application organization]
  (assert (= (:id application) (:id submitted-application)) "Not same application ids.")
  (let [sftp-user (:rakennus-ftp-user organization)
        rakennusvalvonta-directory "/rakennus"
        dynamic-part-of-outgoing-directory (str sftp-user rakennusvalvonta-directory)
        output-dir (str (:outgoing-directory env/config) "/" dynamic-part-of-outgoing-directory)
        _          (fs/mkdirs output-dir)
        file-name  (str output-dir "/" (:id application))
        tempfile   (file (str file-name ".tmp"))
        outfile    (file (str file-name ".xml"))
        canonical-without-attachments  (application-to-canonical application)
        fileserver-address (:fileserver-address env/config)
        begin-of-link (str fileserver-address rakennusvalvonta-directory "/")
        statement-attachments (get-statement-attachments-as-canonical application begin-of-link)
        attachments (get-attachments-as-canonical application begin-of-link)
        attachments-with-generated-pdfs (conj attachments
                                              {:Liite
                                               {:kuvaus "Application when submitted"
                                                :linkkiliitteeseen (str begin-of-link (get-submitted-filename (:id application)))
                                                :muokkausHetki (to-xml-datetime (:submitted application))
                                                :versionumero 1
                                                :tyyppi "hakemus_vireilletullessa"}}
                                              {:Liite
                                               {:kuvaus "Application when sent from Lupapiste"
                                                :linkkiliitteeseen (str begin-of-link (get-current-filename (:id application)))
                                                :muokkausHetki (to-xml-datetime (lupapalvelu.core/now))
                                                :versionumero 1
                                                :tyyppi "hakemus_taustajarjestelmaan_siirettaessa"}})
        canonical-with-statment-attachments  (add-statement-attchments canonical-without-attachments statement-attachments)
        canonical (assoc-in canonical-with-statment-attachments [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto] attachments-with-generated-pdfs)
        xml        (element-to-xml canonical rakennuslupa_to_krysp)
        xml-s (indent-str xml)]
    ;(clojure.pprint/pprint(:attachments application))
    ;(clojure.pprint/pprint canonical)
    ;(println xml-s)
    (validate (indent-str xml))
    (with-open [out-file (writer tempfile)]
      (emit xml out-file))
    (write-attachments attachments output-dir)
    ;(write-statement-attachments statement-attachments output-dir)

    (write-application-pdf-versions output-dir application submitted-application lang)
    (when (fs/exists? outfile) (fs/delete outfile))
    (fs/rename tempfile outfile)))

