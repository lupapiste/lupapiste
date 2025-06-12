(ns lupapalvelu.laundry.client-schema
  (:require [lupapalvelu.system.util :as system-util]
            [sade.schemas :as ssc]
            [schema.core :as sc :refer [defschema]]))

(defschema LaundryOpts
  {:url                                   ssc/NonBlankStr
   :iap-oauth?                            sc/Bool
   (sc/optional-key :iap-oauth-client-id) ssc/NonBlankStr})

(def valid-args! (system-util/config-validator! LaundryOpts))
