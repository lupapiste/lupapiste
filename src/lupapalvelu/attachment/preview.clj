(ns lupapalvelu.attachment.preview
  (:require [clojure.java.io :as io]
            [lupapalvelu.logging :as log :refer [debug warn]]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.storage.gcs :as gcs]
            [lupapalvelu.system.util-components :as components]
            [sade.util :as util]))

(defn- generate-via-pubsub
  "Generates a preview image by dispatching a message via GCP Pub/Sub for vaahtera-laundry to handle.
   The original file must be in GCS."
  [publisher application-id {:keys [fileId storageSystem]}]
  (let [bucket         (gcs/actual-bucket-name (storage/bucket-name {:application application-id}))
        object-id      (storage/actual-object-id application-id fileId)
        suffix         "-preview"
        storage-system (or storageSystem (storage/default-storage-system-id))]
    (cond
      (util/=as-kw storage-system :gcs)
      (if (storage/application-file-exists? storage-system application-id fileId)
        (publisher {:handler            :convert-to-jpeg
                    :message-id         (str bucket "/" object-id suffix)
                    :response-expected? false
                    :data               {:bucket        bucket
                                         :object-key    object-id
                                         :target-bucket bucket
                                         :suffix        suffix
                                         :output-size   1000
                                         :quality       60
                                         :watermark     false}})
        (warn "Attachment file" fileId "for application" application-id
              "not found in storage."))

      :else
      (warn "Attachment file" fileId "for application" application-id
            "is not in GCS and preview image cannot be generated."))))

(def previewable-mime-types
  "List of MIME types that should match what vaahtera-laundry/kuvittaja supports as source type."
  #{"application/pdf"
    "image/jpeg"
    "image/png"
    "image/tiff"
    "image/gif"
    "image/webp"
    "image/avif"
    "image/svg+xml"
    "image/heic"
    "image/heif"})

(defn- version-previewable? [{:keys [contentType]}]
  (contains? previewable-mime-types contentType))

(defn preview-image
  "Generates preview image for the given file. Fails silently (never throws)."
  [system application-id file-version]
  {:pre [(map? system)]}
  (log/with-logging-context
    {:applicationId application-id
     :fileId        (:fileId file-version)}
    (try
      (if (version-previewable? file-version)
        (if-let [publisher (components/system->publisher system :attachment/preview)]
          (generate-via-pubsub publisher application-id file-version)
          (warn "Pub/Sub client not available. Skipping preview generation."))
        (debug "Not generating preview image for mime type" (:contentType file-version)))
      (catch Exception e ;; Just in case
        (warn "Preview image generation failed." (ex-message e))))))

(defn generate-preview-and-return-placeholder!
  [system application-id latest-version]
  {:pre [(map? system)]}
  (if (version-previewable? latest-version)
    (do (preview-image system application-id latest-version)
        {:contentType "image/jpeg"
         :filename    "preview-being-generated.jpg"
         :content     #(io/input-stream (io/resource "attachments/preview-being-generated.jpg"))})
    {:contentType "image/jpeg"
     :filename    "no-preview-available.jpg"
     :content     #(io/input-stream (io/resource "attachments/no-preview-available.jpg"))}))
