(ns lupapalvelu.climate-report
  (:require [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.low-carbon-assessment.config :as low-carbon]
            [lupapalvelu.mongo :as mongo]
            [sade.strings :as ss]))

(defn- linked-climate-report-for-doc-exists?
  "Checks if there already exists a climate-report referencing the given document id"
  [app-id document-id]
  (mongo/any? :applications {:_id app-id
                             :climate-reports.building-ref document-id}))

(defn- climate-report-eligible?
  [doc]
  (boolean (and (-> doc :schema-info :generates-climate-report)
                (-> doc :schema-info :climate-report-schema))))

(defn- get-copy-value [data k]
  (cond
    (= k low-carbon/climate-report-lammitetty-nettoala-path)
    (get-in data (conj low-carbon/building-doc-lammitetty-nettoala-path :value) "")

    :else nil))

(defn- create-climate-report-document [{:keys [schema-info data id]} {created :created :as command}]
  (let [climate-report-schema (schemas/get-schema 1 (:climate-report-schema schema-info))
        to-copy               (:copy-to-climate-report schema-info)
        schema-flags          (tools/resolve-schema-flags command)
        base-values           (tools/create-document-data climate-report-schema (partial tools/default-values schema-flags))
        def-values            (reduce (fn [acc k]
                                        (if-let [v (get-copy-value data k)]
                                          (assoc-in acc (conj k :value) v)
                                          acc))
                                      base-values
                                      to-copy)]
    {:id           (mongo/create-id)
     :created      created
     :schema-info  (select-keys (:info climate-report-schema) model/db-schema-info-keys)
     :data         def-values
     :type         "climate-report"
     :building-ref id}))

(defn make-climate-report-docs [{{:keys [id documents]} :application :as command}]
  (let [location-doc  (some (fn [doc] (when (= (tools/doc-type doc) :location)
                                        doc))
                            documents)
        building-docs (filter (fn [{:keys [data] :as doc}]
                                (and (climate-report-eligible? doc)
                                     (= (get-in data (conj low-carbon/building-doc-kohteen-laji-path :value)) "rakennus")
                                     (some-> (get-in data (conj low-carbon/building-doc-rakennusluokka-vahahiilisyys-path :value))
                                             ss/blank-as-nil)))
                              documents)]

    ;; Only generate climate-reports if some building-document has :rakennusluokka-vahahiilisyys set
    (when (and (some? location-doc)
               (seq building-docs))
      (->> (conj building-docs location-doc)
           (filter climate-report-eligible?)
           (remove #(linked-climate-report-for-doc-exists? id (:id %)))
           (mapv #(create-climate-report-document % command))))))

