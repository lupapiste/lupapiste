(ns lupapalvelu.xml.krysp.yleiset-alueet-mapping
  (:use  [clojure.data.xml]
         [clojure.java.io]
         [clojure.walk :only [prewalk]]
         [sade.util]
         [lupapalvelu.document.canonical-common :only [to-xml-datetime]]
         [lupapalvelu.document.yleiset-alueet-kaivulupa-canonical :only [application-to-canonical]]
         [lupapalvelu.xml.emit :only [element-to-xml]]
         [lupapalvelu.xml.krysp.validator :only [validate]]
         [lupapalvelu.attachment :only [encode-filename]])
  (:require [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [me.raynes.fs :as fs]
            #_[sade.env :as env]
            [lupapalvelu.ke6666 :as ke6666] ;; used in "write-application-pdf-versions"
            [lupapalvelu.mongo :as mongo])  ;; used in "write-attachments"
  #_(:import [java.util.zip ZipOutputStream ZipEntry]))


;; the namespace "yht" added to the base level of all maps in the given vector
(defn- add-yht-namespace-to-vectors-maps [vector-with-maps]
  (into [] (map (fn [m] (assoc m :ns "yht")) vector-with-maps)))

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
                [{:tag :Kayntiosoite :child (add-yht-namespace-to-vectors-maps mapping-common/postiosoite-children)}])
              m))
    (prewalk
      (fn [m] (if (= (:tag m) :postiosoite)
                (assoc
                  (assoc m :tag :postiosoitetieto)
                  :child
                  [{:tag :Postiosoite :child (add-yht-namespace-to-vectors-maps mapping-common/postiosoite-children)}])
                m))
      mapping-common/yritys-child)))


(def maksaja [{:tag :henkilotieto
               :child [{:tag :Henkilo
                        :child (add-yht-namespace-to-vectors-maps mapping-common/henkilo-child)}]}
              {:tag :yritystieto
               :child [{:tag :Yritys
                        :child
                        (prewalk
                          (fn [m] (if (or (= (:tag m) :postiosoite) (= (:tag m) :kayntiosoite))
                                    (assoc m :child (add-yht-namespace-to-vectors-maps mapping-common/postiosoite-children))))
                          mapping-common/yritys-child)}]}
              {:tag :laskuviite}])

(def toimintajakso [{:tag :alkuHetki :ns "yht"}
                    {:tag :loppuHetki :ns "yht"}])

(def osapuoli [{:tag :Osapuoli
                :child [{:tag :henkilotieto
                         :child [{:tag :Henkilo
                                  :child (add-yht-namespace-to-vectors-maps mapping-common/henkilo-child)}]}
                        {:tag :yritystieto
                         :child [{:tag :Yritys
                                  :child yritys-child-modified}]}
                        {:tag :rooliKoodi}]}])

(def vastuuhenkilo [{:tag :Vastuuhenkilo
                     :child [{:tag :etunimi}
                             {:tag :sukunimi}
                             {:tag :osoitetieto
                              :child [{:tag :osoite
                                       :child (add-yht-namespace-to-vectors-maps mapping-common/postiosoite-children)}]}
                             {:tag :puhelinnumero}
                             {:tag :sahkopostiosoite}
                             {:tag :rooliKoodi}]}])


;;  - Mista nakee namespacen? Onko "yak"? Onko muut attrit oikein? Mista tulevat "xlink" ja "xsi"?
(def kaivulupa_to_krysp
  {:tag :YleisetAlueet
   :ns "yak"
   :attr {:xsi:schemaLocation "http://www.paikkatietopalvelu.fi/gml/yhteiset
                               http://www.paikkatietopalvelu.fi/gml/yhteiset/2.0.9/yhteiset.xsd
                               http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus
                               http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus/2.1.1/YleisenAlueenKaytonLupahakemus.xsd"
         :xmlns:yak "http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus"
         :xmlns:yht "http://www.paikkatietopalvelu.fi/gml/yhteiset"
         ;; TODO: Tarvitaanko naita kahta?
         :xmlns:xlink "http://www.w3.org/1999/xlink"
         :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
         }

   :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
           {:tag :yleinenAlueAsiatieto
            :child [{:tag :Tyolupa
                     :child [{:tag :kayttotarkoitus :ns "yht"}
                             {:tag :johtoselvitysviitetieto
                              :child [{:tag :Johtoselvitysviite
                                       :child [{:tag :vaadittuKytkin
                                                ;:tag :tunniste
                                                }]}]}
                             {:tag :maksajatieto
                              :child [{:tag :Maksaja
                                       :child maksaja}]}
                             {:tag :toimintajaksotieto
                              :child [{:tag :Toimintajakso
                                       :child toimintajakso}]}
                             {:tag :lupaAsianKuvaus}
                             {:tag :sijoituslupaviitetieto
                              :child [{:tag :Sijoituslupaviite
                                       :child [{:tag :vaadittuKytkin}
                                               {:tag :tunniste}]}]}
                             {:tag :osapuolitieto
                              ;; hakija ja tyomaasta-vastaava (yritys-osa)
                              :child osapuoli}
                             {:tag :vastuuhenkilotieto
                              :child vastuuhenkilo}]  ;; tyomaasta-vastaava (henkilo-osa)
                     }]}]})

(defn save-application-as-krysp [application lang submitted-application output-dir begin-of-link]

  (let [file-name  (str output-dir "/" (:id application))
        tempfile   (file (str file-name ".tmp"))
        outfile    (file (str file-name ".xml"))
        canonical-without-attachments  (application-to-canonical application lang)
;        statement-attachments (get-statement-attachments-as-canonical application begin-of-link)
;        attachments (get-attachments-as-canonical application begin-of-link)
;        attachments-with-generated-pdfs (conj attachments
;                                              {:Liite
;                                               {:kuvaus "Application when submitted"
;                                                :linkkiliitteeseen (str begin-of-link (get-submitted-filename (:id application)))
;                                                :muokkausHetki (to-xml-datetime (:submitted application))
;                                                :versionumero 1
;                                                :tyyppi "hakemus_vireilletullessa"}}
;                                              {:Liite
;                                               {:kuvaus "Application when sent from Lupapiste"
;                                                :linkkiliitteeseen (str begin-of-link (get-current-filename (:id application)))
;                                                :muokkausHetki (to-xml-datetime (lupapalvelu.core/now))
;                                                :versionumero 1
;                                                :tyyppi "hakemus_taustajarjestelmaan_siirettaessa"}})
;        canonical-with-statement-attachments  (add-statement-attachments canonical-without-attachments statement-attachments)
;        canonical (assoc-in canonical-with-statement-attachments [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto] attachments-with-generated-pdfs)
        xml (element-to-xml #_canonical canonical-without-attachments kaivulupa_to_krysp)
        xml-s (indent-str xml)]
    ;(clojure.pprint/pprint(:attachments application))
    ;(clojure.pprint/pprint canonical-with-statement-attachments)
    ;(println xml-s)
    (validate xml-s)
    (with-open [out-file-stream (writer tempfile)]
      (emit xml out-file-stream))
    (fs/mkdirs output-dir)
;    (write-attachments attachments output-dir)
;    (write-statement-attachments statement-attachments output-dir)

;    (write-application-pdf-versions output-dir application submitted-application lang)

    (when (fs/exists? outfile) (fs/delete outfile))
    (fs/rename tempfile outfile))

  )
