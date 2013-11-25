(ns lupapalvelu.document.poikkeamis-canonical
  (require [lupapalvelu.document.canonical-common :refer :all]
           [clojure.string :as s]))

(defn- root-element [application lang]
  {:Popast
   {:toimituksenTiedot (toimituksen-tiedot application lang)}})

(defn get-toimenpide [{toimenpide :toimenpiteet} common]
  ;(clojure.pprint/pprint toimenpide)
  (merge common {:kuvausKoodi (-> toimenpide :Toimenpide :value)
                 :tavoitetilatieto {:Tavoitetila {:paakayttotarkoitusKoodi (-> toimenpide :kayttotarkoitus :value)
                                                  :asuinhuoneitojenLkm (-> toimenpide :huoneistoja :value)
                                                  :rakennuksenKerrosluku (-> toimenpide :kerroksia :value)
                                                  :kokonaisala (-> toimenpide :kokonaisala :value)
                                                  :kerrosalatieto {:kerrosala {:pintaAla (-> toimenpide :kerrosala :value)
                                                                               :paakayttotarkoitusKoodi (-> toimenpide :kayttotarkoitus :value)}}}}}))

(defn get-toimenpidefull [{{toimenpiteet :toimenpiteet kaytettykerrosala :kaytettykerrosala} :data :as toimenpide}]
  (let [kaytettykerrosala-canonical (when (not (s/blank? (-> kaytettykerrosala :pintaAla :value)))
                                      {:kerrosalatieto {:kerrosala {:pintaAla (-> kaytettykerrosala :pintaAla :value)
                                                                    :paakayttotarkoitusKoodi (-> kaytettykerrosala :kayttotarkoitusKoodi :value)}}})]
      {:Toimenpide (get-toimenpide (:data toimenpide) kaytettykerrosala-canonical)}))


(defn get-toimenpiteet [toimenpiteet]
  (map get-toimenpidefull toimenpiteet))


(defn common-poikkeamis-asia [application poikkeamisasia-path lang kuvaus-avain]
  (let [root (root-element application lang)
        documents (by-type
                    (clojure.walk/postwalk
                      (fn [v]
                        (if (and (string? v)
                                 (s/blank? v))
                          nil
                          v))
                      (:documents application)))
        lisatiedot (:data (first (:lisatiedot documents)))
        hanke (:data (first (:hankkeen-kuvaus documents)))]
    (assoc-in
      root
      poikkeamisasia-path
      {:kasittelynTilatieto (get-state application)
       :kuntakoodi (:municipality application)
       :luvanTunnistetiedot (lupatunnus (:id application))
       :osapuolettieto (osapuolet documents)
       :rakennuspaikkatieto (get-bulding-places (:poikkeusasian-rakennuspaikka documents) application)
       :toimenpidetieto (get-toimenpiteet (:rakennushanke documents))
       :lausuntotieto (get-statements (:statements application))
       :lisatietotieto {:Lisatieto {:asioimiskieli (if (= lang "se")
                                                     "ruotsi"
                                                     "suomi")
                                    :suoramarkkinointikieltoKytkin (true? (-> lisatiedot :suoramarkkinointikielto :value))}}
       :kayttotapaus "Uusi hakemus"
       :asianTiedot {:Asiantiedot {:vahainenPoikkeaminen (-> hanke :poikkeamat :value)
                                   kuvaus-avain (-> hanke :kuvaus :value)}}})))

(defmulti poikkeus-application-to-canonical (fn [application lang] (:permitSubtype application)))

(defmethod poikkeus-application-to-canonical "poikkeamislupa" [application lang]
  (common-poikkeamis-asia application [:Popast :poikkeamisasiatieto :Poikkeamisasia] lang :poikkeamisasianKuvaus ))

(defmethod poikkeus-application-to-canonical "suunnittelutarveratkaisu" [application lang]
  (common-poikkeamis-asia application [:Popast :suunnittelutarveasiatieto :Suunnittelutarveasia] lang :suunnittelutarveasianKuvaus))



