(ns lupapalvelu.ryhti.api.schema-utils
  (:require [lupapalvelu.document.all-schemas]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.shared-tools :as shared-tools]))

(def ryhti-flags
  "Field flags which indicate interesting subsections of documents."
  #{:ryhti-address?
    :ryhti-measurements?})

(defn- paths-with-flag
  "Return a map of schema name to schema paths for all elements where `flag` is truthy."
  [flag]
  (->> (schemas/get-schemas 1)
       (keep (fn [[schema-name schema]]
               (when-let [paths (not-empty (shared-tools/walk-schema
                                             schema
                                             ;; NOTE: we don't at the moment consider
                                             ;; repeating groups here as none of the fields
                                             ;; require it.
                                             (fn [{:keys [path element]}]
                                               (when (get element flag)
                                                 {:result path}))))]
                 [schema-name paths])))
       (into {})))

(def schema-paths-per-flag
  "Map of flag to a map of schema name to paths where the corresponding flag is truthy."
  (reduce
    (fn [acc flag]
      (assoc acc flag (paths-with-flag flag)))
    {}
    ryhti-flags))

(defn- get-fields
  [documents paths-per-schema]
  (->> documents
       (mapcat (fn [doc]
                 (let [schema-name (-> doc :schema-info :name)]
                   (when-let [paths (get paths-per-schema schema-name)]
                     (keep (fn [path]
                             (get-in doc (into [:data] path)))
                           paths)))))
       (into [])))

(defn fields-per-flag
  "Find all fields of the documents with relevant flags.
  Returns a map of flag to a coll of field values."
  [documents]
  (reduce
    (fn [acc flag]
      (if-let [paths (get schema-paths-per-flag flag)]
        (assoc acc flag (get-fields documents paths))
        acc))
    {}
    ryhti-flags))
