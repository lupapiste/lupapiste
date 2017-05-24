(ns lupapalvelu.ui.matti.state
  (:require [rum.core :as rum]))

(defn data-cursor
  "Cursor into state for id. If data has id key and the state has not
  then the value is copied to state."
  [state id data]
  (let [kid (keyword id)
        c   (rum/cursor-in state [kid])]
    (when (and (not (kid @state)) (kid data))
      (reset! c (kid data)))
    c))

(defn state-schema-options
  "Updates options with new schema and corresponding state.
  Options argument includes old schema name and data."
  [{:keys [state schema data] :as options} full-schema]
  (assoc options
         :schema full-schema
         :state (data-cursor state schema data)))
