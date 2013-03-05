(ns lupapalvelu.mongo
  (:refer-clojure :exclude [count])
  (:use monger.operators
        clojure.tools.logging)
  (:require [monger.core :as m]
            [monger.collection :as mc]
            [monger.db :as db]
            [monger.gridfs :as gfs])
  (:import [org.bson.types ObjectId]
           [com.mongodb.gridfs GridFS GridFSInputFile]))

(def ^:const mongouri "mongodb://127.0.0.1/lupapalvelu")

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

;;
;; Database Api
;;

(defn update
  "Updates data into collection by query. Always returns nil."
   [collection query data]
  (mc/update collection query data)
  nil)

(defn update-by-id
  "Updates data into collection by id (which is mapped to _id). Always returns nil."
  [collection id data]
  (mc/update-by-id collection id data)
  nil)

(defn update-by-query
  "Updates data into collection. Returns the number of documents updated"
  [collection query data]
  (.getN (mc/update collection query data)))

(defn insert
  "Inserts data into collection. The 'id' in 'data' (if it exists) is persisted as _id"
  [collection data]
  (mc/insert collection (with-_id data))
  nil)

(defn by-id [collection id]
  (with-id (mc/find-one-as-map collection {:_id id})))

(defn select
  "returns multiple entries by matching the monger query"
  ([collection]
    (select collection {}))
  ([collection query]
    (select collection query {}))
  ([collection query projection]
    (map with-id (mc/find-maps collection query projection))))

(defn select-one
  "returns one entry by matching the monger query"
  ([collection query]
    (with-id (mc/find-one-as-map collection query)))
  ([collection query projection]
    (with-id (mc/find-one-as-map collection query projection))))

(defn ^Boolean update-one-and-return
  "Updates first document in collection matching conditions. Returns updated document or nil."
  [collection conditions document & {:keys [fields sort remove upsert] :or {fields nil sort nil remove false upsert false}}]
  (mc/find-and-modify collection conditions document :return-new true :upsert upsert :remove remove :sort sort :fields fields))

(defn remove-many
  "Returns all documents matching query."
  [collection query] (mc/remove collection query))

(defn set-file-id [^GridFSInputFile input ^String id]
  (.setId input id)
  input)

(defn upload [applicationId file-id filename content-type tempfile timestamp]
  (gfs/store-file
    (gfs/make-input-file tempfile)
    (set-file-id file-id)
    (gfs/filename filename)
    (gfs/content-type content-type)
    (gfs/metadata {:uploaded timestamp, :application applicationId})))

(defn download [file-id]
  (if-let [attachment (gfs/find-one {:_id file-id})]
    {:content (fn [] (.getInputStream attachment))
     :content-type (.getContentType attachment)
     :content-length (.getLength attachment)
     :file-name (.getFilename attachment)
     :application (.getString (.getMetaData attachment) "application")}))

(defn count
  "returns count of objects in collection"
  ([collection]
    (mc/count collection))
  ([collection query]
    (mc/count collection query)))

;;
;; Bootstrappin'
;;

(def connected (atom false))

(defn connect!
  ([]
    (connect! mongouri))
  ([uri]
    (if @connected
      (debug "Already connected!")
      (do
        (debug "Connecting to DB:" uri)
        (m/connect-via-uri! uri)
        (debug "DB is" (.getName (m/get-db)))
        (reset! connected true)))))

(defn ensure-indexes []
  (debug "ensure-indexes")
  (mc/ensure-index :users {:username 1})
  (mc/ensure-index :users {:email 1} {:unique true})
  (mc/ensure-index :users {:municipality 1} {:sparse true})
  (mc/ensure-index :users {:private.apikey 1} {:unique true :sparse true})
  #_(mc/ensure-index "users" {:personId 1} {:unique true})
  (mc/ensure-index :applications {:municipality 1})
  (mc/ensure-index :applications {:auth.id 1})
  (mc/ensure-index :applications {:auth.invite.user.id 1} {:sparse true})
  (mc/ensure-index :activations {:created-at 1} {:expireAfterSeconds (* 60 60 24 7)})
  (mc/ensure-index :vetuma {:created-at 1} {:expireAfterSeconds (* 60 30)})
  (mc/ensure-index :municipalities {:municipalityCode 1}))

(defn clear! []
  (warn "Clearing MongoDB:" mongouri)
  (gfs/remove-all)
  ; Collections must be dropped individially, otherwise index cache will be stale
  (doseq [coll (db/get-collection-names)]
    (when-not (.startsWith coll "system") (mc/drop coll)))
  (ensure-indexes))
