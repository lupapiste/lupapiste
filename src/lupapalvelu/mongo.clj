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

(defn update [collection query data]
  "Updates data into collection by query. Always returns nil."
  (mc/update collection query data)
  nil)

(defn update-by-id [collection id data]
  "Updates data into collection by id (which is mapped to _id). Always returns nil."
  (mc/update-by-id collection id data)
  nil)

(defn insert [collection data]
  "Inserts data into collection. The 'id' in 'data' (if it exists) is persisted as _id"
  (mc/insert collection (with-_id data))
  nil)

(defn by-id [collection id]
  (with-id (mc/find-one-as-map collection {:_id id})))

(defn select
  "returns multiple entries by matching the monger query"
  ([collection]
    (select collection {}))
  ([collection query]
    (map with-id (mc/find-maps collection query))))

(defn select-one
  "returns one entry by matching the monger query"
  [collection query]
  (with-id (mc/find-one-as-map collection query)))

(defn set-file-id [^GridFSInputFile input ^String id]
  (.setId input id)
  input)

(defn upload [applicationId attachmentId filename content-type tempfile timestamp]
  (gfs/store-file
    (gfs/make-input-file tempfile)
    (set-file-id attachmentId)
    (gfs/filename filename)
    (gfs/content-type content-type)
    (gfs/metadata {:uploaded timestamp, :application applicationId})))

(defn download [attachmentId]
  (if-let [attachment (gfs/find-one {:_id attachmentId})]
    {:content (fn [] (.getInputStream attachment))
     :content-type (.getContentType attachment)
     :content-length (.getLength attachment)
     :file-name (.getFilename attachment)}))

;;
;; Bootstrappin'
;;

(defn connect! []
  (debug "Connecting to DB: %s" mongouri)
  (m/connect-via-uri! mongouri)
  (debug "DB is \"%s\"" (str (m/get-db))))

(defn clear! []
  (warn "** Clearing DB **")
  (dorun (map #(mc/remove %) collections))
  (mc/ensure-index "users" {:email 1} {:unique true})
  (mc/ensure-index "users" {:personId 1} {:unique true}))