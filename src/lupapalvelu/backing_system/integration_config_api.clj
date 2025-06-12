(ns lupapalvelu.backing-system.integration-config-api
  "Configuration API for (backing system) integration configuration"
  (:require [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.backing-system.integration :as integration]
            [lupapalvelu.backing-system.integration-config :as config]
            [lupapalvelu.logging :as log]
            [sade.core :refer [fail now ok]]
            [sade.strings :as ss]
            [sade.util :as util]))

(defquery integration-config
  {:description "Find all available integration configurations"
   :parameters  [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (try
    (if-let [organization (util/find-by-id organizationId user-organizations)]
      (ok :data (config/organization->configs organization))
      (fail :error.unknown-organization))
    (catch Exception e
      (log/errorf e "Failed to read organization integrations")
      (fail :error))))

(defn- command->config
  [command]
  (some-> command :data :config ss/trimwalk integration/json-config->config))

(defn- valid-config?
  [command]
  (if-let [config (command->config command)]
    (when-not (integration/valid-config? config)
      (fail :error.incomplete-configuration))
    (fail :error.invalid-configuration)))

(defcommand save-integration-config
  {:parameters       [organizationId config]
   :input-validators [valid-config?]
   :permissions      [{:required [:organization/admin]}]}
  [command]
  (let [config (command->config command)]
    (config/upsert-config! {:config          config
                            :organization-id organizationId
                            :now             (or (:created command) (now))})
    (ok)))
