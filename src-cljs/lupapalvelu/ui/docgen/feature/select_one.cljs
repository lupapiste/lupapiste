(ns lupapalvelu.ui.docgen.feature.select-one
  "Hide fields depending on a radio button choice"
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.docgen.feature.value :as value]))

(def select-one-of-key "_selected")

(defn resolve-selectable-fields
  "Check if siblings contain a radio group (primary) referencing other siblings (secondary).
  Returns a map of secondary field -> primary field path."
  [body parent-path]
  (->> body
       ;; In the schema the "primary" field points to the "secondary" field
       ;; (e.g. a dropdown select has `:other-key` pointing to a string field)
       ;; Here we reverse the direction so that the other field knows it depends
       ;; on the value of the primary field.
       (mapcat (fn [schema]
                 (when (and (= select-one-of-key (:name schema))
                            (= "radioGroup" (:type schema)))
                   (->> (:body schema)
                        (map (fn [subschema]
                               [(:name subschema)
                                {:path (conj parent-path (keyword (:name schema)))}]))))))
       (into {})))

(defn hide-field?
  "Add reverse  information to the secondary field, if applicable."
  [selectable-fields-mapping {:keys [doc-id schema]}]
  (when-let [select-one-field (get selectable-fields-mapping (:name schema))]
    (let [value (<sub [::value/current-field-value doc-id (:path select-one-field)])]
      (not= value (:name schema)))))
