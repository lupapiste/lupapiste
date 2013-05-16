(ns lupapalvelu.document.validator)

(def validators (atom {}))

(defn fetch-values [c]
  (reduce
    (fn [form [k v]]
      (conj form k (str v "*")))
    [] (partition 2 c)))

(defmacro defvalidator [doc-string {:keys [code document fields]} & body]
  `(swap! validators assoc (keyword ~code)
     {:doc ~doc-string
      :fn (fn [~'document]
            (let ~(fetch-values fields data)
              ~@body))}))

(defvalidator "Kokonaisalan oltava vähintään kerrosala"
  {:code "vrk:CR326"
   :document "uusiRakennus"
   :fields [kokonaisala [:mitat :kokonaisala]
            kerrosala   [:mitat :kerrosala]]}
  (when
    (and
      kokonaisala
      kerrosala
      (> kerrosala kokonaisala))
    [:kosh]))

(defn validate
  "Runs all validators, returning list of validation results."
  [document]
  (->>
    validators
    deref
    vals
    (map :fn)
    (map #(apply % [(:data document)]))
    (filter (comp not nil?))))

(validate {:data {:mitat {:kerrosala 10 :kokonaisala 10}}})


