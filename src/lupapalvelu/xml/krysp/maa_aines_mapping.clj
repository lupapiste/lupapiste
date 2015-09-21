(ns lupapalvelu.xml.krysp.maa-aines-mapping
  (:require [sade.util :as util]
            [sade.core :refer [now]]
            [lupapalvelu.permit :as permit]
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

(def maa-aines_to_krysp
  {:tag :MaaAinesluvat
   :ns "ymm"
   :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :MAL "2.1.2")
                 :xmlns:ymm "http://www.paikkatietopalvelu.fi/gml/ymparisto/maa_ainesluvat"}
           mapping-common/common-namespaces)
   :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
           {:tag :maaAineslupaAsiatieto :child [{:tag :MaaAineslupaAsia :child maaAineslupaAsia}]}
           {:tag :kotitarveottoasiaTieto} ; To be mapped in the future?
           ]})

(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent.
   3rd parameter (submitted-application) is not used on MAL applications."
  [application lang submitted-application krysp-version output-dir begin-of-link]
  (let [krysp-polku-lausuntoon [:MaaAinesluvat :maaAineslupaAsiatieto :MaaAineslupaAsia :lausuntotieto]
        canonical-without-attachments  (maa-aines-canonical/maa-aines-canonical application lang)
        statement-given-ids (mapping-common/statements-ids-with-status
                              (get-in canonical-without-attachments krysp-polku-lausuntoon))
        statement-attachments (mapping-common/get-statement-attachments-as-canonical application begin-of-link statement-given-ids)
        attachments-canonical (mapping-common/get-attachments-as-canonical application begin-of-link)
        canonical-with-statement-attachments (mapping-common/add-statement-attachments canonical-without-attachments statement-attachments krysp-polku-lausuntoon)
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:MaaAinesluvat :maaAineslupaAsiatieto :MaaAineslupaAsia :liitetieto]
                    attachments-canonical)
        xml (element-to-xml canonical maa-aines_to_krysp)
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

(permit/register-function permit/MAL :app-krysp-mapper save-application-as-krysp)
