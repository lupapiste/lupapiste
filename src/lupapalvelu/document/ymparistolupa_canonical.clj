(ns lupapalvelu.document.ymparistolupa-canonical
  (:require [lupapalvelu.document.asianhallinta-canonical :as ah-canonical]
            [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.operations :as operations]
            [sade.date :as date]))

(def kuvaus-docs [:yl-hankkeen-kuvaus
                  :yl-uusi-toiminta
                  :yl-toiminnan-muutos])

(defn get-kuvaus-data
  [documents]
  (->> kuvaus-docs
       (keep (fn [doc-type]
               (when-let [doc (get documents doc-type)]
                 (when-let [data (some-> doc first :data)]
                   (when (and (:kuvaus data)
                              (:peruste data))
                     data)))))
       first))

(defn ymparistolupa-canonical [application lang]
  (let [documents    (tools/unwrapped (canonical-common/stripped-documents-by-type application))
        kuvaus       (get-kuvaus-data documents)
        generic-id   {:yksilointitieto (:id application)
                      :alkuHetki       (date/xml-datetime (:created application))}
        hakija-key   (keyword (operations/get-applicant-doc-schema-name application))
        link-permits (:linkPermitData application)]
    {:Ymparistoluvat
     {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
      :ymparistolupatieto
      {:Ymparistolupa
       (merge
         {:kasittelytietotieto (canonical-common/get-kasittelytieto-ymp application :Kasittelytieto)
          :luvanTunnistetiedot (canonical-common/lupatunnus application)
          :toimenpiteet        (ah-canonical/get-toimenpiteet application lang)
          :viitelupatieto      (map canonical-common/get-viitelupatieto link-permits)
          :lausuntotieto       (canonical-common/get-statements (:statements application))
          :maksajatieto        (canonical-common/get-maksajatiedot-if-user-filled-in-data documents)
          :hakija              (remove nil? (map canonical-common/get-yhteystiedot (get documents hakija-key)))
          :toiminta            (select-keys kuvaus [:kuvaus :peruste])
          :laitoksentiedot     {:Laitos (assoc generic-id :kiinttun (:propertyId application))}
          :toiminnanSijaintitieto
          {:ToiminnanSijainti (assoc generic-id :sijaintitieto (canonical-common/get-sijaintitieto application))} }
         (some->> application canonical-common/kohteet (hash-map :kohteet))
         (when (seq (:linkPermitData application))
           {:voimassaOlevatLuvat
            {:luvat
             {:lupa (map
                      (fn [{:keys [id type]}] {:tunnistetieto id :kuvaus type})
                      (:linkPermitData application))}}}))}}}))

(defmethod canonical-common/application->canonical :YL [application lang]
  (ymparistolupa-canonical application lang))

(defmethod canonical-common/application->canonical :YM [application lang]
  (ymparistolupa-canonical application lang))

(defmethod canonical-common/description :YL [_ canonical]
  (get-in canonical [:Ymparistoluvat :ymparistolupatieto :Ymparistolupa :toiminta :kuvaus]))
