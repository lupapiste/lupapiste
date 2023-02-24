(ns lupapalvelu.backing-system.krysp.maa-aines-mapping
  (:require [sade.core :refer [now]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.attachments-canonical :as attachments-canon]
            [lupapalvelu.document.canonical-common :as common]
            [lupapalvelu.document.maa-aines-canonical :as maa-aines-canonical]
            [lupapalvelu.backing-system.krysp.mapping-common :as mapping-common]
            [lupapalvelu.xml.emit :refer [element-to-xml]]))

(def maaAineslupaAsia
  [mapping-common/yksilointitieto
   mapping-common/alkuHetki
   {:tag :kasittelytietotieto :child [{:tag :KasittelyTieto :child mapping-common/ymp-kasittelytieto-children}]}
   {:tag :kiinteistotunnus}
   {:tag :luvanTunnistetiedot :child [mapping-common/ymp-lupatunnus]}
   {:tag :lausuntotieto :child [mapping-common/lausunto_213]}

   {:tag :hakemustieto
    :child [{:tag :Hakemus
             :child [{:tag :hakija :child mapping-common/yhteystietotype-children_213}
                     {:tag :omistaja :child mapping-common/henkilo-child-ns-yht} ; property owner
                     {:tag :ottamistoiminnanYhteyshenkilo :child mapping-common/henkilo-child-ns-yht}
                     {:tag :alueenKiinteistonSijainti :child [(assoc mapping-common/sijantiType :ns "yht")]}
                     {:tag :ottamismaara
                      :child [{:tag :kokonaismaara} ; m^3
                              {:tag :vuotuinenOtto} ; m^3
                              {:tag :ottamisaika} ; vuotta
                              ]}
                     {:tag :paatoksenToimittaminen} ; string enumeration: Noudetaan, Postitetaan, ei tiedossa
                     ; {:tag :viranomaismaksujenSuorittaja :child mapping-common/henkilo-child-ns-yht} REMOVED, using maksajatieto from 2.1.2 onwards
                     {:tag :ottamissuunnitelmatieto
                      :child [{:tag :Ottamissuunnitelma
                               :child [mapping-common/yksilointitieto
                                       mapping-common/alkuHetki
                                       {:tag :ottamissuunnitelmanLaatija
                                        :child (conj mapping-common/henkilo-child-ns-yht {:tag :ammatti} {:tag :koulutus})}
                                       (mapping-common/sijaintitieto "yht")
                                       {:tag :selvitys :child [{:tag :toimenpiteet} {:tag :tutkimukset} {:tag :ainesLaatu} {:tag :ainesMaara}]}
                                       {:tag :luonnonolot :child [{:tag :maisemakuva} {:tag :kasvillisuusJaElaimisto} {:tag :kaavoitustilanne}]}
                                       ;{:tag :toimintaAlueenKuvaus :child mapping-common/liite-children}
                                       ;{:tag :ottamisenJarjestaminen :child mapping-common/liite-children}
                                       {:tag :pohjavesiolot, :child [{:tag :luokitus} {:tag :suojavyohykkeet}]}
                                       {:tag :vedenottamot}
                                       ;{:tag :ymparistoHaittojenVahentaminen :child mapping-common/liite-children}
                                       ;{:tag :alueenJalkihoito :child mapping-common/liite-children}
                                       {:tag :vakuus :child [{:tag :kylla} ; string enumeration Rahaa, Pankkitakaus, ei tiedossa
                                                             {:tag :ei}]}
                                       ;{:tag :valtakirja :child mapping-common/liite-children}
                                       {:tag :toimenpidealue :child [mapping-common/sijantiType]}
                                       ]}]}]}]}

   {:tag :maksajatieto :child [{:tag :Maksaja :child mapping-common/maksajatype-children_213}]}
   (mapping-common/sijaintitieto "yht")
   {:tag :koontiKentta}
   {:tag :liitetieto :child [{:tag :Liite :child mapping-common/liite-children_213}]}
   {:tag :asianKuvaus}])

(def maa-aines_to_krysp_212
  {:tag :MaaAinesluvat
   :ns "ymm"
   :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :MAL "2.1.2")
                 :xmlns:ymm "http://www.paikkatietopalvelu.fi/gml/ymparisto/maa_ainesluvat"}
                (mapping-common/common-namespaces :MAL "2.1.2"))
   :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
           {:tag :maaAineslupaAsiatieto :child [{:tag :MaaAineslupaAsia :child maaAineslupaAsia}]}
           {:tag :kotitarveottoasiaTieto} ; To be mapped in the future?
           ]})

(defn- update-asiatieto [path maa-aines_to_krysp_mapping child]
  (update-in maa-aines_to_krysp_mapping
             [:child]
             mapping-common/update-child-element
             (concat [:maaAineslupaAsiatieto :MaaAineslupaAsia] path)
             {:tag   (last path)
              :child child}))

(def update-hakija
  (partial update-asiatieto [:hakemustieto :Hakemus :hakija]))

(def update-omistaja
  (partial update-asiatieto [:hakemustieto :Hakemus :omistaja]))

(def update-ottamistoiminnan-yhteyshenkilo
  (partial update-asiatieto [:hakemustieto :Hakemus :ottamistoiminnanYhteyshenkilo]))

(def update-maksaja
  (partial update-asiatieto [:maksajatieto :Maksaja]))

