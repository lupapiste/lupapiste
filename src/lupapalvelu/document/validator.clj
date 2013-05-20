(ns lupapalvelu.document.validator
  (:require [lupapalvelu.document.tools :as tools]))

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

(defmacro defvalidator [doc-string {:keys [schema fields]} & body]
  (let [paths (->> fields (partition 2) (map last) vec)]
    `(swap! validators assoc (keyword ~doc-string)
       (fn [{~'data :data {{~'doc-schema :name} :info} :schema}]
         (let [~'d (tools/un-wrapped ~'data)]
           (when (or (not ~schema) (= ~schema ~'doc-schema))
             (let
               ~(reduce into
                  (for [[k v] (partition 2 fields)]
                    [k `(get-in ~'d ~v)]))
               (try
                 (when-let [resp# (do ~@body)]
                   (map (fn [path#] {:path   path#
                                     :result [:warn (name resp#)]}) ~paths))
                 (catch Exception e#
                   {:result [:warn (str "validator")]
                    :reason (str e#)})))))))))

(comment
  (defvalidator "Kokonaisalan oltava vähintään kerrosala"
    {:schema "uusiRakennus"
     :fields [kokonaisala [:mitat :kokonaisala]
              kerrosala   [:mitat :kerrosala]]}
    (and kokonaisala kerrosala (> kerrosala kokonaisala) :vrk:CR326)))
