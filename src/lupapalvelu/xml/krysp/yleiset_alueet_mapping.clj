(ns lupapalvelu.xml.krysp.yleiset-alueet-mapping
  (:require [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [sade.util :refer :all]
            [lupapalvelu.core :refer [now]]
            [clojure.walk :as walk]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.canonical-common :refer [ya-operation-type-to-schema-name-key]]
            [lupapalvelu.document.yleiset-alueet-canonical :as ya-canonical]
            [lupapalvelu.xml.emit :refer [element-to-xml]]))

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

(def kasittelytieto [{:tag :Kasittelytieto
                      :child [{:tag :muutosHetki :ns "yht"}
                              {:tag :hakemuksenTila :ns "yht"}
                              {:tag :asiatunnus :ns "yht"}
                              {:tag :paivaysPvm :ns "yht"}
                              {:tag :kasittelija
                               :child [{:tag :henkilotieto
                                        :child [{:tag :Henkilo
                                                 :child [{:tag :nimi :ns "yht"
                                                          :child [{:tag :etunimi}
                                                                  {:tag :sukunimi}]}]}]}]}]}])

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
                              :child kasittelytieto}
                             {:tag :luvanTunnisteTiedot
                              :child [mapping-common/lupatunnus]}
                             {:tag :alkuPvm}
                             {:tag :loppuPvm}
                             {:tag :sijaintitieto
                              :child [mapping-common/sijantiType]}
                             {:tag :pintaala}
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
                             {:tag :lisaaikatieto
                              :child [{:tag :Lisaaika
                                       :child [{:tag :alkuPvm :ns "yht"}
                                               {:tag :loppuPvm :ns "yht"}
                                               {:tag :perustelu}]}]}
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

(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent.
   3rd parameter (submitted-application) is not used on YA applications."
  [application lang _ krysp-version output-dir begin-of-link]
  (let [lupa-name-key (ya-operation-type-to-schema-name-key
                        (-> application :operations first :name keyword))
        canonical-without-attachments (ya-canonical/application-to-canonical application lang)
        attachments (mapping-common/get-attachments-as-canonical application begin-of-link)
        statement-given-ids (mapping-common/statements-ids-with-status
                              (get-in canonical-without-attachments
                                [:YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :lausuntotieto]))
        statement-attachments (mapping-common/get-statement-attachments-as-canonical application begin-of-link statement-given-ids)
        canonical-with-statement-attachments (add-statement-attachments
                                               lupa-name-key
                                               canonical-without-attachments
                                               statement-attachments)
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :liitetieto]
                    attachments)
        xml (element-to-xml canonical (get-yleiset-alueet-krysp-mapping lupa-name-key))]

    (mapping-common/write-to-disk application attachments statement-attachments xml krysp-version output-dir)))

(permit/register-function permit/YA :app-krysp-mapper save-application-as-krysp)

(defn save-jatkoaika-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [application lang organization krysp-version output-dir begin-of-link]
    (let [lupa-name-key (ya-operation-type-to-schema-name-key
                          (or
                            (-> application :linkPermitData first :operation keyword)
                            :ya-katulupa-vesi-ja-viemarityot))
          canonical (ya-canonical/jatkoaika-to-canonical application lang)
          xml (element-to-xml canonical (get-yleiset-alueet-krysp-mapping lupa-name-key))]

      (mapping-common/write-to-disk application nil nil xml krysp-version output-dir)))
