(ns lupapalvelu.backing-system.asianhallinta.core
  (:require [lupapalvelu.backing-system.asianhallinta.asianhallinta-mapping :as ah-mapping]
            [lupapalvelu.backing-system.integration-config :as integration-config]
            [lupapalvelu.logging :refer [error]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :refer [permit-type]]
            [lupapalvelu.xml.validator :as v]
            [monger.operators :refer [$elemMatch $set]]
            [sade.core :refer [fail!]]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn asianhallinta-enabled? [scope]
  (true? (get-in scope [:caseManagement :enabled])))

;; Compatibility with the generic `integration-config` API

(defn- available?
  [scope]
  (-> scope :caseManagement :ftpUser some?))

(defmethod integration-config/available-targets :sftp
  [_ _]
  ;; The caseManagement configuration doesn't really offer a way to classify
  ;; them as "configured" vs "not configured" as all the keys are always present
  ;; (see `lupapalvelu.sftp.core/update-organization-configuration`)
  #{})

(defmethod integration-config/current-configs :sftp
  [_ organization]
  (->> organization
       :scope
       (filter available?)
       ;; Note that asianhallinta configs are permit-type + municipality -specific.
       ;; Here we make the assumption that within each organization all asianhallinta
       ;; configs for the same permit type are identical, regardless of the scoped
       ;; municipality
       (map (fn [{:keys [permitType municipality caseManagement]}]
              {:id               (format "%s-%s" municipality permitType)
               :integration-type :sftp
               :targets          #{permitType}
               :options          (-> {:version (-> caseManagement :version)}
                                     util/strip-nils)
               :enabled?         (-> caseManagement :enabled boolean)}))))

(defmethod integration-config/upsert-config! :sftp
  [{{{:keys [version]} :options
     :keys             [targets enabled?]} :config
    :keys                                  [organization-id]}]
  ;; `lupapalvelu.sftp.core/update-organization-configuration` already
  ;; handles the insertion logic so all we need to do is handle updating the scope
  (mongo/update-by-query
    :organizations
    {:_id   organization-id
     ;; Note that we ignore the municipality
     :scope {$elemMatch {:permitType (first targets)}}}
    {$set {:scope.$.caseManagement.enabled enabled?
           :scope.$.caseManagement.version version}}))

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
