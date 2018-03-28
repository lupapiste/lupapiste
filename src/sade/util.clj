(ns sade.util
  (:refer-clojure :exclude [pos? neg? zero? max-key])
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t :refer [hours days weeks months years ago from-now]]
            [clj-time.format :as timeformat]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.walk :refer [postwalk prewalk]]
            [me.raynes.fs :as fs]
            [sade.core :refer [fail!]]
            [sade.shared-util :as shared]
            [sade.strings :refer [numeric? decimal-number? trim] :as ss]
            [schema.core :as sc]
            [taoensso.timbre :as timbre :refer [debugf warnf]])
  (:import [java.util.jar JarFile]
           [org.joda.time LocalDateTime]
           [java.io ByteArrayOutputStream]))

(defmacro defalias [alias from]
  `(do (def ~alias ~from)
       (alter-meta! #'~alias merge (select-keys (meta #'~from) [:arglists]))
       ~alias))

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

(defn max-key
  "Like clojure.core/max-key, but has single arity and is nil safe.
  Ignores elements without key."
  [key & ms]
  (some->> (filter key ms) not-empty (apply clojure.core/max-key key)))

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

(defn pathwalk
  "A prewalk that keeps track of the path traversed from the root of the collection"
  ([f coll] (pathwalk f [] coll))
  ([f path coll]
   (let [f-coll (f path coll)]
     (cond (map? f-coll) (into {} (map (fn [[k v]]
                                         [k (pathwalk f (conj path k) v)])
                                       f-coll))
           (set? f-coll) (into #{} (map (fn [v]
                                          (pathwalk f (conj path v) v))
                                        f-coll))
           (coll? f-coll) (into (empty f-coll)
                                (map-indexed
                                 (fn [idx v]
                                   (pathwalk f (conj path idx) v))
                                 f-coll))
           :else f-coll))))

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

(defn key-by
  "Like group-by but returns values unwrapped. Multiple values for same key are omitted, last value is used."
  [f coll]
  (reduce #(assoc %1 (f %2) %2) {} coll))

(defalias drop-nth  shared/drop-nth)
(defalias dissoc-in shared/dissoc-in)

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

(defalias find-by-key shared/find-by-key)
(defalias find-by-id  shared/find-by-id)
(defalias indexed shared/indexed)

(defn replace-by-id
  "Return col of maps where elements are replaced by item when element :id matches item :id"
  [item col]
  (map #(if (= (:id item) (:id %)) item %) col))

(defn filter-map-by-val
  "Returns the mapping for which the value satisfies the predicate.
  (filter-map-by-val pos? {:a 1 :b -1}) => {:a 1}"
  [pred m]
  (shared/filter-map-by-val pred m))

; From clojure.contrib/seq

(defn positions
  "Returns a lazy sequence containing the positions at which pred
   is true for items in coll."
  [pred coll]
  (for [[idx elt] (indexed coll) :when (pred elt)] idx))

(defn position-by-key
  "Returns item index by key"
  [key val coll]
  (first (positions (comp #{val} (keyword key)) coll)))

(defn position-by-id
  "Returns item index by id"
  [id coll]
  (position-by-key :id id coll))

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

(defalias safe-update-in shared/safe-update-in)

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

(defmacro pcond->
  "Takes an expression and a set of pred/form pairs. Threads expr (via ->)
  through each form for which the corresponding pred returns truthy value
  for threaded expr. Otherwise form is skipped and expr is passed to next
  form. Note: like cond-> but static test is replaced by pred"
  [expr & clauses]
  (assert (even? (count clauses)))
  (let [g (gensym)
        steps (map (fn [[pred step]] `(if (~pred ~g) (-> ~g ~step) ~g))
                   (partition 2 clauses))]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))

