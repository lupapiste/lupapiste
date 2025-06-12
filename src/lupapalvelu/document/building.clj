(ns lupapalvelu.document.building
  "Support for upserting building data on the document. Whereas the source data (from the
  backing system building registry) is in the KuntaGML format the target document might be
  Ryhti, as well."
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.building :as building]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.organization :as org]
            [lupapalvelu.ryhti.transform :as ryhti-transform]
            [monger.operators :refer :all]
            [sade.core :refer [fail fail! ok]]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- load-building-data
  "Always in KuntaGML format."
  [url credentials property-id building-id overwrite-all? application]
  (let [all-data (building-reader/->rakennuksen-tiedot-by-id
                   (building-reader/building-xml url
                                                 credentials
                                                 property-id)
                   building-id)]
    (when all-data
      (building/upsert-document-buildings application all-data))
    (if overwrite-all?
      all-data
      (select-keys all-data (keys building-reader/empty-building-ids)))))

(defn- document-updates
  [{:keys [application created]} document building-data clear-ids?]
  (let [schema        (model/get-document-schema document)
        ryhti?        (ryhti-transform/ryhti-doc? document)
        ;; Apartments are handled separately
        building-data (dissoc building-data :huoneistot)
        updates       (->> (if clear-ids?
                             (cond-> building-reader/empty-building-ids
                               ryhti? (assoc :pysyva-rakennustunnus "" ))
                             (cond->> building-data
                               ryhti? (ryhti-transform/building->ryhti application document)))
                           tools/path-vals
                           (filter
                             (fn [[path _]]
                               (model/find-by-name (:body schema) path))))]
    (doc-persistence/validated-model-updates application "documents"
                                             document updates created
                                             :source "krysp")))

(defn- huoneistot-updates
  "Huoneistot are not merged but simply replaced, since otherwise could end up with ghost
  apartments. Returns map with `:mongo-updates`. The update is a 'document-aware' mongo
  update."
  [command document {:keys [huoneistot] :as _building-data}]
  ;; Check that schema includes huoneistot and later we pick only schema-allowed keys from
  ;; the building data. For Ryhti, the selection is done in the transform code.
  (let [schema            (model/get-document-schema document)
        ryhti?            (ryhti-transform/ryhti-doc? document)
        apartments-schema (cond-> schema
                            ryhti? (schemas/get-subschema :rakennuskohteen-osa)
                            true   (schemas/get-subschema :huoneistot))]
    (when-let [apartment-keys (some->> apartments-schema
                                       :body
                                       (map (comp keyword :name))
                                       seq)]
     (letfn [(pack-field [v]
               {:modified    (:created command)
                :value       v
                :source      "krysp"
                :sourceValue v})
             (pack-apartment  [apartment]
               (->> apartment
                    (map (fn [[field v]]
                           [field (pack-field v)]))
                    (into {})))]
       (let [huoneistot (->> huoneistot
                             (map (fn [[k v]]
                                    [k (cond-> v
                                         (not ryhti?) (select-keys apartment-keys)
                                         true         pack-apartment
                                         ryhti?       ryhti-transform/apartment->ryhti)]))
                             (into {}))
             validation (model/validate-fields (:application command)
                                               (model/document-info document schema)
                                               nil
                                               {:huoneistot huoneistot}
                                               (cond-> []
                                                 ryhti? (conj :rakennuskohteen-osa)))]
         (if (empty? validation)
           {:mongo-updates {$set {(util/kw-path :documents.$.data
                                                (when ryhti?
                                                  :rakennuskohteen-osa)
                                                :huoneistot) huoneistot}}}
           (fail! :document-would-be-in-error-after-update :results validation)))))))

(defn update-document-building! [{:keys [application created] :as command}
                                 document-id building-id overwrite?]
  (let [{:keys [url credentials]} (org/get-building-wfs application)
        path                      "buildingId"
        collection                "documents"
        ;; It seems that `building-id` is never other in practise? When the user selects
        ;; other value, `merge-details-from-krysp` is not called, but the regular
        ;; `update-doc` instead.
        other?                    (= model/other-value building-id)
        clear-ids?                (or (ss/blank? building-id) other?)
        document                  (tools/by-id application collection document-id)]
    (cond
      (not (or clear-ids? url)) (fail :error.no-legacy-available)
      (not document)            (fail :error.document-not-found)
      :else
      (let [schema        (model/get-document-schema document)
            path-schema   (model/find-by-name (:body schema) [path])
            converted-doc (when overwrite? ; don't clean data if user doesn't wish to override
                            (model/convert-document-data ; remove old krysp data
                              (fn [_ value] ; pred
                                (= "krysp" (:source value)))
                              (fn [schema value] ; emitter sets default values
                                (-> value
                                    (dissoc :source :sourceValue :modified)
                                    ;; TODO: Should we resolve the schema flags here?
                                    (assoc :value (tools/default-values {} schema))))
                              document
                              nil))
            cleared-data  (-> (:data converted-doc)
                              ;; buildingId is set below explicitly
                              (dissoc :buildingId :huoneistot)
                              ;; Ryhti building documents
                              (util/dissoc-in [:rakennuskohteen-osa :huoneistot]))

            buildingId-updates    (doc-persistence/->model-updates
                                    (cond-> [[path building-id]]
                                      ;; clear other-key, if we just selected buildingId from dropdown
                                      (and (not other?) (:other-key path-schema))
                                      (conj [(:other-key path-schema) ""])))
            buildingId-update-map (doc-persistence/validated-model-updates application collection
                                                                           document buildingId-updates
                                                                           created :source nil)

            clearing-updates    (tools/path-vals (tools/unwrapped cleared-data))
            ;; create updates only when there is data
            clearing-update-map (when-not (util/empty-or-nil? clearing-updates)
                                  (doc-persistence/validated-model-updates application collection
                                                                           document clearing-updates
                                                                           created :source nil))

            building-data (when-not clear-ids?
                            (load-building-data url credentials (:propertyId application)
                                                building-id overwrite? application))


            document-update-map   (document-updates command document building-data clear-ids?)
            huoneistot-update-map (huoneistot-updates command document building-data)

            {:keys [mongo-query
                    mongo-updates]} (util/deep-merge clearing-update-map
                                                     buildingId-update-map
                                                     document-update-map
                                                     huoneistot-update-map)]
        (action/update-application command mongo-query mongo-updates)
        (ok)))))
