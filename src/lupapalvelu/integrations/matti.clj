(ns lupapalvelu.integrations.matti
  (:require [schema.core :as sc]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [clojure.set :as set]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]))


(sc/defschema operation
  {:id ssc/ObjectIdStr
   :name sc/Str
   :displayName {:fi sc/Str
                 :sv sc/Str}
   :description sc/Str
   (sc/optional-key :building) {:new sc/Bool
                                (sc/optional-key :buildingId) sc/Str}})

(sc/defschema base-data
  {:id            sc/Str
   :operations    [operation]
   :propertyId    sc/Str
   :municipality  sc/Str
   :location      {:x sc/Num
                   :y sc/Num}
   :location-wgs84 {:x sc/Num
                    :y sc/Num}
   :address       sc/Str
   :state         (apply sc/enum (map name states/all-states))
   :permitType    (apply sc/enum (map name (keys (permit/permit-types))))
   :applicant     sc/Str
   :infoRequest   sc/Bool
   :link {:fi sc/Str
          :sv sc/Str}})

(sc/defn get-op-data :- operation [op]
  (-> (select-keys op [:id :name :description])
      (assoc :displayName {:fi (i18n/localize :fi "operations" (:name op))
                           :sv (i18n/localize :sv "operations" (:name op))})
      (util/assoc-when :building (get build-mapping (:id op)))))

(defn build-operations [{:keys [primaryOperation secondaryOperations]}]
  (cons (get-op-data primaryOperation) (map get-op-data secondaryOperations)))

(defn make-app-link [id lang]
  (str (env/value :host) "/app/" (name lang) "/authority" "#!/application/" id))

(def location-array-to-map (fn [[x y]] {:x x :y y}))

(sc/defn app-to-json :- base-data [application]
  (-> (select-keys application (keys base-data))
      (update :location-wgs84 location-array-to-map)
      (update :location location-array-to-map)
      (assoc :operations (build-operations application))
      (assoc :link {:fi (make-app-link (:id application) :fi)
                    :sv (make-app-link (:id application) :sv)})
      (set/rename-keys {:location-wgs84 :locationWGS84})))

(defn state-map [state]
  {:name state
   :displayName {:fi (i18n/localize :fi state)
                 :sv (i18n/localize :sv state)}})

(defn state-change-data [application new-state]
  (-> (app-to-json application)
      (assoc :state (name new-state))
      (assoc :fromState (state-map (:state application)))
      (assoc :toState (state-map new-state))))