(defmacro pcond->>
  "Takes an expression and a set of pred/form pairs. Threads expr (via ->>)
  through each form for which the corresponding pred returns truthy value
  for threaded expr. Otherwise form is skipped and expr is passed to next
  form.  Note: like cond-> but static test is replaced by pred"
  [expr & clauses]
  (assert (even? (count clauses)))
  (let [g (gensym)
        steps (map (fn [[pred step]] `(if (~pred ~g) (->> ~g ~step) ~g))
                   (partition 2 clauses))]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))

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

(defn to-datetime-with-timezone ^org.joda.time.DateTime [ts & [zone]]
  (-> ts
      (tc/from-long)
      (t/to-time-zone (or zone (t/default-time-zone)))))

(defn- format-utc-timestamp [^Long timestamp ^String fmt]
  (when timestamp
    (let [dt (tc/from-long timestamp)]
      (timeformat/unparse (timeformat/formatter fmt) dt))))

(defn- local-date-time [timestamp-or-datetime]
  (if (number? timestamp-or-datetime)
    (LocalDateTime. (long timestamp-or-datetime))
    (LocalDateTime. timestamp-or-datetime)))

(defn format-timestamp-local-tz [^Long timestamp ^String fmt]
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

(defn to-finnish-date
  "Timestamp in the Finnish date format without zero-padding.
  For example, 30.1.2018."
  [^Long timestamp]
  (format-timestamp-local-tz timestamp "d.M.YYYY"))

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
  "Returns a timestamp in history. The 'time-key' parameter can be one of these keywords: :day, :week, :month or :year."
  [time-key amount]
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

(defn merge-in
  "Merges the result of (apply f x args) into x"
  [x f & args]
  {:pre [(map? x)]}
  (merge x (apply f x args)))

(defn mongerify
  "Transforms values into ones that could be returned by monger,
   applied recursively into sequences and values of maps.

   Turns keywords into strings (except for keys of maps).
   Turns lists, sets and vectors into vectors."
  [x]
  (cond (keyword? x)     (name x)
        (map? x)         (map-values mongerify x)
        (or (seq? x)
            (vector? x)
            (set? x))    (mapv mongerify x)
        :else            x))

(defn upsert
  [{id :id :as item} coll]
  (if id
    (->> (split-with (fn-> :id (not= id)) coll)
         (#(concat (first %) [item] (rest (second %)))))
    coll))

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

(defalias find-first shared/find-first)

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
     (timbre/debug ~msg ":" (- end# start#) "ms")
     result#))


; Patched from me.raynes.fs.compression
(defn unzip
  "Takes the path to a zipfile source and unzips all files to root of target-dir."
  ([source]
    (unzip source (name source)))
  ([source target-dir]
    (unzip source target-dir "UTF-8"))
  ([source target-dir encoding]
    (let [fallback-encoding "IBM00858"]
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
             (warnf "Malformed zipfile contents in (%s) with encoding: %s. Fallbacking to CP858 encoding" source encoding)
             (unzip source target-dir fallback-encoding))
           (fail! :error.unzipping-error)))))
    target-dir))

(defn is-latest-of?
  "Compares timestamp ts to given timestamps.
   Returns true if ts is greater than all given timestamps, false if
  even one of them is greater (or equal)"
  [ts timestamps]
  {:pre [(integer? ts) (and (sequential? timestamps) (every? integer? timestamps))]}
  (every? (partial > ts) timestamps))

(defalias =as-kw             shared/=as-kw)
(defalias not=as-kw          shared/not=as-kw)
(defalias includes-as-kw?    shared/includes-as-kw?)
(defalias intersection-as-kw shared/intersection-as-kw)
(defalias difference-as-kw   shared/difference-as-kw)
(defalias union-as-kw        shared/union-as-kw)

(defn kw-path
  "a b c -> :a.b.c
   [a b c] -> :a.b.c"
  [& kw]
  (->> kw
       flatten
       (map ss/->plain-string)
       (ss/join ".")
       keyword))

(def split-kw-path shared/split-kw-path)

(defn get-in-tree
  "Gets a branch in (operation)tree by path. Tree should be represented as vectors of pairs.
  (get-in-tree [[:n1 [[:n11 :l11] [:n12 [[:n121 :l121]]]]] [:n2 [[:n21 :l21]]] [:n3 :l3]] [:n1 :n12])
  ; => [[:n121 :l121]]"
  [tree path]
  (reduce #(second (find-first (comp #{%2} first) %1)) tree path))

(defn get-leafs
  "Gets all leafs in (operation)tree. Tree should be represented as vectors of pairs.
  (get-leafs [[:n1 [[:n11 :l11] [:n12 [[:n121 :l121]]]]] [:n2 [[:n21 :l21]]] [:n3 :l3]])
  ; => (:l3 :l11 :l21 :l121)"
  [tree]
  (if (sequential? tree)
    (loop [leafs [] t tree]
      (if-let [children (not-empty (map second t))]
        (recur (concat leafs (remove sequential? children)) (apply concat (filter sequential? children)))
        leafs))
    [tree]))

(defn read-edn-resource [file-path]
  (->> file-path io/resource slurp edn/read-string))

(defn read-edn-file [file-path]
  (-> (io/file file-path) slurp edn/read-string))

(defn distinct-by
  "Given a function comparable-fn and collection coll, builds a new
  collection by keeping elements e for which the result of
  (comparable-fn e) is distinct from the results of previous elements.

  (distinct-by :id [{:id 1 :val :a} {:id 2 :val :b} {:id 1 :val :c}])
  ; => ({:id 1 :val :a} {:id 2 :val :b)"
  [comparable-fn coll]
  (if (empty? coll)
    '()
    (-> (reduce (fn [[coll comparable-results] x]
                  (let [x-comparable (comparable-fn x)]
                         (if (contains? comparable-results x-comparable)
                           [coll comparable-results]
                           [(conj coll x) (conj comparable-results x-comparable)])))
                [[] #{}]
                coll)
        first
        seq)))

(defalias edit-distance shared/edit-distance)

(defn ^java.util.Date object-id-to-date [object-id]
  (-> object-id org.bson.types.ObjectId. bean :time java.util.Date.))
