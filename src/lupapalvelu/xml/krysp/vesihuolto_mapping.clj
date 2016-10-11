(ns lupapalvelu.xml.krysp.vesihuolto-mapping
  (:require
    [lupapalvelu.xml.emit :refer [element-to-xml]]
    [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
    [lupapalvelu.permit :as permit]
    [lupapalvelu.document.attachments-canonical :as attachments-canon]
    [lupapalvelu.document.canonical-common :as common]
    [lupapalvelu.document.vesihuolto-canonical :as vesihuolto-canonical]
    [lupapalvelu.xml.disk-writer :as writer]))

(def vesihuolto-to-krysp_213
  {:tag :Vesihuoltolaki :ns "ymv"
   :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :VVVL "2.1.3")
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

(def vesihuolto-to-krysp_221
  (-> vesihuolto-to-krysp_213
      (assoc-in [:attr :xsi:schemaLocation]
        (mapping-common/schemalocation :VVVL "2.2.1"))

      ; Uses LausuntoYmpType where attachments have not changed

      ; Support for foreign addresses
      (update-in [:child] mapping-common/update-child-element
                 [:vapautukset :Vapautus :vapautushakemustieto :Vapautushakemus :hakija]
                 {:tag :hakija :child mapping-common/yhteystietotype-children_215})

      ; No changes to attachments
  ))

(defn- get-mapping [krysp-version]
  {:pre [krysp-version]}
  (case (name krysp-version)
    "2.1.3" vesihuolto-to-krysp_213
    "2.2.1" vesihuolto-to-krysp_221
    (throw (IllegalArgumentException. (str "Unsupported KRYSP version " krysp-version)))))

(defn- common-map-enums [canonical krysp-version]
  (-> canonical
      (update-in [:Vesihuoltolaki :vapautukset :Vapautus :lausuntotieto] mapping-common/lausuntotieto-map-enum :VVVL krysp-version)))

(defn vesihuolto-element-to-xml [canonical krysp-version]
  (element-to-xml (common-map-enums canonical krysp-version) (get-mapping krysp-version)))

(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent.
   3rd parameter (submitted-application) is not used on VVVL applications."
  [application lang submitted-application krysp-version output-dir begin-of-link]
  (let [krysp-polku-lausuntoon [:Vesihuoltolaki :vapautukset :Vapautus :lausuntotieto]
        canonical-without-attachments  (vesihuolto-canonical/vapautus-canonical application lang)
        statement-given-ids (common/statements-ids-with-status
                              (get-in canonical-without-attachments krysp-polku-lausuntoon))
        statement-attachments (attachments-canon/get-statement-attachments-as-canonical application begin-of-link statement-given-ids)
        attachments-canonical (attachments-canon/get-attachments-as-canonical application begin-of-link)
        canonical-with-statement-attachments (attachments-canon/add-statement-attachments canonical-without-attachments statement-attachments krysp-polku-lausuntoon)
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:Vesihuoltolaki :vapautukset :Vapautus :liitetieto]
                    (mapping-common/add-generated-pdf-attachments application begin-of-link attachments-canonical lang))
        xml (vesihuolto-element-to-xml canonical krysp-version)
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

(defmethod permit/application-krysp-mapper :VVVL [application lang submitted-application krysp-version output-dir begin-of-link]
  (save-application-as-krysp application lang submitted-application krysp-version output-dir begin-of-link))
