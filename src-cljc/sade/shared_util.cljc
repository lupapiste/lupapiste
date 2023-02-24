(ns sade.shared-util
  "Required and passed-through by sade.util"
  (:require [clojure.set :as set]
            [sade.shared-strings :as ss]))

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

(defn find-index
  "Returns the index of the first element matching `pred`."
  [pred coll]
  (first (keep-indexed (fn [i x]
                         (when (pred x)
                           i))
                       coll)))

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
    (map make-kw (ss/split (name (or kw "")) #"\."))))

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
     (#?(:clj Integer/parseInt
         :cljs js/parseInt)
        (cond
          (keyword? x) (name x)
          (number? x)  (str (int x))
          :else        (str x))
        10)
     (catch #?(:clj Exception
               :cljs js/Error)
            _
       default))))

(defn assoc-when
  "Assocs entries with truthy values into m."
  [m & kvs]
  (apply merge m (filter val (apply hash-map kvs))))

(defn assoc-when-pred
  "Assocs entries into m when pred returns truthy for value."
  [m pred & kvs]
  (apply merge m (filter (comp pred val) (apply hash-map kvs))))

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

;; ---------------------------------------------
;; The following are not aliased in sade.util.
;; ---------------------------------------------

(defn kw-path
  "a b c       -> :a.b.c
   [a b c]     -> :a.b.c
   a [b nil] c -> :a.b.c
   nil         -> nil"
  [& path]
  (some->> path
           flatten
           (remove nil?)
           not-empty
           (map #(if (keyword? %)
                   (name %)
                   %))
           (ss/join ".")
           make-kw))

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

(defn clj->js-fn
  "Wraps a ClojureScript function so any arguments given are automatically converted to CLJS from JS"
  [func]
  #?(:clj  func ; in case this is inadvertently used from CLJC code
     :cljs (fn [& args]
             (apply func (map js->clj args)))))

(defn tval
  "Utility function that takes a JS event's target element (input usually) and its value"
  [event]
  #?(:clj  event ; in case this is inadvertently used from CLJC code
     :cljs (-> event .-target .-value)))
