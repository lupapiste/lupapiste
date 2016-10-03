(ns sade.util
  (:refer-clojure :exclude [pos? neg? zero?])
  (:require [clojure.walk :refer [postwalk prewalk]]
            [clojure.java.io :as io]
            [sade.core :refer [fail!]]
            [sade.strings :refer [numeric? decimal-number? trim] :as ss]
            [clj-time.format :as timeformat]
            [clj-time.core :refer [hours days weeks months years ago from-now]]
            [clj-time.coerce :as tc]
            [schema.core :as sc]
            [taoensso.timbre :as timbre :refer [debugf]]
            [me.raynes.fs :as fs])
  (:import [org.joda.time LocalDateTime]
           [java.util.jar JarFile]))

;;
;; Nil-safe number utilities
;;

(defn pos?
  "Like clojure.core/pos?, but nil returns false instead of NPE"
  [n]
  (if n (clojure.core/pos? n) false))

(defn neg?
  "Like clojure.core/neg?, but nil returns false instead of NPE"
  [n]
  (if n (clojure.core/neg? n) false))

(defn zero?
  "Like clojure.core/zero?, but nil returns false instead of NPE"
  [n]
  (if n (clojure.core/zero? n) false))

;; Map utilities

(defn postwalk-map
  "traverses m and applies f to all maps within"
  [f m] (postwalk (fn [x] (if (map? x) (into {} (f x)) x)) m))

(defn prewalk-map
  "traverses m and applies f to all maps within"
  [f m] (prewalk (fn [x] (if (map? x) (into {} (f x)) x)) m))

(defn convert-values
  "Runs a recursive conversion"
  ([m f]
    (postwalk-map (partial map (fn [[k v]] [k (f v)])) m))
  ([m pred f]
    (postwalk-map (partial map (fn [[k v]] (if (pred k v) [k (f v)] [k v]))) m)))

(defn strip-empty-maps
  "removes recursively all keys from map which have empty map as value"
  [m] (postwalk-map (partial filter (comp (partial not= {}) val)) m))

(defn strip-nils
  "removes recursively all keys from map which have value of nil"
  [m] (postwalk-map (partial filter (comp not nil? val)) m))

(defn ensure-sequential
  "Makes sure that the value of key k in map m is sequental"
  [m k] (let [v (k m)] (if (and v (not (sequential? v))) (assoc m k [v]) m)))

; from clojure.contrib/core

(defn map-keys
  "Applies function f to hash-map m keys. Returns hash-map."
  [f m]
  (->> (map (fn [[k v]] [(f k) v]) m)
       (into {})))

(defn map-values
  "Applies function f to hash-map m values. Returns hash-map."
  [f m]
  (->> (map (fn [[k v]] [k (f v)]) m)
       (into {})))

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

(defn some-key
  "Tries given keys and returns the first non-nil value from map m"
  [m & ks]
  (let [k (first ks)]
    (when (and m k)
      (if (nil? (m k))
        (apply some-key m (rest ks))
        (m k)))))

(defn find-by-key
  "Return item from sequence col of maps where element k (keyword) matches value v."
  [k v col]
  (some (fn [m] (when (= v (get m k)) m)) col))

(defn find-by-id
  "Return item from sequence col of maps where :id matches id."
  [id col]
  (some (fn [m] (when (= id (:id m)) m)) col))

