(ns lupapalvelu.xml.krysp.application-as-krysp-to-backing-system
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error fatal]]
            [sade.env :as env]
            [sade.core :refer [fail!]]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            ;; Make sure all the mappers are registered
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :as rl-mapping]
            [lupapalvelu.xml.krysp.poikkeamis-mapping]
            [lupapalvelu.xml.krysp.yleiset-alueet-mapping :as ya-mapping]
            [lupapalvelu.xml.krysp.ymparistolupa-mapping]
            [lupapalvelu.xml.krysp.maa-aines-mapping]
            [lupapalvelu.xml.krysp.maankayton-muutos-mapping]
            [lupapalvelu.xml.krysp.kiinteistotoimitus-mapping]
            [lupapalvelu.xml.krysp.ymparisto-ilmoitukset-mapping :as yi-mapping]
            [lupapalvelu.xml.krysp.vesihuolto-mapping :as vh-mapping]))

(defn- get-begin-of-link [permit-type]
  {:pre  [permit-type]
   :post [%]}
  (str (env/value :fileserver-address) (permit/get-sftp-directory permit-type) "/"))

(defn- resolve-output-directory [organization permit-type]
  {:pre  [organization permit-type]
   :post [%]}
  (let [sftp-user (get-in organization [:krysp (keyword permit-type) :ftpUser])]
    (str (env/value :outgoing-directory) "/" sftp-user (permit/get-sftp-directory permit-type))))

(defn- resolve-krysp-version [organization permit-type]
  {:pre [organization permit-type]}
  (if-let [krysp-version (get-in organization [:krysp (keyword permit-type) :version])]
    (do
      (when-not (re-matches #"\d+\.\d+\.\d+" krysp-version)
        (error (str \' krysp-version "' does not look like a KRYSP version"))
        (fail! :error.integration.krysp-version-wrong-form))
      krysp-version)
    (do
      (error (str "KRYSP version not found for organization " (:id organization) ", permit-type " permit-type))
      (fail! :error.integration.krysp-version-missing))))


(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [application lang submitted-application organization]
  (assert (= (:id application) (:id submitted-application)) "Not same application ids.")
  (let [permit-type   (permit/permit-type application)
        krysp-fn      (permit/get-application-mapper permit-type)
        krysp-version (resolve-krysp-version organization permit-type)
        output-dir    (resolve-output-directory organization permit-type)
        begin-of-link (get-begin-of-link permit-type)]
    (assert krysp-fn "KRYSP mapper function not found/defined?")
    (krysp-fn application lang submitted-application krysp-version output-dir begin-of-link)))

(defn save-review-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [application task user lang]
  (let [permit-type   (permit/permit-type application)
        organization  (organization/get-organization (:organization application))
        krysp-fn      (permit/get-review-mapper permit-type)
        krysp-version (resolve-krysp-version organization permit-type)
        output-dir    (resolve-output-directory organization permit-type)
        begin-of-link (get-begin-of-link permit-type)]
    (assert krysp-fn "KRYSP 'review mapper' function not found/defined?")
    (krysp-fn application task user lang krysp-version output-dir begin-of-link)))

(defn save-unsent-attachments-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [application lang organization]
  (let [permit-type   (permit/permit-type application)
        krysp-version (resolve-krysp-version organization permit-type)
        output-dir    (resolve-output-directory organization permit-type)
        begin-of-link (get-begin-of-link permit-type)]
    (assert (= permit/R permit-type)
      (str "Sending unsent attachments to backing system is not supported for " (name permit-type) " type of permits."))
    (rl-mapping/save-unsent-attachments-as-krysp application lang krysp-version output-dir begin-of-link)))

(defn save-jatkoaika-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [application lang organization]
  (let [permit-type   (permit/permit-type application)
        krysp-version (resolve-krysp-version organization permit-type)
        output-dir    (resolve-output-directory organization permit-type)
        begin-of-link (get-begin-of-link permit-type)]
    (assert (= permit/YA permit-type)
      (str "Saving jatkoaika as krysp is not supported for " (name permit-type) " type of permits."))
    (ya-mapping/save-jatkoaika-as-krysp application lang organization krysp-version output-dir begin-of-link)))

(defn save-aloitusilmoitus-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [application lang organization timestamp building user]
  (let [permit-type   (permit/permit-type application)
        krysp-version (resolve-krysp-version organization permit-type)
        output-dir    (resolve-output-directory organization permit-type)]
    (assert (= permit/R permit-type)
      (str "Sending aloitusilmoitus to backing system is not supported for " (name permit-type) " type of permits."))
    (rl-mapping/save-aloitusilmoitus-as-krysp application lang output-dir timestamp building user krysp-version)))

