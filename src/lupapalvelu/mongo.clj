(ns lupapalvelu.mongo 
  (:use monger.operators
        lupapalvelu.log)
  (:require [monger.core :as m]
            [monger.collection :as mc]
            [monger.gridfs :as gfs]
            [lupapalvelu.fixture.minimal :as minimal])
  (:import [org.bson.types ObjectId]))

(def ^:const mongouri "mongodb://127.0.0.1/lupapalvelu")
(def ^:const users "users")
(def ^:const applications "applications")
(def collections [users applications])

;;
;; Utils
;; 

(defn string-to-objectid [id]
  (ObjectId/massageToObjectId id))

(defn objectid-to-string [id] 
  (.toString id)) 

(defn with-objectid [m]
  (if-let [id (:id m)]
    (-> m
      (assoc :_id (string-to-objectid id))
      (dissoc :id))
    m))

(defn with-id [m]
  (if-let [id (:_id m)]
    (-> m 
      (assoc :id (objectid-to-string id)) 
      (dissoc :_id))
    m))

(defn make-objectid []
  (.toString (ObjectId.)))

;;
;; Mongo Api
;; 

(defn update [collection query data]
  "Updates data into collection by query. Always returns nil."
  (mc/update collection query data)
  nil)

(defn update-by-id [collection id data]
  "Updates data into collection by id (which is mapped to _id). Always returns nil."
  (mc/update-by-id collection (string-to-objectid id) data)
  nil)

(defn insert [collection data]
  "Inserts data into collection. The 'id' in 'data' (if it exists) is converted to MongoDB ObjectID."
  (mc/insert collection (with-objectid data))
  nil)

(defn by-id [collection id]
  (with-id (mc/find-one-as-map collection {:_id (string-to-objectid id)})))

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


(defn upload [file-name content-type temp-file]
  (with-id
    (gfs/store-file
      (gfs/make-input-file temp-file)
      (gfs/filename file-name)
      (gfs/content-type content-type)
      (gfs/metadata {:uploaded (System/currentTimeMillis)}))))

(defn download [attachmentId]
  (if-let [attachment (gfs/find-one (string-to-objectid attachmentId))]
    {:content (fn [] (.getInputStream attachment))
     :content-type (.getContentType attachment)
     :content-length (.getLength attachment)
     :file-name (.get (.getMetaData attachment) "file-name")}))

;;
;; Bootstrappin'
;;

(defn connect! []
  (debug "Connecting to DB: %s" mongouri)
  (m/connect-via-uri! mongouri)
  (debug "DB is \"%s\"" (str (m/get-db))))

(defn- clear! []
  (warn "** Clearing DB **")
  (dorun (map #(mc/remove %) collections))
  (mc/ensure-index "users" {:email 1} {:unique true})
  (mc/ensure-index "users" {:personId 1} {:unique true}))

(defn init-fixture! [name u a]
  (clear!)
  (warn "Initializing DB with profile '%s'" name)
  (dorun (map #(insert users %)        u))
  (dorun (map #(insert applications %) a))
  (str name " data set initialized"))

(defn init-minimal! [] (init-fixture! "minimal" (minimal/users) (minimal/applications)))

(defn init! []
  (init-minimal!))
