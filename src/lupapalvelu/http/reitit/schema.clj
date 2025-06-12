(ns lupapalvelu.http.reitit.schema
  (:require [lupapalvelu.system.util :as system-util]
            [schema.core :as sc :refer [defschema]]
            [sade.schemas :as ssc]))

(defschema RoutesOptions
  {(sc/optional-key :refresh?)        sc/Bool
   (sc/optional-key :default-handler) ssc/Fn
   :components                        sc/Any
   :session-store                     sc/Any
   :cookie-name                       ssc/NonBlankStr})

(def valid-routes-opts! (system-util/config-validator! RoutesOptions))
