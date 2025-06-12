(ns lupapalvelu.http.noir.schema
  (:require [lupapalvelu.system.util :as system-util]
            [schema.core :as sc :refer [defschema]]))

(defschema NoirOptions
  {:mode                                   (sc/enum :dev :test:smoke :qa :prod)
   :ns                                     sc/Symbol
   :resource-options                       {:mime-types {sc/Str sc/Str}}
   (sc/optional-key :session-cookie-attrs) {:secure    sc/Bool
                                            :same-site sc/Keyword
                                            :http-only sc/Bool}})

(def valid-noir-opts! (system-util/config-validator! NoirOptions))
