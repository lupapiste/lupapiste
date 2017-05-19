(ns lupapalvelu.matti.shared
  (:require [schema.core :refer [defschema] :as sc]))

(defschema MattiVerdictSection
  {:id     sc/Str
   :title  sc/Str   ;; Localization key
   :schema sc/Str   ;; Name of section content schema
   :data   sc/Any}) ;; Data must conform to the schema

(defschema MattiVerdict
  {(sc/optional-key :id) sc/Str  ;; Id is created when the verdict is saved the first time
   :name                 sc/Str  ;; Non-localized raw string
   :sections             [MattiVerdictSection]})

(def default-verdict-template
  {:name     ""
   :sections [{:id     "verdict-code"
               :title  "verdict.status"
               :schema "matti-verdict-code"
               :data   {:matti-verdict-code "myonnetty"}}]})
