(ns lupapalvelu.integrations.state-change
  (:require [clj-http.client :as clj-http]
            [clojure.set :as set]
            [lupapalvelu.application-schema :as app-schema]
            [lupapalvelu.cookie :as lupa-cookie]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.integrations.messages :as messages]
            [lupapalvelu.integrations.pubsub :as lip]
            [lupapalvelu.logging :as log]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.system.util-components :as components]
            [monger.operators :refer [$set]]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schema-utils :as ssu]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc])
  (:import [clojure.lang ExceptionInfo]
           (com.mongodb WriteConcern)
           [java.net UnknownHostException]))

(set! *warn-on-reflection* true)

(def displayName {:displayName (zipmap i18n/supported-langs (repeat sc/Str))})

(sc/defschema Operation
  (merge
    (ssu/select-keys app-schema/Operation [:id :name :description])
    displayName
    {(sc/optional-key :building)  {:new                          sc/Bool
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

(sc/defschema StateChangeMessage ; Actual state-change message
  (merge (set/rename-keys ApplicationBaseData {:location-wgs84 :locationWGS84})
         {:fromState     (merge {:name (get app-schema/Application :state)} displayName)
          :toState       (merge {:name (get app-schema/Application :state)} displayName)
          :messageType   sc/Str
          :applicantInfo {:type                          (sc/enum "person" "company")
                          (sc/optional-key :companyId)   ssc/NonBlankStr
                          (sc/optional-key :personId)    ssc/NonBlankStr
                          (sc/optional-key :firstName)   ssc/NonBlankStr
                          (sc/optional-key :lastName)    ssc/NonBlankStr
                          (sc/optional-key :companyName) ssc/NonBlankStr}}))

(sc/defn ^:always-validate get-op-data :- Operation [op]
  (-> (select-keys op [:id :name :description])
      (assoc :displayName (i18n/to-lang-map #(i18n/localize % "operations" (:name op))))))

(def building-id-other-key
  (or
    (some
      (fn [{:keys [name other-key]}]  (when (= name "buildingId") other-key))
      schemas/rakennuksen-valitsin)
    (fail! :error.building-id-schema-missing-other-key)))

(defn- data-string-value [doc field]
  (-> (get-in doc [:data (keyword field)])
      tools/unwrapped
      ss/trim
      ss/blank-as-nil))

(defn- get-building-id
  "Returns building ID based on selected value.
  If selected value is 'other', manually added key will be added. Else
  national-building-id is supplied (and searched both from the document data and buildings
  array)."
  [{:keys [buildings]} selected-value document]
  (if (= "other" selected-value)
    (data-string-value document building-id-other-key)
    (or (data-string-value document schemas/national-building-id)
        ;; Try to find a `selected-value` match in the application buildings.
        (some #(:nationalId (util/find-by-key % selected-value buildings))
              [:nationalId :localId :localShortId]))))

(defn building? [operation]
  (let [metadata (operations/get-operation-metadata (:name operation))]
    (true? (:building metadata))))

(defn structure? [operation]
  (let [metadata (operations/get-operation-metadata (:name operation))]
    (true? (:structure metadata))))

(defn- get-building-data [application document]
  (if-let [select-value (data-string-value document :buildingId)]
    ;; Can have an 'existing' building. However, it is not guaranteed that the building id
    ;; can be successfully resolved, especially for older applications.
    (util/assoc-when{:new false}
                    :buildingId (get-building-id application select-value document))
    {:new true}))

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
      (building? operation)  (util/assoc-when operation :building (get-building-data app op-doc))
      (structure? operation) (assoc operation :structure true)
      :else                  operation)
    operation))

(sc/defn ^:always-validate build-operations :- [Operation]
  [{:keys [primaryOperation secondaryOperations] :as app}]
  (->> (cons (get-op-data primaryOperation)
             (map get-op-data secondaryOperations))
       (map (partial enrich-operation app))))

(defn make-app-link [id lang]
  (str (env/value :host) "/app/" (name lang) "/authority" "#!/application/" id))

(def location-array-to-map (fn [[x y]] {:x x :y y}))

(sc/defn ^:always-validate application-data :- ApplicationBaseData [application]
  (-> (select-keys application (keys ApplicationBaseData))
      (update :location-wgs84 location-array-to-map)
      (update :location location-array-to-map)
      (update :permitSubtype ss/blank-as-nil)
      (assoc :operations (build-operations application))
      (assoc :link (i18n/to-lang-map #(make-app-link (:id application) %)))))

(defn state-map [state]
  {:name        (name state)
   :displayName (i18n/to-lang-map #(i18n/localize % state))})

(defn get-doc-value [doc path-prefix path]
  (tools/unwrapped (get-in doc (into path-prefix path))))

(sc/defn ^:always-validate state-change-data :- StateChangeMessage [application new-state]
  (let [applicant       (domain/get-applicant-document (:documents application))
        applicant-value (partial get-doc-value applicant [:data])
        selected        (applicant-value [:_selected])]
    (-> (application-data application)
        (set/rename-keys {:location-wgs84 :locationWGS84})
        (assoc :state (name new-state))
        (assoc :fromState (state-map (:state application)))
        (assoc :toState (state-map new-state))
        (assoc :messageType "state-change")
        (assoc :applicantInfo (->> (if (= selected "yritys")
                                     {:type        "company"
                                      :companyId   (applicant-value [:yritys :liikeJaYhteisoTunnus])
                                      :companyName (applicant-value [:yritys :yritysnimi])}
                                     {:type      "person"
                                      :firstName (applicant-value [:henkilo :henkilotiedot :etunimi])
                                      :lastName  (applicant-value [:henkilo :henkilotiedot :sukunimi])
                                      :personId  (applicant-value [:henkilo :henkilotiedot :hetu])})
                                   util/strip-blanks
                                   ss/trimwalk)))))

(defn valid-states [new-state old-state]
  (or ((states/all-application-states-but :draft :open) (keyword new-state))
      (and (#{:submitted} (keyword old-state))
           (#{:draft} (keyword new-state)))))

(defn- post-message-json [{:keys [id url data] :as message}]
  (http/post url (messages/wrap-message-options
                   message
                   (lupa-cookie/add-mongo-cookie
                     {:body             (clj-http/json-encode data)
                      :throw-exceptions true})))
  (messages/update-message id
                           {$set {:status       :done
                                  :acknowledged (now)}}
                           WriteConcern/UNACKNOWLEDGED))

(def dmcity-json-queue "lupapiste.application.state-change")

(defn handle-pubsub-message
  [_system {:keys [message-id]}]
  (if-let [message (messages/fetch message-id)]
    (log/with-logging-context
      {:userId        (-> message :initiator :id)
       :applicationId (-> message :application :id)}
      (try
        (post-message-json message)
        (log/debugf "state-change message (id: %s) consumed and acknowledged from queue successfully"
                    message-id)
        true
        (catch UnknownHostException _
          (log/errorf "Unknown host: %s" (:url message))
          true)
        (catch ExceptionInfo ex
          (log/errorf ex "Error when sending consumed state-change message (%s) to %s."
                      message-id (:url message))
          (let [ed (ex-data ex)]
            (if (= (:status ed) 404)
              ;; ACK
              true
              false)))
        (catch Exception e
          (log/errorf e "Error when sending consumed state-change message (%s) to %s. NACKing."
                      message-id (:url message))
          false)))
    (do
      (log/errorf "Pubsub %s: No message %s found from mongo." dmcity-json-queue message-id)
      true)))

(defmethod lip/get-pubsub-subscription-handler :dmcity/state-change-queue
  [_id]
  handle-pubsub-message)

(defn send-state-change-message [application message new-state system]
  (when-let [outgoing-data (state-change-data application new-state)]
    (let [publisher  (components/system->publisher system :dmcity/state-change-queue)
          message-id (messages/create-id)
          message    (-> message
                         (assoc-in [:application :state] (name new-state))
                         (merge {:id           message-id
                                 :direction    "out"
                                 :status       (if publisher "queued" "published")
                                 :messageType  "state-change"
                                 :format       "json"
                                 :transferType "http"
                                 :data         outgoing-data}))]
      (messages/save message)
      (log/with-logging-context {:userId        (-> message :initiator :id)
                                 :applicationId (-> message :application :id)}
        (if publisher
          (publisher {:message-id message-id})
          (post-message-json message))
        (log/debugf "Message %s queued/sent." message-id)))))
