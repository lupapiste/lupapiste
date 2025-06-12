(ns lupapalvelu.elasticsearch.client-schema
  (:require [lupapalvelu.system.util :as system-util]
            [schema.core :as sc :refer [defschema]]))

(defschema IndexSettings
  {:analysis sc/Any})

(defschema IndexConfig
  {:name                       sc/Keyword
   :version                    {:major sc/Int
                                :minor sc/Int}
   (sc/optional-key :settings) IndexSettings})

(defschema ElasticConfig
  {:enabled?                                 sc/Bool
   :settings                                 {:shards   sc/Int
                                              :replicas sc/Int}
   :hosts                                    {sc/Keyword sc/Str}
   :indices                                  [IndexConfig]
   (sc/optional-key :mongo-watcher-enabled?) sc/Bool
   (sc/optional-key :testing?)               sc/Bool
   (sc/optional-key :index-prefix)           sc/Str})

(def valid-elastic-config! (system-util/config-validator! ElasticConfig))
