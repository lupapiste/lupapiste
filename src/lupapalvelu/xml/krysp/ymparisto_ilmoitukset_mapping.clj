(ns lupapalvelu.xml.krysp.ymparisto-ilmoitukset-mapping
  (:require
    [lupapalvelu.xml.emit :refer [element-to-xml]]
    [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
    [lupapalvelu.document.attachments-canonical :as attachments-canon]
    [lupapalvelu.document.canonical-common :as common]
    [lupapalvelu.document.ymparisto-ilmoitukset-canonical :as ct]
    [lupapalvelu.permit :as permit]
    [lupapalvelu.xml.disk-writer :as writer]))

(def ilmoitus_to_krysp_212
  {:tag :Ilmoitukset
   :ns "ymi"
   :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :YI "2.1.2")
                 :xmlns:ymi "http://www.paikkatietopalvelu.fi/gml/ymparisto/ilmoitukset"}
           mapping-common/common-namespaces)

   :child [{:tag :toimituksenTiedot
            :child mapping-common/toimituksenTiedot}
           {:tag :melutarina
            :child [{:tag :Melutarina
                     :child [{:tag :yksilointitieto :ns "yht"}
                             {:tag :alkuHetki :ns "yht"}
                             {:tag :kasittelytietotieto
                              :child [{:tag :Kasittelytieto :child mapping-common/ymp-kasittelytieto-children}]}
                             {:tag :luvanTunnistetiedot
                              :child [mapping-common/lupatunnus]}
                             {:tag :lausuntotieto
                              :child [mapping-common/lausunto_213]}
                             {:tag :toiminnanSijaintitieto
                              :child [{:tag :ToiminnanSijainti :child [{:tag :Osoite :child mapping-common/postiosoite-children-ns-yht}
                                                                       {:tag :Kunta}
                                                                       mapping-common/sijantiType
                                                                       {:tag :Kiinteistorekisterinumero}]}]}

                             {:tag :ilmoittaja :child mapping-common/yhteystietotype-children_213}
                             {:tag :jatkoIlmoitusKytkin} ; boolean
                             {:tag :asianKuvaus} ; String
                             {:tag :toimintatieto :child [{:tag :Toiminta :child [{:tag :yksilointitieto :ns "yht"}
                                                                                  {:tag :alkuHetki :ns "yht"}
                                                                                  {:tag :rakentaminen :child [{:tag :louhinta}
                                                                                                              {:tag :murskaus}
                                                                                                              {:tag :paalutus}
                                                                                                              {:tag :muu}]}
                                                                          {:tag :tapahtuma :child [{:tag :ulkoilmakonsertti}
                                                                                                   {:tag :muu}]}]}]}
                             {:tag :toiminnanKesto :child [{:tag :alkuPvm}
                                                           {:tag :loppuPvm}
                                                           {:tag :arkiAlkuAika}
                                                           {:tag :arkiLoppuAika}
                                                           {:tag :lauantaiAlkuAika}
                                                           {:tag :lauantaiLoppuAika}
                                                           {:tag :sunnuntaiAlkuAika}
                                                           {:tag :sunnuntaiLoppuAika}]}
                             {:tag :melutiedot :child [{:tag :koneidenLkm}
                                                       {:tag :melutaso :child [{:tag :db}
                                                                               {:tag :paiva}
                                                                               {:tag :yo}
                                                                               {:tag :mittaaja}]}]}
                             {:tag :koontikentta}
                             {:tag :liitetieto :child [{:tag :Liite :child mapping-common/liite-children_213}]} ]}]}]})

(def ilmoitus_to_krysp_221
  (-> ilmoitus_to_krysp_212
      (assoc-in [:attr :xsi:schemaLocation]
                (mapping-common/schemalocation :YI "2.2.1"))

      ; Uses LausuntoYmpType where attachments have not changed

      ; Address is read from application, no actual changes
      (update-in [:child] mapping-common/update-child-element
                 [:melutarina :Melutarina :toiminnanSijaintitieto :ToiminnanSijainti :Osoite]
                 {:tag :Osoite :child mapping-common/postiosoite-children-ns-yht-215})

      ; Support for foreign addresses
      (update-in [:child] mapping-common/update-child-element
                 [:melutarina :Melutarina :ilmoittaja]
                 {:tag :ilmoittaja :child mapping-common/yhteystietotype-children_215})

      ; No changes to attachments
      ))

(defn- get-mapping [krysp-version]
  {:pre [krysp-version]}
  (case (name krysp-version)
    "2.1.2" ilmoitus_to_krysp_212
    "2.2.1" ilmoitus_to_krysp_221
    (throw (IllegalArgumentException. (str "Unsupported KRYSP version " krysp-version)))))

(defn- common-map-enums [canonical krysp-version]
  (-> canonical
      (update-in [:Ilmoitukset :melutarina :Melutarina :lausuntotieto] mapping-common/lausuntotieto-map-enum :YI krysp-version)))

(defn ymparistoilmoitus-element-to-xml [canonical krysp-version]
  (element-to-xml (common-map-enums canonical krysp-version) (get-mapping krysp-version)))

(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [application lang submitted-application krysp-version output-dir begin-of-link]
  (let [krysp-polku-lausuntoon [:Ilmoitukset :melutarina :Melutarina :lausuntotieto]
        canonical-without-attachments  (ct/meluilmoitus-canonical application lang)
        statement-given-ids (common/statements-ids-with-status
                              (get-in canonical-without-attachments krysp-polku-lausuntoon))
        statement-attachments (attachments-canon/get-statement-attachments-as-canonical application begin-of-link statement-given-ids)
        attachments-canonical (attachments-canon/get-attachments-as-canonical application begin-of-link)
        canonical-with-statement-attachments (attachments-canon/add-statement-attachments canonical-without-attachments statement-attachments krysp-polku-lausuntoon)
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:Ilmoitukset :melutarina :Melutarina :liitetieto]
                    (mapping-common/add-generated-pdf-attachments application begin-of-link attachments-canonical lang))
        xml (ymparistoilmoitus-element-to-xml canonical krysp-version)
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

(defmethod permit/application-krysp-mapper :YI [application lang submitted-application krysp-version output-dir begin-of-link]
  (save-application-as-krysp application lang submitted-application krysp-version output-dir begin-of-link))
