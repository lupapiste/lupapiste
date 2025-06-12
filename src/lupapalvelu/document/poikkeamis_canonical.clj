(ns lupapalvelu.document.poikkeamis-canonical
  (:require [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.tools :as tools]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- get-toimenpide [{toimenpide :toimenpiteet} kerrosalatieto]
  (let [{:keys [kerrosala kayttotarkoitus]} toimenpide]
    (merge kerrosalatieto {:kuvausKoodi (:Toimenpide toimenpide)
                           :tavoitetilatieto {:Tavoitetila {:paakayttotarkoitusKoodi (:kayttotarkoitus toimenpide)
                                                            :asuinhuoneistojenLkm (:huoneistoja toimenpide)
                                                            :rakennuksenKerrosluku (:kerroksia toimenpide)
                                                            :kokonaisala (:kokonaisala toimenpide)
                                        ; 2.1.3+
                                                            :kerrosala (when-not (ss/blank? kerrosala)
                                                                         kerrosala)
                                        ; 2.1.2
                                                            :kerrosalatieto (when (every? not-empty [kerrosala kayttotarkoitus])
                                                                              {:kerrosala {:pintaAla kerrosala
                                                                                           :paakayttotarkoitusKoodi kayttotarkoitus}})}}})))

(defn- get-toimenpidefull [{{:keys [kaytettykerrosala]} :data :as toimenpide}]
  (let [{:keys [kayttotarkoitusKoodi pintaAla]} kaytettykerrosala
        kaytettykerrosala-canonical (when (some not-empty [kayttotarkoitusKoodi pintaAla])
                                      {:kerrosalatieto {:kerrosala {:pintaAla pintaAla
                                                                    :paakayttotarkoitusKoodi kayttotarkoitusKoodi}}})]
      {:Toimenpide (get-toimenpide (:data toimenpide) kaytettykerrosala-canonical)}))

(defn- get-toimenpiteet [toimenpiteet]
  (remove
    #(or (nil? %) (empty? %))
    (map (comp util/strip-empty-maps
               util/strip-nils
               get-toimenpidefull) toimenpiteet)))

(defn get-poikkeamis-conf [subtype]
  (case subtype
    "poikkeamislupa" {:asia-path    [:Popast :poikkeamisasiatieto :Poikkeamisasia]
                      :kuvaus       :poikkeamisasianKuvaus
                      :kayttotapaus "Uusi poikkeamisasia"}
    "suunnittelutarveratkaisu" {:asia-path    [:Popast :suunnittelutarveasiatieto :Suunnittelutarveasia]
                                :kuvaus       :suunnittelutarveasianKuvaus
                                :kayttotapaus "Uusi suunnittelutarveasia"}))

(defn poikkeus-application-to-canonical [application lang]
  (let [application         (tools/unwrapped application)
        poikkeamisasia-conf (get-poikkeamis-conf (:permitSubtype application))
        documents-by-type   (stripped-documents-by-type application)
        hanke               (:data (first (:hankkeen-kuvaus documents-by-type)))]
    (assoc-in
      {:Popast {:toimituksenTiedot (toimituksen-tiedot application lang)}}
      (:asia-path poikkeamisasia-conf)
      {:kasittelynTilatieto (get-state application)
       :kuntakoodi          (:municipality application)
       :luvanTunnistetiedot (lupatunnus application)
       :osapuolettieto      (osapuolet application documents-by-type lang)
       :rakennuspaikkatieto (get-building-places (:poikkeusasian-rakennuspaikka documents-by-type) application)
       :toimenpidetieto     (get-toimenpiteet (:rakennushanke documents-by-type))
       :lausuntotieto       (get-statements (:statements application))
       :lisatietotieto      {:Lisatieto {:asioimiskieli (if (= lang "se")
                                                          "ruotsi"
                                                          "suomi")}}
       :kayttotapaus        (:kayttotapaus poikkeamisasia-conf)
       :avainsanaTieto      (get-avainsanaTieto application)
       :menettelyTOS        (:tosFunctionName application)
       :asianTiedot         {:Asiantiedot {:vahainenPoikkeaminen         (:poikkeamat hanke)
                                           (:kuvaus poikkeamisasia-conf) (:kuvaus hanke)}}})))


(defmethod application->canonical :P [application lang]
  (poikkeus-application-to-canonical application lang))

(defmethod description :P [application canonical]
  (let [conf (get-poikkeamis-conf (:permitSubtype application))]
    (get-in canonical (conj (:asia-path conf) :asianTiedot :Asiantiedot (:kuvaus conf)))))

(defn poikkeamat [application canonical]
  (let [conf (get-poikkeamis-conf (:permitSubtype application))]
    (get-in canonical (conj (:asia-path conf) :asianTiedot :Asiantiedot :vahainenPoikkeaminen))))
