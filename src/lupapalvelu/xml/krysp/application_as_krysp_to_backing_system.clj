(ns lupapalvelu.xml.krysp.application-as-krysp-to-backing-system
  (:require [lupapalvelu.xml.krysp.rakennuslupa-mapping :as rl-mapping]
            [lupapalvelu.xml.krysp.yleiset-alueet-mapping :as ya-mapping]
            [lupapalvelu.xml.krysp.poikkeamis-mapping :as p-mapping]
            [lupapalvelu.permit :as permit]
            [sade.env :as env]
            [me.raynes.fs :as fs]))

(defn save-application-as-krysp [application lang submitted-application organization]
  (assert (= (:id application) (:id submitted-application)) "Not same application ids.")

  (let [permit-type (keyword (permit/permit-type application))
        krysp-fn (condp = permit-type
                   :YA ya-mapping/save-application-as-krysp
                   :R  rl-mapping/save-application-as-krysp
                   :P   p-mapping/save-application-as-krysp)
        sftp-user ((condp = permit-type
                     :YA :yleiset-alueet-ftp-user
                     :R  :rakennus-ftp-user
                     :P  :poikkari-ftp-user)
                    organization)
        sftp-directory (condp = permit-type
                         :YA "/yleiset_alueet"
                         :R  "/rakennus"
                         :P  "/poikkeusasiat")
        dynamic-part-of-outgoing-directory (str sftp-user sftp-directory)
        output-dir (str (env/value :outgoing-directory) "/" dynamic-part-of-outgoing-directory)
        fileserver-address (env/value :fileserver-address)
        begin-of-link (str fileserver-address sftp-directory "/")]

    (krysp-fn application lang submitted-application output-dir begin-of-link)))
