(ns lupapalvelu.xml.asianhallinta.core
  (:require [taoensso.timbre :refer [error]]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :refer [permit-type]]
            [lupapalvelu.xml.asianhallinta.asianhallinta-mapping :as ah-mapping]
            [lupapalvelu.xml.validator :as v]
            [sade.core :refer [fail! def-]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]))

(def ah-from-dir "/asianhallinta/from_lupapiste")

(def- begin-of-link (str (env/value :fileserver-address) ah-from-dir "/"))

(defn asianhallinta-enabled? [scope]
  (true? (get-in scope [:caseManagement :enabled])))

(defn is-asianhallinta-version?
  "Check if version is supported. Requires scope to resolve version by permit-type"
  [version scope]
  (let [versions (util/convert-values
                     v/supported-asianhallinta-versions-by-permit-type
                     (partial map #(ss/suffix % "ah-")))] ; remove "ah-" prefixes
    (some #(= version %) (get versions (-> scope :permitType keyword)))))

(defn- resolve-ah-version
  "Resolves asianhallinta version from organization's scope"
  [scope]
  {:pre [scope]}

  (when-not (asianhallinta-enabled? scope)
    (error "Case management not enabled for scope: municipality: " (:municipality scope) ", permit-type: " (:permitType scope))
    (fail! :error.integration.asianhallinta-disabled))

  (let [ah-version (get-in scope [:caseManagement :version])]
    (if-not (ss/blank? ah-version)
      (do
       (when-not (is-asianhallinta-version? ah-version scope)
         (error (str \' ah-version "' is unsupported Asianhallinta version, municipality: " (:municipality scope)))
         (fail! :error.integration.asianhallinta-version-wrong-form))
       ah-version)
      (do
        (error (str "Asianhallinta version not found for scope: municipality:" (:municipality scope) ", permit-type: " (:permitType scope)))
        (fail! :error.integration.asianhallinta-version-missing)))))

(defn resolve-output-directory [scope]
  {:pre  [scope]
   :post [%]}
  (if-let [sftp-user (get-in scope [:caseManagement :ftpUser])]
    (str (env/value :outgoing-directory) "/" sftp-user ah-from-dir)
    (do
      (error (str "Asianhallinta SFTP user is not set for municipality " (:municipality scope) " , permit " (:permitType scope)))
      (fail! :error.sftp.user-not-set))))

(defn save-as-asianhallinta
  "Saves application as asianhallinta"
  [application lang submitted-application organization]
  (assert (= (:id application) (:id submitted-application)) "Not same application ids.")
  (let [permit-type   (permit-type application)
        scope         (organization/resolve-organization-scope (:municipality application) permit-type)
        ah-version    (resolve-ah-version scope)
        output-dir    (resolve-output-directory scope)]
    (ah-mapping/uusi-asia-from-application application lang ah-version submitted-application begin-of-link output-dir)))

(defn save-as-asianhallinta-asian-taydennys
  "Saves attachments to asianhallinta"
  [application attachments lang]
  (let [permit-type   (permit-type application)
        scope         (organization/resolve-organization-scope (:municipality application) permit-type)
        ah-version    (resolve-ah-version scope)
        output-dir    (resolve-output-directory scope)]
    (ah-mapping/taydennys-asiaan-from-application application attachments lang ah-version begin-of-link output-dir)))
