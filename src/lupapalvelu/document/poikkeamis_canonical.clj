(ns lupapalvelu.document.poikkeamis-canonical
  (require [lupapalvelu.document.canonical-common :refer :all]
           [clojure.string :as s]))

(defn- root-element [application lang]
  {:Popast
   {:toimituksenTiedot (toimituksen-tiedot application lang)}})

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
                        }}
      ))
  )

(defmulti poikkeus-application-to-canonical (fn [application lang] (:permitSubtype application)))

(defmethod poikkeus-application-to-canonical "poikkeamislupa" [application lang]
  (common-poikkeamis-asia application [:Popast :poikkeamisasiatieto] lang))

(defmethod poikkeus-application-to-canonical "suunnittelutarveratkaisu" [application lang]
  (let [root (root-element application lang)]
    root))



