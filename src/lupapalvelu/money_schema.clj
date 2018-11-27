(ns lupapalvelu.money-schema
  "This namespace holds schemas for money operations"
  (:require [schema.core :as s]))

(s/defschema MoneyResponse
  {:minor s/Num
   :major s/Num
   (s/optional-key :text) s/Str
   :currency s/Str})
