(ns lupapalvelu.integrations.matti
  (:require [taoensso.timbre :refer [infof]]
            [schema.core :as sc]
            [clj-http.client :as clj-http]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.schema-utils :as ssu]
            [sade.util :as util]
            [clojure.set :as set]
            [lupapalvelu.application-schema :as app-schema]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.integrations.messages :as messages]
            [lupapalvelu.states :as states]))

(def displayName {:displayName (zipmap i18n/supported-langs (repeat sc/Str))})

(sc/defschema Operation
  (merge
    (ssu/select-keys app-schema/Operation [:id :name :description])
    displayName
    {(sc/optional-key :building) {:new sc/Bool
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

(sc/defschema StateChangeMessage                            ; Actual state-change message
  (merge (set/rename-keys ApplicationBaseData {:location-wgs84 :locationWGS84})
         {:fromState (merge {:name (get app-schema/Application :state)} displayName)
          :toState   (merge {:name (get app-schema/Application :state)} displayName)}))

(defn to-lang-map [localize-function]
  (reduce (fn [result lang]
            (assoc result (keyword lang) (localize-function lang)))
          {}
          i18n/supported-langs))

(sc/defn ^:always-validate get-op-data :- Operation [op]
  (-> (select-keys op [:id :name :description])
      (assoc :displayName (to-lang-map #(i18n/localize % "operations" (:name op))))))

(def building-id-other-key
  (or
    (some
      (fn [{:keys [name other-key]}]  (when (= name "buildingId") other-key))
      schemas/rakennuksen-valitsin)
    (fail! :error.building-id-schema-missing-other-key)))

(defn get-building-id
  "Returns building ID based on selected value.
  If selected value is 'other', manually added key will be added.
  Else national-building-id is supplied."
  [selected-value doc]
  (if (= "other" selected-value)
    (get-in doc [:data (keyword building-id-other-key)])
    (get-in doc [:data (keyword schemas/national-building-id)])))

(defn get-building-data [document]
  (let [unwrapped (tools/unwrapped document)]
    (if-let [select-value (get-in unwrapped [:data :buildingId])]        ; has 'existing' building
      {:new false :buildingId (get-building-id select-value unwrapped)}
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

(sc/defn ^:always-validate application-data :- ApplicationBaseData [application]
  (-> (select-keys application (keys ApplicationBaseData))
      (update :location-wgs84 location-array-to-map)
      (update :location location-array-to-map)
      (assoc :operations (build-operations application))
      (assoc :link (to-lang-map #(make-app-link (:id application) %)))))

(defn state-map [state]
  {:name (name state)
   :displayName (to-lang-map #(i18n/localize % state))})

(sc/defn ^:always-validate state-change-data :- StateChangeMessage [application new-state]
  (-> (application-data application)
      (set/rename-keys {:location-wgs84 :locationWGS84})
      (assoc :state (name new-state))
      (assoc :fromState (state-map (:state application)))
      (assoc :toState (state-map new-state))))

(def valid-states (states/all-application-states-but :draft :open))

(defn trigger-state-change [command new-state]
  (when (valid-states (keyword new-state))
    (when-let [outgoing-data (state-change-data (:application command) new-state)]
      (let [message-id (messages/create-id)
            app (:application command)
            msg (util/assoc-when
                  {:id message-id :direction "out"
                   :status "published" :messageType "state-change"
                   :partner "matti" :format "json"
                   :created (or (:created command) (now))
                   :application (-> (select-keys app [:id :organization])
                                    (assoc :state (name new-state)))
                   :initator (select-keys (:user command) [:id :username])
                   :data outgoing-data}
                  :action (:action command))]
        (messages/save msg)
        (infof "MATTI state-change payload written to mongo for state '%s', messageId: %s" (name new-state) message-id)
        (when-let [url nil #_(env/value :matti :rest :url)]         ; TODO send to MQ
          (http/post (str url "/" ((env/value :matti :rest :path :state-change)))
                     {:headers          {"X-Username" (env/value :matti :rest :username)
                                         "X-Password" (env/value :matti :rest :password)
                                         "X-Vault"    (env/value :matti :rest :vault)}
                      :body             (clj-http/json-encode outgoing-data)
                      :throw-exceptions true})
          (infof "MATTI JSON sent to state-change endpoint successfully"))))))
