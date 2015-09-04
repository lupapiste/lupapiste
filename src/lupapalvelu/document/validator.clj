(ns lupapalvelu.document.validator
  (:require [schema.core :as sc]
            [sade.util :as util]
            [lupapalvelu.document.tools :as tools]))

(defonce validators (atom {}))

(def Validator
  {:doc                     sc/Str
   :schemas                 [sc/Str]
   (sc/optional-key :level) (sc/enum :tip :warn :error)
   ;;
   ;; *** TODO: ota tassa kayttoon parempi tarkistus. Yritelmia alla kommenteissa. ***
   ;;
   :fields                  [sc/Any]
;   :fields                  [(sc/pair sc/Symbol "variable-symbol" [(sc/either sc/Keyword util/Fn)] "path-part")]  ;; TODO: tama ei toimi
;   :fields                  (sc/pred
;                              (fn [fields]
;                                (println "\n fields first: " (first fields) "\n")
;                                (println "\n fields second: " (second fields) "\n")
;                                (every? (fn [field]
;                                          (and
;                                            (-> field first keyword?)
;                                            (-> field second vector?)
;                                            (every? (fn [part] (or (keyword? part) (fn? part))) (second field)))) (partition 2 fields)))
;                              "field")
   :facts   {:ok            (sc/either
                              []
                              [(sc/pred vector? "The expected OK fact results must be in a vector")])
             :fail          (sc/either
                              []
                              [(sc/pred vector? "The expected FAIL fact results must be in a vector")])}})

(defn validate
  "Runs all validators, returning list of concatenated validation results."
  [document]
  (->>
    validators
    deref
    vals
    (map :fn)
    (map (util/fn-> (apply [document])))
    (reduce concat)
    (filter (comp not nil?))))

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
  {:pre (keyword? code)}
  (let [validator-data (util/ensure-sequential validator-data :schemas)
        validator-result (sc/check Validator validator-data)
        _ (assert (nil? validator-result) (str code validator-result))
        {:keys [doc schemas level fields facts] :or {level :warn}} validator-data
        paths (->> fields (partition 2) (map last) (map starting-keywords) vec)]
    `(doseq [schema# ~schemas]
       (let [validator-code# (keyword (str schema# "-" (name ~code)))]
         (swap! validators assoc validator-code#
           {:code validator-code#
            :doc ~doc
            :paths ~paths
            :level ~level
            :schema schema#
            :facts ~facts
            :fn (fn [{~'data :data {~'doc-schema :name} :schema-info}]
                  (let [~'data (tools/unwrapped ~'data)]
                    (when (or (not schema#) (= schema# ~'doc-schema))
                      (let
                        ~(reduce into
                           (for [[k v] (partition 2 fields)]
                             [k `(->> ~'data ~@v)]))
                        (try
                          (when-let [resp# (do ~@body)]
                            (map (fn [path#] {:path   path#
                                              :result [~level (name validator-code#)]}) ~paths))
                          (catch Exception e#
                            [{:path   []
                              :result [:warn (str "validator")]
                              :reason (str e#)}]))))))})))))
