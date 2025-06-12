(ns lupapalvelu.backing-system.integration
  (:require [lupapalvelu.backing-system.schema :as schemas]
            [lupapalvelu.backing-system.dynasty]
            [lupapalvelu.backing-system.sftp]))

(def integration-types
  "Set of registered integration types"
  (-> schemas/get-properties
      methods
      keys
      set))

(defn- get-coercer
  [json-config]
  (some-> json-config
          (update :integration-type keyword)
          schemas/get-props
          :integration/coercer))

(defn json-config->config
  "Attempts to coerce the JSONish config to a proper integration config.
   Note that the coercer *may* permit missing options. Use `valid-config?` to
   check all options are specified."
  [json-config]
  (try
    (when-let [coercer (get-coercer json-config)]
      (coercer json-config))
    (catch #?(:clj Exception :cljs js/Error) _
      nil)))

(defn valid-config?
  [config]
  (if-let [checker (some-> config schemas/get-props :integration/checker)]
    (nil? (checker config))
    false))

(defn- integration-type->properties
  [integration-type]
  (some-> {:integration-type integration-type} schemas/get-props))

(defn integration-type->name
  [integration-type]
  (some-> integration-type integration-type->properties :integration/name))

(defn integration-type->options
  [integration-type]
  (some-> integration-type integration-type->properties :integration/options))

(defn integration-type->single-target?
  [integration-type]
  (boolean (some-> integration-type integration-type->properties :integration/single-target?)))
