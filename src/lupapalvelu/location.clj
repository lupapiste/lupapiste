(ns lupapalvelu.location
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.coordinate :as coord]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]]))

(defschema XY [(sc/one ssc/LocationX "X") (sc/one ssc/LocationY "Y")])

(defschema LocationParams
  "Set location (EPSG:3067 point) for an operation."
  (merge ssc/Location
         {:id           ssc/ApplicationId
          :operation-id ssc/NonBlankStr}))

(defschema OperationLocation
  {:epsg3067 XY
   :wgs84    [(sc/one sc/Num "X") (sc/one sc/Num "Y")]
   :user     usr/SummaryUser
   :modified ssc/Timestamp})

(defn location-operations
  [application]
  (->> (app-utils/get-operations application)
       (filter (fn [{op-name :name}]
                 (let [{:keys [building structure]} (operations/get-operation-metadata op-name)]
                   (or building structure))))
       seq))

(defn has-location-operations
  [{:keys [application]}]
  (when (and application (empty? (location-operations application)))
    (fail :error.no-location-operations)))

(defn valid-operation [{:keys [application data]}]
  (when-let [op-id (:operation-id data)]
    (when-not (util/find-by-id op-id (location-operations application))
      (fail :error.operation-not-found))))

(defn set-operation-location
  ([{:keys [user created] :as command} options]
   (let [{:keys [operation-id
                 x y]} options
         epsg3067      (app/->location x y)
         wgs84         (coord/convert "EPSG:3067" "WGS84" 5 epsg3067)
         location      (sc/validate OperationLocation
                                    {:epsg3067 epsg3067
                                     :wgs84    wgs84
                                     :user     (usr/summary user)
                                     :modified created})]
     (action/update-application command
                                {:documents {$elemMatch {:schema-info.op.id operation-id}}}
                                {$set {:documents.$.schema-info.op.location location
                                       :modified                            created}})
     ;; Buildings array may or may not have a building for the operation
     (action/update-application command
                                {:buildings {$elemMatch {:operationId operation-id}}}
                                {$set {:buildings.$.location       epsg3067
                                       :buildings.$.location-wgs84 wgs84}})))
  ([{options :data :as command}]
   (set-operation-location command options)))

(defschema LocationOperation
  {:id                            ssc/NonBlankStr ; operation id
   :operation                     ssc/NonBlankStr ; kerrostalo-rivitalo
   (sc/optional-key :building-id) ssc/NonBlankStr ; VTJ-PRT
   (sc/optional-key :tag)         ssc/NonBlankStr ; A
   (sc/optional-key :description) ssc/NonBlankStr ; Headquarters
   (sc/optional-key :location)    XY})

(sc/defn ^:always-validate location-operation-list :- [LocationOperation]
  "Information for each location-supported operation. The details are merged from both the operation and
  buildings array data."
  [{:keys [buildings documents] :as application}]
  (let [documents (tools/unwrapped documents)]
    (map (fn [{op-id :id op-name :name :as operation}]
           (let [building                   (util/find-by-key :operationId op-id buildings)
                 {:keys [data schema-info]} (util/find-first (util/fn-> :schema-info :op :id (= op-id))
                                                             documents)
                 xy                         (util/find-first not-empty
                                                             [(:location building)
                                                              (-> schema-info :op :location :epsg3067)])]
             (util/assoc-when {:id        op-id
                               :operation op-name}
                              :building-id (util/find-first ss/blank-as-nil
                                                              [(:nationalId building)
                                                               (:valtakunnallinenNumero data)])
                              :tag         (-> data :tunnus ss/blank-as-nil)
                              :description (util/find-first ss/blank-as-nil
                                                              [(:description operation)
                                                               (:description building)])
                              ;; Invalid coordinates are ignored.
                              :location (when-not (sc/check XY xy) xy))))
         (location-operations application))))
