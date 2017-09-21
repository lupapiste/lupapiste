(ns lupapalvelu.integrations.matti
  (:require [schema.core :as sc]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [clojure.set :as set]
            [lupapalvelu.application-schema :as app-schema]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.schemas :as schemas]
            [sade.schema-utils :as ssu]))


(sc/defschema Operation
  (merge
    (ssu/select-keys app-schema/Operation [:id :name :description])
    {:displayName                (zipmap i18n/supported-langs (repeat sc/Str))
     (sc/optional-key :building) {:new sc/Bool
                                  (sc/optional-key :buildingId) sc/Str}}))

(def base-keys
  [:address :infoRequest :municipality
   :state :permitType :location-wgs84
   :id :applicant :operations :propertyId
   :location])

(sc/defschema ApplicationBaseData                           ; Format application schema appropriate for integration
  (merge
    (ssu/select-keys app-schema/Application base-keys)
    {:operations     [Operation]
     :location       {:x sc/Num :y sc/Num}
     :location-wgs84 {:x sc/Num :y sc/Num}
     :link           (zipmap i18n/supported-langs (repeat ssc/OptionalHttpUrl))}))

(defn to-lang-map [localize-function]
  (reduce (fn [result lang]
            (assoc result (keyword lang) (localize-function lang)))
          {}
          i18n/supported-langs))

(sc/defn ^:always-validate get-op-data :- Operation [op]
  (-> (select-keys op [:id :name :description])
      (assoc :displayName (to-lang-map #(i18n/localize % "operations" (:name op))))))

(defn get-building-data [document]
  (let [unwrapped (tools/unwrapped document)]
    (if (get-in unwrapped [:data :buildingId])              ; has 'existing' building
      {:new false :buildingId (get-in unwrapped [:data (keyword schemas/national-building-id)])}
      {:new true})))

(defn enrich-operation-with-building [{:keys [documents]} operation]
  (if-let [op-doc (->> documents
                       (util/find-first
                         #(= (:id operation)
                             (get-in % [:schema-info :op :id]))))]
    (util/assoc-when operation :building (get-building-data op-doc))
    operation))

(sc/defn ^:always-validate build-operations :- [Operation]
  [{:keys [primaryOperation secondaryOperations] :as app}]
  (->> (cons (get-op-data primaryOperation)
             (map get-op-data secondaryOperations))
       (map (partial enrich-operation-with-building app))))

(defn make-app-link [id lang]
  (str (env/value :host) "/app/" (name lang) "/authority" "#!/application/" id))

(def location-array-to-map (fn [[x y]] {:x x :y y}))

(sc/defn ^:always-validate app-to-json :- ApplicationBaseData [application]
  (-> (select-keys application (keys ApplicationBaseData))
      (update :location-wgs84 location-array-to-map)
      (update :location location-array-to-map)
      (assoc :operations (build-operations application))
      (assoc :link (to-lang-map #(make-app-link (:id application) %)))))

(defn state-map [state]
  {:name state
   :displayName (to-lang-map #(i18n/localize % state))})

(defn state-change-data [application new-state]
  (-> (app-to-json application)
      (set/rename-keys {:location-wgs84 :locationWGS84})
      (assoc :state (name new-state))
      (assoc :fromState (state-map (:state application)))
      (assoc :toState (state-map new-state))))
