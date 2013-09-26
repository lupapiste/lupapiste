(ns lupapalvelu.document.poikkeamis-canonical
  (require [lupapalvelu.document.canonical-common :refer :all]))

(defn- root-element [application lang]
  {:Popast
   {:toimituksenTiedot (toimituksen-tiedot application lang)
    }})



(defmulti poikkeus-application-to-canonical (fn [application lang] (:permitSubtype application)))

(defmethod poikkeus-application-to-canonical "poikkeamislupa" [application lang]
  (let [root (root-element application lang)]
    (assoc-in
      root
      [:Popast :poikkeamisasiatieto]
      {:Poikkeamisasia {:kasittelynTilatieto (get-state application)
                        :kuntakoodi (:municipality application) }})))

(defmethod poikkeus-application-to-canonical "suunnittelutarveratkaisu" [application lang]
  (let [root (root-element application lang)]
    root))



