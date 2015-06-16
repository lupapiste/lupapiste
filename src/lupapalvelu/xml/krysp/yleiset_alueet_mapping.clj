(ns lupapalvelu.xml.krysp.yleiset-alueet-mapping
  (:require [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [sade.core :refer :all]
            [clojure.walk :as walk]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.canonical-common :refer [ya-operation-type-to-schema-name-key]]
            [lupapalvelu.document.yleiset-alueet-canonical :as ya-canonical]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.xml.disk-writer :as writer]))

;; Tags changed in "yritys-child-modified":
;; :kayntiosoite -> :kayntiosoitetieto
;; :postiosoite -> :postiosoitetieto
;; Added tag :Kayntiosoite after :kayntiosoitetieto
;; Added tag :Postiosoite after :postiosoitetieto
(def- yritys-child-modified_211
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
      mapping-common/yritys-child_211)))

(def maksaja [{:tag :henkilotieto
               :child [{:tag :Henkilo
                        :child mapping-common/henkilo-child-ns-yht}]}
              {:tag :yritystieto
               :child [{:tag :Yritys
                        :child mapping-common/yritys-child-ns-yht_211}]}
              {:tag :laskuviite}])

(def osapuoli [{:tag :henkilotieto
                :child [{:tag :Henkilo
                         :child mapping-common/henkilo-child-ns-yht}]}
               {:tag :yritystieto
                :child [{:tag :Yritys
                         :child yritys-child-modified_211}]}
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

(defn get-yleiset-alueet-krysp-mapping [lupa-name-key krysp-version]
  {:pre [krysp-version]}
  (let [ya_to_krysp_2_1_2 {:tag :YleisetAlueet
                           :ns "yak"
                           :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation "yleisenalueenkaytonlupahakemus" "2.1.2")
                                         :xmlns:yak "http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus"}
                                        mapping-common/common-namespaces)

                           :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
                                   {:tag :yleinenAlueAsiatieto
                                    :child [{:tag lupa-name-key
                                             :child [{:tag :kasittelytietotieto
                                                      :child kasittelytieto}
                                                     {:tag :luvanTunnisteTiedot
                                                      :child [mapping-common/lupatunnus]}
                                                     {:tag :alkuPvm}
                                                     {:tag :loppuPvm}
                                                     (mapping-common/sijaintitieto)
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
                                                                        }]}]}]}]}]}
        ya_to_krysp_2_1_3 (-> ya_to_krysp_2_1_2 (assoc-in [:attr :xsi:schemaLocation]
                                                          (mapping-common/schemalocation "yleisenalueenkaytonlupahakemus" "2.1.3"))
                            (update-in [:child] mapping-common/update-child-element
                                       [:yleinenAlueAsiatieto lupa-name-key :maksajatieto :Maksaja]
                                       {:tag :Maksaja
                                        :child mapping-common/maksajatype-children_213})
                            (update-in [:child] mapping-common/update-child-element
                                       [:yleinenAlueAsiatieto lupa-name-key :osapuolitieto :Osapuoli :yritystieto :Yritys ]
                                       {:tag :Yritys :child mapping-common/yritys-child_213}))]



    (case (name krysp-version)
    "2.1.2" ya_to_krysp_2_1_2
    "2.1.3" ya_to_krysp_2_1_3
    (throw (IllegalArgumentException. (str "Unsupported KRYSP version " krysp-version))))))

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


(defn- map-kayttotarkoitus [canonical lupa-name-key]
  (update-in canonical [:YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :kayttotarkoitus]
             (fn [kayttotarkoitus]
               (case kayttotarkoitus
                 "vesihuoltoverkostoty\u00f6" "kaivu- tai katuty\u00f6lupa"
                 "muu" "kaivu- tai katuty\u00f6lupa"
                 "kaukol\u00e4mp\u00f6verkostoty\u00f6" "kaivu- tai katuty\u00f6lupa"
                 "tietoliikenneverkostoty\u00f6" "kaivu- tai katuty\u00f6lupa"
                 "verkoston liitosty\u00f6" "kaivu- tai katuty\u00f6lupa"
                 kayttotarkoitus))))

(defn- map-enums-212 [canonical lupa-name-key]
  (map-kayttotarkoitus canonical lupa-name-key))

(defn- map-enums
  "Map enumerations in canonical into values supperted by given KRYSP version"
  [canonical lupa-name-key krysp-version]
  {:pre [krysp-version]}
  (case (name krysp-version)
    "2.1.2" (map-enums-212 canonical lupa-name-key)
    canonical ; default: no conversions
    ))

(defn yleisetalueet-element-to-xml [canonical lupa-name-key krysp-version]
  (element-to-xml (map-enums canonical lupa-name-key krysp-version) (get-yleiset-alueet-krysp-mapping lupa-name-key krysp-version)))

(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent.
   3rd parameter (submitted-application) is not used on YA applications."
  [application lang submitted-application krysp-version output-dir begin-of-link]
  (let [lupa-name-key (ya-operation-type-to-schema-name-key
                        (-> application :primaryOperation :name keyword))
        canonical-without-attachments (ya-canonical/application-to-canonical application lang)
        attachments-canonical (mapping-common/get-attachments-as-canonical application begin-of-link)
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
                    attachments-canonical)
        xml (yleisetalueet-element-to-xml canonical lupa-name-key krysp-version)
        all-canonical-attachments (concat attachments-canonical (mapping-common/flatten-statement-attachments statement-attachments))
        attachments-for-write (mapping-common/attachment-details-from-canonical all-canonical-attachments)]

    (writer/write-to-disk
      application
      attachments-for-write
      xml
      krysp-version
      output-dir
      submitted-application
      lang)))

(permit/register-function permit/YA :app-krysp-mapper save-application-as-krysp)

(defn save-jatkoaika-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [application lang organization krysp-version output-dir begin-of-link]
    (let [lupa-name-key (ya-operation-type-to-schema-name-key
                          (or
                            (-> application :linkPermitData first :operation keyword)
                            :ya-katulupa-vesi-ja-viemarityot))
          canonical (ya-canonical/jatkoaika-to-canonical application lang)
          xml (yleisetalueet-element-to-xml canonical lupa-name-key krysp-version)]

      (writer/write-to-disk application nil xml krysp-version output-dir)))
