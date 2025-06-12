(ns lupapalvelu.backing-system.dynasty.api.info
  (:require [lupapalvelu.backing-system.dynasty.api.client :as client]
            [lupapalvelu.json :as json]
            [sade.schemas :as ssc]
            [schema.core :as sc]))

(sc/defschema VersionResponse
  {:appname ssc/NonBlankStr
   :version ssc/NonBlankStr})

(sc/defn version :- VersionResponse
  "> Returns externalinterface.oauth version information.
   Does not require authentication."
  [client :- client/Client
   server-config :- client/ServerConfig]
  (client/get! client "/info" (dissoc server-config :auth)))

(sc/defn dynasty-info :- ssc/NonBlankStr
  "> Returns dynasty version information.
   Does not require authentication."
  [client :- client/Client
   server-config :- client/ServerConfig]
  ;; The API response seems to be double escaped
  (-> (client/get! client "/dynastyinfo" (dissoc server-config :auth))
      json/decode))
