(ns lupapalvelu.mongo
  (:refer-clojure :exclude [count remove update])
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf]]
            [clojure.walk :as walk]
            [clojure.string :as s]
            [monger.operators :refer :all]
            [monger.conversion :refer [from-db-object]]
            [sade.env :as env]
            [sade.util :as util]
            [sade.strings :as ss]
            [monger.core :as m]
            [monger.collection :as mc]
            [monger.db :as db]
            [monger.gridfs :as gfs]
            [monger.command :refer [server-status]]
            [monger.query :as query]
            [sade.status :refer [defstatus]])
  (:import [javax.net.ssl SSLSocketFactory]
           [org.bson.types ObjectId]
           [com.mongodb WriteConcern MongoClientOptions MongoClientOptions$Builder]
           [com.mongodb.gridfs GridFS GridFSInputFile]))


;; $each is missing from monger.operations.
;; https://github.com/michaelklishin/monger/pull/84
(def $each "$each")

(def operators (conj (set (map name (keys (ns-publics 'monger.operators)))) $each))

;;
;; Utils
;;

(defn with-_id [m]
  (if-let [id (:id m)]
    (-> m
      (assoc :_id id)
      (dissoc :id))
    m))

(defn with-id [m]
  (if-let [id (:_id m)]
    (-> m
      (assoc :id id)
      (dissoc :_id))
    m))

(defn create-id []
  (str (ObjectId.)))

; http://docs.mongodb.org/manual/reference/limits/#Restrictions-on-Field-Names
(def key-pattern #"^[^\.\$\u0000]+$")

(defn valid-key? [k]
  (if k
    (if (instance? ObjectId k)
      true
      (let [key (name k)]
        (boolean (and (re-matches key-pattern key) (< (clojure.core/count key) 800)))))
    false))

(defn operator? [s]
  (contains? operators s))

(defn generate-array-updates
  "Returns a map of mongodb array update paths to be used as a value for $set or $unset operation.
   E.g., (generate-array-updates :attachments [true nil nil true nil] true? \"k\" \"v\")
         => {\"attachments.0.k\" \"v\", \"attachments.3.k\" \"v\"}"
  [array-name array pred k v]
  (reduce (fn [m i] (assoc m (str (name array-name) \. i \. (name k)) v)) {} (util/positions pred array)))

(defn remove-null-chars
  "Removes illegal null characters from input.
   Nulls cause 'BSON cstring' exceptions."
  [m]
  (walk/postwalk (fn [v] (if (string? v) (s/replace v "\0" "") v)) m))

;;
;; Database Api
;;

(def isolated {"$isolated" 1})

(defn update-n
  "Updates data into collection by query, returns a number of updated documents."
  [collection query data & {:as opts}]
  (let [options (-> (merge {:write-concern WriteConcern/ACKNOWLEDGED} opts) seq flatten)]
    (.getN (apply mc/update collection (merge isolated query) (remove-null-chars data) options))))

(defn update
  "Updates data into collection by query. Always returns nil."
  [collection query data & opts]
  (apply mc/update collection (merge isolated (remove-null-chars query)) (remove-null-chars data) opts)
  nil)

(defn update-by-id
  "Updates data into collection by id (which is mapped to _id). Always returns nil."
  [collection id data & opts]
  (apply mc/update-by-id collection (remove-null-chars id) (remove-null-chars data) opts)
  nil)

(defn update-by-query
  "Updates data into collection. Returns the number of documents updated"
  [collection query data]
  (.getN (mc/update collection (merge isolated query) (remove-null-chars data) :multi true)))

(defn insert
  "Inserts data into collection. The 'id' in 'data' (if it exists) is persisted as _id"
  [collection data]
  (mc/insert collection (with-_id (remove-null-chars data)))
  nil)

(defn by-id
  ([collection id]
    (with-id (mc/find-one-as-map collection {:_id (remove-null-chars id)})))
  ([collection id projection]
    (with-id (mc/find-one-as-map collection {:_id (remove-null-chars id)} projection))))

(defn select
  "Returns multiple entries by matching the monger query.
   Cursor is snapshotted unless order-by clause is defined"
  ([collection]
    {:pre [collection]}
    (select collection {}))
  ([collection query]
    {:pre [collection (map? query)]}
    (map with-id (query/with-collection (name collection)
                 (query/find (remove-null-chars query))
                 (query/snapshot))))
  ([collection query projection]
    {:pre [collection (map? query) (seq projection)]}
    (map with-id (query/with-collection (name collection)
                 (query/find (remove-null-chars query))
                 (query/fields (if (map? projection) (keys projection) projection))
                 (query/snapshot))))
  ([collection query projection order-by]
    {:pre [collection (map? query) (seq projection) (instance? clojure.lang.PersistentArrayMap order-by)]}
    (map with-id (query/with-collection (name collection)
                   (query/find (remove-null-chars query))
                   (query/fields (if (map? projection) (keys projection) projection))
                   (query/sort order-by)))))

(defn select-one
  "returns one entry by matching the monger query"
  ([collection query]
    {:pre [(map? query)]}
    (with-id (mc/find-one-as-map collection (remove-null-chars query))))
  ([collection query projection]
    {:pre [(map? query)]}
    (with-id (mc/find-one-as-map collection (remove-null-chars query) projection))))

(defn any?
  "check if any"
  ([collection query]
    (mc/any? collection (remove-null-chars query))))

(defn update-one-and-return
  "Updates first document in collection matching conditions. Returns updated document or nil."
  [collection conditions document & {:keys [fields sort remove upsert] :or {fields nil sort nil remove false upsert false}}]
  (mc/find-and-modify collection (remove-null-chars conditions) (remove-null-chars document)
    :return-new true :upsert upsert :remove remove :sort sort :fields fields))

(defn drop-collection [collection]
  (mc/drop collection))

(defn remove
  "Removes documents by id."
  [collection id]
  (.ok (.getLastError (mc/remove collection {:_id (remove-null-chars id)}))))

(defn remove-many
  "Removes all documents matching query. Returns the success status."
  [collection query]
  (.ok (.getLastError (mc/remove collection (remove-null-chars query)))))

(defn set-file-id [^GridFSInputFile input ^String id]
  (.setId input (remove-null-chars id))
  input)

(defn upload [file-id filename content-type content & metadata]
  {:pre [(string? file-id) (string? filename) (string? content-type)
         (or (instance? java.io.File content) (instance? java.io.InputStream content))
         (sequential? metadata)
         (or (even? (clojure.core/count metadata)) (map? (first metadata)))]}
  (let [meta (remove-null-chars (if (map? (first metadata))
                                  (first metadata)
                                  (apply hash-map metadata)))]
    (gfs/store-file
      (gfs/make-input-file content)
      (set-file-id file-id)
      (gfs/filename filename)
      (gfs/content-type content-type)
      (gfs/metadata (assoc meta :uploaded (System/currentTimeMillis))))))

(defn download-find [query]
  (when-let [attachment (gfs/find-one (with-_id (remove-null-chars query)))]
    (let [metadata (from-db-object (.getMetaData attachment) :true)]
      {:content (fn [] (.getInputStream attachment))
       :content-type (.getContentType attachment)
       :content-length (.getLength attachment)
       :file-name (.getFilename attachment)
       :metadata metadata
       :application (:application metadata)})))

(defn download [file-id]
  (download-find {:_id file-id}))

(defn delete-file [query]
  (let [query (with-_id (remove-null-chars query))]
    (info "removing file" query)
    (gfs/remove query)))

(defn delete-file-by-id [file-id]
  (delete-file {:id file-id}))

(defn count
  "returns count of objects in collection"
  ([collection]
    (mc/count collection))
  ([collection query]
    (mc/count collection (remove-null-chars query))))

(defn get-next-sequence-value [sequence-name]
  (:count (update-one-and-return :sequences {:_id (name sequence-name)} {$inc {:count 1}} :upsert true)))

(defn db-mode
  "Database mode is read from env document in systemstatus collection"
  []
  (:mode (by-id :systemstatus "env")))

;;
;; Bootstrappin'
;;

;; From monger.core, pimped with SSL option
(defn mongo-options
  [& { :keys [connections-per-host threads-allowed-to-block-for-connection-multiplier
              max-wait-time connect-timeout socket-timeout socket-keep-alive auto-connect-retry max-auto-connect-retry-time ssl
              safe w w-timeout fsync j] :or [auto-connect-retry true] }]
  (let [mob (MongoClientOptions$Builder.)]
    (when connections-per-host
      (.connectionsPerHost mob connections-per-host))
    (when threads-allowed-to-block-for-connection-multiplier
      (.threadsAllowedToBlockForConnectionMultiplier mob threads-allowed-to-block-for-connection-multiplier))
    (when max-wait-time
      (.maxWaitTime mob max-wait-time))
    (when connect-timeout
      (.connectTimeout mob connect-timeout))
    (when socket-timeout
      (.socketTimeout mob socket-timeout))
    (when socket-keep-alive
      (.socketKeepAlive mob socket-keep-alive))
    (when auto-connect-retry
      (.autoConnectRetry mob auto-connect-retry))
    (when max-auto-connect-retry-time
      (.maxAutoConnectRetryTime mob max-auto-connect-retry-time))
    (when ssl
      (.socketFactory mob (SSLSocketFactory/getDefault)))
    (when safe
      (.safe mob safe))
    (when w
      (.w mob w))
    (when w-timeout
      (.wtimeout mob w-timeout))
    (when j
      (.j mob j))
    (when fsync
      (.fsync mob fsync))
    (.build mob)))

(def server-list
  (let [conf    (env/value :mongodb :servers)
        servers (if (map? conf) (vals conf) conf)]
    (map #(apply m/server-address [(:host %) (:port %)]) servers)))

(defonce connected (atom false))

(defn connect!
  ([]
    (let [conf (env/value :mongodb)
          db   (:dbname conf)
          user (-> conf :credentials :username)
          pw   (-> conf :credentials :password)
          ssl  (:ssl conf)]
      (connect! server-list db user pw ssl)))
  ([^String host ^Long port]
    (let [conf (env/value :mongodb)
          db   (:dbname conf)
          user (-> conf :credentials :username)
          pw   (-> conf :credentials :password)
          ssl  (:ssl conf)]
      (connect! (m/server-address host port) db user pw ssl)))
  ([servers db username password ssl]
    (let [servers (if (string? servers)
                    (let [[host port] (clojure.string/split servers #":")]
                      (m/server-address host (Long/parseLong port)))
                    servers  )]
      (if @connected
       (debug "Already connected!")
       (do
         (debug "Connecting to MongoDB:" (s/join (map str servers)) (if ssl "using ssl" "without encryption"))
         (m/connect! servers (mongo-options :ssl ssl))
         (reset! connected true)
         (m/set-default-write-concern! WriteConcern/JOURNALED)
         (when (and username password)
           (if (m/authenticate (m/get-db db) username (.toCharArray password))
             (debugf "Authenticated to DB '%s' as '%s'" db username)
             (errorf "Authentication to DB '%s' as '%s' failed!" db username)))
         (m/use-db! db)
         (debugf "MongoDB %s mode is %s" (.getName (m/get-db)) (db-mode)))))))

(defn disconnect! []
  (debug "Disconnecting")
  (if @connected
    (do
      (m/disconnect!)
      (reset! connected false))
    (debug "Not connected")))

(defn ensure-indexes []
  (debug "ensure-indexes")
  (mc/ensure-index :users {:username 1} {:unique true})
  (mc/ensure-index :users {:email 1} {:unique true})
  (mc/ensure-index :users {:organizations 1} {:sparse true})
  (mc/ensure-index :users {:private.apikey 1} {:unique true :sparse true})
  (mc/ensure-index :users {:company.id 1} {:sparse true})
  (mc/ensure-index :applications {:municipality 1})
  (mc/ensure-index :applications {:submitted 1})
  (mc/ensure-index :applications {:modified -1})
  (mc/ensure-index :applications {:organization 1})
  (mc/ensure-index :applications {:auth.id 1})
  (mc/ensure-index :applications {:auth.invite.user.id 1} {:sparse true})
  (mc/ensure-index :applications {:address 1})
  (mc/ensure-index :applications {:tags 1})
  (try
    (mc/drop-index :activation "created-at_1") ; no such field "created-at"
    (catch Exception _))
  (mc/ensure-index :activation {:email 1})
  (mc/drop-index :vetuma "created-at_1") ; expiration time has changed
  (mc/ensure-index :vetuma {:created-at 1} {:expireAfterSeconds (* 60 60 2)}) ; 2 h
  (mc/ensure-index :vetuma {:user.stamp 1})
  (mc/ensure-index :vetuma {:sessionid 1})
  (mc/ensure-index :vetuma {:trid 1} {:unique true})
  (mc/ensure-index :organizations {:scope.municipality 1 :scope.permitType 1 })
  (mc/ensure-index :fs.chunks {:files_id 1 :n 1 })
  (mc/ensure-index :open-inforequest-token {:application-id 1})
  (mc/ensure-index :app-links {:link 1})
  ; Disabled TTL for now: (mc/ensure-index :sign-processes {:created 1} {:expireAfterSeconds (env/value :onnistuu :timeout)})
  (mc/ensure-index :companies {:name 1} {:name "company-name"})
  (mc/ensure-index :companies {:y 1} {:name "company-y"})
  (mc/ensure-index :perf-mon {:ts 1} {:expireAfterSeconds (env/value :monitoring :data-expiry)})
  (mc/ensure-index :perf-mon-timing {:ts 1} {:expireAfterSeconds (env/value :monitoring :data-expiry)})
  (try
    (mc/drop-index :organizations "areas.features.geometry_2dsphere")
    (catch Exception _))
  (mc/ensure-index :applications {:location 1} {:min 10000 :max 7779999 :bits 32}))

(defn clear! []
  (if-let [mode (db-mode)]
    (throw (IllegalStateException. (str "Database is running in " mode " mode, not clearing MongoDB")))
    (do
      (warn "Clearing MongoDB")
      (gfs/remove-all)
      ; Collections must be dropped individially, otherwise index cache will be stale
      (doseq [coll (db/get-collection-names)]
        (when-not (or (.startsWith coll "system") (= "poi" coll)) (mc/drop coll)))
      (ensure-indexes))))

(defstatus :mongo (server-status))
