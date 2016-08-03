(ns lupapalvelu.document.ymparistolupa-canonical
  (:require [sade.util :as util]
            [lupapalvelu.document.canonical-common :refer [empty-tag] :as canonical-common]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.operations :as operations]))

(defn ymparistolupa-canonical [application lang]
  (let [documents (tools/unwrapped (canonical-common/documents-by-type-without-blanks application))
        kuvaus    (-> documents :yl-hankkeen-kuvaus first :data)
        generic-id {:yksilointitieto (:id application)
                    :alkuHetki (util/to-xml-datetime (:created application))}
        hakija-key (keyword (operations/get-applicant-doc-schema-name application))]
    {:Ymparistoluvat
     {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
      :ymparistolupatieto
      {:Ymparistolupa
       (merge
         {:kasittelytietotieto (canonical-common/get-kasittelytieto-ymp application :Kasittelytieto)
          :luvanTunnistetiedot (canonical-common/lupatunnus application)
          :lausuntotieto (canonical-common/get-statements (:statements application))
          :maksajatieto (util/assoc-when-pred {} util/not-empty-or-nil? :Maksaja (canonical-common/get-maksajatiedot (first (:ymp-maksaja documents))))
          :hakija (remove nil? (map canonical-common/get-yhteystiedot (get documents hakija-key)))
          :toiminta (select-keys kuvaus [:kuvaus :peruste])
          :laitoksentiedot {:Laitos (assoc generic-id :kiinttun (:propertyId application))}
          :toiminnanSijaintitieto
          {:ToiminnanSijainti (assoc generic-id :sijaintitieto (canonical-common/get-sijaintitieto application))} }
         (when (seq (:linkPermitData application))
           {:voimassaOlevatLuvat
            {:luvat
             {:lupa (map
                      (fn [{:keys [id type]}] {:tunnistetieto id :kuvaus type})
                      (:linkPermitData application))}}})
         )}}}))
