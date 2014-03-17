(ns lupapalvelu.document.poikkeamis-canonical
  (require [lupapalvelu.document.canonical-common :refer :all]
           [lupapalvelu.document.tools :as tools]
           [clojure.string :as s]))

(defn- root-element [application lang]
  {:Popast
   {:toimituksenTiedot (toimituksen-tiedot application lang)}})

(defn- get-toimenpide [{toimenpide :toimenpiteet} common]
  (merge common {:kuvausKoodi (-> toimenpide :Toimenpide)
                 :tavoitetilatieto {:Tavoitetila {:paakayttotarkoitusKoodi (-> toimenpide :kayttotarkoitus)
                                                  :asuinhuoneitojenLkm (-> toimenpide :huoneistoja)
                                                  :rakennuksenKerrosluku (-> toimenpide :kerroksia)
                                                  :kokonaisala (-> toimenpide :kokonaisala)
                                                  :kerrosalatieto {:kerrosala {:pintaAla (-> toimenpide :kerrosala)
                                                                               :paakayttotarkoitusKoodi (-> toimenpide :kayttotarkoitus)}}}}}))

(defn- get-toimenpidefull [{{toimenpiteet :toimenpiteet kaytettykerrosala :kaytettykerrosala} :data :as toimenpide}]
  (let [kaytettykerrosala-canonical (when-not (s/blank? (-> kaytettykerrosala :pintaAla))
                                      {:kerrosalatieto {:kerrosala {:pintaAla (-> kaytettykerrosala :pintaAla)
                                                                    :paakayttotarkoitusKoodi (-> kaytettykerrosala :kayttotarkoitusKoodi)}}})]
      {:Toimenpide (get-toimenpide (:data toimenpide) kaytettykerrosala-canonical)}))


(defn- get-toimenpiteet [toimenpiteet]
  (map get-toimenpidefull toimenpiteet))


(defn common-poikkeamis-asia [application poikkeamisasia-path lang kuvaus-avain kayttotapaus]
  (let [application (tools/unwrapped application)
        root (root-element application lang)
        documents (documents-by-type-without-blanks application)
        lisatiedot (:data (first (:lisatiedot documents)))
        hanke (:data (first (:hankkeen-kuvaus documents)))]
    (assoc-in
      root
      poikkeamisasia-path
      {:kasittelynTilatieto (get-state application)
       :kuntakoodi (:municipality application)
       :luvanTunnistetiedot (lupatunnus (:id application))
       :osapuolettieto (osapuolet documents (:neighbors application) lang)
       :rakennuspaikkatieto (get-bulding-places (:poikkeusasian-rakennuspaikka documents) application)
       :toimenpidetieto (get-toimenpiteet (:rakennushanke documents))
       :lausuntotieto (get-statements (:statements application))
       :lisatietotieto {:Lisatieto {:asioimiskieli (if (= lang "se")
                                                     "ruotsi"
                                                     "suomi")}}
       :kayttotapaus kayttotapaus
       :asianTiedot {:Asiantiedot {:vahainenPoikkeaminen (-> hanke :poikkeamat)
                                   kuvaus-avain (-> hanke :kuvaus)}}})))

(defmulti poikkeus-application-to-canonical (fn [application lang] (:permitSubtype application)))

(defmethod poikkeus-application-to-canonical "poikkeamislupa" [application lang]
  (common-poikkeamis-asia application [:Popast :poikkeamisasiatieto :Poikkeamisasia] lang :poikkeamisasianKuvaus "Uusi poikkeamisasia"))

(defmethod poikkeus-application-to-canonical "suunnittelutarveratkaisu" [application lang]
  (common-poikkeamis-asia application [:Popast :suunnittelutarveasiatieto :Suunnittelutarveasia] lang :suunnittelutarveasianKuvaus "Uusi suunnittelutarveasia"))



