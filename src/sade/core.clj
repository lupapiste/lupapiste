(ns sade.core
  (:require [slingshot.slingshot :refer [throw+ try+]]))

(defn fail [text & args]
  (let [map-args (if (map? (first args)) (first args) (apply hash-map args))]
    (merge map-args {:ok false :text (name text)})))

(defmacro fail! [& args]
  `(throw+ (merge (fail ~@args) {::type ::fail ::file ~*file* ::line ~(:line (meta &form))})))

(defn ok [& kv]
  (let [res (if (and (= 1 (count kv))
                     (-> kv first map?))
              (first kv)
              (apply hash-map kv))]
    (assoc res :ok true)))

(def unauthorized (fail :error.unauthorized))
(defn unauthorized!
  ([] (fail! :error.unauthorized))
  ([desc] (fail! :error.unauthorized :desc desc)))

(def not-accessible (fail :error.application-not-accessible))

(defn ok? [m] (= (:ok m) true))

(defn now [] (System/currentTimeMillis))

(defmacro def- [item value]
  `(def ^{:private true} ~item ~value))

