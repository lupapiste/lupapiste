(ns lupapalvelu.pate.metadata
  (:require [clojure.set :as set]
            [clojure.walk :as walk]
            [sade.schemas :as ssc]
            [schema.core :refer [defschema] :as sc]))

(defschema WrappedValue
  {:_value    sc/Any
   :_user     sc/Str
   :_modified ssc/Timestamp})

(defn- wrapped? [m]
  (and (map? m)
       (= (set (keys m))
          (set (keys WrappedValue)))))

(defn wrap
  "Maps value with given user and timestamp information. Maps are not
  wrapped since they are considered as (repeating) branches. Thus, if
  you want to rewrap value, it should be unwrapped first."
  [user modified value]
  (if (map? value)
    value
    (sc/validate WrappedValue
                 {:_value    value
                  :_user     user
                  :_modified modified})))

(defn unwrap
  "Returns the wrapped underlying value. Unwrapped argument is
  returned unchanged."
  [m]
  (cond-> m
    (wrapped? m) :_value))

(defn wrapper
  "Returns the wrap function for given metadata."
  ([user modified]
   (partial wrap user modified))
  ([{:keys [user created]}]
   (wrapper (:username user) created)))

(defn unwrap-all
  "Unwraps recursively the whole given structure."
  [m]
  (walk/prewalk unwrap m))

(defn wrap-all
  "Wraps the whole structure. For each branch the wrapping stops at the
  first wrap. Branches are maps, all other data types are leaves."
  [wrap-fn m]
  (if (map? m)
    (reduce-kv (fn [acc k v]
                 (assoc acc k (wrap-all wrap-fn v)))
               {}
               m)
    (wrap-fn m)))
