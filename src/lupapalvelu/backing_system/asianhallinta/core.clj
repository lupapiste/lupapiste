(ns lupapalvelu.backing-system.asianhallinta.core
  (:require [lupapalvelu.backing-system.asianhallinta.asianhallinta-mapping :as ah-mapping]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :refer [permit-type]]
            [lupapalvelu.xml.validator :as v]
            [sade.core :refer [fail!]]
            [sade.strings :as ss]
            [sade.util :as util]
            [taoensso.timbre :refer [error]]))

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

(defn save-as-asianhallinta
  "Saves application as asianhallinta"
  [application lang submitted-application organization]
  (assert (= (:id application) (:id submitted-application)) "Not same application ids.")
  (let [permit-type  (permit-type application)
        scope        (organization/resolve-organization-scope (:municipality application) permit-type organization)
        ah-version   (resolve-ah-version scope)
        organization (organization/get-organization (:organization application))]
    (ah-mapping/uusi-asia-from-application application organization lang ah-version submitted-application)))

(defn save-as-asianhallinta-asian-taydennys
  "Saves attachments to asianhallinta. Returns a sequence of attachment file IDs that were saved."
  [application attachments lang]
  (let [permit-type  (permit-type application)
        scope        (organization/resolve-organization-scope (:municipality application) permit-type)
        ah-version   (resolve-ah-version scope)
        organization (organization/get-organization (:organization application))]
    (ah-mapping/taydennys-asiaan-from-application application organization attachments lang ah-version)))

(defn save-statement-request
  [command submitted-application statement lang]
  (ah-mapping/statement-request command
                                submitted-application
                                statement
                                lang))
