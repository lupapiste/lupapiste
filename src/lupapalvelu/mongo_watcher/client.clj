(ns lupapalvelu.mongo-watcher.client
  (:require [integrant.core :as ig]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mongo-watcher.convert :as mw-convert]
            [lupapalvelu.mongo-watcher.resume-tokens :as mw-resume-tokens]
            [lupapalvelu.throttler :as throttler]
            [muuntaja.core :as m]
            [noir.response :as resp]
            [reitit.coercion.malli :as reitit-coercion-malli]
            [reitit.ring :as reitit-ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.spec :as rrs]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [com.mongodb Block
                        MongoClientSettings
                        MongoCommandException
                        MongoCredential
                        MongoException]
           [com.mongodb.client MongoClient
                               MongoClients
                               MongoCollection
                               MongoCursor
                               MongoDatabase]
           [com.mongodb.client.model.changestream ChangeStreamDocument FullDocument]
           [com.mongodb.connection ClusterSettings$Builder SslSettings$Builder]
           [org.bson BsonDocument]))

(set! *warn-on-reflection* true)

(declare restart-watchers!)

(def ^:private skippable-change-stream-error-codes
  "Set of MongoDB change stream error codes that can be skipped. Skipped events
   are handled by fetching the document and calling the event handlers outside
   the normal procedure."
  #{10334 ;; BSONObjectTooLarge
    })

(defn register-cursor! [^MongoCursor cursor *active-cursors]
  (swap! *active-cursors conj cursor)
  cursor)

(defn- safe-close-cursor [^MongoCursor cursor]
  (try
    (.close cursor)
    (catch Exception _e
      (log/debug "Failed to close cursor. Probably already closed"))))

(defn close-cursor! [*active-cursors ^MongoCursor cursor]
  (safe-close-cursor cursor)
  (swap! *active-cursors disj cursor))

(defn close-all-cursors! [mongo-watcher]
  (when-let [*active-cursors (:active-cursors mongo-watcher)]
    (run! (partial close-cursor! *active-cursors) @*active-cursors)))

(defmulti event-handler-callback
  "Returns an event-handler for a MongoDB collection. Handler is triggered
  when changes to the mongo collection is made. The event-handler should be a
  function that performs side effects based on the change stream document. A
  collection can have multiple event handlers.

   - The `callback` function must accept a single argument, which is the change
     document.
   - The function blocks the thread, so it should use asynchronous processing if
     it includes heavy operations.
   - Due to throttled storing of resume tokens, exactly-once delivery of events
     cannot be guaranteed. This should be taken into account, if the event
     handler perfoms side effects that are allowed be triggered only once."
  identity)

(defmethod ig/init-key ::event-handler [[k handler-id] {:keys [collection-name
                                                               elastic]}]
  ;; NOTE: At the moment the event handlers just write to `elastic`, but
  ;; perhaps later they will need other components too -> replace the `elastic`
  ;; client with the full `::components/common` system map when that happens
  (log/infof "Starting %s..." [k handler-id])
  (let [callback-fn (partial (event-handler-callback handler-id) elastic)]
    (util/mmap handler-id collection-name callback-fn)))

(defn get-collection-event-handlers
  "Returns event-handlers for a collection."
  [{:keys [handlers]} collection-name]
  (->> handlers
       (filter (fn [handler]
                 (= collection-name (:collection-name handler))))
       (mapv (juxt :handler-id :callback-fn))))

(defn cs-doc->operation-type [^ChangeStreamDocument doc]
  (some-> doc .getOperationType .getValue keyword))

(defn cs-doc->document-id [^ChangeStreamDocument doc]
  (some-> doc .getDocumentKey (.get "_id") mw-convert/from-bson))

(defn cs-doc->resume-token [^ChangeStreamDocument doc]
  (some-> doc (.getResumeToken) (mw-resume-tokens/resume-token->str)))

(defn cs-doc->timestamp [^ChangeStreamDocument doc]
  (some-> doc (.getClusterTime) (.getTime) (* 1000)))

