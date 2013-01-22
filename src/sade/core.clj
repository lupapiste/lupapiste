(ns sade.core)

(defn fail [text & bindings]
  {:ok false :text (apply format (name text) bindings)})

(defn ok [& kv]
  (merge (apply hash-map kv) {:ok true}))

(defn ok? [m] (= (:ok m) true))

(defn now [] (System/currentTimeMillis))
