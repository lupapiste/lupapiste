(ns lupapalvelu.xml.krysp.ymparisto-ilmoitukset-mapping
  (:require
    [lupapalvelu.xml.emit :refer [element-to-xml]]
    [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
    [lupapalvelu.document.ymparisto-ilmoitukset-canonical :as ct]
    [lupapalvelu.permit :as permit]))

(def ilmoitus_to_krysp
  {:tag :Ilmoitukset
   :ns "ymi"
   :attr {:xsi:schemaLocation "http://www.opengis.net/gml http://schemas.opengis.net/gml/3.1.1/base/gml.xsd
                               http://www.paikkatietopalvelu.fi/gml/yhteiset
                               http://www.paikkatietopalvelu.fi/gml/yhteiset/2.1.1/yhteiset.xsd
                               http://www.paikkatietopalvelu.fi/gml/ymparisto/ilmoitukset
                               http://www.paikkatietopalvelu.fi/gml/ymparisto/ilmoitukset/2.1.1/ilmoitukset.xsd"
          :xmlns:ymi "http://www.paikkatietopalvelu.fi/gml/ymparisto/ilmoitukset"
          :xmlns:yht "http://www.paikkatietopalvelu.fi/gml/yhteiset"
          :xmlns:xlink "http://www.w3.org/1999/xlink"
          :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
          :xmlns:gml "http://www.opengis.net/gml"}
   :child [{:tag :toimituksenTiedot
            :child mapping-common/toimituksenTiedot}
           {:tag :melutarina
            :child [{:tag :Melutarina
                     :child [{:tag :yksilointitieto :ns "yht"}
                             {:tag :alkuHetki :ns "yht"}
                             {:tag :kasittelytietotieto
                              :child mapping-common/ymp-kasittelytieto}
                             {:tag :luvanTunnisteTiedot
                              :child [mapping-common/lupatunnus]}
                             {:tag :lausuntotieto
                              :child [mapping-common/lausunto]}
                             {:tag :ilmoittaja
                              :child [{:tag :nimi}
                                      {:tag :postiosoite :child mapping-common/postiosoite-children-ns-yht}
                                      {:tag :sahkopostiosoite}
                                      {:tag :yhteyshenkilo :child mapping-common/henkilo-child-ns-yht}]}
                             {:tag :toiminnanSijainti :child [{:tag :Osoite :child mapping-common/postiosoite-children-ns-yht}
                                                              {:tag :Kunta}
                                                              mapping-common/sijantiType
                                                              {:tag :Kiinteistorekisterinumero}]}
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
                             {:tag :koontikentta}]}]}]})


(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [application lang submitted-application krysp-version output-dir begin-of-link]
  (let [krysp-polku-lausuntoon [:Ilmoitukset :melutarina :lausuntotieto]
        canonical-without-attachments  (ct/meluilmoitus-canonical application lang)
        statement-given-ids (mapping-common/statements-ids-with-status
                              (get-in canonical-without-attachments krysp-polku-lausuntoon))
        statement-attachments (mapping-common/get-statement-attachments-as-canonical application begin-of-link statement-given-ids)
        attachments (mapping-common/get-attachments-as-canonical application begin-of-link)
        canonical-with-statement-attachments (mapping-common/add-statement-attachments canonical-without-attachments statement-attachments krysp-polku-lausuntoon)
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:Ilmoitukset :melutarina :liitetieto]
                    attachments)
        xml (element-to-xml canonical ilmoitus_to_krysp)]

    (mapping-common/write-to-disk application attachments statement-attachments xml krysp-version output-dir)))

(permit/register-function permit/YI :app-krysp-mapper save-application-as-krysp)
