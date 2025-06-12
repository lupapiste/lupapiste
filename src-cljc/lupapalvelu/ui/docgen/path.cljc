(ns lupapalvelu.ui.docgen.path
  "Utility functions for managing field paths"
  (:require [sade.strings :as ss]
            [sade.util :as util]))

;; Not sure if necessary, but while the old Docgen implementation
;; still exists (and is present in the DOM), use this prefix
;; for all CLJS docgen DOM IDs.
(def cljs-prefix "cljs-")

(defn stringify-path
  [path]
  (mapv (fn [segment]
          (cond
            ;; Typically all paths are keywords or integers, but when referencing
            ;; individual select options we must use the plain string, as the values
            ;; are e.g. "kWh/brm2/vuosi"
            (string? segment)         segment
            (simple-keyword? segment) (name segment)
            (int? segment)            (str segment)
            :else                     (throw (ex-info (str "can't stringify: " (pr-str path)) {:path    path
                                                                                               :segment segment}))))
        path))

(defn keywordify-path
  "Convert integers in path to keywords, `[:foo 0 :bar]` -> `[:foo :0 :bar]`."
  [path]
  (mapv (fn [segment]
          (if (int? segment)
            (keyword (str segment))
            segment))
        path))

(defn integerize-path
  "Opposite of `keywordify-path`."
  [path]
  (mapv (fn [x] (or (util/->int x nil)
                    (keyword x))) path))

(defn to-path
  "Takes path in form on vector of strings, e.g., from API response
  and coerces the elements to keywords."
  [xs]
  (mapv keyword xs))

(defn integerize-coll
  "Try to convert a given seq of values to a seq of integers.
  Throws away values that can't be represented as integers."
  [xs]
  (->> xs
       (keep (fn [x]
               (when-let [result (util/->int x nil)]
                 result)))
       (into [])))

(defn path->str
  "Sometimes (e.g. translation keys, API calls) paths to fields are represented as period separated strings"
  [path]
  (ss/join "." (stringify-path path)))

(defn path->id
  "Build a DOM ID for a path (e.g. a docgen input field)."
  [path doc-id & {:keys [prefix?] :or {prefix? true}}]
  (str (when prefix? cljs-prefix)
       doc-id "-"
       (ss/join "-" (stringify-path path))))

(defn path->label-id
  "Build a DOM ID for a docgen label"
  [path doc-id]
  (str cljs-prefix "label-" (path->id path doc-id :prefix? false)))

(defn path->group-id
  "Build a DOM ID for a docgen group"
  [path doc-id]
  (str cljs-prefix "group-" (path->id path doc-id :prefix? false)))

(defn path->table-id
  "Build a DOM ID for a docgen table"
  [path doc-id]
  (str cljs-prefix "table-" (path->id path doc-id :prefix? false)))

(defn path->append-id
  "Build a DOM ID for an append button"
  [path doc-id]
  (str cljs-prefix doc-id "_" (ss/join "_" (stringify-path path)) "_append"))

(defn path->warning-message-id
  "Calculate the DOM ID of an warning message.
  See `docutils.js/warningId`."
  [path doc-id]
  (let [id (str cljs-prefix "warn-" (path->id path doc-id :prefix? false))]
    (ss/replace id #"\." "-")))

(defn path-without-indexes
  "Paths to the data can sometimes (repeating groups, tables)
  contain integer indexes. Strips those out."
  [path]
  (->> path
       (remove int?)
       (into [])))

(defn- value?
  [m]
  (and (map? m)
       (some? (:value m))
       (not (map? (:value m)))))

(defn- branch?
  [v]
  (and (map? v)
       (boolean (seq v))))

(defn data->path-map
  "Given a `:data` tree, construct a map of path to value.
  The reverse operation is just `assoc-in`.
  Note that we return the whole `:value` map for each path."
  [m]
  {:pre [(map? m)]}
  (loop [result            {}
         [entry & entries] [(when-let [xs (seq m)]
                              {:path    []
                               :content xs})]]
    (if (nil? entry)
      result
      (let [{:keys [path content]} entry
            {values   true
             branches false}       (group-by (comp value? val) content)
            vs                     (->> values
                                        (map (fn [[k v]]
                                               [(conj path k) v])))
            es                     (->> branches
                                        (filter (comp branch? val))
                                        (map (fn [[k v]]
                                               {:path    (conj path k)
                                                :content (seq v)})))]
        (recur (into result vs)
               (into entries es))))))

(defn prefix-matches?
  "Check if the given `path` begins with the `prefix`."
  [path prefix]
  {:pre [(vector? path) (vector? prefix)]}
  (let [prefix-len (count prefix)]
    (or (zero? prefix-len)
        (and (<= 1 prefix-len (count path))
             ;; `subvec` is O(1) so this ought to be fast
             (= prefix (subvec path 0 prefix-len))))))
