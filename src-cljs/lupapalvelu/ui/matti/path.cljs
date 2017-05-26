(ns lupapalvelu.ui.matti.path
  (:require [rum.core :as rum]
            [clojure.string :as s]))

(defn state [path state]
  (rum/cursor-in state (map keyword path)))

(defn extend [path & extra]
  (->> (concat [path] extra)
       flatten
       (remove nil?)
       (map keyword)))

(defn id [path]
  (s/join "-" path))
