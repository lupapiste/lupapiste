(ns lupapalvelu.xml.krysp.yleiset-alueet-mapping
  (:use  [clojure.data.xml]
         [clojure.java.io]
         [clojure.walk :only [prewalk]]
         [sade.util]
         [lupapalvelu.document.canonical-common :only [to-xml-datetime]]
         [lupapalvelu.document.yleiset-alueet-kaivulupa-canonical :only [application-to-canonical]]
         [lupapalvelu.xml.emit :only [element-to-xml]]
         [lupapalvelu.xml.krysp.validator :only [validate]])
  (:require [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [me.raynes.fs :as fs]
            #_[sade.env :as env]
            [lupapalvelu.mongo :as mongo])  ;; used in "write-attachments"
  #_(:import [java.util.zip ZipOutputStream ZipEntry]))


;; tags changed:
;; :kayntiosoite -> :kayntiosoitetieto
;; :postiosoite -> :postiosoitetieto
;; Added tag :Kayntiosoite after :kayntiosoitetieto
;; Added tag :Postiosoite after :postiosoitetieto
(def ^:private yritys-child-modified
  (prewalk
    (fn [m] (if (= (:tag m) :kayntiosoite)
              (assoc
                (assoc m :tag :kayntiosoitetieto)
                :child
                [{:tag :Kayntiosoite :child mapping-common/postiosoite-children-ns-yht}])
              m))
    (prewalk
      (fn [m] (if (= (:tag m) :postiosoite)
                (assoc
                  (assoc m :tag :postiosoitetieto)
                  :child
                  [{:tag :Postiosoite :child mapping-common/postiosoite-children-ns-yht}])
                m))
      mapping-common/yritys-child)))

(def maksaja [{:tag :henkilotieto
               :child [{:tag :Henkilo
                        :child mapping-common/henkilo-child-ns-yht}]}
              {:tag :yritystieto
               :child [{:tag :Yritys
                        :child mapping-common/yritys-child-ns-yht}]}
              {:tag :laskuviite}])

(def osapuoli [{:tag :henkilotieto
                :child [{:tag :Henkilo
                         :child mapping-common/henkilo-child-ns-yht}]}
               {:tag :yritystieto
                :child [{:tag :Yritys
                         :child yritys-child-modified}]}
               {:tag :rooliKoodi}])

(def vastuuhenkilo [{:tag :Vastuuhenkilo
                     :child [{:tag :sukunimi}
                             {:tag :etunimi}
                             {:tag :osoitetieto
                              :child [{:tag :osoite
                                       :child mapping-common/postiosoite-children-ns-yht}]}
                             {:tag :puhelinnumero}
                             {:tag :sahkopostiosoite}
                             {:tag :rooliKoodi}]}])

(def liitetieto [{:tag :Liite
                  :child [{:tag :kuvaus :ns "yht"}
                          {:tag :linkkiliitteeseen :ns "yht"}
                          {:tag :muokkausHetki :ns "yht"}
                          {:tag :versionumero :ns "yht"}
                          {:tag :tekija
                           :child osapuoli}
                          {:tag :tyyppi}
                          {:tag :metatietotieto
                           :child [{:tag :Metatieto
                                    :child [{:tag :metatietoArvo}
                                            {:tag :metatietoNimi}]}]}]}])

(def lausunto [{:tag :Lausunto
                :child [{:tag :viranomainen}
                        {:tag :pyyntoPvm }
                        {:tag :lausuntotieto
                         :child [{:tag :Lausunto
                                  :child [{:tag :viranomainen}
                                          {:tag :lausunto}
                                          {:tag :liitetieto
                                           :child liitetieto}
                                          {:tag :lausuntoPvm}
                                          {:tag :puoltotieto
                                           :child [{:tag :Puolto
                                                    :child [{:tag :puolto}]}]}]}]}]}])

(def kaivulupa_to_krysp
  {:tag :YleisetAlueet
   :ns "yak"
   :attr {:xsi:schemaLocation "http://www.opengis.net/gml http://schemas.opengis.net/gml/3.1.1/base/gml.xsd http://www.paikkatietopalvelu.fi/gml/yhteiset http://www.paikkatietopalvelu.fi/gml/yhteiset/2.0.9/yhteiset.xsd http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus/2.1.2/YleisenAlueenKaytonLupahakemus.xsd"
          :xmlns:yak "http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus"
          :xmlns:yht "http://www.paikkatietopalvelu.fi/gml/yhteiset"
          :xmlns:gml "http://www.opengis.net/gml"
          :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
;         :xmlns:xlink "http://www.w3.org/1999/xlink"           ;; TODO: Tarvitaanko tata?
         }

   :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
           {:tag :yleinenAlueAsiatieto
            :child [{:tag :Tyolupa
                     :child [{:tag :kasittelytietotieto
                              :child [{:tag :Kasittelytieto
                                       :child [{:tag :muutosHetki :ns "yht"}
                                               {:tag :hakemuksenTila :ns "yht"}
                                               {:tag :asiatunnus :ns "yht"}
;; TODO: Lisaa nama? Mita naihin tulisi?
;                                               {:tag :tilanMuutosHetki :ns "yht"}
;                                               {:tag :kunnanYhteyshenkilo :ns "yht"}
;                                               {:tag :paivaysPvm :ns "yht"}
;                                               {:tag :kasittelija}
                                               ]}]}
                             {:tag :alkuPvm}
                             {:tag :loppuPvm}
                             {:tag :sijaintitieto
                              :child [{:tag :Sijainti
                                       :child [{:tag :osoite :ns "yht"
                                                :child [{:tag :yksilointitieto}
                                                        {:tag :alkuHetki}
                                                        {:tag :osoitenimi
                                                         :child [{:tag :teksti}]}]}
                                               {:tag :piste :ns "yht"
                                                :child [{:tag :Point :ns "gml"
                                                         :child [{:tag :pos}]}]}]}]}
                             {:tag :osapuolitieto     ;; hakijan ja tyomaasta-vastaavan yritys-osa
                              :child [{:tag :Osapuoli
                                       :child osapuoli}]}
                             {:tag :vastuuhenkilotieto
                              :child vastuuhenkilo}   ;; tyomaasta-vastaavan henkilo-osa
                             {:tag :maksajatieto
                              :child [{:tag :Maksaja
                                       :child maksaja}]}
                             {:tag :liitetieto
                              :child liitetieto}
                             {:tag :lausuntotieto
                              :child lausunto}
                             {:tag :lupaAsianKuvaus}
                             {:tag :sijoituslupaviitetieto
                              :child [{:tag :Sijoituslupaviite
                                       :child [{:tag :vaadittuKytkin}
                                               {:tag :tunniste}]}]}
                             {:tag :kayttotarkoitus}
                             {:tag :johtoselvitysviitetieto
                              :child [{:tag :Johtoselvitysviite
                                       :child [{:tag :vaadittuKytkin
                                                ;:tag :tunniste
                                                }]}]}]}]}]})

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
        attachment-file-name (mapping-common/get-file-name-on-server file-id (get-in attachment [:latestVersion :filename]))
        link (str begin-of-link attachment-file-name)]
    {:Liite (get-Liite title link attachment type file-id)}))

