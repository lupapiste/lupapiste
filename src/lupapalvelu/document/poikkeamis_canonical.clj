(ns lupapalvelu.document.poikkeamis-canonical
  (require [lupapalvelu.document.canonical-common :refer :all]
           [clojure.string :as s]))

(defn- root-element [application lang]
  {:Popast
   {:toimituksenTiedot (toimituksen-tiedot application lang)
    }})



(defmulti poikkeus-application-to-canonical (fn [application lang] (:permitSubtype application)))

(defmethod poikkeus-application-to-canonical "poikkeamislupa" [application lang]
  (let [root (root-element application lang)
        documents (by-type
                    (clojure.walk/postwalk
                      (fn [v]
                        (if (and (string? v) (s/blank? v))
                          nil
                          v))
                      (:documents application)))]
    (assoc-in
      root
      [:Popast :poikkeamisasiatieto]
      {:Poikkeamisasia {:kasittelynTilatieto (get-state application)
                        :kuntakoodi (:municipality application)
                        :luvanTunnistetiedot (lupatunnus application)
                        :osapuolettieto (osapuolet documents)}
       }
      )))

(defmethod poikkeus-application-to-canonical "suunnittelutarveratkaisu" [application lang]
  (let [root (root-element application lang)]
    root))



