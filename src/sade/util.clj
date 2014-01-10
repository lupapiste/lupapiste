(ns sade.util
  (:require [sade.strings :refer [numeric? decimal-number?]]
            [clj-time.format :as timeformat]
            [clj-time.coerce :as tc]))

; from clojure.contrib/core

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn select
  "Takes a map and a vector of keys, returns a vector of values from map."
  [m [k & ks]]
  (when k
    (cons (get m k) (select m ks))))

; From clojure.contrib/map-utils)
(defn deep-merge-with
  "Like merge-with, but merges maps recursively, applying the given fn
   only when there's a non-map at a particular level.

  (deep-merge-with + {:a {:b {:c 1 :d {:x 1 :y 2}} :e 3} :f 4}
                     {:a {:b {:c 2 :d {:z 9} :z 3} :e 100}})
  -> {:a {:b {:z 3, :c 3, :d {:z 9, :x 1, :y 2}}, :e 103}, :f 4}"
  [f & maps]
  (apply
    (fn m [& maps]
      (if (every? map? maps)
        (apply merge-with m maps)
        (apply f maps)))
    (filter (comp not nil?) maps)))

(defn deep-merge
  "Merges maps recursively using deep-merge-with:
   leaf values from the later maps win conflicts."
  [& maps]
  (apply deep-merge-with (fn [_ x] x) maps))

(defn contains-value? [coll checker]
  (if (coll? coll)
    (if (empty? coll)
      false
      (let [values (if (map? coll) (vals coll) coll)]
        (or
          (contains-value? (first values) checker)
          (contains-value? (rest values) checker))))
    (if checker
      (checker coll)
      false)))

(defn ->int
  "Reads a integer from input. Returns default if not a integer.
   Default default is 0"
  ([x] (->int x 0))
  ([x default]
    (try
      (Integer/parseInt (cond
                          (keyword? x) (name x)
                          (number? x)  (str (int x))
                          :else        (str x)))
      (catch Exception e
        default))))

(defn ->double [v]
  (let [s (str v)]
    (if (or (numeric? s) (decimal-number? s)) (Double/parseDouble s) 0.0)))

(defmacro fn-> [& body] `(fn [x#] (-> x# ~@body)))
(defmacro fn->> [& body] `(fn [x#] (->> x# ~@body)))

;; https://gist.github.com/rplevy/3021378

(defmacro with
  "do things with the first expression passed,
   and produce the result"
  [expr & body]
  `(let [~'% ~expr] ~@body))

(defmacro within
  "do things with the first expression passed (for side effects),
   but produce the value of the first expression"
  [expr & body]
  `(let [~'% ~expr] ~@body ~'%))

(defmacro future* [& body]
  `(future
     (try
       ~@body
       (catch Throwable e#
         (println (format "unhandled exception in future at %s:%d: %s" *file* ~(-> &form meta :line) e#))
         (.printStackTrace e#)
         (throw e#)))))

(defn missing-keys
  "Returns seq of keys from 'required-keys' that are not present or have nil value in 'src-map', or
   nil if all required keys are present. If 'required-keys' is nil, returns nil. Not lazy."
  [src-map required-keys]
  (assert required-keys "required-keys is required (no pun intended)")
  (seq (reduce
         (fn [missing k] (if (nil? (get src-map k)) (cons k missing) missing))
         ()
         required-keys)))


(defn to-local-date [^Long timestamp]
  (when timestamp
    (let [dt (tc/from-long timestamp)]
      (timeformat/unparse (timeformat/formatter "dd.MM.YYYY") dt))))

(defn to-xml-date [^Long timestamp]
  (when timestamp
    (let [dt (tc/from-long timestamp)]
      (timeformat/unparse (timeformat/formatter "YYYY-MM-dd") dt))))

(defn to-xml-datetime [^Long timestamp]
  (when timestamp
    (let [dt (tc/from-long timestamp)]
      (timeformat/unparse (timeformat/formatter "YYYY-MM-dd'T'HH:mm:ss") dt))))

(defn to-xml-date-from-string [^String date-as-string]
  (when date-as-string
    (let [d (timeformat/parse-local-date (timeformat/formatter "dd.MM.YYYY" ) date-as-string)]
      (timeformat/unparse-local-date (timeformat/formatter "YYYY-MM-dd") d))))

(defn to-xml-datetime-from-string [^String date-as-string]
  (when date-as-string
    (let [d (timeformat/parse-local (timeformat/formatter "dd.MM.YYYY" ) date-as-string)]
      (timeformat/unparse-local-date (timeformat/formatter "YYYY-MM-dd'T'HH:mm:ss") d))))

(defn to-millis-from-local-date-string [^String date-as-string]
  (when date-as-string
    (let [d (timeformat/parse (timeformat/formatter "dd.MM.YYYY" ) date-as-string)]
      (tc/to-long d))))


(defn sequable? [x] 
  "Returns true if x can be converted to sequence."
  (or (seq? x)
      (instance? clojure.lang.Seqable x)
      (instance? Iterable x)
      (instance? java.util.Map x)
      (string? x)
      (nil? x)
      (-> x .getClass .isArray)))

(defn empty-or-nil? [x]
  "Returns true if x is either nil or empty if it's sequable."
  (or (nil? x) (and (sequable? x) (empty? x))))

(defn not-empty-or-nil? [x] (not (empty-or-nil? x)))

(defn assoc-when [m & kvs]
  "Assocs entries with not-empty-or-nil values into m."
  (into m (filter #(->> % val not-empty-or-nil?) (apply hash-map kvs))))
