(ns lupapalvelu.backing-system.krysp.yleiset-alueet-mapping
  (:require [lupapalvelu.backing-system.krysp.mapping-common :as mapping-common]
            [sade.core :refer :all]
            [sade.util :as util]
            [clojure.walk :as walk]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.attachments-canonical :as attachments-canon]
            [lupapalvelu.document.canonical-common :as common]
            [lupapalvelu.document.yleiset-alueet-canonical :as ya-canonical]
            [lupapalvelu.xml.emit :refer [element-to-xml]]))

;; Tags changed in "yritys-child-modified":
;; :kayntiosoite -> :kayntiosoitetieto
;; :postiosoite -> :postiosoitetieto
;; Added tag :Kayntiosoite after :kayntiosoitetieto
;; Added tag :Postiosoite after :postiosoitetieto
(def- yritys-child-modified_211
  (walk/prewalk
    (fn [m] (if (= (:tag m) :kayntiosoite)
              (assoc m
                :tag :kayntiosoitetieto
                :child [{:tag :Kayntiosoite :child mapping-common/postiosoite-children-ns-yht}])
              m))
    (walk/prewalk
      (fn [m] (if (= (:tag m) :postiosoite)
                (assoc m
                  :tag :postiosoitetieto
                  :child [{:tag :Postiosoite :child mapping-common/postiosoite-children-ns-yht}])
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

(def osapuoli-215
  (-> osapuoli
    (mapping-common/update-child-element [:henkilotieto :Henkilo] {:tag :Henkilo :child mapping-common/henkilo-child-ns-yht-215})
    (mapping-common/update-child-element [:yritystieto :Yritys]   {:tag :Yritys :child mapping-common/yritys-child_215})))

(def osapuoli-219
  (-> osapuoli
      (mapping-common/update-child-element [:henkilotieto :Henkilo] {:tag :Henkilo :child mapping-common/henkilo-child-ns-yht-219})
      (mapping-common/update-child-element [:yritystieto :Yritys]   {:tag :Yritys :child mapping-common/yritys-child_219})))

(def vastuuhenkilo [{:tag :Vastuuhenkilo
                     :child [{:tag :sukunimi}
                             {:tag :etunimi}
                             {:tag :osoitetieto
                              :child [{:tag :osoite
                                       :child mapping-common/postiosoite-children-ns-yht}]}
                             {:tag :puhelinnumero}
                             {:tag :sahkopostiosoite}
                             {:tag :rooliKoodi}]}])

(def vastuuhenkilo-215
  (mapping-common/update-child-element vastuuhenkilo
    [:osoitetieto :osoite]
    {:tag :osoite :child mapping-common/postiosoite-children-ns-yht-215}))

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

(def liitetieto-215
  (mapping-common/update-child-element liitetieto
                                       [:Liite :tekija]
                                       {:tag :tekija :child osapuoli-215}))

(def liitetieto-219
  (mapping-common/update-child-element liitetieto
                                       [:Liite :tekija]
                                       {:tag :tekija :child osapuoli-219}))

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

(def lausunto-215
  (mapping-common/update-child-element lausunto
    [:Lausunto :lausuntotieto :Lausunto :liitetieto]
    {:tag :liitetieto :child liitetieto-215}))

(def lausunto-219
  (mapping-common/update-child-element lausunto
                                       [:Lausunto :lausuntotieto :Lausunto :liitetieto]
                                       {:tag :liitetieto :child liitetieto-219}))

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

(def- katselmus {:tag :Katselmus
                 :child [{:tag :muuTunnustieto
                          :child [{:tag :MuuTunnus
                                   :child [{:tag :tunnus :ns "yht"}
                                           {:tag :sovellus :ns "yht"}]}]}
                         {:tag :pitoPvm}
                         {:tag :pitaja}
                         {:tag :katselmuksenLaji}
                         {:tag :vaadittuLupaehtonaKytkin}
                         {:tag :huomautustieto
                          :child [{:tag :Huomautus
                                   :child [{:tag :kuvaus}
                                           {:tag :maaraAika}
                                           {:tag :toteamisHetki}
                                           {:tag :toteaja}]}]}
                         {:tag :katselmuspoytakirja :child mapping-common/liite-children_211}
                         {:tag :tarkastuksenTaiKatselmuksenNimi}
                         {:tag :lasnaolijat}
                         {:tag :poikkeamat}]} )

(def- case-body
  [{:tag :kasittelytietotieto
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
   {:tag :paatostieto
    :child [{:tag :Paatos
            :child [{:tag :takuuaikaPaivat}
                    {:tag :lupaehdotJaMaaraykset}
                    {:tag :paatoslinkki}
                    {:tag :paatosdokumentinPvm}
                    {:tag :liitetieto :child liitetieto}]}]}
   {:tag :katselmustieto
    :child [katselmus]}
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
                      }]}]}])


(defn get-yleiset-alueet-krysp-mapping [lupa-name-key krysp-version]
  {:pre [lupa-name-key krysp-version]}
  (let [ya_to_krysp_2_1_2 {:tag :YleisetAlueet
                           :ns "yak"
                           :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :YA "2.1.2")
                                         :xmlns:yak "http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus"}
                                        (mapping-common/common-namespaces :YA "2.1.2"))
                           :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
                                   {:tag :yleinenAlueAsiatieto
                                    :child [{:tag lupa-name-key :child (remove #(= :katselmustieto (:tag %)) case-body)}]}]}
        ya_to_krysp_2_1_3 (-> ya_to_krysp_2_1_2
                              (assoc-in [:attr :xsi:schemaLocation]
                                        (mapping-common/schemalocation :YA "2.1.3"))
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key]
                                         {:tag lupa-name-key :child case-body})
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :maksajatieto :Maksaja]
                                         {:tag :Maksaja :child mapping-common/maksajatype-children_213})
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :osapuolitieto :Osapuoli :yritystieto :Yritys]
                                         {:tag :Yritys :child mapping-common/yritys-child_213})
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :katselmustieto :Katselmus :katselmuspoytakirja]
                                         {:tag :katselmuspoytakirja :child mapping-common/liite-children_213}))
        ya_to_krysp_2_2_0 (-> ya_to_krysp_2_1_3
                              (assoc-in [:attr :xsi:schemaLocation]
                                        (mapping-common/schemalocation :YA "2.2.0"))
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :sijaintitieto]
                                         {:tag :sijaintitieto :child [mapping-common/sijantiType_215]})
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :maksajatieto :Maksaja]
                                         {:tag :Maksaja :child mapping-common/maksajatype-children_215})
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :osapuolitieto :Osapuoli]
                                         {:tag :Osapuoli :child osapuoli-215})
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :lausuntotieto]
                                         {:tag :lausuntotieto :child lausunto-215})
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :liitetieto]
                                         {:tag :liitetieto :child liitetieto-215})
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :paatostieto :Paatos :liitetieto]
                                         {:tag :liitetieto :child liitetieto-215})
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :vastuuhenkilotieto]
                                         {:tag :vastuuhenkilotieto :child vastuuhenkilo-215}))
        ya_to_krysp_2_2_1 (-> ya_to_krysp_2_2_0
                              (assoc-in [:attr :xsi:schemaLocation]
                                        (mapping-common/schemalocation :YA "2.2.1"))
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :katselmustieto :Katselmus :katselmuspoytakirja]
                                         {:tag :katselmuspoytakirja :child mapping-common/liite-children_216}))
        ya_to_krysp_2_2_3 (-> ya_to_krysp_2_2_1
                              (update :attr merge
                                      {:xsi:schemaLocation (mapping-common/schemalocation :YA "2.2.3")
                                       :xmlns:yak "http://www.kuntatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus"}
                                      (mapping-common/common-namespaces :YA "2.2.3")))
        ya_to_krysp_2_2_4 (-> ya_to_krysp_2_2_3
                              (assoc-in [:attr :xsi:schemaLocation]
                                        (mapping-common/schemalocation :YA "2.2.4"))
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :maksajatieto :Maksaja]
                                         {:tag :Maksaja :child mapping-common/maksajatype-children_219})
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :osapuolitieto :Osapuoli]
                                         {:tag :Osapuoli :child osapuoli-219})
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :lausuntotieto]
                                         {:tag :lausuntotieto :child lausunto-219})
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :liitetieto]
                                         {:tag :liitetieto :child liitetieto-219})
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :paatostieto :Paatos :liitetieto]
                                         {:tag :liitetieto :child liitetieto-219})
                              (update-in [:child] mapping-common/update-child-element
                                         [:yleinenAlueAsiatieto lupa-name-key :katselmustieto :Katselmus :katselmuspoytakirja]
                                         {:tag :katselmuspoytakirja :child mapping-common/liite-children_219}))]
    (case (name krysp-version)
      "2.1.2" ya_to_krysp_2_1_2
      "2.1.3" ya_to_krysp_2_1_3
      "2.2.0" ya_to_krysp_2_2_0
      "2.2.1" ya_to_krysp_2_2_1
      "2.2.3" ya_to_krysp_2_2_3
      "2.2.4" ya_to_krysp_2_2_4
      (throw (IllegalArgumentException. (str "Unsupported KRYSP version " krysp-version))))))


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

