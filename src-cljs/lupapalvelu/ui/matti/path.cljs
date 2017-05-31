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

(defn latest
  "The latest non-nil value for the given key in the state* along the
  path. Path is shortened until the value is found. Nil if value not
  found. Key can be also be key sequence."
  [path state* & key]
  (let [v @(state (extend path key) state*) ]
    (if (or (some? v) (empty? path))
      v
      (latest (drop-last path) state* key))))

(defn meta?
  "Truthy if _meta value for the given key is found either within
  _meta options or as latest _meta from the state."
  [{:keys [state path _meta]} key]
  (or (get _meta (keyword key))
      (latest path state :_meta key)))

(defn flip-meta
  "Flips (toggles boolean) on the path _meta."
  [{state* :state path :path} key]
  (swap! (state (extend path :_meta key) state*) not))

(defn css
  "List of CSS classes based on current :css value and status
  classes (matti--edit, matti--view) from the latest _meta."
  [options & other-classes]
  (->> [(if (meta? options :editing?) "matti--edit" "matti--view")
        (:css options)
        other-classes]
       flatten
       (remove nil?)
       (map name)))