(defn- get-statement-attachments-as-canonical [application begin-of-link]
;  (println "\n  get-statement-attachments-as-canonical, application: ")
;  (clojure.pprint/pprint application)
;  (println "\n  get-statement-attachments-as-canonical, begin-of-link: " begin-of-link "\n")

  (let [statement-attachments-by-id (group-by
                                      (fn-> :target :id keyword)
                                      (filter
                                        (fn-> :target :type (= "statement"))
                                        (:attachments application)))
;        _    (println "statement-attachments-by-id: " statement-attachments-by-id)
        canonical-attachments (for [[id attachments] statement-attachments-by-id]
                                {id (for [attachment attachments]
                                      #_(do
                                        (println "*****")
                                        (println "1) statement-attachments-by-id: " statement-attachments-by-id)
                                        (println "2) attachment: " attachment)
                                        (println "3) (-> attachment :target :type): " (-> attachment :target :type))
                                        (get-liite-for-lausunto attachment application begin-of-link))
                                      (get-liite-for-lausunto attachment application begin-of-link))})]

;    (println "\n  get-statement-attachments-as-canonical, canonical-attachments: ")
;    (clojure.pprint/pprint canonical-attachments)
;    (println "\n")

    (not-empty canonical-attachments)))

(defn- get-attachments-as-canonical [application begin-of-link]
;  (println "\n get-attachments-as-canonical, attachments: ")
;  (clojure.pprint/pprint (:attachments application))
;  (println "\n")
  (let [attachments (:attachments application)
        canonical-attachments (for [attachment attachments
                                    ;; TODO: Onko tyyppi "statement" vai "Lausunto"?
                                    :when (and (:latestVersion attachment) (not (= "statement" (-> attachment :target :type))))
                                    :let [type (get-in attachment [:type :type-id] )
                                          title (str (:title application) ": " type "-" (:id attachment))
                                          file-id (get-in attachment [:latestVersion :fileId])
                                          attachment-file-name (mapping-common/get-file-name-on-server file-id (get-in attachment [:latestVersion :filename]))
                                          link (str begin-of-link attachment-file-name)]]
                                {:Liite (get-Liite title link attachment type file-id)})]
    (not-empty canonical-attachments)))

