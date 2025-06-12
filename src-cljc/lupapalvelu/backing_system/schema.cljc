(ns lupapalvelu.backing-system.schema
  "Provides tools to describe integration types as data.

   NOTE: These schemas describe the data shapes passed between the *frontend*
   and the *backend*. They do not necessarily correspond 1:1 with the internal
   representation of the data (e.g. what :options actually get written/read to/from mongo)."
  (:require [sade.shared-schemas :as ssc]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(sc/defschema TranslationKey
  sc/Keyword)

;; Schemas for integration types in general

(sc/defschema OptionSpec
  "Describe a single configurable option as data (e.g. a `username` option)"
  (st/merge
    {:option/name   sc/Keyword
     :option/type   sc/Keyword
     :option/schema sc/Any
     :option/label  TranslationKey}
    (st/optional-keys
      {:option/placeholder TranslationKey
       ;; By default the options are mandatory
       :option/optional?   sc/Bool})))

(sc/defschema IntegrationProperties
  "Describe an integration type as data (e.g. a `dynasty` integration)"
  {:integration/name                             TranslationKey
   :integration/options                          [OptionSpec]
   :integration/schema                           sc/Any
   :integration/checker                          (sc/pred fn?)
   :integration/coercer                          (sc/pred fn?)
   (sc/optional-key :integration/single-target?) sc/Bool})

;; Schemas for instances of integration configurations

(sc/defschema IntegrationType
  sc/Keyword)

(sc/defschema Target
  "Target of the integration (a permit type)"
  ssc/NonBlankStr)

(sc/defschema CommonIntegrationConfig
  "The common fields each integration config (regardless of type) share.
   Use `get-props` to obtain the exact schema, which also includes the `:options`."
  {(sc/optional-key :id)         ssc/NonBlankStr
   :integration-type             IntegrationType
   :targets                      #{Target}
   :enabled?                     sc/Bool
   (sc/optional-key :created-at) ssc/Timestamp
   :options                      {sc/Any sc/Any}})

(sc/defn schema-with-options
  "Constructs a complete schema given the integration-type specific options"
  ([options :- [OptionSpec]]
   (schema-with-options options false))
  ([options :- [OptionSpec]
    optional? :- sc/Bool ]
   (let [make-optional (if optional? st/optional-keys identity)]
     (st/merge CommonIntegrationConfig
               {:options (->> options
                              (map (fn [{:option/keys [name schema optional?]}]
                                     (if optional?
                                       [(sc/optional-key name) schema]
                                       [name schema])))
                              (into {})
                              make-optional)}))))

(defmulti get-properties :integration-type)

(sc/defn get-props :- IntegrationProperties
  "Given an integration config (or at least the type) return the properties that describe the integration type in general"
  {:always-validate true}
  [config :- (st/open-schema {:integration-type sc/Keyword})]
  (get-properties config))
