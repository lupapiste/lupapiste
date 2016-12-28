(ns sade.core
  (:require [slingshot.slingshot :refer [throw+ try+]]))

(defn fail [text & args]
  (let [map-args (if (map? (first args)) (first args) (apply hash-map args))]
    (merge map-args {:ok false :text (name text)})))

(defmacro fail! [& args]
  `(throw+ (merge (fail ~@args) {::type ::fail ::file ~*file* ::line ~(:line (meta &form))})))

(defn ok [& kv]
  (let [first (first kv)
        res   (cond
                (map? first) first
                (nil? first) {}
                :else        (apply hash-map kv))]
    (assoc res :ok true)))

(def unauthorized (fail :error.unauthorized))
(defn unauthorized!
  ([] (fail! :error.unauthorized))
  ([desc] (fail! :error.unauthorized :desc desc)))

(def not-accessible (fail :error.application-not-accessible))

(defn ok? [m] (true? (:ok m)))
(defn fail? [m] (false? (:ok m)))

(defn now ^Long [] (System/currentTimeMillis))

(defmacro def- [item value]
  `(def ^{:private true} ~item ~value))
