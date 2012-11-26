(ns lupapalvelu.mongo
  (:use monger.operators
        lupapalvelu.log)
  (:require [monger.core :as m]
            [monger.collection :as mc]
            [monger.gridfs :as gfs])
  (:import [org.bson.types ObjectId]
           [com.mongodb.gridfs GridFS GridFSInputFile]))

(def ^:const mongouri "mongodb://127.0.0.1/lupapalvelu")
(def ^:const users "users")
(def ^:const applications "applications")
(def collections [users applications])

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
  (.toString (ObjectId.)))

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

;;
;; Bootstrappin'
;;

(defn connect!
  ([]
    (connect! mongouri))
  ([uri]
    (when (nil? m/*mongodb-connection*)
      (debug "Connecting to DB: %s" uri)
      (m/connect-via-uri! uri)
      (debug "DB is \"%s\"" (str (m/get-db))))))

(defn clear! []
  (warn "** Clearing DB **")
  (gfs/remove-all)
  (dorun (map #(mc/remove %) collections))
  (mc/drop-indexes "users")
  (mc/ensure-index "users" {:email 1} {:unique true})
  #_(mc/ensure-index "users" {:personId 1} {:unique true}))
