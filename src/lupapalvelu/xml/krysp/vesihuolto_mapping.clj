(ns lupapalvelu.xml.krysp.vesihuolto-mapping
  (:require
    [lupapalvelu.xml.emit :refer [element-to-xml]]
    [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
    [lupapalvelu.permit :as permit]
    [lupapalvelu.document.vesihuolto-canonical :as vesihuolto-canonical]))

(def vesihuolto-to-krysp {:tag :Vesihuoltolaki :ns "ymv"
                          :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation "ymparisto/vesihuoltolaki" "2.1.3")
                                        :xmlns:ymv "http://www.paikkatietopalvelu.fi/gml/ymparisto/vesihuoltolaki"}
                                       mapping-common/common-namespaces)
                          :child
                          [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
                           {:tag :vapautukset
                            :child [{:tag :Vapautus
                                     :child [{:tag :kasittelytietotieto
                                              :child [{:tag :KasittelyTieto :child mapping-common/ymp-kasittelytieto-children }]}
                                             {:tag :luvanTunnistetiedot
                                              :child [mapping-common/lupatunnus]}
                                             {:tag :lausuntotieto
                                              :child [mapping-common/lausunto_213]}
                                             {:tag :vapautusperuste}
                                             {:tag :vapautushakemustieto
                                              :child [{:tag :Vapautushakemus
                                                       :child [{:tag :hakija
                                                                :child mapping-common/yhteystietotype-children_213}
                                                               {:tag :kohde
                                                                :child [{:tag :kiinteistorekisteritunnus}
                                                                        {:tag :kiinteistonRakennusTieto
                                                                         :child [{:tag :KiinteistonRakennus
                                                                                  :child [{:tag :kayttotarkoitustieto
                                                                                           :child [{:tag :kayttotarkoitus}
                                                                                                   {:tag :muu}]}
                                                                                          {:tag :kohteenVarustelutaso}
                                                                                          {:tag :haetaanVapautustaKytkin}]}]}
                                                                        {:tag :hulevedet
                                                                         :child [{:tag :muu}
                                                                                 {:tag :hulevedet}]}
                                                                        {:tag :talousvedet
                                                                         :child
                                                                         [{:tag :hankinta
                                                                           :child [{:tag :muu}
                                                                                   {:tag :hankinta}]}
                                                                          {:tag :johdatus}
                                                                          {:tag :riittavyys}]}
                                                                        {:tag :jatevedet}]}
                                                               (mapping-common/sijaintitieto "yht")]}]}
                                             {:tag :liitetieto
                                              :child [{:tag :Liite :child mapping-common/liite-children_213}]}
                                             {:tag :asianKuvaus}]}]}]})




(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent.
   3rd parameter (submitted-application) is not used on VVVL applications."
  [application lang _ krysp-version output-dir begin-of-link]
  (let [krysp-polku-lausuntoon [:Vesihuoltolaki :vapautukset :Vapautus :lausuntotieto]
        canonical-without-attachments  (vesihuolto-canonical/vapautus-canonical application lang)
        statement-given-ids (mapping-common/statements-ids-with-status
                              (get-in canonical-without-attachments krysp-polku-lausuntoon))
        statement-attachments (mapping-common/get-statement-attachments-as-canonical application begin-of-link statement-given-ids)
        attachments (mapping-common/get-attachments-as-canonical application begin-of-link)
        canonical-with-statement-attachments (mapping-common/add-statement-attachments canonical-without-attachments statement-attachments krysp-polku-lausuntoon)
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:Vesihuoltolaki :vapautukset :Vapautus :liitetieto]
                    attachments)
        xml (element-to-xml canonical vesihuolto-to-krysp)]

    (mapping-common/write-to-disk application attachments statement-attachments xml krysp-version output-dir)))

(permit/register-function permit/VVVL :app-krysp-mapper save-application-as-krysp)
