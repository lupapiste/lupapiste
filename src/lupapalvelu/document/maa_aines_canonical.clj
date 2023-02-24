(ns lupapalvelu.document.maa-aines-canonical
  (:require [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.operations :as operations]
            [sade.date :as date]
            [sade.util :as util]))

(defn maa-aines-canonical [application lang]
  (let [documents (tools/unwrapped (canonical-common/stripped-documents-by-type application))
        kuvaus    (-> documents :maa-aineslupa-kuvaus first :data :kuvaus)
        hakija-key (keyword (operations/get-applicant-doc-schema-name application))]
    {:MaaAinesluvat
     {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
      :maaAineslupaAsiatieto
      {:MaaAineslupaAsia
       {:yksilointitieto (:id application)
        :kiinteistotunnus (:propertyId application)
        :alkuHetki (date/xml-datetime (:created application))
        :kasittelytietotieto (canonical-common/get-kasittelytieto-ymp application :KasittelyTieto)
        :luvanTunnistetiedot (canonical-common/lupatunnus application)
        :lausuntotieto (canonical-common/get-statements (:statements application))
        :hakemustieto
        {:Hakemus
         {:hakija (remove nil? (map canonical-common/get-yhteystiedot (get documents hakija-key)))}
         }
        :maksajatieto (util/assoc-when-pred {} util/not-empty-or-nil? :Maksaja (canonical-common/get-maksajatiedot (first (:ymp-maksaja documents))))
        :sijaintitieto (canonical-common/get-sijaintitieto application)
        :koontiKentta kuvaus
        :asianKuvaus kuvaus
        }}}}))

(defmethod canonical-common/application->canonical :MAL [application lang]
  (maa-aines-canonical application lang))
