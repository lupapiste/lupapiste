(ns lupapalvelu.storage.gridfs
  (:require [clojure.java.io :as io]
            [monger.conversion :refer [from-db-object]]
            [monger.gridfs :as gfs]
            [taoensso.timbre :refer [info]]
            [sade.core :refer [now]]
            [lupapalvelu.mongo :as mongo])
  (:import [java.io File InputStream]
           [com.mongodb.gridfs GridFSDBFile GridFSInputFile]))

(defn ^{:perfmon-exclude true} set-file-id [^GridFSInputFile input ^String id]
  (.setId input (mongo/remove-null-chars id))
  input)

(defn upload [file-id filename content-type content & metadata]
  {:pre [(string? file-id) (string? filename) (string? content-type)
         (or (instance? File content) (instance? InputStream content))
         (or (nil? metadata) (sequential? metadata))
         (or (even? (clojure.core/count metadata)) (map? (first metadata)))]}
  (let [meta (mongo/remove-null-chars (if (map? (first metadata))
                                  (first metadata)
                                  (apply hash-map metadata)))]
    (with-open [input-stream (io/input-stream content)]
      (gfs/store-file (gfs/make-input-file (mongo/get-gfs) input-stream)
                      (set-file-id file-id)
                      (gfs/filename filename)
                      (gfs/content-type content-type)
                      (gfs/metadata (assoc meta :uploaded (now)))))))

(defn- ^{:perfmon-exclude true} gridfs-file-as-map [^GridFSDBFile attachment]
  (let [metadata (from-db-object (.getMetaData attachment) :true)]
    {:content     (fn [] (.getInputStream attachment))
     :contentType (.getContentType attachment)
     :size        (.getLength attachment)
     :filename    (.getFilename attachment)
     :fileId      (.getId attachment)
     :metadata    metadata
     :application (:application metadata)}))

(defn file-metadata
  "Returns only GridFS file metadata as map. Use download-find to get content."
  [query]
  (gfs/find-one-as-map (mongo/get-gfs) (mongo/with-_id (mongo/remove-null-chars query))))

(defn update-file-by-query
  [query updates]
  {:pre [(mongo/max-1-elem-match? query) (map? query) (map? updates)]}
  (mongo/update-by-query :fs.files query updates))

(defn download-find-many [query]
  (map gridfs-file-as-map (gfs/find (mongo/get-gfs) (mongo/with-_id (mongo/remove-null-chars query)))))

(defn download-find [query]
  (when-let [attachment (gfs/find-one (mongo/get-gfs) (mongo/with-_id (mongo/remove-null-chars query)))]
    (gridfs-file-as-map attachment)))

(defn ^{:perfmon-exclude true} download
  "Downloads file from Mongo GridFS"
  [file-id]
  (download-find {:_id file-id}))

(defn delete-file [query]
  {:pre [(seq query)]}
  (let [query (mongo/with-_id (mongo/remove-null-chars query))]
    (info "removing file" query)
    (gfs/remove (mongo/get-gfs) query)))

(defn ^{:perfmon-exclude true} delete-file-by-id [file-id]
  {:pre [(string? file-id)]}
  (delete-file {:id file-id}))
