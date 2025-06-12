(ns lupapalvelu.visma-sign-schema
  (:require [sade.shared-schemas :as ssc]
            [schema.core :as sc :refer [defschema]]))

(defschema Config
  {:base-url                            ssc/NonBlankStr
   (sc/optional-key :client-identifier) ssc/NonBlankStr
   (sc/optional-key :secret)            ssc/NonBlankStr
   :success                             {:email ssc/Email}})
