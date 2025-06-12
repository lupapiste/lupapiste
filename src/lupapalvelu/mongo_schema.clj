(ns lupapalvelu.mongo-schema
  (:require [lupapalvelu.system.util :as system-util]
            [sade.schemas :as ssc]
            [schema.core :as sc :refer [defschema]]))

(defschema ConnectionArgs
  {;; Default db, used for authentication
   :dbname                                          ssc/NonBlankStr
   ;; Optional current db, for e.g. tests
   (sc/optional-key :current-db)                    ssc/NonBlankStr
   :servers                                         {sc/Keyword {:host ssc/NonBlankStr
                                                                 :port sc/Int}}
   (sc/optional-key :credentials)                   {(sc/optional-key :username) ssc/NonBlankStr
                                                     (sc/optional-key :password) ssc/NonBlankStr}
   (sc/optional-key :ssl-enabled)                   sc/Bool
   (sc/optional-key :ssl-invalid-host-name-allowed) sc/Bool})

(def valid-connection-args! (system-util/config-validator! ConnectionArgs))
