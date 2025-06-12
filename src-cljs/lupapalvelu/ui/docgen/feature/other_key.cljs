(ns lupapalvelu.ui.docgen.feature.other-key
  "Handle the `:other-key` feature.
  We use the term primary field to describe the field with the `:other-key`
  property, which points to the hideable secondary field.

  In practice this feature provides a \"show this field if that checkbox is checked\"
  functionality."
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.docgen.feature.value :as value]))

(defn resolve-other-fields
  "Fields can sometimes refer to their siblings using `:other-key`.
  Returns a map of secondary field -> primary field path & type."
  [body parent-path]
  (->> body
       ;; In the schema the "primary" field points to the "secondary" field
       ;; (e.g. a dropdown select has `:other-key` pointing to a string field)
       ;; Here we reverse the direction so that the other field knows it depends
       ;; on the value of the primary field.
       (keep (fn [{:keys [other-key] :as schema}]
               (when other-key
                 [other-key {:path (conj parent-path (keyword (:name schema)))
                             :type (keyword (:type schema))}])))
       (into {})))

(defn with-primary-source
  "Add reverse `:other-key` information to the secondary field, if applicable."
  [other-mapping options]
  (if-let [other-source (get other-mapping (-> options :schema :name))]
    ;; Note that technically this value propagates down to all the descendants,
    ;; but the secondary fields should typically be just text inputs so it shouldn't matter.
    (assoc options :primary-source other-source)
    options))

(defn- secondary-field-hidden?
  "Check if the value of the primary field mandates that the secondary field is hidden."
  [{:keys [primary-source doc-id]}]
  (when primary-source
    (let [primary-value (<sub [::value/current-field-value doc-id (:path primary-source)])]
      (case (:type primary-source)
        :checkbox (false? primary-value)
        :select   (not= "other" primary-value)))))

(defn with-secondary-field-hidden-flag
  "Add flag to this (secondary) field if the primary field hides this field."
  [options]
  (if (secondary-field-hidden? options)
    (assoc options :secondary-field-hidden? true)
    options))
