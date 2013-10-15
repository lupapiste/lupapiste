(ns lupapalvelu.document.poikkeamis-canonical
  (require [lupapalvelu.document.canonical-common :refer :all]
           [clojure.string :as s]))

(defn- root-element [application lang]
  {:Popast
   {:toimituksenTiedot (toimituksen-tiedot application lang)}})

(defn get-toimenpide [toimenpide common]
  (merge common {:kuvausKoodi (-> toimenpide :Toimenpide :value)
                 :tavoitetilatieto {:Tavoitetila {:paakayttotarkoitusKoodi (-> toimenpide :kayttotarkoitus :value)
                                                  :asuinhuoneitojenLkm (-> toimenpide :huoneistoja :value)
                                                  :rakennuksenKerrosluku (-> toimenpide :kerroksia :value)
                                                  :kokonaisala (-> toimenpide :kokonaisala :value)
                                                  :kerrosalatieto {:kerrosala {:pintaAla (-> toimenpide :kerrosala :value)
                                                                               :paakayttotarkoitusKoodi (-> toimenpide :kayttotarkoitus :value)}}}}}))

(defn get-toimenpiteet [{{toimenpiteet :toimenpiteet kaytettykerrosala :kaytettykerrosala} :data :as d}]
  (let [kaytettykerrosala-canonical (when (not (s/blank? (-> kaytettykerrosala :pintaAla :value)))
                                      {:kerrosalatieto {:kerrosala {:pintaAla (-> kaytettykerrosala :pintaAla :value)
                                                                    :paakayttotarkoitusKoodi (-> kaytettykerrosala :kayttotarkoitusKoodi :value)}}})]
    (for [[_ toimenpide] toimenpiteet]
      {:Toimenpide (get-toimenpide toimenpide kaytettykerrosala-canonical)})))

(defn common-poikkeamis-asia [application poikkeamisasia-path lang]
  (let [root (root-element application lang)
        documents (by-type
                    (clojure.walk/postwalk
                      (fn [v]
                        (if (and (string? v)
                                 (s/blank? v))
                          nil
                          v))
                      (:documents application)))]

    (assoc-in
      root
      poikkeamisasia-path
      {:Poikkeamisasia {:kasittelynTilatieto (get-state application)
                        :kuntakoodi (:municipality application)
                        :luvanTunnistetiedot (lupatunnus application)
                        :osapuolettieto (osapuolet documents)
                        :rakennuspaikkatieto (get-bulding-places (:poikkeusasian-rakennuspaikka documents) application)
                        :toimenpidetieto (get-toimenpiteet (first (:rakennushanke documents))) ;sanity check only one toimenpide in lupapiste.
                        }}
      ))
  )

(defmulti poikkeus-application-to-canonical (fn [application lang] (:permitSubtype application)))

(defmethod poikkeus-application-to-canonical "poikkeamislupa" [application lang]
  (common-poikkeamis-asia application [:Popast :poikkeamisasiatieto] lang))

(defmethod poikkeus-application-to-canonical "suunnittelutarveratkaisu" [application lang]
  (let [root (root-element application lang)]
    root))



