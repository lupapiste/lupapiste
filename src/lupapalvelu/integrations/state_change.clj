(ns lupapalvelu.integrations.state-change
  (:require [taoensso.timbre :refer [errorf debugf infof warn]]
            [monger.operators :refer [$set]]
            [clojure.set :as set]
            [clj-http.client :as clj-http]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.schema-utils :as ssu]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [taoensso.nippy :as nippy]
            [lupapalvelu.application-schema :as app-schema]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.integrations.messages :as messages]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.organization :as org])
  (:import (com.mongodb WriteConcern)))

(def displayName {:displayName (zipmap i18n/supported-langs (repeat sc/Str))})

(sc/defschema Operation
  (merge
    (ssu/select-keys app-schema/Operation [:id :name :description])
    displayName
    {(sc/optional-key :building) {:new sc/Bool
                                  (sc/optional-key :buildingId) sc/Str}
     (sc/optional-key :structure) sc/Bool}))

(def base-keys
  [:address :infoRequest :municipality
   :state :permitType :permitSubtype
   :id :applicant :propertyId
   :location-wgs84 :location])

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
          :toState   (merge {:name (get app-schema/Application :state)} displayName)
          :messageType sc/Str}))

(sc/defn ^:always-validate get-op-data :- Operation [op]
  (-> (select-keys op [:id :name :description])
      (assoc :displayName (i18n/to-lang-map #(i18n/localize % "operations" (:name op))))))

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

(defn building? [operation]
  (let [metadata (operations/get-operation-metadata (:name operation))]
    (true? (:building metadata))))

(defn structure? [operation]
  (let [metadata (operations/get-operation-metadata (:name operation))]
    (true? (:structure metadata))))

(defn get-building-data [document]
  (let [unwrapped (tools/unwrapped document)]
    (if-let [select-value (get-in unwrapped [:data :buildingId])]        ; has 'existing' building
      {:new false :buildingId (get-building-id select-value unwrapped)}
      {:new true})))

(defn enrich-operation
  "Add :building or :structure key to operation if necessary."
  [{:keys [documents] :as app} operation]
  (if-let [op-doc (and
                    (util/=as-kw (permit/permit-type app) :R)
                    (->> documents
                         (util/find-first
                           #(= (:id operation)
                               (get-in % [:schema-info :op :id])))))]
    (cond
      (building? operation) (util/assoc-when operation :building (get-building-data op-doc))
      (structure? operation) (assoc operation :structure true)
      :else operation)
    operation))

(sc/defn ^:always-validate build-operations :- [Operation]
  [{:keys [primaryOperation secondaryOperations] :as app}]
  (->> (cons (get-op-data primaryOperation)
             (map get-op-data secondaryOperations))
       (map (partial enrich-operation app))))

(defn make-app-link [id lang]
  (str (env/value :host) "/app/" (name lang) "/authority" "#!/application/" id))

(def location-array-to-map (fn [[x y]] {:x x :y y}))

(def nil-as-nil (fn [value] (when-not (nil? value) value)))

(sc/defn ^:always-validate application-data :- ApplicationBaseData [application]
  (-> (select-keys application (keys ApplicationBaseData))
      (update :location-wgs84 location-array-to-map)
      (update :location location-array-to-map)
      (update :permitSubtype nil-as-nil)
      (assoc :operations (build-operations application))
      (assoc :link (i18n/to-lang-map #(make-app-link (:id application) %)))))

(defn state-map [state]
  {:name (name state)
   :displayName (i18n/to-lang-map #(i18n/localize % state))})

(sc/defn ^:always-validate state-change-data :- StateChangeMessage [application new-state]
  (-> (application-data application)
      (set/rename-keys {:location-wgs84 :locationWGS84})
      (assoc :state (name new-state))
      (assoc :fromState (state-map (:state application)))
      (assoc :toState (state-map new-state))
      (assoc :messageType "state-change")))

(defn valid-states [new-state old-state]
  (or ((states/all-application-states-but :draft :open) (keyword new-state))
      (and (#{:submitted} (keyword old-state))
           (#{:draft} (keyword new-state)))))

(sc/defschema EndpointData
  {:url                          sc/Str
   (sc/optional-key :headers)    {sc/Str sc/Str}
   (sc/optional-key :basic-auth) [sc/Str]})

(sc/defn ^:always-validate get-state-change-endpoint-data :- (sc/maybe EndpointData) [organization]
  (when-let [url (get-in @organization [:state-change-endpoint :url])]
    (let [endpoint-conf (:state-change-endpoint @organization)
          crypto-iv     (:crypto-iv-s endpoint-conf)
          endpoint {:url     (ss/strip-trailing-slashes url)
                    :headers (->> (get-in @organization [:state-change-endpoint :header-parameters])
                                  (map (fn [header] {(str (:name header)) (str (org/decode-credentials (:value header) crypto-iv))}))
                                  (apply merge))}]
    (when (= (:auth-type endpoint-conf) "basic")
      (assoc endpoint :basic-auth (org/get-credentials {:username  (:basic-auth-username endpoint-conf)
                                                        :password  (:basic-auth-password endpoint-conf)
                                                        :crypto-iv crypto-iv})))
    endpoint)))

(defn send-via-http [message-id data {:keys [url headers]}]
  (http/post url
             {:headers          headers
              :body             (clj-http/json-encode data)
              :throw-exceptions true})
  (messages/update-message message-id {$set {:status "done" :acknowledged (now)}} WriteConcern/UNACKNOWLEDGED)
  (infof "JSON sent to state-change endpoint successfully"))

(when (env/feature? :jms)

(def matti-json-queue "lupapiste.application.state-change")

(defn create-state-change-consumer [session]
  (fn [{:keys [url headers options data]}]
    (logging/with-logging-context {:userId (:user-id options) :applicationId (:application-id options)}
      (try
        (send-via-http (:message-id options) data {:url url :headers headers})
        (jms/commit session)
        (debugf "state-change message (id: %s) consumed and acknowledged from queue successfully" (:message-id options))
        (catch Exception e      ; this is most likely a slingshot exception from clj-http
          (errorf "Failed to send consumed state-change message to %s: %s %s." url (type e) (.getMessage e))
          (errorf "Message (id: %s) rollback initiated" (:message-id options))
          (jms/rollback session))))))

(defn create-jms-session []
  (if-let [conn (jms/get-default-connection)]
    (-> conn
        (jms/create-transacted-session)
        (jms/register-session :consumer))
    (warn "No JMS connection available")))

(def json-consumer-session (create-jms-session))

(when json-consumer-session
  (defonce state-change-consumer
           (jms/create-nippy-consumer
             json-consumer-session
             matti-json-queue
             (create-state-change-consumer json-consumer-session))))

(sc/defn ^:always-validate send-via-jms [state-change-data :- StateChangeMessage endpoint-data :- EndpointData options]
  (jms/produce-with-context matti-json-queue (nippy/freeze (assoc endpoint-data :data state-change-data :options options)))
  (debugf "Produced state-change (%s) msg (id: %s to JMS queue %s"
          (get-in state-change-data [:toState :name])
          (:message-id options)
          matti-json-queue))
)

(defn trigger-state-change [{user :user organization :organization :as command} new-state]
  (when (valid-states new-state (get-in command [:application :state]))
    (when-let [outgoing-data (state-change-data (:application command) new-state)]
      (let [message-id (messages/create-id)
            jms? (env/feature? :jms)
            app (:application command)
            msg (util/assoc-when
                  {:id message-id :direction "out"
                   :status (if jms? "queued" "published") :messageType "state-change"
                   :partner "matti" :format "json" :transferType "http"
                   :created (or (:created command) (now))
                   :application (-> (select-keys app [:id :organization])
                                    (assoc :state (name new-state)))
                   :initator (select-keys (:user command) [:id :username])
                   :data outgoing-data}
                  :action (:action command))]
        (messages/save msg)
        (if-let [endpoint (get-state-change-endpoint-data organization)]
          (if (and jms? json-consumer-session)
            (send-via-jms outgoing-data endpoint {:user-id (:id user) :message-id message-id :application-id (:id app)})
            (send-via-http message-id outgoing-data endpoint))
          (warn "No state-change endpoint defined!"))))))
