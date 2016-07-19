(ns lupapalvelu.file-upload
  (:require [monger.operators :refer :all]
            [sade.util :as util]
            [schema.core :as sc]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mime :as mime])
  (:import (java.io File)))

(def UploadFile
  {:filename      sc/Str
   :tempfile      File
   :content-type  sc/Str
   :size          sc/Num})

(defn save-file
  "Saves given file to mongo GridFS, with metadata (map or kvs).
   File is file map from request, actual file object is taken from :tempfile key."
  [file & metadata]
  {:pre [(sc/validate UploadFile file)]}
  (let [metadata (if (map? (first metadata))
                   (first metadata)
                   (apply hash-map metadata))
        file-id (mongo/create-id)
        sanitized-filename (mime/sanitize-filename (:filename file))
        content-type       (mime/mime-type sanitized-filename)]
    (mongo/upload file-id sanitized-filename content-type (:tempfile file) metadata)
    {:fileId file-id
     :filename sanitized-filename
     :size (:size file)
     :contentType content-type
     :metadata metadata}))

(defn- two-hours-ago []
  ; Matches vetuma session TTL
  (util/get-timestamp-ago :hour 2))

(defn cleanup-uploaded-files []
  (when-not @mongo/connection
    (mongo/connect!))
  (mongo/delete-file {$and [{:metadata.linked {$exists true}}
                            {:metadata.linked false}
                            {:metadata.uploaded {$lt (two-hours-ago)}}]}))
