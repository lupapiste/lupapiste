(ns lupapalvelu.document.parties-canonical
  (:require [lupapalvelu.document.rakennuslupa-canonical :as rl-canonical]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.disk-writer :as writer]
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :as rl-mapping]))

(defn strip-canonical-for-parties
  "Dissoc application related data from canonical"
  [canonical]
  (update-in canonical
             [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia]
             dissoc :rakennuspaikkatieto :toimenpidetieto :lausuntotieto))

(defn parties-to-canonical [application lang]
  (->> (rl-canonical/application-to-canonical application lang)
       strip-canonical-for-parties))


(defmethod permit/parties-krysp-mapper :R [application lang krysp-version output-dir]
  (let [canonical (parties-to-canonical application lang)
        xml       (rl-mapping/rakennuslupa-element-to-xml canonical krysp-version)]
    (writer/write-to-disk application nil xml krysp-version output-dir)))
