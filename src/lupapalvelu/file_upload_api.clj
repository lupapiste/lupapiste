(ns lupapalvelu.file-upload-api
  (:require [noir.core :refer [defpage]]
            [noir.response :as resp]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.vetuma :as vetuma]))

(defn- save-file [file]
  (let [file-id (mongo/create-id)
        sanitized-filename (mime/sanitize-filename (:filename file))]
    (mongo/upload file-id sanitized-filename (:content-type file) (:tempfile file) :sessionId (vetuma/session-id))
    {:id file-id
     :filename sanitized-filename
     :size (:size file)
     :contentType (:content-type file)}))

(defpage [:post "/api/upload/file"] {files :files}
  (let [file-info {:files (pmap save-file files)}]
    (->> (assoc file-info :ok true)
      (resp/json)
      (resp/content-type "text/plain")
      (resp/status 200))))
