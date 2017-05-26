(ns lupapalvelu.ui.matti.path
  (:require [rum.core :as rum]
            [lupapalvelu.ui.common :as common]
            [clojure.string :as s]))

(defn state [path state]
  (rum/cursor-in state (map keyword path)))

(defn extend [path & extra]
  (->> (concat [path] extra)
       flatten
       (remove nil?)
       (map keyword)))

(defn id [path]
  (s/replace (->> (filter identity path)
                  (map name)
                  (s/join "-"))
             "." "-"))

(defn loc [path]
  (->> (filter identity path)
       (map name)
       (s/join ".")
       common/loc))

(defn ancestor-meta [path state meta-key]
  )
