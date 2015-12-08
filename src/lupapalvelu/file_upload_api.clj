(ns lupapalvelu.file-upload-api
  (:require [noir.core :refer [defpage]]
            [noir.response :as resp]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [defcommand]]
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

(defn- file-upload-in-database
  [{{attachment-id :attachmentId} :data}]
  (let [file-found? (mongo/any? :fs.files {:_id attachment-id "metadata.sessionId" (vetuma/session-id)})]
    (when-not file-found?
      (fail :error.file-upload.not-found))))

(defn- attachment-not-linked
  [{{attachment-id :attachmentId} :data}]
  (when-let [{{bulletin-id :bulletinId} :metadata} (mongo/select-one :fs.files {:_id attachment-id "metadata.sessionId" (vetuma/session-id)})]
    (when-not (empty? bulletin-id)
      (fail :error.file-upload.already-linked))))

(defcommand remove-uploaded-file
  {:parameters [attachmentId]
   :input-validators [file-upload-in-database attachment-not-linked]
   :user-roles #{:anonymous}}
  [_]
  (println "REMOVING FILE" attachmentId)
  )