(defn- common-map-enums [canonical lupa-name-key krysp-version]
  (-> canonical
      (update-in [:YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :lausuntotieto] mapping-common/lausuntotieto-map-enum :YA krysp-version)))

(defn- map-enums
  "Map enumerations in canonical into values supperted by given KRYSP version"
  [canonical lupa-name-key krysp-version]
  {:pre [krysp-version]}
  (-> (case (name krysp-version)
        "2.1.2" (map-enums-212 canonical lupa-name-key)
        canonical)
      (common-map-enums lupa-name-key krysp-version)))

(defn yleisetalueet-element-to-xml [canonical lupa-name-key krysp-version]
  {:pre [(map? canonical) lupa-name-key (string? krysp-version)]}
  (let [canon   (map-enums canonical lupa-name-key krysp-version)
        mapping (get-yleiset-alueet-krysp-mapping lupa-name-key krysp-version)]
    (element-to-xml canon mapping)))

(defn- save-as-krysp
  [application organization lang lupa-name-key krysp-version canonical-without-attachments begin-of-link]
  (let [attachments-canonical (attachments-canon/get-attachments-as-canonical application organization begin-of-link)
        statement-given-ids (common/statements-ids-with-status
                              (get-in canonical-without-attachments
                                      [:YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :lausuntotieto]))
        statement-attachments (attachments-canon/get-statement-attachments-as-canonical application organization begin-of-link statement-given-ids)
        canonical-with-statement-attachments (attachments-canon/add-statement-attachments
                                               canonical-without-attachments
                                               statement-attachments
                                               [:YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :lausuntotieto])
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :liitetieto]
                    (mapping-common/add-generated-pdf-attachments application begin-of-link attachments-canonical lang))
        xml (yleisetalueet-element-to-xml canonical lupa-name-key krysp-version)
        all-canonical-attachments (concat attachments-canonical (attachments-canon/flatten-statement-attachments statement-attachments))
        attachments-for-write (mapping-common/attachment-details-from-canonical all-canonical-attachments)]
    {:xml xml
     :attachments attachments-for-write}))

