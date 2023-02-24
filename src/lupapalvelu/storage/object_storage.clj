(ns lupapalvelu.storage.object-storage
  (:require [ring.util.codec :as codec]
            [sade.core :refer [now]]
            [sade.util :as util]
            [sade.env :as env]))

(defprotocol ObjectStorage
  (put-input-stream [client bucket file-id filename content-type input-stream content-length metadata-map]
    "Puts the provided content input stream under the given id in the specified bucket.

    `bucket` should be one of the buckets defined in `lupapalvelu.storage.object-storage`. If it is nil, the
    object will go to the common temp file bucket from which it can be linked to an application later.

    `file-id` should contain the full path with any relevant prefix, e.g. \"LP-753-2020-90001/[file_uuid]\"")
  (put-file-or-input-stream [client bucket file-id filename content-type is-or-file metadata-map]
    "Puts the provided content under the given id in the specified bucket.
    The content may be a (temp) file handle or an input stream.
    If it's a stream and storage is S3, consider using put-input-stream if the content length in bytes is known, as
    otherwise the stream will be copied to a byte array first. For GCS there is no distinction.

    `bucket` should be one of the buckets defined in `lupapalvelu.storage.object-storage`. If it is nil, the
    object will go to the common temp file bucket from which it can be linked to an application later.

    `file-id` should contain the full path with any relevant prefix, e.g. \"LP-753-2020-90001/[file_uuid]\"")
  (copy-file-object [client from-bucket to-bucket old-id new-id]
    "Copies object within the storage system from one bucket to another.")
  (move-file-object [client from-bucket to-bucket old-id new-id]
    "Moves object within the storage system from one bucket to another by copying the object and then deleting
     it from the source bucket.")
  (download [client bucket file-id]
    "Downloads the given `file-id` from `bucket` which should be one of the buckets defined in
    `lupapalvelu.storage.object-storage`. Returns nil if the object is not found. Otherwise returns the following map:
    {:content     function which returns the content input stream on application
     :contentType MIME type of the content, e.g. \"application/pdf\"
     :size        number of bytes in the content
     :filename    filename provided when the file was uploaded into storage
     :fileId      last part of the object key, i.e. file UUID
     :metadata    keyword->string map of any other metadata provided when the file was stored}")
  (list-file-objects [client bucket prefix]
    "Lists all objects in the bucket starting with the `prefix` (which might be a path like dev_sipoo/rakennus).
     Returns a list of file data maps matching the one returned by the download method.")
  (object-exists? [client bucket id]
    "Checks if the given file `id` exists in `bucket`.")
  (delete [client bucket id]
    "Deletes the given file `id` from `bucket`. Returns always `nil`.")
  (delete-unlinked-files [client older-than]
    "Deletes all files `older-than` from the unlinked files bucket, i.e. orphaned uploads."))

(def process-bucket "sign-process")
(def application-bucket "application-files")
(def user-bucket "user-files")
(def unlinked-bucket "unlinked-files")
(def bulletin-bucket "bulletin-files")
(def sftp-bucket (env/value :gcs :sftp-bucket))

(def buckets [process-bucket application-bucket user-bucket unlinked-bucket bulletin-bucket])

(defn generate-user-metadata
  "Returns a Java Map<String,String> compatible map"
  [metadata filename]
  (reduce (fn [result [k v]]
            (assoc result (name k) (-> (if (keyword? v)
                                         (name v)
                                         (str v))
                                       codec/url-encode)))
          {"uploaded" (str (now))
           "filename" (codec/url-encode filename)}
          metadata))

(defn process-retrieved-metadata [metadata-map]
  (->> metadata-map
       (map (fn [[k v]]
              [(case k
                 "sessionid" :sessionId
                 (keyword k))
               (case k
                 "uploaded" (util/to-long v)
                 "linked" (= v "true")
                 (codec/url-decode v))]))
       (into {})))
