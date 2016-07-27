(ns lupapalvelu.file-upload
  (:require [monger.operators :refer :all]
            [sade.util :as util]
            [schema.core :as sc]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mime :as mime])
  (:import (java.io File InputStream)))

(def FileData
  {:filename                        sc/Str
   :content                         (sc/cond-pre File InputStream)
   (sc/optional-key :content-type)  sc/Str
   (sc/optional-key :size)          sc/Num})

(defn save-file
  "Saves file or input stream to mongo GridFS, with metadata (map or kvs).
   Filedata is map (see FileData schema).
   Map of file specific data (fileId, metadata, contentType...) is returned."
  [filedata & metadata]
  {:pre [(sc/validate FileData filedata)]}
  (let [metadata (if (map? (first metadata))
                   (first metadata)
                   (apply hash-map metadata))
        file-id            (mongo/create-id)
        sanitized-filename (mime/sanitize-filename (:filename filedata))
        content-type       (mime/mime-type sanitized-filename)

        result (mongo/upload file-id sanitized-filename content-type (:content filedata) metadata)]
    {:fileId file-id
     :filename sanitized-filename
     :size (or (:size filedata) (:length result))
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
