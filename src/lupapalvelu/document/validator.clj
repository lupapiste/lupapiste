(ns lupapalvelu.document.validator)

(def validators (atom {}))

(defn validate
  "Runs all validators, returning list of validation results."
  [document]
  (->>
    validators
    deref
    vals
    (map #(apply % [document]))
    (filter (comp not nil?))))

(defmacro defvalidator [doc-string {:keys [schema fields]} & body]
  `(swap! validators assoc (keyword ~doc-string)
     (fn [{~'data :data}]
       (eval
         (let ~(reduce into
                 (for [[k v] (partition 2 fields)]
                   [k `(get-in ~'data ~v)]))
           (try
             (when-let [resp# ~@body]
               {:result [:warn (name resp#)]})
             (catch Exception e# [:err "kosh"])))))))

(defvalidator "Kokonaisalan oltava vähintään kerrosala"
  {:schema "uusiRakennus"
   :fields [kokonaisala [:mitat :kokonaisala]
            kerrosala   [:mitat :kerrosala]]}
  (and kokonaisala kerrosala (> kerrosala kokonaisala) :vrk:CR326))

(println "warn:" (validate {:data {:mitat {:kerrosala 10 :kokonaisala 9}}}))
(println " err:" (validate {:data {:mitat {:kerrosala 10 :kokonaisala "abba"}}}))
(println "  ok:" (validate {:data {:mitat {:kerrosala 10 :kokonaisala 10}}}))
