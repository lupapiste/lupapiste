(ns lupapalvelu.attachment.preview
  (:require [clojure.java.io :as io]
            [lupapalvelu.integrations.pubsub :as lip]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.storage.gcs :as gcs]
            [lupapiste-pubsub.protocol :as pubsub]
            [sade.util :as util]
            [taoensso.timbre :refer [error warn]])
  (:import [mount.core DerefableState]))

(defn- generate-via-pubsub
  "Generates a preview image by dispatching a message via GCP Pub/Sub for vaahtera-laundry to handle.
   The original file must be in GCS."
  [application-id {:keys [fileId storageSystem]}]
  (let [bucket    (gcs/actual-bucket-name (storage/bucket-name {:application application-id}))
        object-id (storage/actual-object-id application-id fileId)
        suffix "-preview"
        storage-system (or storageSystem (storage/default-storage-system-id))]
    (cond
      (instance? DerefableState lip/client)
      (warn "Pub/Sub client not started with mount/start. Skipping preview generation.")

      (util/=as-kw storage-system :gcs)
      (if (storage/application-file-exists? storage-system application-id fileId)
        (pubsub/publish lip/client
                        "to-conversion-service"
                        {:handler            :convert-to-jpeg
                         :message-id         (str bucket "/" fileId suffix)
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

(defn preview-image
  "Generates preview image for the given file. Fails silently (never throws)."
  [application-id file-version]
  (logging/with-logging-context
    {:applicationId application-id
     :fileId        (:fileId file-version)}
    (try
      (if lip/client
        (generate-via-pubsub application-id file-version)
        (warn "Pub/Sub client not available. Skipping preview generation."))
     (catch Exception e  ;; Just in case
       (warn "Preview image generation failed." (ex-message e))))))

(defn generate-preview-and-return-placeholder!
  [application-id latest-version]
  (preview-image application-id latest-version)
  {:contentType "image/jpeg"
   :filename    "preview-being-generated.jpg"
   :content     #(io/input-stream (io/resource "preview-being-generated.jpg"))})
