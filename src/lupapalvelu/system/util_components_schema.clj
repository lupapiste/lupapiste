(ns lupapalvelu.system.util-components-schema
  (:require [lupapalvelu.system.config :as config]
            [lupapalvelu.system.util :as system-util]
            [schema.core :as sc :refer [defschema]]))

(defschema CommonComponents
  {(sc/optional-key :mongo)         sc/Any
   (sc/optional-key :mongo-watcher) sc/Any
   (sc/optional-key :elastic)       sc/Any
   (sc/optional-key :laundry)       sc/Any
   (sc/optional-key :config)        config/Config
   (sc/optional-key :publishers)    sc/Any})

(def valid-components! (system-util/config-validator! CommonComponents))
