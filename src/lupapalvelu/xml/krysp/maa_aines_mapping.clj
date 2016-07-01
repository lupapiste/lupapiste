(ns lupapalvelu.xml.krysp.maa-aines-mapping
  (:require [sade.util :as util]
            [sade.core :refer [now]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.attachments-canonical :as attachments-canon]
            [lupapalvelu.document.canonical-common :as common]
            [lupapalvelu.document.maa-aines-canonical :as maa-aines-canonical]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.xml.disk-writer :as writer]))

(def maaAineslupaAsia
  [mapping-common/yksilointitieto
   mapping-common/alkuHetki
   {:tag :kasittelytietotieto :child [{:tag :KasittelyTieto :child mapping-common/ymp-kasittelytieto-children}]}
   {:tag :kiinteistotunnus}
   {:tag :luvanTunnistetiedot :child [mapping-common/lupatunnus]}
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
           mapping-common/common-namespaces)
   :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
           {:tag :maaAineslupaAsiatieto :child [{:tag :MaaAineslupaAsia :child maaAineslupaAsia}]}
           {:tag :kotitarveottoasiaTieto} ; To be mapped in the future?
           ]})

(def maa-aines_to_krysp_221
  (-> maa-aines_to_krysp_212
      (assoc-in [:attr :xsi:schemaLocation]
                (mapping-common/schemalocation :MAL "2.2.1"))

      ; Uses LausuntoYmpType where attachments have not changed

      ; Support for foreign addresses
      (update-in [:child] mapping-common/update-child-element
                 [:maaAineslupaAsiatieto :MaaAineslupaAsia :hakemustieto :Hakemus :hakija]
                 {:tag :hakija :child mapping-common/yhteystietotype-children_215})

      ; Support for foreign addresses
      ; (omistaja element not in the canonical model at the time of writing)
      (update-in [:child] mapping-common/update-child-element
                 [:maaAineslupaAsiatieto :MaaAineslupaAsia :hakemustieto :Hakemus :omistaja]
                 {:tag :omistaja :child mapping-common/henkilo-child-ns-yht-215})

      ; Support for foreign addresses
      ; (ottamistoiminnanYhteyshenkilo element not in the canonical model at the time of writing)
      (update-in [:child] mapping-common/update-child-element
                 [:maaAineslupaAsiatieto :MaaAineslupaAsia :hakemustieto :Hakemus :ottamistoiminnanYhteyshenkilo]
                 {:tag :ottamistoiminnanYhteyshenkilo :child mapping-common/henkilo-child-ns-yht-215})

      ; Support for foreign addresses
      ; (ottamissuunnitelmanLaatija element not in the canonical model at the time of writing)
      (update-in [:child] mapping-common/update-child-element
                 [:maaAineslupaAsiatieto :MaaAineslupaAsia :hakemustieto :Hakemus :ottamissuunnitelmatieto :Ottamissuunnitelma :ottamissuunnitelmanLaatija]
                 {:tag :ottamissuunnitelmanLaatija
                  :child (conj mapping-common/henkilo-child-ns-yht-215 {:tag :ammatti} {:tag :koulutus})})

      ; Support for foreign addresses
      (update-in [:child] mapping-common/update-child-element
                 [:maaAineslupaAsiatieto :MaaAineslupaAsia :maksajatieto :Maksaja]
                 {:tag :Maksaja :child mapping-common/maksajatype-children_215})

      ; No changes to attachments
      ))

(defn- get-mapping [krysp-version]
  {:pre [krysp-version]}
  (case (name krysp-version)
    "2.1.2" maa-aines_to_krysp_212
    "2.2.1" maa-aines_to_krysp_221
    (throw (IllegalArgumentException. (str "Unsupported KRYSP version " krysp-version)))))

(defn- common-map-enums [canonical krysp-version]
  (-> canonical
      (update-in [:MaaAinesluvat :maaAineslupaAsiatieto :MaaAineslupaAsia :lausuntotieto] mapping-common/lausuntotieto-map-enum :MAL krysp-version)))

(defn maa-aines-element-to-xml [canonical krysp-version]
  (element-to-xml (common-map-enums canonical krysp-version) (get-mapping krysp-version)))

(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent.
   3rd parameter (submitted-application) is not used on MAL applications."
  [application lang submitted-application krysp-version output-dir begin-of-link]
  (let [krysp-polku-lausuntoon [:MaaAinesluvat :maaAineslupaAsiatieto :MaaAineslupaAsia :lausuntotieto]
        canonical-without-attachments  (maa-aines-canonical/maa-aines-canonical application lang)
        statement-given-ids (common/statements-ids-with-status
                              (get-in canonical-without-attachments krysp-polku-lausuntoon))
        statement-attachments (attachments-canon/get-statement-attachments-as-canonical application begin-of-link statement-given-ids)
        attachments-canonical (attachments-canon/get-attachments-as-canonical application begin-of-link)
        canonical-with-statement-attachments (attachments-canon/add-statement-attachments canonical-without-attachments statement-attachments krysp-polku-lausuntoon)
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:MaaAinesluvat :maaAineslupaAsiatieto :MaaAineslupaAsia :liitetieto]
                    (mapping-common/add-generated-pdf-attachments application begin-of-link attachments-canonical lang))
        xml (maa-aines-element-to-xml canonical krysp-version)
        all-canonical-attachments (concat attachments-canonical (attachments-canon/flatten-statement-attachments statement-attachments))
        attachments-for-write (mapping-common/attachment-details-from-canonical all-canonical-attachments)]

    (writer/write-to-disk
      application
      attachments-for-write
      xml
      krysp-version
      output-dir
      submitted-application
      lang)))

(permit/register-function permit/MAL :app-krysp-mapper save-application-as-krysp)
