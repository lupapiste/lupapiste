(ns lupapalvelu.file-upload
  (:require [monger.operators :refer :all]
            [sade.util :as util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.vetuma :as vetuma]))

(defn save-file [file]
  (let [file-id (mongo/create-id)
        sanitized-filename (mime/sanitize-filename (:filename file))]
    (mongo/upload file-id sanitized-filename (:content-type file) (:tempfile file) :sessionId (vetuma/session-id) :linked false)
    {:id file-id
     :filename sanitized-filename
     :size (:size file)
     :contentType (:content-type file)}))

(defn- two-hours-ago []
  ; Matches vetuma session TTL
  (util/get-timestamp-ago :hour 2))

(defn cleanup-uploaded-files []
  (when-not @mongo/connection
    (mongo/connect!))
  (mongo/delete-file {$and [{:metadata.linked {$exists true}}
                            {:metadata.linked false}
                            {:metadata.uploaded {$lt (two-hours-ago)}}]}))
