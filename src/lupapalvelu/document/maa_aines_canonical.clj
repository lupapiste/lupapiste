(ns lupapalvelu.document.maa-aines-canonical
  (require [sade.util :as util]
           [lupapalvelu.document.canonical-common :refer [empty-tag] :as canonical-common]
           [lupapalvelu.document.tools :as tools]))

(defn maa-aines-canonical [application lang]
  (let [documents (tools/unwrapped (canonical-common/documents-by-type-without-blanks application))
        kuvaus    nil]
    {:MaaAinesluvat
     {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
      :maaAineslupaAsiatieto
      {:MaaAineslupaAsia
       {:yksilointitieto (:id application)
        :alkuHetki (util/to-xml-datetime (:created application))
        :kasittelytietotieto (canonical-common/get-kasittelytieto-ymp application :KasittelyTieto)
        :luvanTunnistetiedot (canonical-common/lupatunnus (:id application))

        :sijaintitieto (first (canonical-common/get-sijaintitieto application))
        :lausuntotieto (canonical-common/get-statements (:statements application))}}}}))
