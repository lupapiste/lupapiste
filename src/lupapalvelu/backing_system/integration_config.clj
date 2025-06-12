(ns lupapalvelu.backing-system.integration-config
  "Provides a somewhat generic API for managing (backing system) integration configs"
  (:require [lupapalvelu.backing-system.integration :as integration]
            [lupapalvelu.backing-system.schema :as schemas]
            [schema.core :as sc]))

(defmulti available-targets
  "Check which targets (= permit types) are candidates for integration.

   Should not return any targets where an integration (of the same type) has already
   been defined."
  (fn [integration-type _organization]
    integration-type))

(sc/defn all-available-targets :- {schemas/IntegrationType #{schemas/Target}}
  "Return all available integration targets which do not yet have an integration specified.

   Note: We do not cross-check target availability across different integration types."
  [organization]
  (->> integration/integration-types
       (keep (fn [integration-type]
               (let [targets (available-targets integration-type organization)]
                 (when (seq targets)
                   [integration-type targets]))))
       (into {})))

(defmulti current-configs
  "Find all integrations for `integration-type`."
  (fn [integration-type _organization]
    integration-type))

(defn- current-integrations
  [organization]
  (->> integration/integration-types
       (mapcat #(current-configs % organization))
       (into [])))

(sc/defschema OrganizationConfigs
  {:available {schemas/IntegrationType #{schemas/Target}}
   :configs   [schemas/CommonIntegrationConfig]})

(sc/defn organization->configs :- OrganizationConfigs
  {:always-validate true}
  [organization]
  {:available (all-available-targets organization)
   :configs   (current-integrations organization)})

(defmulti upsert-config!
  "Insert or update the given config"
  (fn [{:keys [config]}]
    (:integration-type config)))
