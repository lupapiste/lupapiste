(ns lupapalvelu.matti.shared
  (:require [schema.core :refer [defschema] :as sc]))

(defschema MattiGrid
  {:columns (apply sc/enum (range 1 13)) ;; Grid size (.matti-grid-n)
   :rows    [[{(sc/optional-key :col)    sc/Int ;; Column width (.col-n). Default 1.
               (sc/optional-key :align)  (sc/enum :left :right :center :full)
               ;; Mandatory if child components update data. Id is
               ;; used as path part in the state.
               (sc/optional-key :id)     sc/Str
               (sc/optional-key :schema) (sc/conditional
                                          string? sc/Str      ;; Docgen schema name
                                          :else   sc/Keyword  ;; matti-schemas key
                                          )}]]})

(defschema MattiVerdictSection
  {:id     sc/Str  ;; Also title localization key
   :grid   MattiGrid
   :data   sc/Any}) ;; Data must conform to the grid contents

(defschema MattiVerdict
  {(sc/optional-key :id) sc/Str  ;; Id is created when the verdict is saved the first time
   :name                 sc/Str  ;; Non-localized raw string
   :sections             [MattiVerdictSection]})


(def matti-schemas {})

(def default-verdict-template
  {:name     ""
   :sections [{:id    "matti.verdict"
               :grid  {:columns 4
                       :rows    [[{;;:col    1
                                   :align  :full
                                   :id "select"
                                   :schema "matti-verdict-code"}]]}
               :data  {:matti-verdict-code "myonnetty"}}]})

(sc/validate MattiVerdict default-verdict-template)
