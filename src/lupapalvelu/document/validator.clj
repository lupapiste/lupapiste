(ns lupapalvelu.document.validator)

(def validators (atom {}))

(defmacro defvalidator [doc-string {:keys [code document fields]} & body]
  `(swap! validators assoc (keyword ~code)
     {:doc ~doc-string
      :fn (fn [~'_]
            (let [~'kerrosala 10
                  ~'kokonaisala 9]
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


