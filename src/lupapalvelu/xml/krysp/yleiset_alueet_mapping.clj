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

(def osapuoli [{:tag :Osapuoli
                :child [{:tag :henkilotieto
                         :child [{:tag :Henkilo
                                  :child mapping-common/henkilo-child-ns-yht}]}
                        {:tag :yritystieto
                         :child [{:tag :Yritys
                                  :child yritys-child-modified}]}
                        {:tag :rooliKoodi}]}])

(def vastuuhenkilo [{:tag :Vastuuhenkilo
                     :child [{:tag :sukunimi}
                             {:tag :etunimi}
                             {:tag :osoitetieto
                              :child [{:tag :osoite
                                       :child mapping-common/postiosoite-children-ns-yht}]}
                             {:tag :puhelinnumero}
                             {:tag :sahkopostiosoite}
                             {:tag :rooliKoodi}]}])


;;  - Mista nakee namespacen? Onko "yak"? Onko muut attrit oikein? Mista tulevat "xlink" ja "xsi"?
(def kaivulupa_to_krysp
  {:tag :YleisetAlueet
   :ns "yak"
   :attr {:xsi:schemaLocation "http://www.opengis.net/gml http://schemas.opengis.net/gml/3.1.1/base/gml.xsd http://www.paikkatietopalvelu.fi/gml/yhteiset http://www.paikkatietopalvelu.fi/gml/yhteiset/2.0.9/yhteiset.xsd http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus/2.1.1/YleisenAlueenKaytonLupahakemus.xsd"
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
;; TODO: Lisaa nama?
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
                             {:tag :osapuolitieto
                              ;; hakija ja tyomaasta-vastaava (yritys-osa)
                              :child osapuoli}
                             {:tag :vastuuhenkilotieto
                              :child vastuuhenkilo}   ;; tyomaasta-vastaava (henkilo-osa)
                             {:tag :maksajatieto
                              :child [{:tag :Maksaja
                                       :child maksaja}]}
;; TODO: Lisaa tama?
                             {:tag :paatostieto
                              :child [{:tag :Paatos
                                       :child [{:tag :takuuaikaPaivat}]}]}
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
                                                }]}]}]
                     }]}]})

(defn save-application-as-krysp [application lang submitted-application output-dir begin-of-link]
  (let [file-name  (str output-dir "/" (:_id application))
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
    (fs/mkdirs output-dir)  ;; this has to be called before calling with-open below
    (with-open [out-file-stream (writer tempfile)]
      (emit xml out-file-stream))
;    (write-attachments attachments output-dir)
;    (write-statement-attachments statement-attachments output-dir)

;    (write-application-pdf-versions output-dir application submitted-application lang)

    (when (fs/exists? outfile) (fs/delete outfile))
    (fs/rename tempfile outfile))

  )
