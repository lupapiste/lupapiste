(ns lupapalvelu.xml.krysp.application-as-krysp-to-backing-system
  (:require
    [lupapalvelu.xml.krysp.rakennuslupa-mapping :as rl-mapping]
    [lupapalvelu.xml.krysp.yleiset-alueet-mapping :as ya-mapping]))

(defn save-application-as-krysp [application lang submitted-application organization]
  (assert (= (:id application) (:id submitted-application)) "Not same application ids.")
  (let [permit-type (keyword (permit/permit-type application))
        krysp-fn (condp = permit-type
                   :YA ya-mapping/save-application-as-krysp
                   :R  rl-mapping/save-application-as-krysp)]
    (krysp-fn application (-> command :data :lang) submitted-application organization))
