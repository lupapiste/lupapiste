(ns sade.core
  (:require [slingshot.slingshot :refer [throw+ try+]]
            [sade.strings :as ss]))

(defn fail [text & args]
  (let [map-args (if (map? (first args)) (first args) (apply hash-map args))]
    (merge map-args {:ok false :text (name text)})))

(defmacro fail! [& args]
  `(throw+ (merge (fail ~@args) {::type ::fail ::file ~*file* ::line ~(:line (meta &form))})))

(defn ok [& kv]
  (assoc (apply hash-map kv) :ok true))

(def unauthorized (fail :error.unauthorized))
(defn unauthorized!
  ([] (fail! :error.unauthorized))
  ([desc] (fail! :error.unauthorized :desc desc)))

(defn ok? [m] (= (:ok m) true))

(defn now [] (System/currentTimeMillis))

(defmacro def- [item value]
  `(def ^{:private true} ~item ~value))

(defn update-in-repeating
  ([m [k & ks] f & args]
    (if (every? (comp ss/numeric? name) (keys m))
      (apply hash-map (mapcat (fn [[repeat-k v]] [repeat-k (apply update-in-repeating v (conj ks k) f args)] ) m))
      (if ks
        (assoc m k (apply update-in-repeating (get m k) ks f args))
        (assoc m k (apply f (get m k) args))))))