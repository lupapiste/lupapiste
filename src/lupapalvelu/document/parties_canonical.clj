(ns lupapalvelu.document.parties-canonical
  (:require [sade.core :refer :all]
            [lupapalvelu.document.rakennuslupa-canonical :as rl-canonical]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.disk-writer :as writer]
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :as rl-mapping]))

(defn strip-canonical-for-parties
  "Dissoc application related data from canonical"
  [canonical]
  (update-in canonical
             [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia]
             dissoc :rakennuspaikkatieto :toimenpidetieto :lausuntotieto))

(defn- transform-to-designers-message
  "Select only designer parties and set kayttotarkoitus accordingly"
  [canonical]
  (let [asia-path [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia]
        parties-path (conj asia-path :osapuolettieto :Osapuolet)]
    (-> (assoc-in canonical (conj asia-path :kayttotapaus) "Uuden suunnittelijan nime\u00e4minen")
        (update-in parties-path select-keys [:suunnittelijatieto]))))

(defn parties-to-canonical [application lang]
  (-> (rl-canonical/application-to-canonical application lang)
      strip-canonical-for-parties
      transform-to-designers-message))

(defmethod permit/parties-krysp-mapper :R [application lang krysp-version output-dir]
  (let [canonical (parties-to-canonical application lang)
        xml       (rl-mapping/rakennuslupa-element-to-xml canonical krysp-version)]
    (writer/write-to-disk application nil xml krysp-version output-dir)
    true))