(defn- write-attachments [attachments output-dir]
  (doseq [attachment attachments]
    (let [file-id (get-in attachment [:Liite :fileId])
          attachment-file (mongo/download file-id)
          content (:content attachment-file)
          attachment-file-name (str output-dir "/" (mapping-common/get-file-name-on-server file-id (:file-name attachment-file)))
          attachment-file (file attachment-file-name)
          ]
      (with-open [out (output-stream attachment-file)
                  in (content)]
        (copy in out)))))

(defn- write-statement-attachments [attachments output-dir]
  (let [f (for [fi attachments]
            (vals fi))
        files (reduce concat (reduce concat f))]
    (write-attachments files output-dir)))

(defn- add-statement-attachments [canonical statement-attachments]
;  (println "\n  add-statement-attachments, canonical: ")
;  (clojure.pprint/pprint canonical)
;  (println "\n  add-statement-attachments, statement-attachments: ")
;  (clojure.pprint/pprint statement-attachments)
;  (println "\n")

  ;; if we have no statement-attachments to add return the canonical map itself
  (if (empty? statement-attachments)
    canonical
    (reduce
      (fn [c a]
;        (println "\n *****  add-statement-attachments, c: ")
;        (clojure.pprint/pprint c)
;        (println "\n  add-statement-attachments, a: ")
;        (clojure.pprint/pprint a)
;        (println "*****\n")

        (let [lausuntotieto (get-in c [:YleisetAlueet :yleinenAlueAsiatieto :Tyolupa :lausuntotieto])
;              _  (do
;                   (println "\n *****  add-statement-attachments, lausuntotieto: ")
;                   (clojure.pprint/pprint lausuntotieto)
;                   (println "\n"))
              lausunto-id (name (first (keys a)))
              paivitettava-lausunto (some #(if (= (get-in % [:Lausunto :id]) lausunto-id) %) lausuntotieto)
              index-of-paivitettava (.indexOf lausuntotieto paivitettava-lausunto)
              paivitetty-lausunto (assoc-in
                                    paivitettava-lausunto
                                    [:Lausunto :lausuntotieto :Lausunto :liitetieto]
                                    ((keyword lausunto-id) a))
              paivitetty (assoc
                           lausuntotieto
                           index-of-paivitettava
                           paivitetty-lausunto)]
          (assoc-in c [:YleisetAlueet :yleinenAlueAsiatieto :Tyolupa :lausuntotieto] paivitetty)))
      canonical
      statement-attachments)))

;;
;;   **** TODO: Miten testataan attachmentteja ja statementteja? ****
;;              Tuleeko canonicaliin vain statement attachmentit, mutta ei tavallisia ("attachments")?
;;

(defn save-application-as-krysp [application lang submitted-application output-dir begin-of-link]
  (let [file-name  (str output-dir "/" (:id application))
        tempfile   (file (str file-name ".tmp"))
        outfile    (file (str file-name ".xml"))
        canonical-without-attachments  (application-to-canonical application lang)
        attachments (get-attachments-as-canonical application begin-of-link)
        statement-attachments (get-statement-attachments-as-canonical application begin-of-link)
        canonical-with-statement-attachments (add-statement-attachments
                                               canonical-without-attachments
                                               statement-attachments)
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:YleisetAlueet :yleinenAlueAsiatieto :Tyolupa :liitetieto]
                    attachments)
        xml (element-to-xml canonical #_canonical-without-attachments kaivulupa_to_krysp)
        xml-s (indent-str xml)]

    ;(clojure.pprint/pprint (:attachments application))
    ;(clojure.pprint/pprint canonical-with-statement-attachments)
    ;(println xml-s)

;    (println "\n attachments: ")
;    (clojure.pprint/pprint attachments)
;    (println "\n")

;    (println "\n statement-attachments: ")
;    (clojure.pprint/pprint statement-attachments)
;    (println "\n")

;    (println "\n canonical-with-statement-attachments: ")
;    (clojure.pprint/pprint canonical-with-statement-attachments)

;    (println "\n save-application-as-krysp, canonical: ")
;    (clojure.pprint/pprint canonical)

;    (println "\n canonical-with-statement-attachments's liitetieto: "
;      (:liitetieto canonical-with-statement-attachments))

;    (println "\n")

    (validate xml-s)
    (fs/mkdirs output-dir)  ;; this has to be called before calling with-open below
    (with-open [out-file-stream (writer tempfile)]
      (emit xml out-file-stream))

    (write-attachments attachments output-dir)   ;;TODO: Tehdaanko attachmenteilla muuta kuin kirjoitetaan levylle?
    (write-statement-attachments statement-attachments output-dir)

    (when (fs/exists? outfile) (fs/delete outfile))
    (fs/rename tempfile outfile)))


