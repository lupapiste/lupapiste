(ns lupapalvelu.ui.attachment.file-upload
  (:require [lupapalvelu.ui.hub :as hub]))

(defn bindToElem [options]
  (.bindFileInput js/lupapisteApp.services.fileUploadService options))

(defn subscribe-files-uploaded [input-id callback]
  (hub/subscribe {:eventType "fileuploadService::filesUploaded"
                  :input input-id
                  :status "success"}
                 callback))

(defn subscribe-bind-attachments-status [opts callback]
  (hub/subscribe (assoc opts :eventType "attachmentsService::bind-attachments-status") callback))

(defn bind-attachments [files]
  (.bindAttachments js/lupapisteApp.services.attachmentsService files))
