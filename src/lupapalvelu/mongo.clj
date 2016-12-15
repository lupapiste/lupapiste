(ns lupapalvelu.mongo
  (:refer-clojure :exclude [count remove update distinct])
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error errorf]]
            [clojure.walk :as walk]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [monger.operators :refer :all]
            [monger.conversion :refer [from-db-object]]
            [sade.env :as env]
            [sade.util :refer [fn->>] :as util]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [monger.core :as m]
            [monger.collection :as mc]
            [monger.db :as db]
            [monger.gridfs :as gfs]
            [monger.command :refer [server-status]]
            [monger.query :as query]
            [monger.credentials :as mcred]
            [sade.status :refer [defstatus]])
  (:import [javax.net.ssl SSLSocketFactory]
           [org.bson.types ObjectId]
           [com.mongodb DB WriteConcern MapReduceCommand$OutputType MapReduceOutput]
           [com.mongodb.gridfs GridFS GridFSDBFile GridFSInputFile]))


(def operators (set (map name (keys (ns-publics 'monger.operators)))))

(def default-write-concern WriteConcern/JOURNALED)

(defonce connection (atom nil))
(defonce ^:private dbs (atom {}))

(def default-db-name (:dbname (env/value :mongodb)))
(def ^:dynamic *db-name* default-db-name)

;;
;; Utils
;;

(defmacro with-db [db-name & body]
  `(binding [*db-name* ~db-name]
     ~@body))

(defn ^{:perfmon-exclude true} db-selection-middleware
  [handler]
  (fn [request]
    (let [db-name (get-in request [:cookies "test_db_name" :value] default-db-name)]
      (with-db db-name
        (handler request)))))

(defn ^{:perfmon-exclude true} ^DB get-db []
  {:pre [@connection]}
  (locking dbs
    (or (get @dbs *db-name*)
        (when-let [db (m/get-db @connection *db-name*)]
          (swap! dbs assoc *db-name* db)
          db))))

(defn ^{:perfmon-exclude true} ^GridFS get-gfs []
  (m/get-gridfs @connection (.getName (get-db))))

(defn ^{:perfmon-exclude true} with-_id [m]
  (if-let [id (:id m)]
    (-> m
      (assoc :_id id)
      (dissoc :id))
    m))

(defn ^{:perfmon-exclude true} with-id [m]
  (if-let [id (:_id m)]
    (-> m
      (assoc :id id)
      (dissoc :_id))
    m))

(defn ^{:perfmon-exclude true} create-id []
  (str (ObjectId.)))

; http://docs.mongodb.org/manual/reference/limits/#Restrictions-on-Field-Names
(def key-pattern #"^[^\.\$\u0000]+$")

(defn ^{:perfmon-exclude true} valid-key? [k]
  (if k
    (if (instance? ObjectId k)
      true
      (let [key (name k)]
        (boolean (and (re-matches key-pattern key) (< (clojure.core/count key) 800)))))
    false))

(defn ^{:perfmon-exclude true} operator? [s]
  (contains? operators s))

(defn ^{:perfmon-exclude true} max-1-elem-match? [m]
  (let [walked (walk/postwalk (fn [x]
                                (if (map? x)
                                  (filter (partial = $elemMatch) (concat (keys x) (flatten (vals x))))
                                  x)) m)]
    (<= (clojure.core/count walked) 1)))

(defn ^{:perfmon-exclude true} generate-array-updates
  "Returns a map of mongodb array update paths to be used as a value for $set or $unset operation.
   E.g., (generate-array-updates :attachments [true nil nil true nil] true? \"k\" \"v\")
         => {\"attachments.0.k\" \"v\", \"attachments.3.k\" \"v\"}"
  [array-name array pred & kvs]
  (reduce (fn [m i] (->> (apply hash-map kvs)
                         (util/map-keys (fn->> name (str (name array-name) \. i \.)))
                         (merge m)))
          {} (util/positions pred array)))

(defn ^{:perfmon-exclude true} remove-null-chars
  "Removes illegal null characters from input.
   Nulls cause 'BSON cstring' exceptions."
  [m]
  (walk/postwalk (fn [v] (if (string? v) (s/replace v "\0" "") v)) m))

;;
;; Simple cache support
;;

(defn ^{:perfmon-exclude true} with-mongo-meta [m]
  (assoc m :created (java.util.Date.)))

(defn ^{:perfmon-exclude true} without-mongo-meta [m]
  (dissoc m :id :_id :created))

;;
;; Database Api
;;

(def isolated {"$isolated" 1})

(defn update-n
  "Updates data into collection by query, returns a number of updated documents."
  [collection query data & {:as opts}]
  {:pre [(max-1-elem-match? query)]}
  (let [options (-> (merge {:write-concern default-write-concern} opts) seq flatten)]
    (.getN (mc/update (get-db) collection (merge isolated query) (remove-null-chars data) options))))

(defn update
  "Updates data into collection by query. Always returns nil."
  [collection query data & opts]
  {:pre [(max-1-elem-match? query)]}
  (mc/update (get-db) collection (merge isolated (remove-null-chars query)) (remove-null-chars data) opts)
  nil)

(defn update-by-id
  "Updates data into collection by id (which is mapped to _id). Always returns nil."
  [collection id data & opts]
  (mc/update-by-id (get-db) collection (remove-null-chars id) (remove-null-chars data) opts)
  nil)

(defn update-by-query
  "Updates data into collection with 'multi' set to true. Returns the number of documents updated"
  [collection query data & opts]
  {:pre [(max-1-elem-match? query)]}
  (.getN (mc/update (get-db) collection (merge isolated query) (remove-null-chars data) (apply hash-map :multi true opts))))

(defn update-one-and-return
  "Updates first document in collection matching conditions. Returns updated document or nil."
  [collection query document & {:keys [fields sort remove upsert] :or {fields nil sort nil remove false upsert false}}]
  {:pre [(max-1-elem-match? query)]}
  (mc/find-and-modify (get-db) collection (remove-null-chars query) (remove-null-chars document)
    {:return-new true :upsert upsert :remove remove :sort sort :fields fields}))

(defn insert
  "Inserts data into collection. The 'id' in 'data' (if it exists) is persisted as _id"
  ([collection data] (insert collection data default-write-concern))
  ([collection data concern] (mc/insert (get-db) collection (with-_id (remove-null-chars data))) nil))

(defn insert-batch
  ([collection data] (insert-batch collection data default-write-concern))
  ([collection data concern] (mc/insert-batch (get-db) collection (map (comp with-_id remove-null-chars) data))))

(defn by-id
  ([collection id]
    (with-id (mc/find-one-as-map (get-db) collection {:_id (remove-null-chars id)})))
  ([collection id projection]
    (with-id (mc/find-one-as-map (get-db) collection {:_id (remove-null-chars id)} projection))))

(defn find-maps
  "Wrapper for monger.collection/find-maps 3-arity version
   Queries for objects in this collection.
   This function returns clojure Seq of Maps."
  ([coll ref]
   (mc/find-maps (get-db) coll ref)))

(defn distinct
  "Wrapper for monger.collection/distinct. Finds distinct values for a key."
  ([coll key]
   (mc/distinct (get-db) coll key))
  ([coll key query]
   (mc/distinct (get-db) coll key (remove-null-chars query))))

(defmacro with-collection
  "Simple wrapper for monger.query/with-collection which gets db and passes it to monger with args."
  [collection & body]
  `(query/with-collection (get-db) ~collection ~@body))

(defn select
  "Returns multiple entries by matching the monger query.
   Cursor is snapshotted unless order-by clause is defined"
  ([collection]
    {:pre [collection]}
    (select collection {}))
  ([collection query]
    {:pre [collection (map? query)]}
    (map with-id (with-collection (name collection)
                                        (query/find (remove-null-chars query))
                                        (query/snapshot))))
  ([collection query projection]
    {:pre [collection (map? query) (seq projection)]}
    (map with-id (with-collection (name collection)
                                        (query/find (remove-null-chars query))
                                        (query/fields (if (map? projection) (keys projection) projection))
                                        (query/snapshot))))
  ([collection query projection order-by]
   {:pre [collection (map? query) (seq projection) (instance? clojure.lang.PersistentArrayMap order-by)]}
   (map with-id (with-collection (name collection)
                  (query/find (remove-null-chars query))
                  (query/fields (if (map? projection) (keys projection) projection))
                  (query/sort order-by)))))

(defn select-one
  "Returns one entry by matching the monger query, nil if query did not match."
  ([collection query]
    {:pre [(map? query)]}
    (with-id (mc/find-one-as-map (get-db) collection (remove-null-chars query))))
  ([collection query projection]
    {:pre [(map? query)]}
    (with-id (mc/find-one-as-map (get-db)  collection (remove-null-chars query) projection))))

(defn select-ordered
  "Convenience select for ordered results without projection requirement."
  [collection query order-by]
  {:pre [collection (map? query) (instance? clojure.lang.PersistentArrayMap order-by)]}
  (map with-id (with-collection (name collection)
                 (query/find (remove-null-chars query))
                 (query/sort order-by))))

(defn- ^{:perfmon-exclude true} wrap-js-function [s & args]
  (if (s/starts-with? s "function")
    s
    (str "function(" (s/join \, (map name args)) "){" s "}")))

(defn map-reduce
  "Returns map-reduce results inline.
   Mapper-js and reducer-js are JavaScript functions or their bodies as Strings.
   Both should return the same keys:
   - reducer is not called if there is only one result
   - Mapped results are reduced in chucks and reducer is called multible times
     as more data is processed. This means that reducer 'values' paramerter
     might not contain all the data at once, if there are multiple chucks."
  [collection query mapper-js reducer-js]
  {:pre [(keyword? collection) (map? query) (string? mapper-js) (string? reducer-js)]}
  (let [mapper-js-fn  (wrap-js-function mapper-js)
        reducer-js-fn (wrap-js-function reducer-js :key :values)
        output (mc/map-reduce (get-db) collection mapper-js-fn reducer-js-fn nil MapReduceCommand$OutputType/INLINE query)]
    (map with-id (from-db-object ^DBObject (.results ^MapReduceOutput output) true))))

(defn any?
  "check if any"
  ([collection query]
    (mc/any? (get-db) collection (remove-null-chars query))))

(defn drop-collection [collection]
  (mc/drop (get-db) collection))

(defn remove
  "Removes documents by id."
  [collection id]
  (.wasAcknowledged (mc/remove (get-db) collection {:_id (remove-null-chars id)})))

(defn remove-many
  "Removes all documents matching query. Returns the success status."
  [collection query]
  (.wasAcknowledged (mc/remove (get-db) collection (remove-null-chars query))))

;;
;; Grid FS
;;
(defn ^{:perfmon-exclude true} set-file-id [^GridFSInputFile input ^String id]
  (.setId input (remove-null-chars id))
  input)

(defn upload [file-id filename content-type content & metadata]
  {:pre [(string? file-id) (string? filename) (string? content-type)
         (or (instance? java.io.File content) (instance? java.io.InputStream content))
         (or (nil? metadata) (sequential? metadata))
         (or (even? (clojure.core/count metadata)) (map? (first metadata)))]}
  (let [meta (remove-null-chars (if (map? (first metadata))
                                  (first metadata)
                                  (apply hash-map metadata)))]
    (with-open [input-stream (io/input-stream content)]
      (gfs/store-file (gfs/make-input-file (get-gfs) input-stream)
        (set-file-id file-id)
        (gfs/filename filename)
        (gfs/content-type content-type)
        (gfs/metadata (assoc meta :uploaded (now)))))))

(defn- ^{:perfmon-exclude true} gridfs-file-as-map [^GridFSDBFile attachment]
  (let [metadata (from-db-object (.getMetaData attachment) :true)]
    {:content (fn [] (.getInputStream attachment))
     :contentType (.getContentType attachment)
     :size (.getLength attachment)
     :filename (.getFilename attachment)
     :fileId (.getId attachment)
     :metadata metadata
     :application (:application metadata)}))

(defn file-metadata
  "Returns only GridFS file metadata as map. Use download-find to get content."
  [query]
  (gfs/find-one-as-map (get-gfs) (with-_id (remove-null-chars query))))

(defn update-file-by-query
  [query updates]
  {:pre [(max-1-elem-match? query) (map? query) (map? updates)]}
  (update-by-query :fs.files query updates))

(defn download-find-many [query]
  (map gridfs-file-as-map (gfs/find (get-gfs) (with-_id (remove-null-chars query)))))

(defn download-find [query]
  (when-let [attachment (gfs/find-one (get-gfs) (with-_id (remove-null-chars query)))]
    (gridfs-file-as-map attachment)))

(defn ^{:perfmon-exclude true} download
  "Downloads file from Mongo GridFS"
  [file-id]
  (download-find {:_id file-id}))

(defn delete-file [query]
  {:pre [(seq query)]}
  (let [query (with-_id (remove-null-chars query))]
    (info "removing file" query)
    (gfs/remove (get-gfs) query)))

(defn ^{:perfmon-exclude true} delete-file-by-id [file-id]
  {:pre [(string? file-id)]}
  (delete-file {:id file-id}))

(defn count
  "returns count of objects in collection"
  ([collection]
    (mc/count (get-db) collection))
  ([collection query]
    (mc/count (get-db) collection (remove-null-chars query))))

(defn ^{:perfmon-exclude true} get-next-sequence-value [sequence-name]
  (:count (update-one-and-return :sequences {:_id (name sequence-name)} {$inc {:count 1}} :upsert true)))

(defn ^{:perfmon-exclude true} db-mode
  "Database mode is read from env document in systemstatus collection"
  []
  (:mode (by-id :systemstatus "env")))

;;
;; Bootstrappin'
;;

(def server-list
  (let [conf    (env/value :mongodb :servers)
        servers (if (map? conf) (vals conf) conf)]
    (map #(apply m/server-address [(:host %) (:port %)]) servers)))

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
          dbname   (:dbname conf)
          user (-> conf :credentials :username)
          pw   (-> conf :credentials :password)
          ssl  (:ssl conf)]
      (connect! [(m/server-address host port)] dbname user pw ssl)))
  ([servers dbname username password ssl]
    (let [servers (if (string? servers)
                    (let [[host port] (clojure.string/split servers #":")]
                      [(m/server-address host (Long/parseLong port))])
                    servers)
          options (m/mongo-options {:write-concern default-write-concern})]
      (if @connection
       (debug "Already connected!")
       (do
         (debugf "Connecting to %s MongoDB (%s) database '%s' as user '%s'"  env/target-env (s/join (map str servers)) dbname username)
         (let [conn (if (and username password)
                      (m/connect servers options (mcred/create username dbname password))
                      (m/connect servers options))
               db   (m/get-db conn dbname)]
           (reset! connection conn)
           (swap! dbs assoc dbname db))
         )))))

(defn disconnect! []
  (debug "Disconnecting")
  (if @connection
    (do
      (m/disconnect @connection)
      (reset! dbs {})
      (reset! connection nil))
    (debug "Not connected")))

(defn ensure-index
  ([coll keys]
   (mc/ensure-index (get-db) coll keys))
  ([coll keys opts]
   (mc/ensure-index (get-db) coll keys opts)))

(defn drop-index
  [coll idx]
  (mc/drop-index (get-db) coll idx))

(defn ^{:perfmon-exclude true} ensure-indexes [& {:keys [ts] :or {ts (now)}}]
  (debug "ensure-indexes")
  (ensure-index :users {:username 1} {:unique true})
  (ensure-index :users {:email 1} {:unique true})
  (ensure-index :users {:organizations 1} {:sparse true})
  (ensure-index :users {:private.apikey 1} {:unique true :sparse true})
  (ensure-index :users {:company.id 1} {:sparse true})
  (ensure-index :applications {:municipality 1})
  (ensure-index :applications {:submitted 1})
  (ensure-index :applications {:modified -1})
  (ensure-index :applications {:organization 1})
  (ensure-index :applications {:auth.id 1})
  (ensure-index :applications {:auth.invite.user.id 1} {:sparse true})
  (ensure-index :applications {:address 1})
  (ensure-index :applications {:tags 1})
  (ensure-index :applications {:buildings.nationalId 1})
  (ensure-index :applications {:organization 1 :state 1})
  (ensure-index :applications {:location 1} {:min 10000 :max 7779999 :bits 32})
  (ensure-index :applications {:archived.application 1})
  (ensure-index :applications {:archived.completed 1})
  (ensure-index :applications {:attachments.id 1})
  (ensure-index :applications {:permitSubtype -1 :infoRequest 1})             ;; For application search
  (ensure-index :applications {:applicant 1})                                 ;; For application search
  (ensure-index :applications {:state 1})                                     ;; For application search
  (ensure-index :applications {:authority.lastName 1 :authority.firstName 1}) ;; For application search
  (ensure-index :activation {:email 1})
  (ensure-index :vetuma {:created-at 1} {:expireAfterSeconds (* 60 60 2)}) ; 2 h
  (ensure-index :vetuma {:user.stamp 1})
  (ensure-index :vetuma {:sessionid 1})
  (ensure-index :vetuma {:trid 1} {:unique true})
  (ensure-index :organizations {:scope.municipality 1 :scope.permitType 1 })
  (try
    (ensure-index :fs.chunks {:files_id 1 :n 1 })
    (catch Exception e
      (warn "Failed ensuring index:" (.getMessage e))))
  (ensure-index :open-inforequest-token {:application-id 1})
  (ensure-index :app-links {:link 1})
  ; Disabled TTL for now: (mc/ensure-index :sign-processes {:created 1} {:expireAfterSeconds (env/value :onnistuu :timeout)})
  (ensure-index :companies {:name 1} {:name "company-name"})
  (ensure-index :companies {:y 1} {:name "company-y"})
  (ensure-index :perf-mon-timing {:ts 1} {:expireAfterSeconds (env/value :monitoring :data-expiry)})
  (ensure-index :propertyCache {:created 1} {:expireAfterSeconds (* 60 60 24)}) ; 24 h
  (ensure-index :propertyCache (array-map :kiinttunnus 1 :x 1 :y 1) {:unique true, :name "kiinttunnus_x_y"})
  (ensure-index :buildingCache {:created 1} {:expireAfterSeconds (* 60 60 12)}) ; 12 h
  (ensure-index :buildingCache {:propertyId 1} {:unique true})
  (ensure-index :ssoKeys {:ip 1} {:unique true})
  (ensure-index :assignments {:application.id 1, :recipient.id 1, :states.type 1})
  (infof "ensure-indexes took %d ms" (- (now) ts)))

(defn clear! []
  (if-let [mode (db-mode)]
    (throw (IllegalStateException. (str "Database is running in " mode " mode, not clearing MongoDB")))
    (do
      (warn "Clearing MongoDB")
      (gfs/remove-all (get-gfs))
      ; Collections must be dropped individially, otherwise index cache will be stale
      (doseq [coll (db/get-collection-names (get-db))]
        (when-not (or (ss/starts-with coll "system") (= "poi" coll)) (mc/drop (get-db) coll)))
      (ensure-indexes))))

(defstatus :mongo (server-status (get-db)))
