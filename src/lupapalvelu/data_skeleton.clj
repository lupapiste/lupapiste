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
              (if (accessor-key? x)
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
     element    ::element} ::array-from}]
  (->> ((get accessor-functions collection) context)
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
          (build-id-map context accessor-functions x)

          (build-array? x)
          (build-array context accessor-functions x)

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