(defn- remove-array-indices [kw-path]
  (->> kw-path
       util/split-kw-path
       (remove #(re-matches #"^\d+$" (name %)))
       util/kw-path))

(defn change-stream-doc->clj [^ChangeStreamDocument doc]
  (let [document-id  (cs-doc->document-id doc)
        resume-token (cs-doc->resume-token doc)
        db-event-ts  (cs-doc->timestamp doc)]
    (log/with-logging-context {:document-id  document-id
                               :resume-token resume-token}
      (try
        (let [updates        (some->> (some-> (.getUpdateDescription doc)
                                              .getUpdatedFields)
                                      (map (fn [[k v]] [(keyword k) (mw-convert/from-bson v)]))
                                      (into {}))
              removed-fields (some->> (some-> (.getUpdateDescription doc)
                                              .getRemovedFields)
                                      (mapv keyword))]
          {:id             document-id
           :namespace      {:database-name   (-> doc .getNamespace .getDatabaseName)
                            :collection-name (-> doc .getNamespace .getCollectionName keyword)}
           :operation-type (cs-doc->operation-type doc)
           :full-document  (some->> doc .getFullDocument (mw-convert/from-bson {:keywords? true}) mongo/with-id)
           :updates        updates
           :removed-fields removed-fields
           ;; Combine updated and removed fields to set and remove array indices from the kw-path so that the format
           ;; matches projections of mongo queries etc.
           :changed-fields (->> updates
                                keys
                                (into removed-fields)
                                (map remove-array-indices)
                                set)
           :resume-token   resume-token
           :db-event-ts    db-event-ts})
        (catch Exception e
          (log/error e "Failed to convert change stream document to clj map. Processing skipped.")
          {:id           document-id
           :resume-token resume-token
           :db-event-ts  db-event-ts})))))

(defn run-event-handlers! [mongo-watcher collection-name doc]
  (run! (fn [[handler-id callback-fn]]
          (log/with-logging-context {:handler-id handler-id}
            (try
              (callback-fn doc)
              (catch Exception e
                (log/error e "Error in mongo change stream event handler")))))
        (get-collection-event-handlers mongo-watcher collection-name)))

(defn handle-event
  "Returns resume-token if one available"
  [mongo-watcher collection-name resume-token-store-fn change-stream-document]
  (when-let [{:keys [id operation-type resume-token] :as doc} (change-stream-doc->clj change-stream-document)]
    (log/with-logging-context {:document-id (:id doc)}
      (when (and id operation-type)
        (run-event-handlers! mongo-watcher collection-name doc))
      (when resume-token
        (resume-token-store-fn collection-name resume-token)
        resume-token))))

(defn get-collection-by-name ^MongoCollection [^MongoDatabase db collection-name]
  (.getCollection db (name collection-name)))

(defn get-database-by-name ^MongoDatabase [^MongoClient mongo-client db-name]
  (.getDatabase mongo-client db-name))

(defn get-next-change-stream-doc
  "If reading the change stream cursor fails with a BSONObjectTooLarge error,
   the event can still be retrieved (without FullDocument) by creating a new cursor
   that resumes after the previous event. The previous event's resume token is required
   to fetch the failed one."
  [mongo-db collection-name resume-token]
  (let [coll              (get-collection-by-name mongo-db collection-name)
        cursor            (-> (.watch coll BsonDocument)
                              (.resumeAfter (mw-resume-tokens/str->resume-token resume-token))
                              (.iterator))
        change-stream-doc (some-> (.next cursor)
                                  change-stream-doc->clj)]
    (safe-close-cursor cursor)
    change-stream-doc))

(defn handle-failed-change-stream-event [mongo-watcher collection-name {:keys [id] :as doc}]
  (log/infof "Handling failed change stream event. Collection: %s, ID %s" collection-name id)
  (run-event-handlers! mongo-watcher collection-name (assoc doc :full-document (mongo/by-id collection-name id))))

(defn watcher-cursor
  "Creates and returns a MongoDB change stream cursor for the specified collection.
  Resumes from the last stored resume token if available, enabling the watcher to
  continue from where it left off after restarts or failures.

  Cursor creation may fail even before .next is invoked if the upcoming change
  event exceeds the 16 MB BSON size limit (BSONObjectTooLarge). In that case the
  next resume token is fetched via get-next-change-stream-doc (without
  FullDocument) to skip the oversized event. The skipped event is handled
  separately in a fallback procedure. This repeats until successful.

  Arguments:
  - db: The MongoDatabase instance.
  - collection-name: The keyword or string name of the collection to watch.

  Returns:
  - A MongoCursor for iterating over change stream documents."
  ^MongoCursor [mongo-watcher ^MongoDatabase db collection-name *active-cursors resume-token]
  (log/with-logging-context {:collection collection-name}
    (log/infof "Starting to watch mongo collection '%s' in db '%s'" collection-name (.getName db))
    (let [coll (get-collection-by-name db collection-name)]
      (loop [current-resume-token resume-token]
        (let [cursor (try
                       (log/info "Creating new change stream cursor. Resuming after" current-resume-token)
                       (-> (.watch coll BsonDocument)
                           (cond-> current-resume-token (.resumeAfter (mw-resume-tokens/str->resume-token current-resume-token)))
                           (.fullDocument FullDocument/UPDATE_LOOKUP)
                           (.iterator))
                       (catch MongoCommandException e
                         (if (skippable-change-stream-error-codes (.getErrorCode e))
                           (log/warn "Failed to create change stream cursor resuming after:" current-resume-token)
                           (throw e))))]
          (if cursor
            ;; Register and return cursor
            (register-cursor! cursor *active-cursors)
            ;; Find next resume token and retry
            (let [{:keys [id resume-token] :as doc} (get-next-change-stream-doc db collection-name current-resume-token)]
              (log/warn "Trying next resume token. Document id of the failed event was:" id)
              (handle-failed-change-stream-event mongo-watcher collection-name doc)
              (recur resume-token))))))))

(defn recover-from-failure! [mongo-watcher *client-state]
  (reset! *client-state :recovering)
  (log/info "Mongo watcher is trying to recover from failure by restarting watchers. Restarting watchers in 5 seconds..")
  (Thread/sleep 5000)
  (restart-watchers! mongo-watcher))

(defn watch-collection [mongo-db collection-name mongo-watcher {:keys [disable-throttler]}]
  (let [*active-cursors       (:active-cursors mongo-watcher)
        *client-state         (:state mongo-watcher)
        resume-token          (mw-resume-tokens/fetch collection-name)
        cursor                (watcher-cursor mongo-watcher mongo-db collection-name *active-cursors resume-token)
        resume-token-store-fn (if disable-throttler
                                mw-resume-tokens/store
                                ;; Store resume token only once per 5 seconds per collection
                                (partial (throttler/make-throttler 5000) mw-resume-tokens/store))]
    (future
      (log/with-logging-context {:collection collection-name}
        (try
          (loop [cur               cursor
                 last-resume-token resume-token]
            (if-let [change-stream-document (try
                                              (.next cur)
                                              (catch MongoCommandException e
                                                (if (skippable-change-stream-error-codes (.getErrorCode e))
                                                  ;; If skippable error, return nil to trigger retry and skip
                                                  (log/with-logging-context {:error-code      (.getErrorCode e)
                                                                             :error-code-name (.getErrorCodeName e)
                                                                             :error-message   (.getErrorMessage e)}
                                                    (log/warn "Change stream cursor next failed"))
                                                  (throw e))))]
              (if-not (#{:drop :invalidate} (cs-doc->operation-type change-stream-document))
                (let [new-resume-token (handle-event mongo-watcher collection-name resume-token-store-fn change-stream-document)]
                  (recur cur (or new-resume-token last-resume-token)))
                (do
                  (log/warn "Mongo collection %s dropped. Closing change stream cursor.." collection-name)
                  (close-cursor! *active-cursors cur)))
              (do (log/with-logging-context {:last-successful-resume-token last-resume-token}
                    (log/warn "Trying to restart mongo-watcher from last successful resume token." last-resume-token)) ;TODO poista muuttuja
                  (close-cursor! *active-cursors cur)
                  (recur (watcher-cursor mongo-watcher mongo-db collection-name *active-cursors last-resume-token)
                         last-resume-token))))
          (catch MongoException e
            ;; When cursor is no longer in *active-cursors it has been deliberately closed and
            ;; exception can be ignored
            (when (contains? @*active-cursors cursor)
              (log/error e "Change Stream cursor MongoException")
              (recover-from-failure! mongo-watcher *client-state)))
          (catch Exception e
            (log/error e "Change Stream closed or failed.")
            (recover-from-failure! mongo-watcher *client-state)))))))

(defn start-watching!
  ([mongo-watcher]
   (start-watching! mongo-watcher nil))
  ([{:keys [client dbname handlers] :as mongo-watcher} opts]
   (mw-resume-tokens/create-resume-tokens-document!)
   (let [mongo-db    (get-database-by-name client dbname)
         collections (->> handlers
                          (map :collection-name)
                          (into #{}))]
     (doseq [collection-name collections]
       (watch-collection mongo-db collection-name mongo-watcher opts)))))

(defn build-mongo-settings [{:keys [servers username password ssl-enabled ssl-invalid-host-name-allowed]}]
  (-> (MongoClientSettings/builder)
      (.applyToClusterSettings
        (reify Block
          (apply [_ builder]
            (-> ^ClusterSettings$Builder builder
                (.hosts servers)
                .build))))
      (.applyToSslSettings
        (reify Block
          (apply [_ builder]
            (-> ^SslSettings$Builder builder
                (.enabled (boolean ssl-enabled))
                (.invalidHostNameAllowed (boolean ssl-invalid-host-name-allowed))))))
      (.writeConcern mongo/default-write-concern)
      (cond-> (and username password)
        (.credential (MongoCredential/createCredential username
                                                       (env/value :mongodb :dbname) ; authentication needs to be done against default database
                                                       (.toCharArray ^String password))))
      (.build)))

(defn create-client [servers dbname]
  (let [conf                          (env/value :mongodb)
        servers                       (mongo/make-server-list servers)
        username                      (-> conf :credentials :username)
        ssl-enabled                   (env/value :mongodb :ssl-enabled)
        ssl-invalid-host-name-allowed (env/value :mongodb :ssl-invalid-host-name-allowed)
        settings-param-map            (util/mmap servers username dbname ssl-enabled ssl-invalid-host-name-allowed)
        settings                      (build-mongo-settings
                                        (assoc settings-param-map :password (-> conf :credentials :password)))]
    (log/infof "Creating connection to (%s) MongoDB (%s) database '%s' for change stream watcher as user '%s' with opts %s"
               env/target-env (ss/join (map str servers)) dbname username settings-param-map)
    (MongoClients/create ^MongoClientSettings settings)))

(defn create-client-and-start-watching [dbname servers handlers]
  (let [*client-state   (atom :starting)
        *active-cursors (atom #{})
        client          (create-client servers dbname)
        mongo-watcher   {:state          *client-state
                         :client         client
                         :active-cursors *active-cursors
                         :dbname         dbname
                         :handlers       handlers}]
    (reset! *client-state :running)
    (start-watching! mongo-watcher)
    mongo-watcher))

(defmethod ig/init-key ::watcher [k {:keys [dbname servers conn handlers]}]
  (log/infof "Starting %s..." k)
  (log/infof ".. with %d handlers" (count handlers))
  (binding [mongo/*connection* conn]
    (create-client-and-start-watching dbname servers handlers)))

(defmethod ig/halt-key! ::watcher [k mongo-watcher]
  (log/infof "Stopping %s..." k)
  (assert mongo-watcher)
  (reset! (-> mongo-watcher :state) :closing)
  (close-all-cursors! mongo-watcher)
  (.close ^MongoClient (:client mongo-watcher))
  (reset! (-> mongo-watcher :state) :closed))

(defn restart-watchers! [mongo-watcher]
  (log/info "Restarting Mongo watchers..")
  (when (and mongo-watcher
             (some-> mongo-watcher :state))
    (let [{:keys [state]}       mongo-watcher
          [old-state new-state] (swap-vals! state (fn [state]
                                                    (if (#{:running :recovering} state)
                                                      :restarting-watchers
                                                      state)))]
      (if (and (#{:running :recovering} old-state)
               (= new-state :restarting-watchers))
        (do
          (close-all-cursors! mongo-watcher)
          (start-watching! mongo-watcher)
          (reset! state :running))
        (log/info "Cannot restart change stream watchers: state is not :running or :recovering")))))

(defn routes
  [mongo-watcher]
  ["/mongo-watcher/healthcheck"
   {:get {:handler (fn [_]
                     (let [state (some-> mongo-watcher :state deref)]
                       (if (= state :running)
                         (resp/json {:status state})
                         (resp/status 503 (resp/json {:status state})))))}}])

(defn make-router
  [mongo-watcher]
  (reitit-ring/ring-handler
    (reitit-ring/router
      (routes mongo-watcher)
      {:validate rrs/validate
       :data     {:muuntaja   m/instance
                  :coercion   reitit-coercion-malli/coercion
                  :middleware [muuntaja/format-negotiate-middleware
                               muuntaja/format-request-middleware
                               muuntaja/format-response-middleware
                               ;; TODO : needed?
                               ;; reitit-ring-coercion/coerce-exceptions-middleware
                               ;; reitit-ring-coercion/coerce-request-middleware
                               ;; reitit-ring-coercion/coerce-response-middleware
                               ]}})))

(defmethod ig/init-key ::routes [k {:keys [mongo-watcher]}]
  (log/infof "Starting %s..." k)
  (make-router mongo-watcher))
