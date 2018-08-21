(ns lupapalvelu.pate.metadata
  (:require [clojure.set :as set]
            [clojure.walk :as walk]
            [sade.schemas :as ssc]
            [schema.core :refer [defschema] :as sc]))

(defschema WrappedValue
  {:_value    sc/Any
   :_user     sc/Str
   :_modified ssc/Timestamp})

(defprotocol Wrapper
  (wrap [this value]
    "Wraps the given value with metadata")
  (unwrap [this m]
    "Returns the underlying/initial value. If m is not a wrapped value
    it is returned as it is.")
  (rewrap [this m]
    "Rewraps m only if it has been wrapped."))

(defn- wrapped? [m]
  (and (map? m)
       (= (set (keys m))
          (set (keys WrappedValue)))))

(deftype Metadata [user modified]
  Wrapper
  ;; Maps are not wrapped since they are considered as (repeating) branches.
  (wrap [_ value] (if (map? value)
                    value
                    (sc/validate WrappedValue
                                 {:_value    value
                                  :_user     user
                                  :_modified modified})))
  (unwrap [_ m] (cond-> m
                  (wrapped? m) :_value))
  (rewrap [this m] (cond-> m
                     (wrapped? m) (->>  :_value (wrap this)))))

(deftype Identity []
  Wrapper
  (wrap [_ value] value)
  (unwrap [_ m] m)
  (rewrap [_ m] m))

(defn wrapper
  ([user modified]
   (Metadata. user modified))
  ([{:keys [user created]}]
   (wrapper (:username user) created)))

(defn unwrap-all
  "Unwraps recursively the whole given structure."
  [wrapper m]
  (walk/prewalk (partial unwrap wrapper) m))

(defn rewrap-all
  "Rewraps recursively the whole given structure."
  [wrapper m]
  (walk/prewalk (partial rewrap wrapper) m))
