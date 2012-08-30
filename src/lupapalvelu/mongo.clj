(ns lupapalvelu.mongo 
  (:use monger.operators)
  (:require [monger.core :as m]
            [monger.collection :as mc]
            [monger.gridfs :as gfs]
            [lupapalvelu.fixture.full :as fixture])
  (:import [org.bson.types ObjectId]))

(def ^:const mongouri "mongodb://127.0.0.1/lupapalvelu")
(def ^:const partys "partys")
(def ^:const partyGroupings "partyGroupings")
(def ^:const applications "applications")

;;
;; Utils
;; 

(defn string-to-objectid [id]
  (ObjectId/massageToObjectId id))

(defn objectid-to-string [id] 
  (.toString id)) 

(defn- with-objectid [data]
  (if-let [id (:id data)]
    (-> data 
      (assoc :_id (string-to-objectid id)) 
      (dissoc :id))))

(defn- with-id [data]
  (if-let [id (:_id data)]
    (-> data 
      (assoc :id (objectid-to-string id)) 
      (dissoc :_id))))

;;
;; Mongo Api
;; 

(defn update [collection id data]
  "Updates data into collection by id (which is mapped to _id). Always returns nil."
  (mc/update-by-id collection (string-to-objectid id) data)
  nil)

(defn insert [collection data]
  "Inserts data into collection. Re-uses 'id' as  Always returns nil."
  (if (contains? data :id)
    (mc/insert collection (with-objectid data))
    (mc/insert collection data))
  nil)

(defn by-id [collection id]
  (with-id (mc/find-one-as-map collection {:_id (string-to-objectid id)})))

(defn all [collection]
  (map with-id (mc/find-maps collection)))

(defn select [collection data]
  (map with-id (mc/find-maps collection data)))

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

(defn init []
  (m/connect-via-uri! mongouri)
  (mc/remove partys)
  (mc/remove partyGroupings)
  (mc/remove applications)
  (dorun (map #(insert partys %) (fixture/partys)))
  (dorun (map #(insert partyGroupings %) (fixture/partyGroupings)))
  (dorun (map #(insert applications %) (fixture/applications)))
  )