(defn- update-ottamissuunnitelman-laatija [maa-aines_to_krysp_mapping child]
  (update-asiatieto [:hakemustieto :Hakemus :ottamissuunnitelmatieto :Ottamissuunnitelma :ottamissuunnitelmanLaatija]
                    maa-aines_to_krysp_mapping
                    (conj child {:tag :ammatti} {:tag :koulutus})))

(def maa-aines_to_krysp_221
  (-> maa-aines_to_krysp_212
      (assoc-in [:attr :xsi:schemaLocation]
                (mapping-common/schemalocation :MAL "2.2.1"))

      ; Uses LausuntoYmpType where attachments have not changed

      ; Support for foreign addresses
      (update-hakija mapping-common/yhteystietotype-children_215)

      ; Support for foreign addresses
      ; (omistaja element not in the canonical model at the time of writing)
      (update-omistaja mapping-common/henkilo-child-ns-yht-215)

      ; Support for foreign addresses
      ; (ottamistoiminnanYhteyshenkilo element not in the canonical model at the time of writing)
      (update-ottamistoiminnan-yhteyshenkilo mapping-common/henkilo-child-ns-yht-215)

      ; Support for foreign addresses
      ; (ottamissuunnitelmanLaatija element not in the canonical model at the time of writing)
      (update-ottamissuunnitelman-laatija mapping-common/henkilo-child-ns-yht-215)

      ; Support for foreign addresses
      (update-maksaja mapping-common/maksajatype-children_215)

      ; No changes to attachments
      ))

(def maa-aines_to_krysp_223
  (-> maa-aines_to_krysp_221
      (update-in [:attr] merge
                 {:xsi:schemaLocation (mapping-common/schemalocation :MAL "2.2.3")
                  :xmlns:ymm "http://www.kuntatietopalvelu.fi/gml/ymparisto/maa_ainesluvat"}
                 (mapping-common/common-namespaces :MAL "2.2.3"))))

(def maa-aines_to_krysp_224
  (-> maa-aines_to_krysp_223
      (update-in [:attr] merge
                 {:xsi:schemaLocation (mapping-common/schemalocation :MAL "2.2.4")
                  :xmlns:ymm "http://www.kuntatietopalvelu.fi/gml/ymparisto/maa_ainesluvat"}
                 (mapping-common/common-namespaces :MAL "2.2.4"))
      ; Support for ulkomainen hetu
      (update-hakija mapping-common/yhteystietotype-children_219)

      ; Support for ulkomainen hetu
      ; (omistaja element not in the canonical model at the time of writing)
      (update-omistaja mapping-common/henkilo-child-ns-yht-219)

      ; Support for ulkomainen hetu
      ; (ottamistoiminnanYhteyshenkilo element not in the canonical model at the time of writing)
      (update-ottamistoiminnan-yhteyshenkilo mapping-common/henkilo-child-ns-yht-219)

      ; Support for ulkomainen hetu
      ; (ottamissuunnitelmanLaatija element not in the canonical model at the time of writing)
      (update-ottamissuunnitelman-laatija mapping-common/henkilo-child-ns-yht-219)

      ; Support for ulkomainen hetu
      (update-maksaja mapping-common/maksajatype-children_219)))

(defn- get-mapping [krysp-version]
  {:pre [krysp-version]}
  (case (name krysp-version)
    "2.1.2" maa-aines_to_krysp_212
    "2.2.1" maa-aines_to_krysp_221
    "2.2.3" maa-aines_to_krysp_223
    "2.2.4" maa-aines_to_krysp_224
    (throw (IllegalArgumentException. (str "Unsupported KRYSP version " krysp-version)))))

(defn- common-map-enums [canonical krysp-version]
  (-> canonical
      (update-in [:MaaAinesluvat :maaAineslupaAsiatieto :MaaAineslupaAsia :lausuntotieto] mapping-common/lausuntotieto-map-enum :MAL krysp-version)))

(defn maa-aines-element-to-xml [canonical krysp-version]
  (element-to-xml (common-map-enums canonical krysp-version) (get-mapping krysp-version)))

(defmethod permit/application-krysp-mapper :MAL
  [application organization lang krysp-version begin-of-link]
  (let [krysp-polku-lausuntoon [:MaaAinesluvat :maaAineslupaAsiatieto :MaaAineslupaAsia :lausuntotieto]
        canonical-without-attachments  (maa-aines-canonical/maa-aines-canonical application lang)
        statement-given-ids (common/statements-ids-with-status
                              (get-in canonical-without-attachments krysp-polku-lausuntoon))
        statement-attachments (attachments-canon/get-statement-attachments-as-canonical application organization begin-of-link statement-given-ids)
        attachments-canonical (attachments-canon/get-attachments-as-canonical application organization begin-of-link)
        canonical-with-statement-attachments (attachments-canon/add-statement-attachments canonical-without-attachments statement-attachments krysp-polku-lausuntoon)
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:MaaAinesluvat :maaAineslupaAsiatieto :MaaAineslupaAsia :liitetieto]
                    (mapping-common/add-generated-pdf-attachments application begin-of-link attachments-canonical lang))
        xml (maa-aines-element-to-xml canonical krysp-version)
        all-canonical-attachments (concat attachments-canonical (attachments-canon/flatten-statement-attachments statement-attachments))
        attachments-for-write (mapping-common/attachment-details-from-canonical all-canonical-attachments)]
    {:xml xml
     :attachments attachments-for-write}))
