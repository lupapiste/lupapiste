(ns lupapalvelu.xml.krysp.ymparisto-ilmoitukset-mapping
  (:require
    [lupapalvelu.xml.emit :refer [element-to-xml]]
    [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
    [lupapalvelu.document.ymparisto-ilmoitukset-canonical :as ct]
    [lupapalvelu.permit :as permit]))

(def ilmoitus_to_krysp
  {:tag :Ilmoitukset
   :ns "ymi"
   :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation "ymparisto/ilmoitukset" "2.1.2")
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
                             {:tag :ilmoittaja ; FIXME
                              :child mapping-common/ymp-osapuoli-children}
                             {:tag :toiminnanSijaintitieto
                              :child [{:tag :toiminnanSijainti :child [{:tag :Osoite :child mapping-common/postiosoite-children-ns-yht}
                                                                       {:tag :Kunta}
                                                                       mapping-common/sijantiType
                                                                       {:tag :Kiinteistorekisterinumero}]}]}

                             ; {:tag :ilmoittaja}}
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
                             {:tag :toiminnanKesto :child [{:tag :alkuHetki}
                                                           {:tag :loppuHetki}]}
                             {:tag :melutiedot :child [{:tag :koneidenLkm}
                                                       {:tag :melutaso :child [{:tag :db}
                                                                               {:tag :paiva}
                                                                               {:tag :yo}
                                                                               {:tag :mittaaja}]}]}
                             {:tag :koontikentta}
                             {:tag :liitetieto :child [{:tag :Liite :child mapping-common/liite-children_213}]} ]}]}]})


(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [application lang submitted-application krysp-version output-dir begin-of-link]
  (let [krysp-polku-lausuntoon [:Ilmoitukset :melutarina :Melutarina :lausuntotieto]
        canonical-without-attachments  (ct/meluilmoitus-canonical application lang)
        statement-given-ids (mapping-common/statements-ids-with-status
                              (get-in canonical-without-attachments krysp-polku-lausuntoon))
        statement-attachments (mapping-common/get-statement-attachments-as-canonical application begin-of-link statement-given-ids)
        attachments (mapping-common/get-attachments-as-canonical application begin-of-link)
        canonical-with-statement-attachments (mapping-common/add-statement-attachments canonical-without-attachments statement-attachments krysp-polku-lausuntoon)
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:Ilmoitukset :melutarina :Melutarina :liitetieto]
                    attachments)
        xml (element-to-xml canonical ilmoitus_to_krysp)]

    (mapping-common/write-to-disk application attachments statement-attachments xml krysp-version output-dir)))

(permit/register-function permit/YI :app-krysp-mapper save-application-as-krysp)
