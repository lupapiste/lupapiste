(ns lupapalvelu.ui.admin.import-export.impl
  (:require [schema.core :as sc]
            [lupapalvelu.admin.import-export.model :as model]))

(defmulti -render-items
  "Render multiple items in a read-only view (e.g. in a table)"
  (fn [target _items]
    target))

(sc/defn render-items
  [target :- model/Targets
   items :- {sc/Str sc/Any}]
  (-render-items target items))

(defmulti -select-items
  "Render multiple items in a selector (e.g. in a table)"
  identity)

(sc/defn select-items
  [target :- model/Targets]
  (-select-items target))
