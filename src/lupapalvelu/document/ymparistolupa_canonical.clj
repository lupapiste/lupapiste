(ns lupapalvelu.document.ymparistolupa-canonical
  (require [sade.util :as util]
           [lupapalvelu.document.canonical-common :refer [empty-tag] :as canonical-common]
           [lupapalvelu.document.tools :as tools]
           ))

(defn ymparistolupa-canonical [application lang]
  (let [documents (tools/unwrapped (canonical-common/documents-by-type-without-blanks application))
        kuvaus    (-> documents :yl-hankkeen-kuvaus first :data)]
    {:Ymparistoluvat
     {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
      :ymparistolupatieto
      {:Ymparistolupa
       (merge
         {:kasittelytietotieto (canonical-common/get-kasittelytieto-ymp application :Kasittelytieto)
          :luvanTunnistetiedot (canonical-common/lupatunnus (:id application))
          :lausuntotieto (canonical-common/get-statements (:statements application))
          :hakija (map canonical-common/->ymp-osapuoli (:hakija documents))
          :toiminta (select-keys kuvaus [:kuvaus :peruste])
          :tiedotToiminnanSijainnista
          {:TiedotToiminnanSijainnista
           {:yksilointitieto (:propertyId application)
            :alkuHetki (util/to-xml-datetime (:created application))
            :sijaintitieto (first (canonical-common/get-sijaintitieto application))}}
          }
         (when (seq (:linkPermitData application))
           {:voimassaOlevatLuvat
            {:luvat
             {:lupa (map
                      (fn [{:keys [id type]}] {:tunnistetieto id :kuvaus type})
                      (:linkPermitData application))}}}
           )
          ; TODO, kun saadaan skeemaan paikka:
          ; - drawings
          ; - maksaja
         )}

     }}))

