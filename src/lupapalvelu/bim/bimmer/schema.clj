(ns lupapalvelu.bim.bimmer.schema
  "Schemas for BIM integration responses coming from the bimmer service."
  (:require [schema-tools.core :as st]
            [schema.core :as sc]))

(sc/defschema ExtractionResponse
  (sc/conditional
    :data
    (st/merge {:response-to sc/Str
               :data        {:properties sc/Any}})
    :error
    (st/merge {:response-to sc/Str
               :error       sc/Str})))

(sc/defschema CheckingResponse sc/Any)

(def valid-extraction-response! (sc/validator ExtractionResponse))

(def valid-checking-response! (sc/validator CheckingResponse))
