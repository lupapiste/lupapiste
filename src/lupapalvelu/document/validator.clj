(ns lupapalvelu.document.validator
  (:require [schema.core :as sc :refer [defschema]]
            [lupapalvelu.document.tools :as tools]))

(defonce validators (atom {}))

(defschema Validator
  {:doc                     sc/Str
   :schemas                 [sc/Str]
   (sc/optional-key :level) (sc/enum :tip :warn :error)
   :fields                  (sc/pred
                              (fn [fields]
                                (every? (fn [field-pair]
                                          (and
                                            (-> field-pair first symbol?)
                                            (-> field-pair second vector?)
                                            (every? #(or (keyword? %) (fn? %) (symbol? %)) (second field-pair))))
                                        (partition 2 fields)))
                              "field")
   :facts                   {:ok   [(sc/pred vector? "The expected OK fact results must be in a vector")]
                             :fail [(sc/pred vector? "The expected FAIL fact results must be in a vector")]}})

(defn- concat-if-some [prev val-result]
  (if val-result
    (concat prev val-result)
    prev))

(defn validate
  "Runs all validators, returning list of concatenated validation results."
  [document]
  (->> (vals @validators)
       (map :fn)
       (pmap #(apply % [document]))
       (reduce concat-if-some)))

(defn- starting-keywords
  "Returns vector of starting keywords of vector until
   a non-keyword is found. [:a :b even? :c] -> [:a :b]"
  [v]
  (last
    (reduce
      (fn [[stop result] x]
        (if (or stop (not (keyword? x)))
          [true result]
          [false (conj result x)]))
      [false []] v)))

(defmacro defvalidator
  "Macro to create document-level validators. Unwraps data etc."
  [code validator-data & body]
  {:pre [(keyword? code)]}
  (let [validator-result (sc/check (dissoc Validator :schemas) (dissoc validator-data :schemas))
        _ (assert (nil? validator-result) (str code validator-result))
        {:keys [doc schemas level fields facts] :or {level :warn}} validator-data
        paths (->> fields (partition 2) (map last) (map starting-keywords) vec)]
    `(let [schema-vals# ~schemas]
       (sc/check (get Validator :schemas) schema-vals#)
       (doseq [schema# schema-vals#]
         (let [validator-code# (keyword (str schema# "-" (name ~code)))]
           (swap! validators assoc validator-code#
                  {:code   validator-code#
                   :doc    ~doc
                   :paths  ~paths
                   :level  ~level
                   :schema schema#
                   :facts  ~facts
                   :fn     (fn [{~'data :data {~'doc-schema :name} :schema-info}]
                             (let [~'data (tools/unwrapped ~'data)]
                               (when (or (not schema#) (= schema# ~'doc-schema))
                                 (let
                                   ~(reduce into
                                            (for [[k v] (partition 2 fields)]
                                              [k `(->> ~'data ~@v)]))
                                   (try
                                     (when (do ~@body)
                                       (map (fn [path#] {:path   path#
                                                         :result [~level (name ~code)]}) ~paths))
                                     (catch Exception e#
                                       [{:path   []
                                         :result [:warn (str "validator")]
                                         :reason (str e#)}]))))))}))))))
