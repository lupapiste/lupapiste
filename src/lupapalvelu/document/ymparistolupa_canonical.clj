(ns lupapalvelu.document.ymparistolupa-canonical
  (require [sade.util :as util]
           [lupapalvelu.document.canonical-common :refer [empty-tag] :as canonical-common]
           [lupapalvelu.document.tools :as tools]
           ))

(defn ymparistolupa-canonical [application lang]
  (let [documents (tools/unwrapped (canonical-common/documents-by-type-without-blanks application))
        kuvaus    (-> documents :yl-hankkeen-kuvaus first :data)
        generic-id {:yksilointitieto (:id application)
                    :alkuHetki (util/to-xml-datetime (:created application))}]
    {:Ymparistoluvat
     {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
      :ymparistolupatieto
      {:Ymparistolupa
       (merge
         {:kasittelytietotieto (canonical-common/get-kasittelytieto-ymp application :Kasittelytieto)
          :luvanTunnistetiedot (canonical-common/lupatunnus (:id application))
          :lausuntotieto (canonical-common/get-statements (:statements application))
          :hakija (remove nil? (map canonical-common/get-yhteystiedot (:hakija documents)))
          :toiminta (select-keys kuvaus [:kuvaus :peruste])
          :laitoksentiedot {:Laitos (assoc generic-id :kiinttun (:propertyId application))}
          :toiminnanSijaintitieto
          {:ToiminnanSijainti (assoc generic-id :sijaintitieto (canonical-common/get-sijaintitieto application))} }
         (util/assoc-when {} :maksaja (canonical-common/get-maksajatiedot (first (:maksaja documents))))
         (when (seq (:linkPermitData application))
           {:voimassaOlevatLuvat
            {:luvat
             {:lupa (map
                      (fn [{:keys [id type]}] {:tunnistetieto id :kuvaus type})
                      (:linkPermitData application))}}})
         )}}}))
