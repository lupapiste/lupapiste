(ns lupapalvelu.xml.krysp.application-as-krysp-to-backing-system
  (:require [sade.env :as env]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            ;; Make sure all the mappers are registered
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :as rl-mapping]
            [lupapalvelu.xml.krysp.poikkeamis-mapping]
            [lupapalvelu.xml.krysp.yleiset-alueet-mapping :as ya-mapping]))

(defn- get-begin-of-link [permit-type]
  {:pre  [permit-type]
   :post [%]}
  (str (env/value :fileserver-address) (permit/get-sftp-directory permit-type) "/"))

(defn resolve-output-directory [organization permit-type]
  {:pre  [organization permit-type]
   :post [%]}
  (let [sftp-user (get-in organization [:krysp (keyword permit-type) :ftpUser])]
    (str (env/value :outgoing-directory) "/" sftp-user (permit/get-sftp-directory permit-type))))

(defn resolve-krysp-version [organization permit-type]
  {:pre [organization permit-type]}
  (if-let [krysp-version (get-in organization [:krysp (keyword permit-type) :version])]
    krysp-version
    (throw (IllegalStateException. (str "KRYSP version not found for organization " (:id organization) ", permit-type " permit-type)))))

(defn save-application-as-krysp [application lang submitted-application organization]
  (assert (= (:id application) (:id submitted-application)) "Not same application ids.")
  (let [permit-type   (permit/permit-type application)
        krysp-fn      (permit/get-application-mapper permit-type)
        krysp-version (resolve-krysp-version organization permit-type)
        output-dir    (resolve-output-directory organization permit-type)
        begin-of-link (get-begin-of-link permit-type)]
    (krysp-fn application lang submitted-application krysp-version output-dir begin-of-link)))

(defn save-review-as-krysp [application task user lang]
  (let [permit-type   (permit/permit-type application)
        organization  (organization/get-organization (:organization application))
        krysp-fn      (permit/get-review-mapper permit-type)
        krysp-version (resolve-krysp-version organization permit-type)
        output-dir    (resolve-output-directory organization permit-type)
        begin-of-link (get-begin-of-link permit-type)]
    (krysp-fn application task user lang krysp-version output-dir begin-of-link)))

(defn save-unsent-attachments-as-krysp [application lang organization]
  (let [permit-type   (permit/permit-type application)
        krysp-version (resolve-krysp-version organization permit-type)
        output-dir    (resolve-output-directory organization permit-type)
        begin-of-link (get-begin-of-link permit-type)]
    (assert (= permit/R permit-type)
      (str "Sending unsent attachments to backing system is not supported for " (name permit-type) " type of permits."))
    (rl-mapping/save-unsent-attachments-as-krysp application lang krysp-version output-dir begin-of-link)))

(defn save-jatkoaika-as-krysp [application lang organization]
  (let [permit-type   (permit/permit-type application)
        krysp-version (resolve-krysp-version organization permit-type)
        output-dir    (resolve-output-directory organization permit-type)
        begin-of-link (get-begin-of-link permit-type)]
    (assert (= permit/YA permit-type)
      (str "Saving jatkoaika as krysp is not supported for " (name permit-type) " type of permits."))
    (ya-mapping/save-jatkoaika-as-krysp application lang organization krysp-version output-dir begin-of-link)))
