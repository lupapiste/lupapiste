(ns sade.core
  (:require [lupapalvelu.logging :refer [error]]
            [schema.core :as sc :refer [defschema]]
            [slingshot.slingshot :refer [throw+]]))

(set! *warn-on-reflection* true)

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

(defschema Fail
  {:ok        (sc/eq false)
   :text      sc/Str
   sc/Keyword sc/Any})

(defschema Success
  {:ok        (sc/eq true)
   sc/Keyword sc/Any})

(defschema Response
  (sc/conditional
    (comp false? :ok) Fail
    :else Success))

(def unauthorized (fail :error.unauthorized))
(defn unauthorized!
  ([] (fail! :error.unauthorized))
  ([desc] (fail! :error.unauthorized :desc desc)))

(def not-accessible (fail :error.application-not-accessible))

(defn ok? [m] (true? (:ok m)))
(defn fail? [m] (false? (:ok m)))

(defn now ^Long [] (System/currentTimeMillis))

(defmacro def- [item & args]
  `(def ~(with-meta item (assoc (meta item) :private true)) ~@args))

(defn error-and-fail! [error-msg fail-key]
  (error error-msg)
  (fail! fail-key))