(defn replace-by-id
  "Return col of maps where elements are replaced by item when element :id matches item :id"
  [item col]
  (map #(if (= (:id item) (:id %)) item %) col))

; From clojure.contrib/seq

(defn indexed
  "Returns a lazy sequence of [index, item] pairs, where items come
  from 's' and indexes count up from zero.

  (indexed '(a b c d))  =>  ([0 a] [1 b] [2 c] [3 d])"
  [s]
  (map vector (iterate inc 0) s))

(defn positions
  "Returns a lazy sequence containing the positions at which pred
   is true for items in coll."
  [pred coll]
  (for [[idx elt] (indexed coll) :when (pred elt)] idx))

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
    (if (fn? checker)
      (checker coll)
      (= coll checker))))

(defn ->keyword [x]
  ((if (number? x)
     (comp keyword str)
     keyword)
   x))

(defn ->int
  "Reads a integer from input. Returns default if not an integer.
   Default default is 0"
  ([x] (->int x 0))
  ([x default]
    (try
      (Integer/parseInt (cond
                          (keyword? x) (name x)
                          (number? x)  (str (int x))
                          :else        (str x))
                        10)
      (catch Exception _
        default))))

(defn ->double
  "Reads a double from input. Return default if not a double.
  Default is 0.0"
  ([v] (->double v 0.0))
  ([v default]
   (let [s (str v)]
     (try
       (Double/parseDouble s)
       (catch Exception _
         default)))))

(defn to-long
  "Parses string to long. If string is not numeric returns nil."
  [^String s]
  (when (numeric? s)
    (Long/parseLong s)))

(defn ->long
  "Parses strings and numbers to longs. Returns nil for other types and invalid strings."
  [x]
  (cond
    (string? x) (to-long x)
    (number? x) (long x)))

(defn abs [n]
  {:pre [(number? n)]}
  (Math/abs n))

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

(defn- format-utc-timestamp [^Long timestamp ^String fmt]
  (when timestamp
    (let [dt (tc/from-long timestamp)]
      (timeformat/unparse (timeformat/formatter fmt) dt))))

(defn- local-date-time [timestamp-or-datetime]
  (if (number? timestamp-or-datetime)
    (LocalDateTime. (long timestamp-or-datetime))
    (LocalDateTime. timestamp-or-datetime)))

(defn- format-timestamp-local-tz [^Long timestamp ^String fmt]
  (when timestamp
    (let [dt (local-date-time (->long timestamp))]
      (timeformat/unparse-local (timeformat/formatter fmt) dt))))

(defn to-local-date [^Long timestamp]
  (format-timestamp-local-tz timestamp "dd.MM.YYYY"))

(defn to-local-datetime [^Long timestamp]
  (format-timestamp-local-tz timestamp "dd.MM.yyyy HH:mm"))

(defn to-xml-date [^Long timestamp]
  (format-timestamp-local-tz timestamp "YYYY-MM-dd"))

(defn to-xml-datetime [^Long timestamp]
  (format-utc-timestamp timestamp "YYYY-MM-dd'T'HH:mm:ss"))

(defn to-xml-local-datetime [^Long timestamp]
  (format-timestamp-local-tz timestamp "YYYY-MM-dd'T'HH:mm:ss"))

(defn to-xml-date-from-string [^String date-as-string]
  (when-not (ss/blank? date-as-string)
    (let [d (timeformat/parse-local-date (timeformat/formatter "dd.MM.YYYY" ) date-as-string)]
      (timeformat/unparse-local-date (timeformat/formatter "YYYY-MM-dd") d))))

(defn to-xml-datetime-from-string [^String date-as-string]
  (when-not (ss/blank? date-as-string)
    (let [d (timeformat/parse-local (timeformat/formatter "dd.MM.YYYY" ) date-as-string)]
      (timeformat/unparse-local-date (timeformat/formatter "YYYY-MM-dd'T'HH:mm:ssZ") d))))

(defn to-millis-from-local-date-string [^String date-as-string]
  (when-not (ss/blank? date-as-string)
    (let [d (timeformat/parse (timeformat/formatter "dd.MM.YYYY" ) date-as-string)]
      (tc/to-long d))))

(defn to-millis-from-local-datetime-string [^String datetime-as-string]
  (when-not (ss/blank? datetime-as-string)
    (let [d (timeformat/parse (timeformat/formatter-local "YYYY-MM-dd'T'HH:mm" ) datetime-as-string)]
      (tc/to-long d))))

(defn to-RFC1123-datetime [^Long timestamp]
  (format-utc-timestamp timestamp "EEE, dd MMM yyyy HH:mm:ss 'GMT'"))

(def time-pattern #"^([012]?[0-9]):([0-5]?[0-9])(:([0-5][0-9])(\.(\d))?)?$")

(defn to-xml-time-from-string [^String time-s]
  (when-let [matches (and time-s (seq (filter #(and % (Character/isDigit (first %))) (rest (re-matches time-pattern time-s)))))]
    (let [fmt (case (count matches)
                2 "%02d:%02d:00"
                3 "%02d:%02d:%02d"
                4 "%02d:%02d:%02d.%d")]
      (apply format fmt (map ->int matches)))))

(defn- get-timestamp-ago-or-from-now
  [ago-from-now-fn time-key amount]
  {:pre [(#{:hour :day :week :month :year} time-key)]}
  (let [time-fn (case time-key
                  :hour hours
                  :day days
                  :week weeks
                  :month months
                  :year years)]
    (tc/to-long (-> amount time-fn ago-from-now-fn))))

(defn get-timestamp-ago
  [time-key amount]
  "Returns a timestamp in history. The 'time-key' parameter can be one of these keywords: :day, :week, :month or :year."
  (get-timestamp-ago-or-from-now ago time-key amount))

(defn get-timestamp-from-now
  "Returns a timestamp in future. The 'time-key' parameter can be one of these keywords: :day, :week, :month or :year."
  [time-key amount]
  (get-timestamp-ago-or-from-now from-now time-key amount))

(defn sequable?
  "Returns true if x can be converted to sequence."
  [x]
  (or (seq? x)
      (instance? clojure.lang.Seqable x)
      (instance? Iterable x)
      (instance? java.util.Map x)
      (string? x)
      (nil? x)
      (-> x .getClass .isArray)))

(defn empty-or-nil?
  "Returns true if x is either nil or empty if it's sequable."
  [x]
  (or (nil? x) (and (sequable? x) (empty? x))))

(defn not-empty-or-nil? [x] (not (empty-or-nil? x)))

(defn boolean? [x] (instance? Boolean x))

(defn assoc-when
  "Assocs entries with truthy values into m."
  [m & kvs]
  (apply merge m (filter val (apply hash-map kvs))))

(defn assoc-when-pred
  "Assocs entries into m when pred returns truthy for value."
  [m pred & kvs]
  (apply merge m (filter (comp pred val) (apply hash-map kvs))))

(defn relative-local-url? [^String url]
  (not (or (not (string? url)) (ss/starts-with url "//") (re-matches #"^\w+://.*" url))))

(defmulti ->version-array type)
(defmethod ->version-array :default
  [_]
  [0 0 0])
(defmethod ->version-array clojure.lang.PersistentArrayMap
  [{major :major minor :minor micro :micro :as v :or {major 0 minor 0 micro 0}}]
  (mapv ->int [major minor micro]))
(defmethod ->version-array clojure.lang.PersistentVector
  [v]
  (->> (concat v [0 0 0]) (take 3) (mapv ->int)))
(defmethod ->version-array java.lang.String
  [s]
  (->> (ss/split s #"\.") ->version-array))
(defmethod ->version-array clojure.lang.Keyword
  [k]
  (->> (name k) ->version-array))

(defn compare-version [comparator-fn source target]
  (loop [s (->version-array source) t (->version-array target)]
    (if (and (> (count s) 1) (= (first s) (first t)))
      (recur (rest s) (rest t))
      (comparator-fn (first s) (first t)))))

(defn version-is-greater-or-equal
  "True if given version string is greater than version defined in target map, else nil"
  [^String source, ^clojure.lang.IPersistentMap target]
  {:pre [(map? target) (every? #(target %) [:major :minor :micro]) (string? source)]}
  (let [[source-major source-minor source-micro] (map #(->int %) (ss/split source #"\."))
        source-major (or source-major 0)
        source-minor (or source-minor 0)
        source-micro (or source-micro 0)]
    (or
      (> source-major (:major target))
      (and (= source-major (:major target)) (> source-minor (:minor target)))
      (and (= source-major (:major target)) (= source-minor (:minor target)) (>= source-micro (:micro target))))))

(def Fn (sc/pred fn? "Function"))

(def IFn (sc/pred ifn? "Function"))

(defn compare-difficulty [accessor-keyword values a b]
  {:pre [(keyword? accessor-keyword) (vector? values)]}
  (let [a (accessor-keyword a)
        b (accessor-keyword b)]
    (cond
      (nil? b) -1
      (nil? a) 1
      :else (- (.indexOf values a) (.indexOf values b)))))

(defn every-key-in-map? [target-map required-keys]
  (every? (-> target-map keys set) required-keys))

(defn separate-emails [^String email-str]
  (->> (ss/split email-str #"[,;]") (map ss/trim) set))

(defn find-first
  "Returns first element from coll for which (pred item)
   returns true. pred must be free of side-effects."
  [pred coll]
  (first (filter pred coll)))

(defn get-files-by-regex
  "Takes all files (and folders) from given path and filters them by regex. Not recursive. Returns sequence of File objects."
  [path ^java.util.regex.Pattern regex]
  {:pre [(instance? java.util.regex.Pattern regex) (or (string? path)
                                                       (instance? java.net.URL path))]}
  (filter
    #(re-matches regex (.getName %))
    (-> path io/file (.listFiles) seq)))

(defn select-values [m keys]
  (map #(get m %) keys))

(defn this-jar
  "utility function to get the name of jar in which this function is invoked"
  [& [ns]]
  (-> (or ns (class *ns*))
    .getProtectionDomain .getCodeSource .getLocation .getPath))

(defn list-jar [jar-path inner-dir]
  (if-let [jar         (JarFile. jar-path)]
    (let [inner-dir    (if (and (not (ss/blank? inner-dir)) (not= \/ (last inner-dir)))
                         (str inner-dir "/")
                         (str inner-dir))
          inner-dir    (if (ss/starts-with inner-dir "/")
                         (subs inner-dir 1)
                         inner-dir)
          entries      (enumeration-seq (.entries jar))
          names        (map (fn [x] (.getName x)) entries)
          snames       (filter #(ss/starts-with % inner-dir) names)
          fsnames      (map #(subs % (count inner-dir)) snames)]
      fsnames)))

(defmacro timing [msg body]
  `(let [start# (System/currentTimeMillis)
         result# ~body
         end# (System/currentTimeMillis)]
     (debugf (str ~msg ": %dms") (- end# start#))
     result#))


; Patched from me.raynes.fs.compression
(defn unzip
  "Takes the path to a zipfile source and unzips all files to root of target-dir."
  ([source]
    (unzip source (name source)))
  ([source target-dir]
    (unzip source target-dir "UTF-8"))
  ([source target-dir encoding]
    (let [fallback-encoding "IBM437"]
      (try

       (with-open [zip (java.util.zip.ZipFile. (fs/file source) (java.nio.charset.Charset/forName encoding))]
        (let [entries (enumeration-seq (.entries zip))
              target-file #(->> (.getName %)
                             fs/base-name
                             (fs/file target-dir))]
          (doseq [entry entries :when (not (.isDirectory ^java.util.zip.ZipEntry entry))
                  :let [f (target-file entry)]]
            (fs/mkdirs (fs/parent f))
            (io/copy (.getInputStream zip entry) f))))

       (catch IllegalArgumentException e
         (if-not (= encoding fallback-encoding)
           (do
             (debugf "Malformed zipfile contents in (%s) with encoding: %s. Fallbacking to CP437 encoding" source encoding)
             (unzip source target-dir fallback-encoding))
           (fail! :error.unzipping-error)))))
    target-dir))

(defn is-latest-of?
  "Compares timestamp ts to given timestamps.
   Returns true if ts is greater than all given timestamps, false if even one of them is greater (or equal)"
  [ts timestamps]
  {:pre [(integer? ts) (and (sequential? timestamps) (every? integer? timestamps))]}
  (every? (partial > ts) timestamps))

(defn =as-kw
  "Converts arguments to keywords and compares if they are the same"
  ([x] true)
  ([x y] (= (keyword x) (keyword y)))
  ([x y & more] (apply = (keyword x) (keyword y) (map keyword more))))

(defn not=as-kw
  "Converts arguments to keywords and compares if they are the same"
  ([x] false)
  ([x y] (not= (keyword x) (keyword y)))
  ([x y & more] (apply not= (keyword x) (keyword y) (map keyword more))))

(defn kw-path
  "a b c -> :a.b.c"
  [& kw]
  (->> kw
       (map name)
       (ss/join ".")
       keyword))

(defn get-in-tree [tree path]
  (reduce #(second (find-first (comp #{%2} first) %1)) tree path))

(defn get-leafs [tree]
  (loop [leafs [] t tree]
    (if-let [children (not-empty (map second t))]
      (recur (concat leafs (remove coll? children)) (apply concat (filter coll? children)))
      leafs)))
