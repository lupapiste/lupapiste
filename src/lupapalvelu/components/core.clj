(ns lupapalvelu.components.core)

(defn get-component [components c]
  (or (components c) (throw (Exception. (format "Unknown component: %s" (name c))))))

(defn get-component-deps [components c]
  (or (:depends (get-component components c)) []))

(defn exclude [found depends]
  (filter (comp not (set found)) depends))

(defn get-dependencies [components found c]
  (conj (reduce (partial get-dependencies components) found (exclude found (get-component-deps components c))) c))

(defn component-resources [components kind c]
  (map (partial str (name c) \/) (kind (get-component components c))))

(defn get-resources [components kind c]
  (mapcat (partial component-resources components kind) (get-dependencies components [] c)))
