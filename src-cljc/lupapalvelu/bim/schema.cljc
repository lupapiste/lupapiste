(ns lupapalvelu.bim.schema
  "BIM (IFC file) schemas.
  See also `lupapalvelu.bim.checking-plan.schema` for the admin config schemas."
  (:require [schema.core :as sc]))

(def model-types
  "Type of BIM model used by the linked-file-metadata"
  [:architectural :structural :location :technical])

(def model-phases
  "Phase of application that a BIM model is tied to; basically the model of the building
  before (submitted plan), during (amended plan) or after (actually completed plan) construction.
  The list is in chronological order when considering a single model."
  [:submission-time :construction-time :completion-time])

(def post-verdict-model-phases
  "Model phases that correspond to post-verdict application states."
  #{:construction-time :completion-time})

(sc/defschema ModelType
  (->> (map name model-types)
       (apply sc/enum)))
