(ns lupapalvelu.xml.krysp.application-as-krysp-to-backing-system
  (:require [lupapalvelu.xml.krysp.rakennuslupa-mapping :as rl-mapping]
            [lupapalvelu.xml.krysp.yleiset-alueet-mapping :as ya-mapping]
            [lupapalvelu.xml.krysp.poikkeamis-mapping :as p-mapping]
            [lupapalvelu.permit :as permit]
            [sade.env :as env]
            [me.raynes.fs :as fs]))


(defn- get-sftp-directory [permit-type]
  (condp = permit-type
    :YA "/yleiset_alueet"
    :R  "/rakennus"
    :P  "/poikkeusasiat"))

(defn- get-output-directory [permit-type organization]
  (let [sftp-user ((condp = permit-type
                     :YA :yleiset-alueet-ftp-user
                     :R  :rakennus-ftp-user
                     :P  :poikkari-ftp-user)
                    organization)]
    (str
      (env/value :outgoing-directory)
      "/"
      sftp-user
      (get-sftp-directory permit-type))))

(defn- get-begin-of-link [permit-type]
  (str
    (env/value :fileserver-address)
    (get-sftp-directory permit-type)
    "/"))


(defn save-application-as-krysp [application lang submitted-application organization]
  (assert (= (:id application) (:id submitted-application)) "Not same application ids.")

  (let [permit-type (keyword (permit/permit-type application))
        krysp-fn (condp = permit-type
                   :YA ya-mapping/save-application-as-krysp
                   :R  rl-mapping/save-application-as-krysp
                   :P   p-mapping/save-application-as-krysp)
        output-dir (get-output-directory permit-type organization)
        begin-of-link  (get-begin-of-link permit-type)]
    (krysp-fn application lang submitted-application output-dir begin-of-link)))


(defn save-unsent-attachments-as-krysp [application lang organization user]
  (let [permit-type (keyword (permit/permit-type application))
        output-dir (get-output-directory permit-type organization)
        begin-of-link  (get-begin-of-link permit-type)]
    (assert (= :R permit-type)
      (str "Sending unsent attachments to backing system is not supported for " (name permit-type) " type of permits."))
    (rl-mapping/save-unsent-attachments-as-krysp application lang output-dir begin-of-link)))
