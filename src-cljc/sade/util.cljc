(ns sade.util
  "Various utilities both for Clojure and/or ClojureScript."
  (:refer-clojure :exclude [pos? neg? zero? max-key])
  (:require #?@(:clj [[clojure.edn :as edn]
                      [clojure.java.io :as io]
                      [clojure.pprint :as pp]])
            [clojure.set :as set]
            [clojure.walk :as walk]
            [sade.strings :as ss])
  #?(:clj (:import [clojure.lang APersistentVector
                                 Keyword
                                 PersistentArrayMap
                                 PersistentVector]
                   [java.io File]
                   [java.net URL]
                   [java.util.jar JarEntry JarFile]
                   [java.util.regex Pattern]))
  #?(:cljs (:require-macros [sade.util])))

#?(:clj (set! *warn-on-reflection* true))

(defn find-first
  "Returns first element from coll for which (pred item)
   returns true. pred must be free of side-effects."
  [pred coll]
  (first (filter pred coll)))

(defn find-by-key
  "Return item from sequence col of maps where element k (keyword)
  matches value v."
  [k v col]
  (some (fn [m] (when (= v (get m k)) m)) col))

(defn find-by-keys
  "Returns the first item in coll that is a 'supermap' of the given map.

  (find-by-keys {:foo 1 :bar :hello}
                [{:foo 1} {:foo 1 :baz 3 :bar :hello} {:foo 1 :bar :hello}])
  => {:foo 1 :baz 3 :bar :hello}
  (find-by-keys {:foo 1 :bar nil} [{:foo 1}])
  => nil
  (find-by-keys {:foo 1 :bar nil} [{:foo 1} {:foo 1 :bar nil}])
  => {:foo 1 :bar nil}
  (find-by-keys nil [{:foo 1}]) => nil
  (find-by-keys {} [{:foo 1}]) => {:foo 1}"
  [m coll]
  (find-first #(= (select-keys % (keys m)) m) coll))

(defn find-by-id
  "Return item from sequence col of maps where :id matches id."
  [id col]
  (find-by-key :id id col))

(defn remove-by-key
  "Removes from `coll` (of maps) by matching `v-or-pred` (value or predicate) to `k` value."
  [k v-or-pred coll]
  (let [pred (if (fn? v-or-pred)
               v-or-pred
               #(= % v-or-pred))]
    (remove #(pred (get % k)) coll)))

(defn remove-by-id
  "Returns sequence with the item matching the given id removed."
  [id col]
  (remove-by-key :id id col))

(defn find-index
  "Returns the index of the first element matching `pred`."
  [pred coll]
  (first (keep-indexed (fn [i x]
                         (when (pred x)
                           i))
                       coll)))

(defn pos?
  "Like `clojure.core/pos?`, but nil returns false instead of NPE"
  [n]
  (if n (clojure.core/pos? n) false))

(defn neg?
  "Like `clojure.core/neg?`, but nil returns false instead of NPE"
  [n]
  (if n (clojure.core/neg? n) false))

(defn zero?
  "Like `clojure.core/zero?`, but nil returns false instead of NPE"
  [n]
  (if n (clojure.core/zero? n) false))

(defn max-key
  "Like clojure.core/max-key, but has single arity and is nil safe.
  Ignores elements without key."
  [key & ms]
  (some->> (filter key ms) not-empty (apply clojure.core/max-key key)))

(defn make-kw
  "Makes keyword from a number as well."
  [x]
  (keyword (cond-> x
             (number? x) str)))

(defn =as-kw
  "Converts arguments to keywords and compares if they are the same"
  ([_] true)
  ([x y] (= (make-kw x) (make-kw y)))
  ([x y & more] (apply = (make-kw x) (make-kw y) (map make-kw more))))

(defn not=as-kw
  "Converts arguments to keywords and compares if they are not the same"
  [& xs]
  (not (apply =as-kw xs)))

(defn includes-as-kw?
  "True, if any coll item matches x as keyword."
  [coll x]
  (boolean (some (partial =as-kw x) coll)))

(defn intersection-as-kw
  "Intersection of given collections as keywords. The result is a
  list. The items in the result set are taken from the first coll.
  (intersection-as-kw [\"yi\" :er \"san\"] '(:yi \"er\" :si) => (\"yi\" :er)"
  [& colls]
  (let [kw-set (apply set/intersection (map #(set (map make-kw %)) colls))]
    (filter #(includes-as-kw? kw-set %) (first colls))))

(defn difference-as-kw
  "Difference operation where collection items are first converted to
  keywords. Returns a keyword vector."
  [& colls]
  (->> colls
       (map (fn [coll]
              (map make-kw coll)))
       (map set)
       (apply set/difference)
       vec))

(defn union-as-kw
  "Union of given collections as keywords. The result is a keyword list
  with no nils, no duplicates."
  [& colls]
  (->> colls
       (apply concat)
       (remove nil?)
       (map make-kw)
       distinct))

(defn filter-map-by-val
  "Returns the mapping for which the value satisfies the predicate.
  (filter-map-by-val pos? {:a 1 :b -1}) => {:a 1}"
  [pred m]
  (into {} (filter (fn [[_ v]] (pred v)) m)))

(defn split-kw-path
  ":a.b.c -> [:a :b :c]"
  [kw]
  (when-not (nil? kw)
    (mapv make-kw (ss/split (name (or kw "")) #"\."))))

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

(defn indexed
  "Returns a lazy sequence of [index, item] pairs, where items come
  from 's' and indexes count up from offset (default: 0).

  (indexed '(a b c d))    =>  ([0 a] [1 b] [2 c] [3 d])
  (indexed 1 '(a b c d))  =>  ([1 a] [2 b] [3 c] [4 d])"
  ([s]
   (indexed 0 s))
  ([offset s]
   (map vector (iterate inc offset) s)))

(defn drop-nth
  "Drops the nth item from vector, if it exists"
  [n v]
  (if (get v n)
    (into (subvec v 0 n) (subvec v (inc n)))
    v))

(defn sequentialize
  "Makes a vector out of x if it is not already sequential"
  [x]
  (if (sequential? x)
    x
    [x]))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a
  new nested structure. keys is a sequence of keys. Any empty maps
  that result will not be present in the new structure.  Supports also
  vector index values among the keys sequence just like
  `clojure.core/update-in` does."
  [m [k & ks]]
  (letfn [(rm [w i]
            (cond
              (map? w)        (dissoc w i)
              (sequential? w) (drop-nth i w)
              :else           w))
          (add [w i x]
            (cond-> w
              (coll? w) (assoc i x)))]
    (if ks
      (if-let [next-m (get m k)]
        (let [result (dissoc-in next-m ks)]
          (if (empty? result)
            (rm m k)
            (add m k result)))
        m)
      (rm m k))))

(defn edit-distance
  "Levenshtein distance between strings a and b using Wagner–Fischer
  algorithm. The implementation is iterative, but uses O(|a|*|b|) space."
  [a b]
  {:pre [(string? a) (string? b)]}
  (let [length-of-a (count a)
        length-of-b (count b)]
    (if (or (zero? length-of-a)
            (zero? length-of-b))
      ;; If either one of the strings is empty, the distance is simply
      ;; the length of the other
      (max length-of-a length-of-b)

      ;; Otherwise, calculate the distance with Wagner-Fischer
      (let [distances (->> (concat (map #(vector [% 0] %)
                                        (range (inc length-of-a)))
                                   (map #(vector [0 %] %)
                                        (range (inc length-of-b))))
                           (into {}))]
       (loop [d distances
              i 1
              j 1]
         (let [substitution-cost (if (= (get a (dec i)) (get b (dec j))) 0 1)
               d-i-j (min (inc (d [(dec i) j]))
                          (inc (d [i (dec j)]))
                          (+ (d [(dec i) (dec j)]) substitution-cost))]
           (if (and (= i length-of-a)
                    (= j length-of-b))
             d-i-j
             (recur (assoc d [i j] d-i-j)
                    (if (= j length-of-b)
                      (inc i) i)
                    (if (= j length-of-b)
                      1 (inc j))))))))))

(defn safe-update-in
  "Like update-in, but does nothing in case the given path does not exist"
  [a-map path f & params]
  (if (empty? path)
    (apply f a-map params)
    (let [[fst & rst] path]
      (if (contains? a-map fst)
        (apply update a-map fst safe-update-in rst f params)
        a-map))))

(defn update-values
  "Updates the values accessed by the given `keys` with `fn`"
  [a-map keys f & params]
  (let [keys-set (set keys)]
    (into {}
          (map (fn [[k v]]
                 (if (keys-set k)
                   [k (apply f v params)]
                   [k v]))
               a-map))))


(defn ->int
  "Reads a integer from input. Returns default if not an integer.
   Default default is 0"
  ([x] (->int x 0))
  ([x default]
   (try
     (let [as-str (cond
                    (keyword? x) (name x)
                    (number? x)  (str (int x))
                    :else        (str x))
           as-int (#?(:clj Integer/parseInt
                      :cljs js/parseInt) as-str 10)]
       (if #?(:clj false :cljs (NaN? as-int))
         default
         as-int))
     (catch #?(:clj Exception
               :cljs js/Error) _
       default))))

(defn ->double
  "Coerces value to double. Returns default if coercion fails, or if
  the result would be NaN or infinite. The default is 0.0, but can be
  any type/value."
  ([v] (->double v 0.0))
  ([v default]
   (let [to-nil (fn [d pred] (when-not (pred d) d))]
     (cond
       (number? v) (double v)
       (string? v) (or (some-> (parse-double v)
                               (to-nil NaN?)
                               (to-nil infinite?))
                       default)
       :else       default))))

(defn ->safe-double
  "Like `->double` but supports both comma (,) and period (.) decimal separators for
  strings. Default for `default` is 0.0."
  ([v default]
   (->double (cond-> v
               (string? v) (ss/replace #"," "."))
             default))
  ([v] (->safe-double v 0.0)))

(defn assoc-when
  "Assocs entries with truthy values into m."
  ([m k v]
   (cond-> m
     v (assoc k v)))
  ([m k1 v1 k2 v2]
   (-> m
       (assoc-when k1 v1)
       (assoc-when k2 v2)))
  ([m k1 v1 k2 v2 k3 v3]
   (-> m
       (assoc-when k1 v1)
       (assoc-when k2 v2)
       (assoc-when k3 v3)))
  ([m k1 v1 k2 v2 k3 v3 & kvs]
   (loop [m           (assoc-when m k1 v1 k2 v2 k3 v3)
          [k v & kvs] kvs]
     (let [m (assoc-when m k v)]
       (if (seq kvs)
         (recur m kvs)
         m)))))

(defn assoc-when-pred
  "Assocs entries into m when pred returns truthy for value."
  ([m pred k v]
   (cond-> m
     (pred v) (assoc k v)))
  ([m pred k1 v1 k2 v2]
   (-> m
       (assoc-when-pred pred k1 v1)
       (assoc-when-pred pred  k2 v2)))
  ([m pred k1 v1 k2 v2 k3 v3]
   (-> m
       (assoc-when-pred pred k1 v1)
       (assoc-when-pred pred k2 v2)
       (assoc-when-pred pred k3 v3)))
  ([m pred k1 v1 k2 v2 k3 v3 & kvs]
   (loop [m           (assoc-when-pred m pred k1 v1 k2 v2 k3 v3)
          [k v & kvs] kvs]
     (let [m (assoc-when-pred m pred k v)]
       (if (seq kvs)
         (recur m kvs)
         m)))))

(defn emptyish?
  "True if `arg` is either an empty collection, blank string or nil."
  [arg]
  (boolean (cond
             (nil? arg)    true
             (string? arg) (ss/blank? arg)
             (coll? arg)   (empty? arg))))

(def fullish? (complement emptyish?))

(defn update-by-pred
  "Maps through `coll` and calls `f` for every item that matches `pred`. `f` receives the
  item as the first argument followed by any `args` given.

  Example, double all the odd items:
  (update-by-pred odd? * [0 1 2 3 4] 2) -> (0 2 2 6 4)"
  [pred f coll & args]
  (map (fn [x]
         (if (pred x)
           (apply f x args)
           x))
       coll))

(defn update-by-key
  "Maps through `coll` and calls `f` for every item that has a key `k` with value `v`. `f`
  receives the item as the first argument followed by any `args` given.

  Example, connect the dots.
  (update-by-key :type :dot assoc
                 [{:type :dot} {:type :square} {:type :dot}]
                 :connected? true)
  -> ({:type :dot, :connected? true}
      {:type :square}
      {:type :dot, :connected? true})"
  [k v f coll & args]
  (apply update-by-pred #(= (get % k) v) f coll args))

(defn update-by-id
  "Maps through `coll` and calls `f` for every item whose `:id` value is `idv`. `f` receives
  the item as the first argument followed by any `args` given.

  Example:
  (update-by-id \"b\" #(update % :name str \" (default)\")
                [{:id \"a\" :name \"Alice\"}
                 {:id \"b\" :name \"Bob\"}
                 {:id \"c\" :name \"Cecil\"}])
  -> ({:id \"a\" :name \"Alice\"}
      {:id \"b\" :name \"Bob (default)\"}
      {:id \"c\" :name \"Cecil\"})"
  [id f coll & args]
  (apply update-by-key :id id f coll args))

(def consv
  "Like `cons` but the result is always vector."
  (comp vec cons))

(defn conjv
  "Like `conv` but `coll` is casted to vector. Thus, the items (`xs`) are always appended."
  [coll & xs]
  (apply conj (vec coll) xs))

(defn filter-by-key
  "Filters `coll` (of maps) by matching `v-or-pred` (value or predicate) to `k` value."
  [k v-or-pred coll]
  (let [pred (if (fn? v-or-pred)
               v-or-pred
               #(= % v-or-pred))]
    (filter #(pred (get % k)) coll)))

;; Map utilities

(defn postwalk-map
  "traverses m and applies f to all maps within"
  [f m] (walk/postwalk (fn [x] (if (map? x) (into {} (f x)) x)) m))

(defn convert-values
  "Runs a recursive conversion"
  ([m f]
    (postwalk-map (partial map (fn [[k v]] [k (f v)])) m))
  ([m pred f]
    (postwalk-map (partial map (fn [[k v]] (if (pred k v) [k (f v)] [k v]))) m)))

(defn strip-empty-maps
  "removes recursively all keys from map which have empty map as value"
  [m] (postwalk-map (partial filter (comp (partial not= {}) val)) m))

(defn strip-empty-collections
  "removes recursively all keys from map which are empty collections"
  [m] (postwalk-map (partial filter (comp #(or (not (coll? %))
                                               (not-empty %))
                                          val))
                    m))
(defn strip-nils
  "removes recursively all keys from map which have value of nil"
  [m] (postwalk-map (partial filter (comp not nil? val)) m))

(defn strip-matches
  "Removes recursively every key, whose value matches `pred`."
  [m pred]
  (postwalk-map (partial remove (comp pred val)) m))

(defn strip-blanks
  "Removes recursively keys for blank and nil values."
  [m]
  (strip-matches m (fn [v]
                     (or (nil? v)
                         (and (string? v) (ss/blank? v))))))

(defn ensure-sequential
  "Makes sure that the value of key k in map m is sequental"
  [m k] (let [v (k m)] (if (and v (not (sequential? v))) (assoc m k [v]) m)))

(defn kw-path
  "a b c       -> :a.b.c
   [a b c]     -> :a.b.c
   a [b nil] c -> :a.b.c
   nil         -> nil"
  [& path]
  #?(:cljs (some->> path
                    flatten
                    (remove nil?)
                    not-empty
                    (map #(if (keyword? %)
                            (name %)
                            %))
                    (ss/join ".")
                    make-kw))
  #?(:clj (some->> path
                   flatten
                   (remove nil?)
                   not-empty
                   (map ss/->plain-string)
                   (ss/join ".")
                   keyword)))

(defn int-string?
  "Returns true if the string is an integer, e.g. \"-21\""
  [s]
  (and (string? s) (re-matches #"(-|\+)?[0-9]+" s)))

(defmacro fn->  [& body] `(fn [x#] (-> x# ~@body)))
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

(defn js->clj-fn
  "Wraps a JavaScript function so any arguments given are automatically converted to JS from CLJS"
  [func]
  #?(:clj  func ; in case this is inadvertently used from CLJC code
     :cljs (fn [& args]
             (apply func (map clj->js args)))))

(defn tval
  "Utility function that takes a JS event's target element (input usually) and its value"
  [event]
  #?(:clj  event ; in case this is inadvertently used from CLJC code
     :cljs (-> event .-target .-value)))

(defn format-keyed-map-as-list [separator field-keys obj]
  (->> obj
       ((apply juxt field-keys))
       (ss/join separator)))

(defn insert
  "Inserts new items into the coll, based on the existing ones.
  The item->insert-map fn takes one parameter, the current item,
  and returns nil if no items should be inserted, or a map if they should:

  {:add-before   [item]  ; items that should be inserted before this item
   :add-after    [item]  ; items that should be included after this item
   :replace-with [item]} ; items that should replace the current item

  E.g. (insert [1 0 7] {1 {:add-after [2 3]}
                        7 {:add-before [5 6]}
                        0 {:replace-with [4]}})
       => [1 2 3 4 5 6 7]"
  [coll item->insert-map]
  (reduce (fn [acc item]
            (let [{:keys [add-before add-after replace-with]} (item->insert-map item)]
              (-> acc ; use into instead of concat to avoid stack overflows with large colls
                  (into add-before)
                  (into (or replace-with [item]))
                  (into add-after))))
          []
          coll))

(defn update-in-when-pred
  "Like update-in but does not update if the pred returns truthy for the value at the end of the path.
  Useful when you don't want the update to create a path where there wasn't one before"
  [pred m ks f & args]
  (if (-> m (get-in ks) pred)
    (apply update-in m ks f args)
    m))

(def update-in-when
  "Like update-in but does not update if the value at the end of the path is nil"
  (partial update-in-when-pred some?))

(defn kw-flatten-map
  "'Flattens' the given `m` by converting nested (non-empty) maps into kw-path fields. This
  is sometimes useful when map contents need to be transformed into mongo updates. Each
  key is prefixed with `prefix` if given.

  (kw-flatten map \"hi\" {:foo {:bar 1 :baz {:one :hello :two {} :three [{:a :b}]}}})
  => {:hi.foo.bar       1
      :hi.foo.baz.one   :hello
      :hi.foo.baz.three [{:a :b}]
      :hi.foo.baz.two   {}}"
  ([prefix m]
   (letfn [(prefix-keys [prefix m inner?]
             (some-> (if (and (map? m) (not-empty m))
                       (->> m
                            (map (fn [[k v]]
                                   (prefix-keys (kw-path prefix k) v true)))
                            (apply merge))
                       {prefix (cond-> m (not inner?) not-empty)})
                     strip-nils
                     not-empty))]
     (prefix-keys prefix m false)))
  ([m] (kw-flatten-map nil m)))

(defmacro mmap
  "make a hash-map

   given a map `m`, associate values of `kvs` under a keyword
   from that symbols name, e.g.

     (def b 2)
     (mmap {:a 1} b)
       =>  {:a 1 :b 2}

   or, make one from values only
   (mmap b) => {:b 2}"
  {:style/indent 0}
  [m & kvs]
  (let [kvs (if (map? m) kvs (conj kvs m))
        m   (if (map? m) m {})]
    (->> kvs
         (map (fn [sym] [(-> sym name keyword) sym]))
         (into m))))

(defn compare-colls
  "Comparator, which compares collections per element.
  Returns a negative number, zero, or a positive number when coll-a is logically \"less than\",
  \"equal to\", or \"greater than\" coll-b.
  Inputs may have different lengths. Shorter collections are considered \"less than\"
  if otherwise identical."
  [coll-a coll-b]
  (let [length (max (count coll-a) (count coll-b))]
    (or (->> (map (fn [a b]
                    (if (= (type a) (type b))
                      (compare a b)
                      (compare (str a) (str b))))
                  (concat coll-a (repeat nil))
                  (concat coll-b (repeat nil)))
             (take length)
             (drop-while zero?)
             first)
        0)))

(defn pathwalk
  "A prewalk that keeps track of the path traversed from the root of the collection"
  ([f coll] (pathwalk f [] coll))
  ([f path coll]
   (let [f-coll (f path coll)]
     (cond (map? f-coll)  (into {} (map (fn [[k v]]
                                          [k (pathwalk f (conj path k) v)])
                                        f-coll))
           (set? f-coll)  (into #{} (map (fn [v]
                                           (pathwalk f (conj path v) v))
                                         f-coll))
           (coll? f-coll) (into (empty f-coll)
                                (map-indexed
                                  (fn [idx v]
                                    (pathwalk f (conj path idx) v))
                                  f-coll))
           :else          f-coll))))

(defn key-by
  "Like group-by but returns values unwrapped. Multiple values for same key are omitted,
  last value is used."
  [f coll]
  (reduce #(assoc %1 (f %2) %2) {} coll))

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

(defn replace-by-id
  "Return col of maps where elements are replaced by item when element :id matches item :id"
  [item col]
  (map #(if (= (:id item) (:id %)) item %) col))

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

(defn mapv-some
  "Like [[mapv]], but returns `nil` immediately if `f` returns `nil` for any element of `coll`."
  [f coll]
  (loop [res (transient []), coll coll]
    (if (seq coll)
      (when-some [v (f (first coll))]
        (recur (conj! res v) (rest coll)))
      (persistent! res))))

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

(defn deep-merge-with-key
  "Like deep-merge, but allows custom merging behavior based on the key.
   `f` is a function [k v1 v2] that returns the merged value.
   If `f` returns nil, default deep-merge behavior is used:
   - if both values are maps, they are merged recursively.
   - otherwise, the later value (v2) wins.

   To ensure custom behavior is applied even to keys that are not present in all maps,
   the function `f` is called for every key in the union of the maps.
   However, standard deep-merge behavior for non-conflicting keys is to just keep them.

   If `f` returns the special value :sade.util/skip, the key is omitted from the result."
  [f & maps]
  (letfn [(m [k v1 v2]
            (let [custom (f k v1 v2)]
              (cond
                (= custom ::skip) ::skip
                (some? custom) custom
                (and (map? v1) (map? v2)) (merge-step v1 v2)
                :else (if (some? v2) v2 v1))))
          (merge-step [m1 m2]
            (let [all-keys (set/union (set (keys (or m1 {}))) (set (keys (or m2 {}))))]
              (reduce (fn [acc k]
                        (let [res (m k (get m1 k) (get m2 k))]
                          (if (= res ::skip)
                            acc
                            (assoc acc k res))))
                      {}
                      all-keys)))]
    (reduce merge-step (filter (comp not nil?) maps))))

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

(defn to-long
  "Parses string to long. If string is not numeric returns nil."
  [^String s]
  (when (ss/numeric? s)
    (parse-long s)))

(defn ->long
  "Parses strings and numbers to longs. Returns nil for other types and invalid strings."
  [x]
  (cond
    (string? x) (to-long x)
    (number? x) (long x)))

(defn missing-keys
  "Returns seq of keys from 'required-keys' that are not present or have nil value in 'src-map', or
   nil if all required keys are present. If 'required-keys' is nil, returns nil. Not lazy."
  [src-map required-keys]
  (assert required-keys "required-keys is required (no pun intended)")
  (seq (reduce
         (fn [missing k] (if (nil? (get src-map k)) (cons k missing) missing))
         ()
         required-keys)))

#?(:clj
   (def time-pattern #"^([012]?[0-9]):([0-5]?[0-9])(:([0-5][0-9])(\.(\d))?)?$"))

#?(:clj
   (defn to-xml-time-from-string [^String time-s]
     (when-let [matches (and time-s
                             (seq (filter #(and % (Character/isDigit ^Character (first %)))
                                          (rest (re-matches time-pattern time-s)))))]
       (let [fmt (case (count matches)
                   2 "%02d:%02d:00"
                   3 "%02d:%02d:%02d"
                   4 "%02d:%02d:%02d.%d")]
              (apply format fmt (map ->int matches))))))

(defn empty-or-nil?
  "Returns true if x is either nil or empty if it's seqable."
  [x]
  (or (nil? x) (and (seqable? x) (empty? x))))

(def not-empty-or-nil? (complement empty-or-nil?))

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

(defn relative-local-url? [url]
  (not (or (not (string? url))
           (ss/starts-with? url "//")
           (re-matches #"^\w+://.*" url))))

#?(:clj (defmulti ->version-array type))

#?(:clj (defmethod ->version-array :default
          [_]
          [0 0 0]))

#?(:clj (defmethod ->version-array PersistentArrayMap
          [{major :major minor :minor micro :micro :or {major 0 minor 0 micro 0}}]
          (mapv ->int [major minor micro])))

#?(:clj (defmethod ->version-array PersistentVector
          [v]
          (->> (concat v [0 0 0]) (take 3) (mapv ->int))))

#?(:clj (defmethod ->version-array String
          [s]
          (->> (ss/split s #"\.") ->version-array)))

#?(:clj (defmethod ->version-array Keyword
          [k]
          (->> (name k) ->version-array)))

#?(:clj (defn compare-version [comparator-fn source target]
          (loop [s (->version-array source) t (->version-array target)]
            (if (and (> (count s) 1) (= (first s) (first t)))
              (recur (rest s) (rest t))
              (comparator-fn (first s) (first t))))))

(defn version-is-greater-or-equal
  "True if given version string is greater than version defined in target map, else nil"
  [source target]
  {:pre [(map? target) (every? #(target %) [:major :minor :micro]) (string? source)]}
  (let [[source-major source-minor source-micro] (map #(->int %) (ss/split source #"\."))
        source-major (or source-major 0)
        source-minor (or source-minor 0)
        source-micro (or source-micro 0)]
    (or
      (> source-major (:major target))
      (and (= source-major (:major target)) (> source-minor (:minor target)))
      (and (= source-major (:major target)) (= source-minor (:minor target)) (>= source-micro (:micro target))))))

(defn compare-difficulty [accessor-keyword ^APersistentVector values a b]
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

#?(:clj
   (defn get-files-by-regex
     "Takes all files (and folders) from given path and filters them by regex. Not recursive.
  Returns sequence of File objects."
     [path ^Pattern regex]
     {:pre [(instance? Pattern regex) (or (string? path) (instance? URL path))]}
     (filter
       #(re-matches regex (.getName ^File %))
       (-> path io/file (.listFiles) seq))))

#?(:clj
   (defn get-files-by-regex-recursive
     "Takes all files under a path then filter them by regex"
     [path ^Pattern regex]
     (letfn [(get-path-files [^File file]
               (->> file
                    (.listFiles)
                    (mapcat (fn [^File file]
                              (if (.isDirectory file)
                                (get-path-files file)
                                [file])))))]
       (filter
         #(re-matches regex (.getName ^File %))
         (get-path-files (io/file path))))))

(defn select-values [m keys]
  (map #(get m %) keys))

#?(:clj
   (defn this-jar
     "utility function to get the name of jar in which this function is invoked"
     [& [ns]]
     (let [^Class ns (or ns (class *ns*))]
       (-> ns .getProtectionDomain .getCodeSource .getLocation .getPath))))

#?(:clj
   (defn list-jar [^String jar-path inner-dir]
     (when-let [jar (JarFile. jar-path)]
       (let [inner-dir (if (and (not (ss/blank? inner-dir)) (not= \/ (last inner-dir)))
                         (str inner-dir "/")
                         (str inner-dir))
             inner-dir (if (ss/starts-with? inner-dir "/")
                         (subs inner-dir 1)
                         inner-dir)
             entries   (enumeration-seq (.entries jar))
             names     (map (fn [^JarEntry x] (.getName x)) entries)
             snames    (filter #(ss/starts-with? % inner-dir) names)
             fsnames   (map #(subs % (count inner-dir)) snames)]
         fsnames))))

(defn is-latest-of?
  "Compares timestamp ts to given timestamps.
   Returns true if ts is greater than all given timestamps, false if
  even one of them is greater (or equal)"
  [ts timestamps]
  {:pre [(integer? ts) (and (sequential? timestamps) (every? integer? timestamps))]}
  (every? (partial > ts) timestamps))

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

#?(:clj (defn read-edn-resource [file-path]
          (->> file-path io/resource slurp edn/read-string)))

#?(:clj (defn read-edn-file [file-path]
          (-> (io/file file-path) slurp edn/read-string)))

#?(:clj (defn write-edn-file [file-path data]
          (->> (pp/pprint data)
               with-out-str
               (spit file-path))))

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

#?(:clj (defn file->byte-array [file]
          (let [file (io/file file)
                b-array (byte-array (.length file))]
            (with-open [is (io/input-stream file)]
              (.read is b-array)
              b-array))))

(def keyset (comp set keys))

(defn parse-version
  "Takes a version string of form \"a.b.c...\" with N fields
  and returns a vector of `[a b c ..]` or throws."
  [s]
  (when (and (string? s)
             (seq s)
             (re-matches #"^\d+(\.\d+)*$" s))
    (mapv parse-long (ss/split s #"\."))))

(def sort-desc (partial sort #(compare %2 %1)))

(defn move-item
  "Move a specific item within list. List is treated as a circle buffer.
  `direction`  `:up` (left) or `:down` (right)
  `pred` The first match is the moved item. If none matches, nil is returned.
  `items` All items

  (move-item :up zero? [-2 -1 0 1 2])  ->  [-2 0 -1 1 2]
  (move-item :down pos? [-2 -1 0 1 2]) ->  [-2 -1 0 2 1]
  (move-item :up neg? [-2 -1 0 1 2])   ->  [-1 0 1 2 -2]"
  [direction pred items]
  (let [up?   (= direction :up)
        down? (not up?)]
    (loop [[x & xs] items
           passed   []]
      (let [found? (pred x)]
        (cond
          (nil? x)           nil ; The row no longer exists, no updates needed.

          (and found? up?)   ; Up like a rocket machine
          (if (seq passed)
            (concat (butlast passed) [x (last passed)] xs)
            (concat xs [x]))

          (and found? down?) ; Down like a roller coaster
          (if (seq xs)
            (concat  passed [(first xs) x] (rest xs))
            (cons x passed))

          :else              (recur xs (conj passed x)))))))

(defn all-values
  "Recursively collects all values from a map, including values of maps inside arrays.
   Returns a lazy sequence"
  [data]
  (cond
    (map? data) (mapcat all-values (vals data))
    (coll? data) (mapcat all-values data)
    (nil? data) []
    :else [data]))

(defn parent-paths
  "Takes a keyword path (e.g. :pate-verdicts.data.kuntalupatunnus._value) and
   returns a set of its parent paths.

   (parent-paths :pate-verdicts.data.kuntalupatunnus._value)
   => #{:pate-verdicts.data.kuntalupatunnus :pate-verdicts :pate-verdicts.data}
  "
  [path]
  (let [parts (butlast (split-kw-path path))]
    (->> (range (count parts))
         (map (comp kw-path #(take % parts) inc))
         set)))

(defn remove-path-collisions
  "From a collection of kw-paths remove such paths that point to a child of
   another path in that collection. Can, for example, be used for preventing
   path collisions in mongo query projections
   "
  [paths]
  (->> paths
       (remove #(seq (set/intersection (parent-paths %) (set paths))))
       (into (empty paths))))

(defn deep-select-keys
  "Returns a map containing only the entries at the given keyword paths (as dotted keywords).
   Supports nested maps and collections. Strips nil values and empty collections from the result.

   Arguments:
   m          must be a map
   kw-paths   must be a collection of keywords (dotted or otherwise)
   "
  [m kw-paths]
  (letfn [(select-sub-paths [m paths]
            (if (map? m)
              (let [keypaths (->> paths
                                  (group-by first)
                                  (map-values (partial keep (comp seq rest))))]
                (into {}
                      (keep (fn [[k v]]
                              (when-let [sub-paths (k keypaths)]
                                (let [sub-value (cond
                                                  (nil? (seq sub-paths)) v
                                                  (map? v) (select-sub-paths v sub-paths)
                                                  (coll? v) (keep #(not-empty (select-sub-paths % sub-paths)) v)
                                                  :else v)
                                      sub-value (if (coll? sub-value)
                                                  (not-empty sub-value)
                                                  sub-value)]
                                  (when (some? sub-value)
                                    [k sub-value]))))
                            m)))
              {}))]
    (strip-nils (select-sub-paths m (map split-kw-path (remove-path-collisions kw-paths))))))

(defn random-uuid-str
  "Returns
    - clojure.core/random-uuid in clj
    - cljs.core/random-uuid in cljs
   converted to string.

   According to both clj and cljs documentations, it is UUID Type 4."
  []
  (str (random-uuid)))

(defn all-blank?
  "Recursively checks a datastructure that all values are blank"
  [data]
  (cond
    (map? data)    (every? all-blank? (vals data))
    (coll? data)   (every? all-blank? data)
    (nil? data)    true
    (string? data) (ss/blank? data)
    :else          false))

(defn prefix-matches?
  "Check if the given `coll` (a vector) begins with the `prefix` (a vector)."
  [coll prefix]
  {:pre [(vector? coll) (vector? prefix)]}
  (let [prefix-len (count prefix)]
    (or (zero? prefix-len)
        (and (<= 1 prefix-len (count coll))
             ;; `subvec` is O(1) so this ought to be fast
             (= prefix (subvec coll 0 prefix-len))))))
