(ns lupapalvelu.data-skeleton
  (:require [clojure.walk :refer [postwalk prewalk walk]]
            [sade.core :refer [def-]]
            [sade.util :as util]))

;;
;; Helpers for operating on the verdict skeleton
;;

(def- accessor-key? (comp boolean ::access))

(defn- build-id-map? [x]
  (boolean (::id-map-from x)))

(defn- build-array? [x]
  (boolean (::array-from x)))

(defn- assoc-context [element context]
  (postwalk (fn [x]
              (if (or (accessor-key? x)
                      (build-id-map? x)
                      (build-array? x))
                (assoc x ::context context)
                x))
            element))

(defn- build-id-map
  "Builds a collection skeleton dynamically based on the data present in the
   `application` and `verdict`."
  [context accessor-functions
   {{collection ::collection
     element    ::element} ::id-map-from}]
  (->> ((get accessor-functions collection) context)
       (group-by :id)
       (util/map-values (comp (partial assoc-context element) first))
       not-empty))

(defn- build-array
  [context accessor-functions
   {{collection ::collection
     element    ::element :as arr} ::array-from}]
  (->> context
       ((get accessor-functions collection (fn [_] (throw (ex-info "No accessor function" arr)))))
       (mapv (partial assoc-context element))))

(defn- fetch-with-accessor
  "Given the `application` under migration, the source `verdict` and
  current `timestamp`, returns a function for accessing desired data
  from the `application` and `verdict`. Used with `prewalk`."
  [context accessor-functions]
  (fn [x]
    (cond (accessor-key? x)
          (if-let [accessor-fn (get accessor-functions (::access x))]
            (accessor-fn (assoc context :context (::context x)))
            (throw (ex-info "Missing accessor" x)))

          (build-id-map? x)
          (build-id-map (assoc context :context (::context x))
                        accessor-functions x)

          (build-array? x)
          (build-array (assoc context :context (::context x))
                       accessor-functions x)

          :else x)))

;;
;; Public API
;;

(defn access [accessor-key]
  {::access accessor-key})


(defn id-map-from [collection-key element-skeleton]
  {::id-map-from {::collection collection-key
                  ::element element-skeleton}})

(defn array-from [collection-key element-skeleton]
  {::array-from {::collection collection-key
                 ::element element-skeleton}})

(defn build-with-skeleton [data-skeleton
                           initial-context
                           accessor-functions]
  (prewalk (fetch-with-accessor initial-context
                                accessor-functions)
           data-skeleton))

;;
;; Accessor building functions
;;

(defn from-context
  "Returns a function that produces a result from context by accessing
  values from given `path` in order. `path` can contain functions,
  keywords, sets (applied as functions), non-negative integers (used
  with `nth`).  A default value can be provided as an optional
  argument

  ((from-context [:foo 0 last])
    {:foo [[1 2 3]]}) => 3
  ((from-context [:foo 0 last #{1 2}])
    {:foo [[1 2 3]]}) => nil
  ((from-context [:foo 0 last #{1 2}]
                 :default)
    {:foo [[1 2 3]]}) => :default"
  [path & [default]]
  (fn from-context-fn [context]
    (loop [[f & rest] path
           result     context]
      (cond (nil? result)                      default
            (nil? f)                           result
            (or (fn? f) (keyword? f) (set? f)) (recur rest (f result))
            (and (integer? f) (not (neg? f)))  (recur rest (nth result f))
            :else (throw (ex-info "Illegal value in path"
                                  {:path path
                                   :value f
                                   :contex context
                                   :result result}))))))
