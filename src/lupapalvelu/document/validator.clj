(ns lupapalvelu.document.validator)

(def validators (atom {}))

(defn fetch-values [c]
  (let [value (reduce
                (fn [form [k v]]
                  (conj form k 10))
                [] (partition 2 c))]
    (println value)
    value))

(defn validate
  "Runs all validators, returning list of validation results."
  [document]
  (->>
    validators
    deref
    vals
    (map #(apply % [(:data document)]))
    (filter (comp not nil?))))

(defmacro defvalidator [doc-string {:keys [document fields]} & body]
  `(swap! validators assoc (keyword ~doc-string)
     (fn [~'d]
       (eval
         (let ~(reduce into
                 (for [[k v] (partition 2 fields)]
                   [k `(get-in ~'d ~v)]))
           (try
             (when-let [resp# ~@body]
               {:result [:warn (name resp#)]})
             (catch Exception e# [:err "kosh"])))))))

(defvalidator "Kokonaisalan oltava vähintään kerrosala"
  {:document "uusiRakennus"
   :fields [kokonaisala [:mitat :kokonaisala]
            kerrosala   [:mitat :kerrosala]]}
  (and kokonaisala kerrosala (> kerrosala kokonaisala) :vrk:CR326))

#_(defvalidator
  {:code      "vrk:CR326"
   :text      "Kokonaisalan oltava vähintään kerrosala"
   :document  "uusiRakennus"
   :fields    [kokonaisala [:mitat :kokonaisala]
               kerrosala   [:mitat :kerrosala]]}
  (and kokonaisala kerrosala (> kerrosala kokonaisala)))

(println "warn:" (validate {:data {:mitat {:kerrosala 10 :kokonaisala 9}}}))
(println " err:" (validate {:data {:mitat {:kerrosala 10 :kokonaisala "abba"}}}))
(println "  ok:" (validate {:data {:mitat {:kerrosala 10 :kokonaisala 10}}}))
