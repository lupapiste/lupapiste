(ns lupapalvelu.attachment.preview
  (:require [taoensso.timbre :refer [debugf warnf error errorf]]
            [com.netflix.hystrix.core :as hystrix]
            [me.raynes.fs :as fs]
            [sade.util :as util]
            [lupapiste-commons.preview :as commons-preview]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.file-upload :as file-upload])
  (:import (com.netflix.hystrix HystrixCommandProperties HystrixThreadPoolProperties HystrixCommand$Setter)))

(hystrix/defcommand create-preview!
  {:hystrix/group-key   "Attachment"
   :hystrix/command-key "Create preview"
   :hystrix/thread-pool-key :preview-generation-thread-pool
   :hystrix/init-fn     (fn fetch-request-init [_ ^HystrixCommand$Setter setter]
                          (doto setter
                            (.andCommandPropertiesDefaults
                              (.withExecutionTimeoutInMilliseconds (HystrixCommandProperties/Setter) (* 2 60 1000)))
                            (.andThreadPoolPropertiesDefaults
                              (doto (HystrixThreadPoolProperties/Setter)
                                (.withCoreSize 1)
                                (.withMaxQueueSize Integer/MAX_VALUE)))))
   :hystrix/fallback-fn (constantly nil)}
  [file-id filename content-type application-id & [db-name]]
  (try
    (when (commons-preview/converter content-type)
      (let [preview-file-id (str file-id "-preview")
            preview-filename (str (fs/name filename) ".jpg")]
        (mongo/with-db (or db-name mongo/default-db-name)
          (when (seq (mongo/file-metadata {:id file-id}))
            (with-open [placeholder (commons-preview/placeholder-image-is)]
              (file-upload/save-file {:fileId   preview-file-id
                                      :filename "no-preview-available.jpg"
                                      :content  placeholder}
                                     :application application-id))
            (if-let [preview-content (util/timing (format "Creating preview: id=%s, type=%s file=%s" file-id content-type filename)
                                                  ;; It's possible at least in tests to delete the file before preview
                                                  ;; generation runs.
                                                  (when-let [content-fn (:content (mongo/download file-id))]
                                                    (with-open [content (content-fn)]
                                                      (commons-preview/create-preview content content-type))))]
              (do (debugf "Saving preview: id=%s, type=%s file=%s" file-id content-type filename)
                  (mongo/delete-file-by-id preview-file-id)
                  (file-upload/save-file {:fileId   preview-file-id
                                          :filename preview-filename
                                          :content  preview-content}
                                         :application application-id)
                  (.close preview-content))
              (warnf "Preview image of %s file '%s' (id=%s) was not generated" content-type filename file-id))))))
    (catch Throwable t
      (error t "Preview generation failed"))))

(defn preview-image!
  "Creates a preview image in own thread pool."
  [application-id file-id filename content-type]
  {:pre [(every? string? [application-id file-id filename content-type])]}
  (let [db-name mongo/*db-name*]
    (hystrix/queue #'create-preview! file-id filename content-type application-id db-name)))

(defn generate-preview-and-return-placeholder!
  [application-id file-id filename content-type]
  (preview-image! application-id file-id filename content-type)
  {:contentType "image/jpeg"
   :filename    "no-preview-available.jpg"
   :content     commons-preview/placeholder-image-is})
