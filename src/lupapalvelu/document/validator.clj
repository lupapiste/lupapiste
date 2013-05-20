(ns lupapalvelu.document.validator
  (:require [lupapalvelu.document.tools :as tools]
            [lupapalvelu.clojure15 :refer [some->>]]))

(def validators (atom {}))

(defn validate
  "Runs all validators, returning list of validation results."
  [document]
  (->>
    validators
    deref
    vals
    (map #(apply % [document]))
    (reduce concat)
    (filter (comp not nil?))))

(defn- starting-keywords [v]
  (last
    (reduce
      (fn [[stop result] x]
        (if (or stop (not (keyword? x)))
          [true result]
          [false (conj result x)]))
      [false []] v)))

(defmacro defvalidator
  "Macro to create document-level validators. Unwraps data etc."
  [doc-string {:keys [schema fields]} & body]
  (let [paths (->> fields (partition 2) (map last) (map starting-keywords) vec)]
    `(swap! validators assoc (keyword ~doc-string)
       (fn [{~'data :data {{~'doc-schema :name} :info} :schema}]
         (let [~'d (tools/un-wrapped ~'data)]
           (when (or (not ~schema) (= ~schema ~'doc-schema))
             (let
               ~(reduce into
                  (for [[k v] (partition 2 fields)]
                    [k `(some->> ~'d ~@v)]))
               (try
                 (when-let [resp# (do ~@body)]
                   (map (fn [path#] {:path   path#
                                     :result [:warn (name resp#)]}) ~paths))
                 (catch Exception e#
                   {:result [:warn (str "validator")]
                    :reason (str e#)})))))))))