(defn resolve-lupa-name-key [application]
  (let [primary-operation (-> application :primaryOperation :name keyword)
        operation-type (if (= :ya-jatkoaika primary-operation)
                         (or (-> application :linkPermitData first :operation keyword)
                             :ya-katulupa-vesi-ja-viemarityot)
                         primary-operation)]
    (common/ya-operation-type-to-schema-name-key operation-type)))

(defmethod permit/application-krysp-mapper :YA
  [application organization lang krysp-version begin-of-link]
  (let [lupa-name-key (resolve-lupa-name-key application)
        canonical-without-attachments (common/application->canonical application lang)]
    (save-as-krysp application organization lang lupa-name-key krysp-version canonical-without-attachments begin-of-link)))

(defmethod permit/review-krysp-mapper :YA
  [application organization review user lang krysp-version begin-of-link]
  (let [lupa-name-key (resolve-lupa-name-key application)
        attachment-target {:type "task" :id (:id review)}
        target-pred  #(= attachment-target (:target %))

        attachments (filter target-pred (:attachments application))
        canonical-attachments (attachments-canon/get-attachments-as-canonical
                                {:attachments attachments :title (:title application)}
                                organization
                                begin-of-link
                                (every-pred target-pred attachments-canon/no-statements-no-verdicts))

        all-canonical-attachments (seq (filter identity canonical-attachments))

        canonical-without-attachments (common/review->canonical application review {:lang lang :user user})
        canonical (-> canonical-without-attachments
                      (cond-> (seq canonical-attachments) (assoc-in [:YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :liitetieto] canonical-attachments)))

        xml (yleisetalueet-element-to-xml canonical lupa-name-key krysp-version)
        attachments-for-write (mapping-common/attachment-details-from-canonical all-canonical-attachments)]
    {:xml xml
     :attachments attachments-for-write}))
