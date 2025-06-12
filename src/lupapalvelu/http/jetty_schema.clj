(ns lupapalvelu.http.jetty-schema
  (:require [lupapalvelu.system.util :as system-util]
            [sade.schemas :as ssc]
            [schema.core :as sc :refer [defschema]]))

(defschema JettyOptions
  {:join?                          sc/Bool
   (sc/optional-key :key-password) ssc/NonBlankStr
   (sc/optional-key :keystore)     ssc/NonBlankStr
   (sc/optional-key :max-threads)  sc/Int
   :port                           sc/Int
   :send-server-version?           sc/Bool
   (sc/optional-key :ssl-port)     sc/Int
   (sc/optional-key :ssl?)         sc/Bool})

(defschema JettyArgs
  {:handler (sc/pred fn? 'http-handler)
   :opts    JettyOptions})

(def valid-jetty-args! (system-util/config-validator! JettyArgs))
