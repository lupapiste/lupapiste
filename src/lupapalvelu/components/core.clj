(ns lupapalvelu.components.core)

(defn get-component [components c]
  (or (components c) (throw (Exception. (format "Unknown component: %s" (name c))))))

(defn exclude [found depends]
  (filter (comp not (set found)) depends))

(defn get-dependencies [components found component]
  (conj (reduce (partial get-dependencies components) found (exclude found (or (:depends (get-component components component)) []))) component))

(defn component-resources [components kind component]
  (map (partial str (name component) \/) (kind (get-component components component))))

(defn get-resources [components kind component]
  (mapcat (partial component-resources components kind) (get-dependencies components [] component)))
