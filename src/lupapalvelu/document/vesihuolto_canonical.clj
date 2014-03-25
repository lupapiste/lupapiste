(ns lupapalvelu.document.vesihuolto-canonical
  (require [lupapalvelu.document.vesihuolto-schemas :as vh-schemas]
           [lupapalvelu.document.canonical-common :refer :all]
           [lupapalvelu.document.tools :as tools]))

(defn vapautus-canonical [application lang]
  (let [application (tools/unwrapped application)
        documents (documents-by-type-without-blanks application)]
    {:Vesihuoltolaki
     {:toimituksenTiedot (toimituksen-tiedot application lang)
      :vapautukset
      {:Vapautus
       {:kasittelytietotieto (get-kasittelytieto-ymp application :KasittelyTieto)
        :luvanTunnistetiedot (lupatunnus (:id application))
        :lausuntotieto (get-statements (:statements application))
        :vapautusperuste nil
        :vapautushakemustieto
        {:Vapautushakemus
         {:hakija ()}}
        {}
        :asianKuvaus (:kuvaus (first (:hankkeen-kuvaus-vesihuolto documents)))
        }}}})
  )