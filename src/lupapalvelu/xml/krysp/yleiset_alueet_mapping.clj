(ns lupapalvelu.xml.krysp.yleiset-alueet-mapping
  (:require [me.raynes.fs :as fs]
            [sade.util :refer :all]
            [lupapalvelu.core :refer [now]]
            [lupapalvelu.mongo :as mongo]
            [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.canonical-common :refer [to-xml-date to-xml-datetime ya-operation-type-to-schema-name-key]]
            [lupapalvelu.document.yleiset-alueet-canonical :refer [application-to-canonical]]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.xml.krysp.validator :refer [validate]]))

;; Tags changed in "yritys-child-modified":
;; :kayntiosoite -> :kayntiosoitetieto
;; :postiosoite -> :postiosoitetieto
;; Added tag :Kayntiosoite after :kayntiosoitetieto
;; Added tag :Postiosoite after :postiosoitetieto
(def ^:private yritys-child-modified
  (walk/prewalk
    (fn [m] (if (= (:tag m) :kayntiosoite)
              (assoc
                (assoc m :tag :kayntiosoitetieto)
                :child
                [{:tag :Kayntiosoite :child mapping-common/postiosoite-children-ns-yht}])
              m))
    (walk/prewalk
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

(defn get-yleiset-alueet-krysp-mapping [lupa-name-key]
  {:tag :YleisetAlueet
   :ns "yak"
   :attr {:xsi:schemaLocation
          "http://www.opengis.net/gml http://schemas.opengis.net/gml/3.1.1/base/gml.xsd
           http://www.paikkatietopalvelu.fi/gml/yhteiset
           http://www.paikkatietopalvelu.fi/gml/yhteiset/2.1.0/yhteiset.xsd
           http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus
           http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus/2.1.2/YleisenAlueenKaytonLupahakemus.xsd"
          :xmlns:yak "http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus"
          :xmlns:yht "http://www.paikkatietopalvelu.fi/gml/yhteiset"
          :xmlns:gml "http://www.opengis.net/gml"
          :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"}

   :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
           {:tag :yleinenAlueAsiatieto
            :child [{:tag lupa-name-key
                     :child [{:tag :kasittelytietotieto
                              :child [{:tag :Kasittelytieto
                                       :child [{:tag :muutosHetki :ns "yht"}
                                               {:tag :hakemuksenTila :ns "yht"}
                                               {:tag :asiatunnus :ns "yht"}
;                                               {:tag :tilanMuutosHetki :ns "yht"}
;                                               {:tag :kunnanYhteyshenkilo :ns "yht"}
                                               {:tag :paivaysPvm :ns "yht"}
                                               {:tag :kasittelija
                                                :child [{:tag :henkilotieto
                                                         :child [{:tag :Henkilo
                                                                  :child [{:tag :nimi :ns "yht"
                                                                           :child [{:tag :etunimi}
                                                                                   {:tag :sukunimi}]}]}]}]}]}]}
                             {:tag :luvanTunnisteTiedot
                              :child [mapping-common/lupatunnus]}
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
                             {:tag :lupakohtainenLisatietotieto
                              :child [{:tag :LupakohtainenLisatieto
                                       :child [{:tag :selitysteksti :ns "yht"}
                                               {:tag :arvo :ns "yht"}]}]}
                             {:tag :kayttojaksotieto
                              :child [{:tag :Kayttojakso
                                       :child [{:tag :alkuHetki :ns "yht"}
                                               {:tag :loppuHetki :ns "yht"}]}]}
                             {:tag :toimintajaksotieto
                              :child [{:tag :Toimintajakso
                                       :child [{:tag :alkuHetki :ns "yht"}
                                               {:tag :loppuHetki :ns "yht"}]}]}
                             {:tag :valmistumisilmoitusPvm}
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
        attachment-file-name (mapping-common/get-file-name-on-server
                               file-id
                               (get-in attachment [:latestVersion :filename]))
        link (str begin-of-link attachment-file-name)]
    {:Liite (get-Liite title link attachment type file-id)}))

(defn- get-statement-attachments-as-canonical [application begin-of-link allowed-statement-ids]
  (let [statement-attachments-by-id (group-by
                                      (fn-> :target :id keyword)
                                      (filter
                                        (fn-> :target :type (= "statement"))
                                        (:attachments application)))
        canonical-attachments (for [id allowed-statement-ids]
                                {(keyword id) (for [attachment ((keyword id) statement-attachments-by-id)]
                                                (get-liite-for-lausunto attachment application begin-of-link))})]
    (not-empty canonical-attachments)))


(defn- get-attachments-as-canonical [application begin-of-link]
  (let [attachments (:attachments application)
        canonical-attachments (for [attachment attachments
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
          attachment-file (io/file attachment-file-name)
          ]
      (with-open [out (io/output-stream attachment-file)
                  in (content)]
        (io/copy in out)))))

(defn- write-statement-attachments [attachments output-dir]
  (let [f (for [fi attachments]
            (vals fi))
        files (reduce concat (reduce concat f))]
    (write-attachments files output-dir)))

(defn- add-statement-attachments [lupa-name-key canonical statement-attachments]
  ;; if we have no statement-attachments to add return the canonical map itself
  (if (empty? statement-attachments)
    canonical
    (reduce
      (fn [c a]
        (let [lausuntotieto (get-in c [:YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :lausuntotieto])
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
          (assoc-in c [:YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :lausuntotieto] paivitetty)))
      canonical
      statement-attachments)))

(defn save-application-as-krysp [application lang submitted-application output-dir begin-of-link]
  (let [lupa-name-key ((-> application :operations first :name keyword) ya-operation-type-to-schema-name-key)
        file-name  (str output-dir "/" (:id application))
        tempfile   (io/file (str file-name ".tmp"))
        outfile    (io/file (str file-name ".xml"))
        canonical-without-attachments  (application-to-canonical application lang)
        attachments (get-attachments-as-canonical application begin-of-link)
        statement-given-ids (mapping-common/statements-ids-with-status
                              (get-in canonical-without-attachments
                                [:YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :lausuntotieto]))
        statement-attachments (get-statement-attachments-as-canonical application begin-of-link statement-given-ids)
        canonical-with-statement-attachments (add-statement-attachments
                                               lupa-name-key
                                               canonical-without-attachments
                                               statement-attachments)
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :liitetieto]
                    attachments)
        xml (element-to-xml canonical (get-yleiset-alueet-krysp-mapping lupa-name-key))
        xml-s (xml/indent-str xml)]


;    (println "\n lupa-name-key: ")
;    (clojure.pprint/pprint lupa-name-key)
;    (println "\n")

;    (println "output-dir: " output-dir)

;    (clojure.pprint/pprint (:attachments application))
;    (clojure.pprint/pprint canonical-with-statement-attachments)
;    (println xml-s)

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
    (fs/mkdirs output-dir)  ;; this has to be called before calling "with-open" below
    (with-open [out-file-stream (io/writer tempfile)]
      (xml/emit xml out-file-stream))

    (write-attachments attachments output-dir)
    (write-statement-attachments statement-attachments output-dir)

    (when (fs/exists? outfile) (fs/delete outfile))
    (fs/rename tempfile outfile)))

(permit/register-mapper permit/YA :app-krysp-mapper save-application-as-krysp)
