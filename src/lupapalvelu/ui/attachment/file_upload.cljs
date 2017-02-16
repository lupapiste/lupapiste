(ns lupapalvelu.ui.attachment.file-upload)

(defonce upload-service (js/LUPAPISTE.FileuploadService.))
(defonce attachment-service js/lupapisteApp.services.attachmentsService)

(defn bindToElem [options]
  (.bindFileInput upload-service options (js-obj "replaceFileInput" false)))

(defn subscribe-files-uploaded [input-id callback]
  (.subscribe js/hub
              (js-obj "eventType" "fileuploadService::filesUploaded"
                      "input" input-id
                      "status" "success")
              callback))


