(ns lupapalvelu.mongo
  (:refer-clojure :exclude [count remove update distinct any?])
  (:require [clojure.walk :as walk]
            [integrant.core :as ig]
            [lupapalvelu.logging :refer [debug infof warn]]
            [lupapalvelu.mongo-schema :as mongo-schema]
            [monger.collection :as mc]
            [monger.conversion :refer [as-field-selector from-db-object to-db-object]]
            [monger.core :as m]
            [monger.credentials :as mcred]
            [monger.db :as db]
            [monger.gridfs :as gfs]
            [monger.operators :refer :all]
            [monger.query :as query]
            [sade.core :refer [def- now]]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :refer [fn->>] :as util]
            [schema.core :as sc :refer [defschema]]
            [slingshot.slingshot :refer [throw+]])
  (:import [com.mongodb BasicDBObject
                        DB
                        MongoClient
                        MongoCommandException
                        ReadPreference
                        WriteConcern]
           [com.mongodb.client.model DBCollectionCountOptions DBCollectionFindOptions IndexOptions]
           [com.mongodb.gridfs GridFS]
           [java.time Duration]
           [java.util.concurrent TimeUnit]
           [org.bson.types ObjectId]))

(set! *warn-on-reflection* true)

(def operators (set (map name (keys (ns-publics 'monger.operators)))))

(def default-write-concern WriteConcern/JOURNALED)

(defn make-server-list
  [servers]
  (->> (if (map? servers)
         (vals servers)
         servers)
       (mapv #(apply m/server-address [(:host %) (:port %)]))))

(def- dbs
  "A cache for get-db."
  (atom {}))

;;;; States
;;;; ===================================================================================================================

(defmethod ig/assert-key ::connection [_ args]
  (mongo-schema/valid-connection-args! (dissoc args :deps)))

(defmethod ig/init-key ::connection [k {:keys [dbname current-db servers
                                               credentials
                                               ssl-enabled ssl-invalid-host-name-allowed]}]
  (infof "Starting %s..." k)
  (let [servers  (make-server-list servers)
        username (-> credentials :username)
        password (-> credentials :password)
        options  (m/mongo-options {:write-concern                 default-write-concern
                                   :ssl-enabled                   ssl-enabled
                                   :ssl-invalid-host-name-allowed ssl-invalid-host-name-allowed})]
    (infof "Connecting to %s MongoDB (%s) database '%s' as user '%s' with opts %s"
           env/target-env (ss/join (map str servers)) dbname credentials (pr-str options))
    (when (and current-db (not= current-db dbname))
      (infof "Using database %s instead of %s" current-db dbname))
    {::client     (if (and username password)
                    (m/connect servers options (mcred/create username dbname password))
                    (m/connect servers options))
     ::current-db (or current-db dbname)
     ::default-db dbname}))

(defmethod ig/halt-key! ::connection [k {::keys [client]}]
  (infof "Stopping %s..." k)
  (assert client)
  (m/disconnect client)
  ;; Invalidate the get-db cache.
  (reset! dbs {}))

(defmethod ig/init-key ::database [k {:keys [conn]}]
  (infof "Starting %s..." k)
  conn)

(def ^:dynamic *connection* nil)

;;;; Utils
;;;; ===================================================================================================================

(defn client
  [instance]
  (::client instance))

(defn mongo-client?
  [connection]
  (instance? MongoClient connection))

(defn connected?
  []
  (mongo-client? (client *connection*)))

(defn default-db-name
  "Returns the name of the default DB (e.g. used for authentication)"
  []
  {:pre [(connected?)]}
  (-> *connection* ::default-db))

(defn current-db-name
  "Returns the name of the current DB.
  Same as `default-db-name`, except when running tests."
  []
  (when (connected?)
    (-> *connection* ::current-db)))

(defn ^{:perfmon-exclude true} get-db ^DB []
  {:pre [(connected?)]}
  (let [current (current-db-name)]
    (locking dbs
      (or (get @dbs current)
          (when-let [db (m/get-db (client *connection*) current)]
            (swap! dbs assoc current db)
            db)))))

(defn ^{:perfmon-exclude true} get-gfs ^GridFS []
  {:pre [(connected?)]}
  (m/get-gridfs (client *connection*) (.getName (get-db))))

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

(defn ^{:perfmon-exclude true} create-object-id []
  (ObjectId.))

(defn ^{:perfmon-exclude true} str->object-id [^String s]
  (ObjectId. s))

(defn ensure-id [m]
  (if (:id m) m (assoc m :id (create-id))))

; http://docs.mongodb.org/manual/reference/limits/#Restrictions-on-Field-Names
(def key-pattern #"^[^\.\$\u0000]+$")

(defn ^{:perfmon-exclude true} valid-key? [k]
  (if k
    (if (instance? ObjectId k)
      true
      (let [key (name k)]
        (boolean (and (re-matches key-pattern key) (< (clojure.core/count key) 800)))))
    false))

(defn ^{:perfmon-exclude true} escape-key [k]
  (if (string? k)
    (-> (ss/replace k "." "_DOT_")
        (ss/replace "$" "_DOLLAR_")
        (ss/replace "\u0000" "_NULL_"))
    k))

(defn ^{:perfmon-exclude true} unescape-key [k]
  (if (string? k)
    (-> (ss/replace k "_DOT_" ".")
        (ss/replace "_DOLLAR_" "$")
        (ss/replace "_NULL_" "\u0000"))
    k))

(defn ^{:perfmon-exclude true} operator? [s]
  (contains? operators s))

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
  (walk/postwalk (fn [v] (if (string? v) (ss/replace v "\0" "") v)) m))

;;;; Simple cache support
;;;; ===================================================================================================================

(defn ^{:perfmon-exclude true} with-mongo-meta [m]
  (assoc m :created (java.util.Date.)))

(defn ^{:perfmon-exclude true} without-mongo-meta [m]
  (dissoc m :id :_id :created))

;;;; Database Api
;;;; ===================================================================================================================

(defn update
  "Updates data into collection by query. Always returns nil."
  [collection query data & opts]
  (mc/update (get-db) collection (remove-null-chars query) (remove-null-chars data) opts)
  nil)

(defn update-by-id
  "Updates data into collection by id (which is mapped to _id). Always returns nil."
  [collection id data & opts]
  {:pre [(seq data)]}
  (mc/update-by-id (get-db) collection (remove-null-chars id) (remove-null-chars data) opts)
  nil)

(defn update-by-query
  "Updates data into collection with 'multi' set to true. Returns the number of documents updated"
  [collection query data & opts]
  (.getN (mc/update (get-db) collection query (remove-null-chars data) (apply hash-map :multi true opts))))

(defn update-one-and-return
  "Updates first document in collection matching conditions. Returns updated document or nil."
  [collection query document & {:keys [fields sort remove upsert] :or {fields nil sort nil remove false upsert false}}]
  (mc/find-and-modify (get-db) collection (remove-null-chars query) (remove-null-chars document)
    {:return-new true :upsert upsert :remove remove :sort sort :fields fields}))

(defn insert
  "Inserts data into collection. The 'id' in 'data' (if it exists) is persisted as _id"
  ([collection data] (insert collection data default-write-concern))
  ([collection data _] (mc/insert (get-db) collection (with-_id (remove-null-chars data))) nil))

(defn insert-batch
  ([collection data] (insert-batch collection data default-write-concern))
  ([collection data _] (mc/insert-batch (get-db) collection (map (comp with-_id remove-null-chars) data))))

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
   (mc/find-maps (get-db) coll ref))
  ([coll ref fields]
   (mc/find-maps (get-db) coll ref fields)))

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
  "Returns multiple entries by matching the monger query."
  ([collection]
    {:pre [collection]}
    (select collection {}))
  ([collection query]
    {:pre [collection (map? query)]}
    (map with-id (with-collection (name collection)
                                  (query/find (remove-null-chars query)))))
  ([collection query projection]
    {:pre [collection (map? query) (seq projection)]}
    (map with-id (with-collection (name collection)
                   (query/find (remove-null-chars query))
                   (query/fields (if (map? projection) (keys projection) projection)))))
  ([collection query projection order-by]
   {:pre [collection (map? query) (seq projection) (instance? clojure.lang.PersistentArrayMap order-by)]}
   (map with-id (with-collection (name collection)
                  (query/find (remove-null-chars query))
                  (query/fields (if (map? projection) (keys projection) projection))
                  (query/sort order-by)))))

(defn- make-find-options  ^DBCollectionFindOptions
  [{:keys [projection order-by limit max-time batch-size skip read-secondary?]}]
  (cond-> (-> (DBCollectionFindOptions.)
              (.projection (as-field-selector (or projection [])))
              (.sort (to-db-object (or order-by {})))
              (.limit (or limit 0))
              (.maxTime (or max-time 0) TimeUnit/MILLISECONDS)
              (.batchSize (or batch-size 256))
              (.skip (or skip 0)))
    ;; Try to read from secondary Mongo replica, but fall back to primary
    read-secondary? (.readPreference (ReadPreference/secondaryPreferred))))

(defn select-options
  "Returns multiple entries by matching the monger query. Like `select` but supports
  `projection`, `order-by`, `max-time` (milliseconds), `batch-size` (default 256), `skip`,
  `limit`, `read-secondary?`, and `xf` option map.

  If the transducer `xf` is provided, the evaluation is eager."
  ([collection query {:keys [xf max-time] :as options}]
   {:pre [collection (map? query) (or (nil? options) (map? options))]}
   (let [start      (now)
         deadline   (when max-time (+ start max-time))
         collection (.getCollection (get-db) (name collection))
         query      (-> query remove-null-chars to-db-object)
         options    (make-find-options options)
         convert    (fn [x]
                      (let [ts (now)]
                        (when (and deadline (< deadline ts))
                          (throw+ {:type     :timeout
                                   :start    start
                                   :end      ts
                                   :deadline deadline})))
                      (-> x (from-db-object true) with-id))]
     (with-open [cursor (.find collection query options)]
       (if xf
         (into [] (comp (map convert) xf) cursor)
         (map convert cursor)))))
  ([collection query]
   (select-options collection query nil)))

(defn- make-count-options  ^DBCollectionCountOptions
  [{:keys [limit max-time skip read-secondary?]}]
  (cond-> (-> (DBCollectionCountOptions.)
              (.limit (int (or limit 0)))
              (.maxTime (or max-time 0) TimeUnit/MILLISECONDS)
              (.skip (int (or skip 0))))
    ;; Try to read from secondary Mongo replica, but fall back to primary
    read-secondary? (.readPreference (ReadPreference/secondaryPreferred))))

(defn count-options
  "Returns the number of documents that match `query`. Takes into account `max-time` (milliseconds), `skip`,
  `limit`, and `read-secondary?` fields in the `options` map."
  ([collection query options]
   {:pre [collection (map? query) (or (nil? options) (map? options))]}
   (let [collection (.getCollection (get-db) (name collection))
         query      (-> query remove-null-chars to-db-object)
         options    (make-count-options options)]
     (.count collection query options)))
  ([collection query]
   (count-options collection query nil)))

(defn snapshot
  "Returns multiple entries by matching the monger query. Results are sorted by _id."
  ([collection]
   {:pre [collection]}
   (snapshot collection {}))
  ([collection query]
   {:pre [collection (map? query)]}
   (map with-id (with-collection (name collection)
                                 (query/find (remove-null-chars query))
                                 (query/sort {:_id 1}))))
  ([collection query projection]
   {:pre [collection (map? query) (seq projection)]}
   (map with-id (with-collection (name collection)
                                 (query/find (remove-null-chars query))
                                 (query/fields (if (map? projection) (keys projection) projection))
                                 (query/sort {:_id 1})))))

(defn select-one
  "Returns one entry by matching the monger query, nil if query did not match."
  ([collection query]
    {:pre [(map? query)]}
    (with-id (mc/find-one-as-map (get-db) collection (remove-null-chars query))))
  ([collection query projection]
    {:pre [(map? query)]}
    (with-id (mc/find-one-as-map (get-db)  collection (remove-null-chars query) projection))))

(defn select-ordered
  "Convenience select for ordered results without projection requirement
   and with the option to limit the number of results"
  ([collection query order-by]
   (select-ordered collection query order-by 0))
  ([collection query order-by limit]
   {:pre [collection (map? query) (instance? clojure.lang.PersistentArrayMap order-by)]}
   (map with-id (with-collection (name collection)
                  (query/find (remove-null-chars query))
                  (query/sort order-by)
                  (query/limit limit)))))

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

(defn remove-by-id
  "Convenience wrapper for `remove`. Enforces requirement that `id` is a non-blank string."
  [collection id]
  {:pre [(ss/not-blank? id)]}
  (remove collection id))

(defn remove-many
  "Removes all documents matching query. Returns the success status."
  [collection query]
  (.wasAcknowledged (mc/remove (get-db) collection (remove-null-chars query))))

(defn count
  "returns count of objects in collection"
  ([collection]
   (mc/count (get-db) collection))
  ([collection query]
   (mc/count (get-db) collection (remove-null-chars query))))

(defn aggregate
  ([collection stages]
   (mc/aggregate (get-db) (name collection) stages :cursor {}))
  ([collection stages allow-disk-use?]
   (mc/aggregate (get-db) (name collection) stages :cursor {} :allow-disk-use allow-disk-use?)))

(defn ^{:perfmon-exclude true} get-next-sequence-value [sequence-name]
  (:count (update-one-and-return :sequences {:_id (name sequence-name)} {$inc {:count 1}} :upsert true)))

(defn ^{:perfmon-exclude true} db-mode
  "Database mode is read from env document in systemstatus collection"
  []
  (:mode (by-id :systemstatus "env")))

;;;; Indexes
;;;; ===================================================================================================================

(defn ensure-index
  ([coll keys]
   (mc/ensure-index (get-db) coll keys))
  ([coll keys opts]
   (mc/ensure-index (get-db) coll keys opts)))

(defn ensure-partial-index
  "Monger lacks IndexOptions support, which is needed if we want to create partial index.

  https://docs.mongodb.com/manual/core/index-partial/
  https://mongodb.github.io/mongo-java-driver/3.6/driver/tutorials/indexes/"
  ([coll keys selector index-name]
   {:pre [coll keys selector]}
   (.. ^MongoClient (client *connection*)
       (getDatabase (.getName (get-db)))
       (getCollection (name coll))
       (createIndex
         ^BasicDBObject (as-field-selector keys)
         (.partialFilterExpression
           (cond-> (IndexOptions.)
             index-name (.name index-name))
           (to-db-object selector)))))
  ([coll keys selector]
   (ensure-partial-index coll keys selector nil)))

(defn drop-index
  [coll idx]
  (mc/drop-index (get-db) coll idx))

(defn drop-index-and-catch
  "Drops index, and catches 'index not found' exception."
  [coll idx]
  (try
    (drop-index coll (name idx))
    (warn "Mongo index dropped: " idx)
    (catch MongoCommandException e
      (when-not (ss/includes? (.getMessage e) "index not found")
        (throw e)))))

(def ^:private index-base
  {:fields                     {sc/Keyword sc/Int}
   (sc/optional-key :version)  ssc/PosInt ;; Default is 1
   (sc/optional-key :dropped?) sc/Bool})

(defschema ^:private IndexDefinition
  (sc/conditional
    ;; Partial index
    :filter-expression (merge index-base
                              {:filter-expression {sc/Keyword sc/Any}})
    ;; Simple or compound index.
    :else (merge index-base
                 {(sc/optional-key :options) {sc/Keyword sc/Any}})))

(sc/defn ^:private ^:always-validate define-index
  "Ensures or drops index `index-name` for collection `coll`. If `version` is given and
  higher than one, the old index versions are dropped."
  [index-name :- ssc/NonBlankStr
   coll       :- sc/Keyword
   {:keys [fields dropped? filter-expression version
           options]}  :- IndexDefinition]
  (let [vname        (fn [v]
                       ;; For backwards compatibility v1 suffix is not used.
                       (cond-> index-name
                         (> v 1) (str  "_v" v)))
        version      (or version 1)
        current-name (vname version)]
    (doseq [old-version (range 1 version)]
      (drop-index-and-catch coll (vname old-version)))
    (cond
      dropped?
      (drop-index-and-catch coll current-name)

      filter-expression
      (ensure-partial-index coll fields filter-expression current-name)

      :else
      (ensure-index coll fields (assoc options :name current-name)))))

(sc/defn ^:private ^:always-validate define-indexes
  "Convenience function for defining mutiple indexes for a collection. `index-def-map`
  keys are index names."
  [coll          :- sc/Keyword
   index-def-map :- {sc/Keyword IndexDefinition}]
  (doseq [[k v] index-def-map]
    (define-index (name k) coll v)))

(defn ^{:perfmon-exclude true} ensure-indexes [& {:keys [ts] :or {ts (now)}}]
  (debug "ensure-indexes")

  ;; Users
  (ensure-index :users {:username 1} {:unique true})
  (ensure-index :users {:email 1} {:unique true})
  (ensure-index :users {:private.apikey 1} {:unique true :sparse true})
  (ensure-index :users {:oauth.client-id 1} {:unique true :sparse true})
  (ensure-index :users {:company.id 1} {:sparse true})
  (ensure-index :users {:role 1})

  ;; Applications
  (ensure-index :applications {:municipality 1})
  (drop-index-and-catch :applications "submitted_1")
  (ensure-index :applications {:modified -1})
  (ensure-index :applications {:reportingDbOverrideTs -1} {:sparse true})
  (ensure-index :applications {:organization 1 :modified -1})
  (ensure-index :applications {:organization 1 :submitted -1})
  (ensure-partial-index :applications {:organization 1 :verdictDate -1} {:verdictDate {$exists true}})
  (ensure-index :applications {:auth.id 1 :modified -1})
  (ensure-index :applications {:auth.id 1 :submitted -1})
  (ensure-partial-index :applications {:auth.id 1 :verdictDate -1} {:verdictDate {$exists true}})
  (ensure-index :applications {:auth.invite.user.id 1} {:sparse true})
  (ensure-index :applications {:address 1})
  (ensure-index :applications {:tags 1})
  (ensure-index :applications {:buildings.nationalId 1})
  (ensure-index :applications {:organization 1 :state 1})
  (drop-index-and-catch :applications "location_1") ; legacy
  (drop-index-and-catch :applications "archived.application_1") ; according to $indexStats, this was not hit
  (drop-index-and-catch :applications "archived.completed_1") ; according to $indexStats, this was not hit
  (ensure-index :applications {:attachments.id 1})
  (drop-index-and-catch :applications "permitType_1")                                ;; For application search
  ;; not selective enoough, not really matched (11 times in ~70 days) https://docs.mongodb.com/manual/tutorial/create-queries-that-ensure-selectivity/
  (drop-index-and-catch :applications "permitSubtype_-1_infoRequest_1")
  ;; not really used (21 times in ~70 days)
  (drop-index-and-catch :applications "applicant_1")
  ;; not selective enough https://docs.mongodb.com/manual/tutorial/create-queries-that-ensure-selectivity/
  ;; also better to use org+state than only state
  (drop-index-and-catch :applications "state_1")
  (drop-index-and-catch :applications "authority.lastName_1_authority.firstName_1")
  (ensure-index :applications {:handlers.userId 1 :modified -1} {:sparse true}) ;; For application search
  (drop-index-and-catch :applications "creator.firstName_1")
  (drop-index-and-catch :applications "creator.lastName_1")
  #_(ensure-index :applications {:_creatorIndex 1}) ; most likely never hit, maybe common "free-text-index" field should be created?

  ;; These are for application search and foreman history documents search
  (drop-index-and-catch :applications "documents.data.henkilotiedot.hetu.value_1")
  (drop-index-and-catch :applications "documents.data.henkilotiedot.hetu.value_new")
  (drop-index-and-catch :applications "documents.data.henkilotiedot.ulkomainenHenkilotunnus.value_1")
  (drop-index-and-catch :applications "documents.tj-document_1")
  (ensure-index :applications {:documents.data.henkilotiedot.hetu.value 1} {:sparse true :name "henkilotiedot_hetu"})
  (ensure-index :applications {:documents.data.henkilotiedot.ulkomainenHenkilotunnus.value 1} {:sparse true :name "henkilotiedot_ulkomainenHenkilotunnus"})
  (ensure-index :applications {:documents.tj-document 1} {:sparse true :name "tj-document"})
  (ensure-index :applications {:documents.state 1} {:sparse true :name "documents_state"})
  (ensure-index :applications {:documents.data.kuntaRoolikoodi.value 1} {:sparse true :name "kuntaRoolikoodi"})
  (ensure-index :applications {:building-product-catalogs.building-ref 1} {:sparse true :name "building-ref"})
  ;; Replaced by `:primary_op_state` compound index below.
  (drop-index-and-catch :applications "primaryOperation.name_1")

  (ensure-index :applications {:location-wgs84 "2dsphere"})
  (ensure-index :applications {:drawings.geometry-wgs84 "2dsphere"}) ; used by lupadoku search
  (drop-index-and-catch :applications "verdicts.kuntalupatunnus_1")
  (drop-index-and-catch :applications "pate-verdicts.kuntalupatunnus_1")
  (drop-index-and-catch :applications "pate-verdicts.kuntalupatunnus._value_1")
  ;; LPK-4709 supports batchrun query, improves query latency in lupamonster 35s -> 0.3s.
  (ensure-index :applications {:pate-verdicts.state._value 1})
  ;; Compound index is better since facta-imported filter is typically used with
  ;; organization (authority user or conversion). This seems like a good candidate for a
  ;; partial index, since facta-imported is present only in the converted
  ;; applications. However, I did not get that index to match. Furthermore, since mongo
  ;; might use only one index per query, let's start with not providing one for
  ;; facta-imported. We'll revisit the issue if needed.
  (drop-index-and-catch :applications "facta-imported_1")
  #_(ensure-index :applications {:organization 1 :facta-imported 1})
  ;; Especially for verdicts/contracts admin report
  (drop-index-and-catch :applications "verdictDate_1") ;; Non-sparse
  (drop-index-and-catch :applications "agreementSigned_1") ;; Non-sparse

  (define-indexes :applications
    {:verdict_date          {:fields  {:verdictDate 1}
                             :options {:sparse true}}
     :agreement_signed      {:fields  {:agreementSigned 1}
                             :options {:sparse true}}
     ;; Compoundish organization version is needed, otherwise organization field would
     ;; match some other compound index.
     :org_agreement_signed  {:fields            {:organization 1 :agreementSigned -1}
                             :filter-expression {:agreementSigned {$exists true}}}
     :org_verdict_ktunnus   {:fields            {:organization             1
                                                 :verdicts.kuntalupatunnus 1}
                             :filter-expression {:verdicts.kuntalupatunnus {$exists true}}}
     :org_pate_ktunnus      {:fields            {:organization                       1
                                                 :pate-verdicts.data.kuntalupatunnus 1}
                             :filter-expression {:pate-verdicts.data.kuntalupatunnus {$exists true}}}
     ;; Applications search and admin reporting
     :permit_type_submitted {:fields {:permitType 1 :submitted 1}}
     :permit_type_opened    {:fields {:permitType 1 :opened 1}}
     ;; State needed for notices batchrun, for example
     :primary_op_state      {:fields {:primaryOperation.name 1 :state 1}}
     :processing_status     {:fields            {:_stats.processing.status     1
                                                 :_stats.processing.complexity 1}
                             :version           2
                             :filter-expression {:_stats.processing.status {$exists true}}}})

  ;; Password reset tokens
  (ensure-partial-index :token
                        {:token-type 1 :data.email 1 :created -1}
                        {:token-type "password-reset"})

  ;; Lookup `:ryhti.permanentPermitIdentifier` -> application
  (ensure-partial-index :applications
                        {:ryhti.permanentPermitIdentifier 1}
                        {:ryhti.permanentPermitIdentifier {$exists true}})

  ;; Processing entries (requests and delays)
  (define-index "entries" :processing-entries {:fields {:application.id  1
                                                        :seen-by.user-id 1
                                                        :deleted         1}})

  ;; Submitted applications
  (ensure-index :submitted-applications {:modified -1})

  ;; Activation
  (ensure-index :activation {:email 1})

  ;; Vetuma
  (ensure-index :vetuma {:created-at 1} {:expireAfterSeconds (* 60 60 2)}) ; 2 h
  (ensure-index :vetuma {:user.stamp 1})
  (ensure-index :vetuma {:sessionid 1})
  (ensure-index :vetuma {:trid 1} {:unique true})

  ;; Organizations
  (ensure-index :organizations {:scope.municipality 1 :scope.permitType 1 :scope.bulletins.redirect-names.name 1})
  (drop-index-and-catch :organizations "ad-login.trusted-domains_1") ; moved to :ad-logins collection below

  ;; AD logins
  (ensure-index :ad-logins {:trusted-domains 1})

  ;; Fs.chunks
  (try
    (ensure-index :fs.chunks {:files_id 1 :n 1 })
    (catch Exception e
      (warn "Failed ensuring index:" (.getMessage e))))

  ;; Open inforequest token
  (ensure-index :open-inforequest-token {:application-id 1})

  ;; App links
  (ensure-index :app-links {:link 1})
  (drop-index-and-catch :app-links "conversion-document-id_1") ;; Old non-sparse index
  (ensure-index :app-links {:conversion-document-id 1} {:sparse true :name "cdi_1"})
  ;; Disabled TTL for now: (mc/ensure-index :sign-processes {:created 1} {:expireAfterSeconds (env/value :onnistuu :timeout)})

  ;; Companies
  (ensure-index :companies {:name 1} {:name "company-name"})
  (ensure-index :companies {:y 1} {:name "company-y"})

  ;; Property cache
  (ensure-index :propertyCache {:created 1} {:expireAfterSeconds (* 60 60 24)}) ; 24 h
  (ensure-index :propertyCache (array-map :kiinttunnus 1 :x 1 :y 1) {:unique true, :name "kiinttunnus_x_y"})

  ;; Property municipality cache
  (ensure-index :propertyMunicipalityCache {:created 1} {:expireAfterSeconds (* 60 60 24)}) ; 24 h
  (ensure-index :propertyMunicipalityCache {:propertyId 1} {:unique true})

  ;; Building cache
  (drop-index-and-catch :buildingCache "created_1") ;; Old 12 h
  (ensure-index :buildingCache {:created 1} {:expireAfterSeconds (* 60 60) ;; 1 h
                                             :name               "created_hour"})
  (ensure-index :buildingCache {:propertyId 1} {:unique true})

  ;; SSO keys
  (ensure-index :ssoKeys {:ip 1} {:unique true})

  ;; Assignments
  (drop-index-and-catch :assignments "application.id_1_recipient.id_1_states.type_1")
  ;; matches assignments-for-application query
  (ensure-index :assignments {:application.id 1 :status 1 :application.organization 1})
  ;; matches aggregation in lupapalvelu.assignment/search
  (ensure-index :assignments {:application.organization 1, :recipient.id 1 :modified -1})
  (drop-index-and-catch :assignments "status_1")
  ;; matches assignment-count query
  (ensure-index :assignments {:status 1 :states.type 1 :recipient.id 1})
  (ensure-index :assignments {:modified 1})

  ;; Application bulletins
  (ensure-index :application-bulletins {:versions.bulletinState 1})
  (ensure-index :application-bulletins {:versions.municipality 1})
  (ensure-partial-index :application-bulletins {:versions.category 1} {:versions.category {$exists true}})

  ;; Application bulletin comments
  (define-index "bulletin_version" :application-bulletin-comments {:fields {:bulletinId 1
                                                                            :versionId  1}})

  ;; Integration messages
  (ensure-index :integration-messages {:application.id 1})
  (ensure-index :integration-messages {:created -1})

  ;; Archive API usage
  (ensure-index :archive-api-usage {:logged -1})

  ;; Jobs
  (ensure-index :jobs {:created 1} {:expireAfterSeconds (* 60 60)}) ; 1 h

  ;; Price catalogues
  (ensure-index :price-catalogues {:organization-id 1})

  ;; Invoices
  (ensure-index :invoices {:organization-id 1})
  (ensure-index :invoices {:application-id 1})
  (ensure-index :invoices {:state 1})
  (ensure-index :invoices {:created -1})
  (ensure-index :invoices {:transfer-batch-id 1})
  (ensure-index :invoice-transfer-batches {:created -1})

  ;; Building info
  (ensure-index :building-info {:organization 1})

  ;; Linked file metadata
  (drop-index-and-catch :linked-file-metadata
                        "target-entity.applications-id_1_target-entity.file-id_1")
  (ensure-index :linked-file-metadata {:target-entity.application-id 1
                                       :target-entity.file-id        1}
                {:unique true})

  ;; Conversion
  (ensure-index :conversion {:organization 1 :backend-id 1})
  (drop-index-and-catch :conversion "organization_1_LP-id_1")
  (ensure-index :conversion {:LP-id 1} {:unique true :sparse true})

  (define-index "schema_name" :conversion-section-schemas {:fields  {:name 1}
                                                           :options {:unique true}})

  ;; Imports
  (define-index "organization" :import-configs {:fields {:organization 1}})
  (define-indexes :import-rows
    {:config-id {:fields {:config-id 1}}
     :errors    {:fields  {:config-id 1 :errors 1}
                 :options {:sparse true}}
     :deleted   {:fields  {:config-id 1 :deleted 1}
                 :options {:sparse true}}
     :added     {:fields  {:config-id 1 :added.id 1}
                 :options {:sparse true}}})

  ;; Pate verdict templates
  (define-index "templates" :verdict-templates {:fields {:organization 1
                                                         :category     1}})

  ;; Rami ETL migrations
  (drop-index-and-catch :applications "drawings.array")
  ;; NOTE: array indexes of the type {:drawings.0 {$exists true}} do not work; use {:drawings {$gt []}} like here
  (ensure-partial-index :applications {:drawings 1} {:drawings {$gt []}} "drawings_not_empty")

  (ensure-index :replies {:application-id 1})

  ;; Ryhti API auth token
  ;; The auth tokens live for 60min so we store them for 50min
  (ensure-index :ryhti-tokens {:created 1} {:expireAfterSeconds (* 60 50)})
  (ensure-index :ryhti-tokens {:organization 1 :ryhti-api-configuration.municipality 1} {:unique true})

  ;;; AsiointiAPI notification registrations
  (let [coll :asiointiapi-notification-registrations]
    ;; No duplicate registrations
    (ensure-index coll
                  {:organization-id 1
                   :target.url      1}
                  {:unique true})
    ;; Registration lookup by organization
    (ensure-index coll
                  {:organization-id 1}))
  ;;; AsiointiAPI notifications
  (let [coll :asiointiapi-notifications]
    ;; Fast notification lookup for pending messages
    ;; Should be relatively small, unless some consumer is stuck/broken
    ;; See `lupapalvelu.asiointi.notifications.notification/registration->pending-notification-id`
    (ensure-partial-index coll
                          {:registration-id 1}
                          {:pending true}
                          "pending_asiointiapi_notifications")
    ;; Cleanup old delivered notifications
    (ensure-index coll
                  {:delivered-at 1}
                  {:expireAfterSeconds (-> (Duration/ofDays 30)
                                           .toSeconds)}))
  ;;; AsiointiAPI logging entries
  ;; Cleanup old entries
  (ensure-index :asiointiapi-logging-entries
                {:received-at 1}
                {:expireAfterSeconds (-> (Duration/ofDays 7)
                                         .toSeconds)})

  ;; Sessions (server-side session store)
  ;; TTL must be >= default-session-timeout (4 h) in web.clj. The extra hour is a safety buffer.
  (ensure-index :sessions
                {:updated-at 1}
                {:expireAfterSeconds (-> (Duration/ofHours 5)
                                         .toSeconds)})
  (ensure-index :sessions
                {:user-id 1}
                {:sparse true}) ; Only logged-in users have user-id in the session data

  (infof "ensure-indexes took %d ms" (- (now) ts)))

(defmethod ig/init-key ::indices [k {:keys [conn]}]
  (infof "Starting %s..." k)
  (binding [*connection* conn]
    (ensure-indexes)))

(defn clear! []
  (if-let [mode (db-mode)]
    (throw (IllegalStateException. (str "Database is running in " mode " mode, not clearing MongoDB")))
    (do
      (warn "Clearing MongoDB")
      (gfs/remove-all (get-gfs))
      ; Collections must be dropped individially, otherwise index cache will be stale
      (doseq [coll (db/get-collection-names (get-db))]
        (when-not (or (ss/starts-with? coll "system") (= "poi" coll)) (mc/drop (get-db) coll)))
      (ensure-indexes))))
