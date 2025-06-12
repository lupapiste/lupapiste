(ns lupapalvelu.document.maa-aines-canonical
  (:require [lupapalvelu.document.asianhallinta-canonical :as ah-canonical]
            [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.operations :as operations]
            [sade.date :as date]
            [sade.util :as util]))

(defn maa-aines-canonical [application lang]
  (let [documents    (tools/unwrapped (canonical-common/stripped-documents-by-type application))
        kuvaus       (-> documents :maa-aineslupa-kuvaus first :data :kuvaus)
        hakija-key   (keyword (operations/get-applicant-doc-schema-name application))
        link-permits (:linkPermitData application)]
    {:MaaAinesluvat
     {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
      :maaAineslupaAsiatieto
      {:MaaAineslupaAsia
       (util/assoc-when
         {:yksilointitieto     (:id application)
          :kiinteistotunnus    (:propertyId application)
          :alkuHetki           (date/xml-datetime (:created application))
          :kasittelytietotieto (canonical-common/get-kasittelytieto-ymp application :KasittelyTieto)
          :luvanTunnistetiedot (canonical-common/lupatunnus application)
          :toimenpiteet        (ah-canonical/get-toimenpiteet application lang)
          :lausuntotieto       (canonical-common/get-statements (:statements application))
          :hakemustieto        {:Hakemus {:hakija (remove nil? (map canonical-common/get-yhteystiedot
                                                                    (get documents hakija-key)))}}
          :viitelupatieto      (map canonical-common/get-viitelupatieto link-permits)
          :maksajatieto        (canonical-common/get-maksajatiedot-if-user-filled-in-data documents)
          :sijaintitieto       (canonical-common/get-sijaintitieto application)
          :koontiKentta        kuvaus
          :asianKuvaus         kuvaus}
         :kohteet (canonical-common/kohteet application))}}}))

(defmethod canonical-common/application->canonical :MAL [application lang]
  (maa-aines-canonical application lang))

(defmethod canonical-common/description :MAL [_ canonical]
  (get-in canonical [:MaaAinesluvat :maaAineslupaAsiatieto :MaaAineslupaAsia :asianKuvaus]))
