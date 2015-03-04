(ns lupapalvelu.xml.asianhallinta.core
  (:require [taoensso.timbre :refer [error]]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :refer [permit-type]]
            [lupapalvelu.xml.asianhallinta.uusi_asia_mapping :as ua-mapping]
            [sade.core :refer [fail! def-]]
            [sade.env :as env]))

(def ah-from-dir "/asianhallinta/from_lupapiste")

(def- begin-of-link (str (env/value :fileserver-address) ah-from-dir "/"))

(defn- asianhallinta-enabled? [scope]
  (true? (get-in scope [:caseManagement :enabled])))

(defn- resolve-ah-version [scope]
  "Resolves asianhallinta version from organization's scope"
  {:pre [scope]}

  (when-not (asianhallinta-enabled? scope)
    (error "Case management not enabled for scope: municipality: " (:municipality scope) ", permit-type: " (:permitType scope))
    (fail! :error.integration.asianhallinta-disabled))

  (if-let [ah-version (get-in scope [:caseManagement :version])]
    (do
      (when-not (re-matches #"\d+\.\d+" ah-version)
        (error (str \' ah-version "' does not look like a Asianhallinta version"))
        (fail! :error.integration.asianhallinta-version-wrong-form))
      ah-version)
    (do
      (error (str "Asianhallinta version not found for scope: municipality:" (:municipality scope) ", permit-type: " (:permitType scope)))
      (fail! :error.integration.asianhallinta-version-missing))))

(defn- resolve-output-directory [scope]
  {:pre  [scope]
   :post [%]}
  (when-let [sftp-user (get-in scope [:caseManagement :ftpUser])]
    (str (env/value :outgoing-directory) "/" sftp-user ah-from-dir)))

(defn save-as-asianhallinta [application lang submitted-application organization]
  "Saves application as asianhallinta"
  (assert (= (:id application) (:id submitted-application)) "Not same application ids.")
  (let [permit-type   (permit-type application)
        scope         (organization/resolve-organization-scope (:municipality application) permit-type)
        ah-version    (resolve-ah-version scope)
        output-dir    (resolve-output-directory scope)]
    (ua-mapping/uusi-asia-from-application application lang ah-version submitted-application begin-of-link output-dir)))

