(ns lupapalvelu.attachment.preview
  (:require [taoensso.timbre :refer [debugf warnf error errorf]]
            [me.raynes.fs :as fs]
            [sade.util :as util]
            [lupapiste-commons.external-preview :as ext-preview]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.file-upload :as file-upload]
            [lupapiste-commons.threads :as threads]
            [sade.env :as env]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.storage.s3 :as s3])
  (:import [java.io InputStream]))

(defonce preview-generation-pool (threads/threadpool 2 "preview-generation-worker"))

(defn- download-and-generate! [download-fn application-id file-id filename content-type preview-file-id preview-filename]
  (if-let [preview-content (util/timing
                             (format "Creating preview: id=%s, type=%s file=%s" file-id content-type filename)
                             ;; It's possible at least in tests to delete the file before preview
                             ;; generation runs.
                             (when-let [content-fn (:content (download-fn file-id))]
                               (with-open [content (content-fn)]
                                 (ext-preview/create-preview content
                                                             content-type
                                                             (env/value :muuntaja :url)))))]
    (do (debugf "Saving preview: id=%s, type=%s file=%s" file-id content-type filename)
        (file-upload/save-file {:fileId   preview-file-id
                                :filename preview-filename
                                :content  preview-content}
                               :application application-id)
        (.close ^InputStream preview-content))
    (warnf "Preview image of %s file '%s' (id=%s) was not generated" content-type filename file-id)))

(defn- mongo-preview! [db-name application-id file-id filename content-type preview-file-id preview-filename]
  (mongo/with-db (or db-name mongo/default-db-name)
    (when (seq (mongo/file-metadata {:id file-id}))
      (download-and-generate! mongo/download
                              application-id
                              file-id
                              filename
                              content-type
                              preview-file-id
                              preview-filename))))

(defn- s3-preview! [application-id file-id filename content-type preview-file-id preview-filename]
  (if (s3/object-exists? application-id file-id)
    (download-and-generate! (partial s3/download application-id)
                            application-id
                            file-id
                            filename
                            content-type
                            preview-file-id
                            preview-filename)
    (throw (Exception. (str "Attachment file " file-id " for application " application-id " not found in S3")))))

(defn preview-image!
  "Creates a preview image in a separate thread pool."
  [application-id file-id filename content-type]
  {:pre [(every? string? [application-id file-id filename content-type])]}
  (let [db-name mongo/*db-name*
        preview-file-id (str file-id "-preview")
        preview-filename (str (fs/name filename) ".jpg")]
    (threads/submit
      preview-generation-pool
      (logging/with-logging-context
        {:applicationId application-id}
        (try
          (if (env/feature? :s3)
            (s3-preview! application-id file-id filename content-type preview-file-id preview-filename)
            (mongo-preview! db-name application-id file-id filename content-type preview-file-id preview-filename))
          (catch Throwable t
            (error t "Preview generation failed")))))))

(defn generate-preview-and-return-placeholder!
  [application-id file-id filename content-type]
  (preview-image! application-id file-id filename content-type)
  {:contentType "image/jpeg"
   :filename    "no-preview-available.jpg"
   :content     ext-preview/placeholder-image-is})
