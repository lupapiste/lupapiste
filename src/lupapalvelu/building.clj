(ns lupapalvelu.building
  (:require [monger.operators :refer :all]
            [clojure.string :as s]
            [taoensso.timbre :refer [info warnf]]
            [schema.core :as sc]
            [lupapalvelu.action :as action]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [sade.coordinate :as coord]
            [sade.core :as core]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.document.tools :as tools]))

(defn- doc->building-data
  [{{{national-id :value} :valtakunnallinenNumero {short-id :value} :tunnus} :data {{operation-id :id} :op} :schema-info}
   include-bldgs-without-nid?]
  (when (or (and short-id include-bldgs-without-nid?) (ss/not-blank? national-id))
    {:operation-id operation-id
     :national-id national-id
     :short-id    short-id}))

(defn building-ids
  "Gathers building-id data from documents."
  [{:keys [documents]} & [include-bldgs-without-nid?]]
  (->> (map #(doc->building-data % include-bldgs-without-nid?) documents)
       (remove nil?)))

(defn building-id-mapping
  "Returns mapping from building short-id to national-id."
  [application]
  (->> (building-ids application)
       (map (juxt :national-id :short-id))
       (remove (partial some nil?))
       (into {})))

(defn buildingId-in-document?
  "Predicate to validate that given operation id matches document's operation id.
   Document schema is checked to contain valtakunnallinenNumero"
  [op-id {{op :op :as info} :schema-info}]
  (let [schema-body (:body (schemas/get-schema info))]
    (when (and op
               (:id op)
               (some #(= (:name %) schemas/national-building-id) schema-body))
      (= (:id op) op-id))))

(defn push-building-updates
  "Push the provided building data to building-updates array in the
  application. The data is used to buid the actual building array when
  the application is given verdict."
  [{:keys [documents]} operation-id national-building-id location-map apartments-data timestamp]
  (when (some (partial buildingId-in-document? operation-id) documents)
    {$push {:building-updates
            {:operationId operation-id
             :nationalBuildingId national-building-id
             :location location-map
             :timestamp timestamp
             :apartments apartments-data}}}))

(defn document-buildingid-updates-for-operation
  "Generates valtakunnallinenNumero updates to documents regarding operation.
   Example update: {documents.1.data.valtakunnalinenNumero.value '123456001M'}"
  [{:keys [documents]} buildingId op-id]
  (mongo/generate-array-updates :documents
                                documents
                                (partial buildingId-in-document? op-id)
                                "data.valtakunnallinenNumero.value"
                                buildingId))

(defn buildings-array-buildingid-updates-for-operation
  "Generates nationalId (valtakunnallinenNumero) updates to buildings array regarding operation.
   Example update: {buildings.1.nationalId '123456001M'}"
  [{:keys [buildings]} buildingId op-id]
  (mongo/generate-array-updates :buildings
                                buildings
                                (comp #{op-id} :operationId)
                                "nationalId"
                                buildingId))

(defn buildings-array-location-updates-for-operation
  "Generates location.x location.y updates to buildings array regarding operation.
   Example update: {buildings.1.location [5232.12 1234.12]}"
  [{:keys [buildings]} {:keys [x y] :as location-map} op-id]
  (when location-map
    (if-not (sc/check ssc/Location location-map)
      (merge
        (mongo/generate-array-updates :buildings
                                      buildings
                                      (comp #{op-id} :operationId)
                                      "location"
                                      [x y])
        (mongo/generate-array-updates :buildings
                                      buildings
                                      (comp #{op-id} :operationId)
                                      "location-wgs84"
                                      (coord/convert "EPSG:3067" :WGS84 5 [x y])))
      (warnf "Invalid location update to buildings array, x: %s, y: %s" x y))))

(defn review-buildings-national-id-updates
  "Generates updates for each review tasks' valtakunnalinenNumero. Example update:

  {:tasks.0.data.rakennus.1.rakennus.valtakunnallinenNumero {:value '123456001M'
                                                             :modified 1555087307184}
   :tasks.1.data.rakennus.1.rakennus.valtakunnallinenNumero {:value '123456001M'
                                                             :modified 1555087307184}
   :tasks.3.data.rakennus.1.rakennus.valtakunnallinenNumero {:value '123456001M'
                                                             :modified 1555087307184}'}"
  [{:keys [tasks buildings]} op-id national-id timestamp]
  (when-let [index (:index (util/find-by-key :operationId op-id buildings))]
    (->> tasks
         (map-indexed (fn [i task]
                        (when-let [build-key (and (util/=as-kw (get-in task [:schema-info :name])
                                                               :task-katselmus)
                                                  (first (util/find-first (fn [[_ v]]
                                                                            (and (= (get-in v [:rakennus :jarjestysnumero :value])
                                                                                    index)
                                                                                 (not= (get-in v [:rakennus :valtakunnallinenNumero :value])
                                                                                       national-id)))
                                                                          (get-in task [:data :rakennus]))))]
                          [(util/kw-path :tasks i :data.rakennus build-key
                                         :rakennus.valtakunnallinenNumero)
                           {:value    national-id
                            :modified timestamp}])))
         (into {})
         not-empty)))

(defn find-correct-pht-value-from-updates
  "Apartment-to-update is a map and apartment-updates is a coll of maps
   Only those apartments are updated that have a value for huoneistonumero.
   Single apartment-update and apartment-to-update are considered match if they have same values for [:huoneistonumero :jakokirjain :porras].
   apartment-updates that have same values for [:huoneistonumero :jakokirjain :porras] but have different values for pht are discarded/ignored
   If match is found returns pht value"
  [apartment-to-update apartment-updates]
  (when-not (ss/blank? (:huoneistonumero apartment-to-update))
    (let [data-matcher               #(reduce (fn [_ kw]
                                                (if (ss/=trim-i (str (get apartment-to-update kw))
                                                                (str (get % kw)))
                                                  true
                                                  (reduced nil)))
                                              nil
                                              [:huoneistonumero :jakokirjain :porras])
          matching-apartment-updates  (filter #(data-matcher %) apartment-updates)
          pht-updates                 (->> (map #(:pysyvaHuoneistotunnus %) matching-apartment-updates)
                                           (remove nil?)
                                           distinct)
          only-one-pht-update?        (= 1 (count pht-updates))]
      (when (> (count pht-updates) 1)
        (warnf "Invalid apartment updates: %s" (vec matching-apartment-updates)))
      (when only-one-pht-update?
        (first pht-updates)))))

(defn apartment-pht-updates-for-building
  "Finds matching building by operation-id and then updates pht for apartments on the building.
   Note that single apartment-update might update pht values for many apartments
   Example update:
   {:buildings.0.apartments.0.pysyvaHuoneistotunnus.value '0987654321'
    :buildings.0.apartments.1.pysyvaHuoneistotunnus.value '1234567890'}"
  [buildings updates-for-apartments op-id]
  (let [[building-index building-to-update] (util/find-first #(= (-> % second :operationId) op-id)
                                                             (zipmap (range) buildings))
        apartments-with-indexes             (zipmap (range) (:apartments building-to-update))]
    (when building-to-update
      (reduce (fn [pht-updates [apartment-index apartment]]
                (util/assoc-when
                  pht-updates
                  (util/kw-path :buildings building-index :apartments apartment-index :pysyvaHuoneistotunnus)
                  (find-correct-pht-value-from-updates (tools/unwrapped apartment) (distinct updates-for-apartments))))
              {} apartments-with-indexes))))

(defn apartment-pht-updates-for-document
  "Finds matching document by operation-id and updates pht for apartments on the document.
   Note that single apartment-update might update pht values for many apartments
   Example update:
   {:documents.1.data.huoneistot.0.pysyvaHuoneistotunnus {:modified 1555087307184 :value '1234567890'
    :documents.1.data.huoneistot.1.pysyvaHuoneistotunnus {:modified 1555087307184 :value '96874594'}"
  [documents updates-for-apartments timestamp op-id]
  (let [[document-index document-to-update] (util/find-first #(= (-> % second :schema-info :op :id) op-id)
                                                             (zipmap (range) documents))
        apartments-with-indexes             (-> document-to-update :data :huoneistot)]
    (when document-to-update
      (reduce (fn [pht-updates [apartment-index apartment]]
                (let [apartment (tools/unwrapped apartment)
                      pht-value (->> updates-for-apartments
                                     distinct
                                     (find-correct-pht-value-from-updates apartment))]
                  ;; Only update value (and more importantly the :modified timestamp) when necessary
                  ;; to prevent false positives from appearing on authorities' updated apps lists
                  (cond-> pht-updates
                    (and pht-value (-> apartment :pysyvaHuoneistotunnus (not= pht-value)))
                    (assoc (util/kw-path :documents document-index :data :huoneistot apartment-index :pysyvaHuoneistotunnus)
                           {:value pht-value :modified timestamp}))))
              {} apartments-with-indexes))))

(defn- operation-building-id-updates [operation-buildings application]
  (remove
    util/empty-or-nil?
    (map
      (fn [{op-id :operationId buildingId :nationalId}]
        (document-buildingid-updates-for-operation application buildingId op-id))
      operation-buildings)))

(defn operation-building-pht-updates [operation-buildings timestamp application]
  (let [documents (:documents application)]
    (reduce (fn [updates building]
              (let [updates-for-apartments (:apartments building)
                    apartment-updates (apartment-pht-updates-for-document documents updates-for-apartments timestamp (:operationId building))]
                (merge updates apartment-updates)))
            {} operation-buildings)))

(defn building-updates
  "Returns updates for: buildings array of all buildings, document specific buildingId updates and document specific pht updates for apartments.
   Return value is a map of updates. Example: {$set {:buildings [{:nationalId '123'}] :documents.1.data.valtakunnallinenNumero.value '123'
                                                                                      :documents.1.data.huoneistot.0.pysyvaHuoneistotunnus.value '456'
                                                                                      :documents.1.data.huoneistot.1.pysyvaHuoneistotunnus.value '789'}}"
  [application timestamp buildings]
  (when (seq buildings)
    (let [operation-buildings             (filter :operationId buildings)
          building-id-updates-for-docs    (operation-building-id-updates operation-buildings application)
          building-pht-updates-for-docs   (operation-building-pht-updates operation-buildings timestamp application)
          operation-building-updates      (conj building-id-updates-for-docs building-pht-updates-for-docs)]
      (when-not (empty? (conj building-id-updates-for-docs building-pht-updates-for-docs))
        (info "operation building updates from verdict" (pr-str operation-building-updates)))
      {$set (apply merge (conj operation-building-updates {:buildings buildings}))})))

(defn upsert-document-buildings
  "Update or insert vrk building data to application"
  [application building-data]
  (action/update-application (action/application->command application)
                             {$pull {:document-buildings {:vtj-prt (:valtakunnallinenNumero building-data)}}})
  (action/update-application (action/application->command application)
                             {$push {:document-buildings {:id          (mongo/create-id)
                                                          :vtj-prt     (:valtakunnallinenNumero building-data)
                                                          :building    building-data
                                                          :created     (core/now)}}}))
