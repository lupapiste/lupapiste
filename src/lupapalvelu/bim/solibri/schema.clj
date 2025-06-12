(ns lupapalvelu.bim.solibri.schema
  "Schemas for responses coming from the Solibri BIM integration"
  (:require [schema.core :as sc]))

(sc/defschema CheckingJobResponse
              {:id        sc/Str
               :message   sc/Str
               sc/Keyword sc/Any})

