(ns lupapalvelu.xml.krysp.maa-aines-mapping
  (:require [sade.util :as util]
            [lupapalvelu.core :refer [now]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.maa-aines-canonical :as maa-aines-canonical]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [lupapalvelu.xml.emit :refer [element-to-xml]]))

(def maaAineslupaAsia
  [{:tag :yksilointitieto :ns "yht"}
   {:tag :alkuHetki :ns "yht"}
   {:tag :kasittelytietotieto :child [{:tag :KasittelyTieto :child mapping-common/ymp-kasittelytieto-children}]}
   {:tag :luvanTunnistetiedot :child [mapping-common/lupatunnus]}
   {:tag :lausuntotieto :child [mapping-common/lausunto]}

   ; TODO

   {:tag :liitetieto :child [{:tag :Liite :child mapping-common/liite-children}]}

   ])

(def maa-aines_to_krysp
  {:tag :MaaAinesluvat
   :ns "ymm"
   :attr {:xsi:schemaLocation "http://www.paikkatietopalvelu.fi/gml/yhteiset
                               http://www.paikkatietopalvelu.fi/gml/yhteiset/2.1.0/yhteiset.xsd
                               http://www.paikkatietopalvelu.fi/gml/ymparisto/maa_ainesluvat
                               http://www.paikkatietopalvelu.fi/gml/ymparisto/maa_ainesluvat/2.1.1/maaAinesluvat.xsd
                               http://www.opengis.net/gml http://schemas.opengis.net/gml/3.1.1/base/gml.xsd"
          :xmlns:ymm "http://www.paikkatietopalvelu.fi/gml/ymparisto/maa_ainesluvat"
          :xmlns:yht "http://www.paikkatietopalvelu.fi/gml/yhteiset"
          :xmlns:gml "http://www.opengis.net/gml"
          :xmlns:xlink "http://www.w3.org/1999/xlink"
          :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"}
   :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
           {:tag :maaAineslupaAsiatieto :child [{:tag :MaaAineslupaAsia :child maaAineslupaAsia}]}]})

(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent.
   3rd parameter (submitted-application) is not used on MAL applications."
  [application lang _ krysp-version output-dir begin-of-link]
  (let [krysp-polku-lausuntoon [:MaaAinesluvat :maaAineslupaAsiatieto :MaaAineslupaAsia :lausuntotieto]
        canonical-without-attachments  (maa-aines-canonical/maa-aines-canonical application lang)
        statement-given-ids (mapping-common/statements-ids-with-status
                              (get-in canonical-without-attachments krysp-polku-lausuntoon))
        statement-attachments (mapping-common/get-statement-attachments-as-canonical application begin-of-link statement-given-ids)
        attachments (mapping-common/get-attachments-as-canonical application begin-of-link)
        canonical-with-statement-attachments (mapping-common/add-statement-attachments canonical-without-attachments statement-attachments krysp-polku-lausuntoon)
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:MaaAinesluvat :maaAineslupaAsiatieto :MaaAineslupaAsia :liitetieto]
                    attachments)
        xml (element-to-xml canonical maa-aines_to_krysp)]

    (mapping-common/write-to-disk application attachments statement-attachments xml krysp-version output-dir)))

(permit/register-function permit/MAL :app-krysp-mapper save-application-as-krysp)
