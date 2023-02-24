(ns lupapalvelu.mongo
  (:refer-clojure :exclude [count remove update distinct any?])
  (:require [clojure.walk :as walk]
            [monger.collection :as mc]
            [monger.conversion :refer [from-db-object to-db-object as-field-selector]]
            [monger.core :as m]
            [monger.credentials :as mcred]
            [monger.db :as db]
            [monger.gridfs :as gfs]
            [monger.operators :refer :all]
            [monger.query :as query]
            [mount.core :refer [defstate]]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :refer [fn->>] :as util]
            [taoensso.timbre :refer [debug debugf infof warn]])
  (:import [com.mongodb DB DBObject WriteConcern MapReduceCommand$OutputType MapReduceOutput MongoClient MongoCommandException]
           [com.mongodb.client.model IndexOptions]
           [com.mongodb.gridfs GridFS]
           [org.bson.types ObjectId]))

(def operators (set (map name (keys (ns-publics 'monger.operators)))))

(def default-write-concern WriteConcern/JOURNALED)

(def default-db-name (:dbname (env/value :mongodb)))
(def ^:dynamic *db-name* default-db-name)

(defn- server-list []
  (let [conf    (env/value :mongodb :servers)
        servers (if (map? conf) (vals conf) conf)]
    (map #(apply m/server-address [(:host %) (:port %)]) servers)))

(def- dbs
  "A cache for get-db."
  (atom {}))

;;;; States
;;;; ===================================================================================================================

(defstate connection
  :start (let [conf (env/value :mongodb)
               servers (server-list)
               dbname (:dbname conf)
               username (-> conf :credentials :username)
               password (-> conf :credentials :password)
               options (m/mongo-options {:write-concern default-write-concern})]
           (debugf "Connecting to %s MongoDB (%s) database '%s' as user '%s'"
                   env/target-env (ss/join (map str servers)) dbname username)
           (if (and username password)
             (m/connect servers options (mcred/create username dbname password))
             (m/connect servers options)))

  :stop (do (debug "Disconnecting from MongoDB")
            (m/disconnect connection)
            (reset! dbs {})))                               ; Invalidate the get-db cache.

;;;; Utils
;;;; ===================================================================================================================

(defn connected? [] (instance? MongoClient connection))

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
  {:pre [(connected?)]}
  (locking dbs
    (or (get @dbs *db-name*)
        (when-let [db (m/get-db connection *db-name*)]
          (swap! dbs assoc *db-name* db)
          db))))

(defn ^{:perfmon-exclude true} ^GridFS get-gfs []
  {:pre [(connected?)]}
  (m/get-gridfs connection (.getName (get-db))))

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

(defn ^{:perfmon-exclude true} max-1-elem-match? [m]
  (let [walked (walk/postwalk (fn [x]
                                (if (map? x)
                                  (filter (partial = $elemMatch) (concat (keys x) (flatten (vals x))))
                                  x)) m)]
    (<= (clojure.core/count walked) 1)))

(defn dollar-path [s]
  (let [[_ path] (re-find #"(^[^\.]+)\.\$" s)]
       path))

(defn ^{:perfmon-exclude true} update-with-$-has-corresponding-elem-match-in-query?
  [q u]
  (boolean
   (let [[dollar-field :as dollar-fields] (some->> (get u $set)
                                                   keys
                                                   (map (comp dollar-path name))
                                                   (clojure.core/remove nil?)
                                                   clojure.core/distinct)]
     (cond
       ;; Can't misuse $ operator if you don't use it at all
       (= (clojure.core/count dollar-fields) 0) true

       ;; There should be a distinct field preceding the $ operator
       ;; with a corresponding $elemMatch in query
       (= (clojure.core/count dollar-fields) 1)
       (when-let [query-field-value  (or (get q dollar-field)
                                         (get q (keyword dollar-field)))]
         (and (map? query-field-value)
              (contains? query-field-value $elemMatch)))

       ;; There should never be more than one field updated at a time
       ;; with the $ operator
       :else false))))

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
  {:pre [(max-1-elem-match? query)
         (update-with-$-has-corresponding-elem-match-in-query? query data)]}
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
  {:pre [(max-1-elem-match? query)
         (update-with-$-has-corresponding-elem-match-in-query? query data)]}
  (.getN (mc/update (get-db) collection query (remove-null-chars data) (apply hash-map :multi true opts))))

(defn update-one-and-return
  "Updates first document in collection matching conditions. Returns updated document or nil."
  [collection query document & {:keys [fields sort remove upsert] :or {fields nil sort nil remove false upsert false}}]
  {:pre [(max-1-elem-match? query)
         (update-with-$-has-corresponding-elem-match-in-query? query document)]}
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

(defn- ^{:perfmon-exclude true} wrap-js-function [s & args]
  (if (ss/starts-with s "function")
    s
    (str "function(" (ss/join \, (map name args)) "){" s "}")))

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

(defn remove-by-id
  "Convenience wrapperr for `remove`. Enforces requirement that `id` is a non-blank string."
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
  [coll keys selector]
  (.. connection
      (getDatabase (.getName (get-db)))
      (getCollection (name coll))
      (createIndex
        (as-field-selector keys)
        (.partialFilterExpression
          (IndexOptions.)
          (to-db-object selector)))))

(defn drop-index
  [coll idx]
  (mc/drop-index (get-db) coll idx))

(defn drop-index-and-catch
  "Drops index, and catches 'index not found' exception."
  [coll idx]
  (try
    (drop-index coll (name idx))
    (catch MongoCommandException e
      (when-not (ss/contains? (.getMessage e) "index not found")
        (throw e)))))

(defn ^{:perfmon-exclude true} ensure-indexes [& {:keys [ts] :or {ts (now)}}]
  (debug "ensure-indexes")
  (ensure-index :users {:username 1} {:unique true})
  (ensure-index :users {:email 1} {:unique true})
  (ensure-index :users {:private.apikey 1} {:unique true :sparse true})
  (ensure-index :users {:oauth.client-id 1} {:unique true :sparse true})
  (ensure-index :users {:company.id 1} {:sparse true})
  (ensure-index :users {:role 1})
  (ensure-index :applications {:municipality 1})
  (drop-index-and-catch :applications "submitted_1")
  (ensure-index :applications {:modified -1})
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
  (ensure-index :applications {:permitType 1})                                ;; For application search
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
  (ensure-index :applications {:documents.data.henkilotiedot.hetu.value 1} {:sparse true}) ;; For application search
  (ensure-index :applications {:location-wgs84 "2dsphere"})
  (ensure-index :applications {:drawings.geometry-wgs84 "2dsphere"}) ; used by lupadoku search
  (ensure-index :applications {:verdicts.kuntalupatunnus 1})
  (drop-index-and-catch :applications "pate-verdicts.kuntalupatunnus_1")
  (drop-index-and-catch :applications "pate-verdicts.kuntalupatunnus._value_1")
  ;; LPK-4709 supports batchrun query, improves query latency in lupamonster 35s -> 0.3s.
  (ensure-index :applications {:pate-verdicts.state._value 1})
  (ensure-index :applications {:facta-imported 1})
  ;; Especially for verdicts/contracts admin report
  (ensure-index :applications {:verdictDate 1})
  (ensure-index :applications {:agreementSigned 1})
  (ensure-index :submitted-applications {:modified -1})
  (ensure-index :activation {:email 1})
  (ensure-index :vetuma {:created-at 1} {:expireAfterSeconds (* 60 60 2)}) ; 2 h
  (ensure-index :vetuma {:user.stamp 1})
  (ensure-index :vetuma {:sessionid 1})
  (ensure-index :vetuma {:trid 1} {:unique true})
  (ensure-index :organizations {:scope.municipality 1 :scope.permitType 1})
  (ensure-index :organizations {:ad-login.trusted-domains 1})
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
  (ensure-index :propertyMunicipalityCache {:created 1} {:expireAfterSeconds (* 60 60 24)}) ; 24 h
  (ensure-index :propertyMunicipalityCache {:propertyId 1} {:unique true})
  (ensure-index :buildingCache {:created 1} {:expireAfterSeconds (* 60 60 12)}) ; 12 h
  (ensure-index :buildingCache {:propertyId 1} {:unique true})
  (ensure-index :ssoKeys {:ip 1} {:unique true})
  (drop-index-and-catch :assignments "application.id_1_recipient.id_1_states.type_1")
  ; matches assignments-for-application query
  (ensure-index :assignments {:application.id 1 :status 1 :application.organization 1})
  ; matches aggregation in lupapalvelu.assignment/search
  (ensure-index :assignments {:application.organization 1, :recipient.id 1 :modified -1})
  (drop-index-and-catch :assignments "status_1")
  ; matches assignment-count query
  (ensure-index :assignments {:status 1 :states.type 1 :recipient.id 1})
  (ensure-index :assignments {:modified 1})
  (ensure-index :application-bulletins {:versions.bulletinState 1})
  (ensure-index :application-bulletins {:versions.municipality 1})
  (ensure-index :integration-messages {:application.id 1})
  (ensure-index :integration-messages {:created -1})
  (ensure-index :archive-api-usage {:logged -1})
  (ensure-index :jobs {:created 1} {:expireAfterSeconds (* 60 60)}) ; 1 h
  (ensure-index :price-catalogues {:organization-id 1})
  (ensure-index :invoices {:organization-id 1})
  (ensure-index :invoices {:application-id 1})
  (ensure-index :invoices {:state 1})
  (ensure-index :invoices {:created -1})
  (ensure-index :building-info {:organization 1})
  (drop-index-and-catch :linked-file-metadata
                        "target-entity.applications-id_1_target-entity.file-id_1")
  (ensure-index :linked-file-metadata {:target-entity.application-id 1
                                       :target-entity.file-id 1} {:unique true})
  (infof "ensure-indexes took %d ms" (- (now) ts)))

(defstate indices
  :start (ensure-indexes))

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
