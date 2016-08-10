(ns lupapalvelu.attachment.preview
  (:require [taoensso.timbre :refer [error debugf]]
            [com.netflix.hystrix.core :as hystrix]
            [me.raynes.fs :as fs]
            [sade.util :as util]
            [lupapiste-commons.preview :as commons-preview]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.file-upload :as file-upload])
  (:import (com.netflix.hystrix HystrixCommandProperties)
           (java.util.concurrent ThreadFactory Executors)))

(defn thread-factory []
  (let [security-manager (System/getSecurityManager)
        thread-group (if security-manager
                       (.getThreadGroup security-manager)
                       (.getThreadGroup (Thread/currentThread)))]
    (reify
      ThreadFactory
      (newThread [this runnable]
        (doto (Thread. thread-group runnable "preview-worker")
          (.setDaemon true)
          (.setPriority Thread/NORM_PRIORITY))))))

(defonce preview-threadpool (Executors/newFixedThreadPool 1 (thread-factory)))

(hystrix/defcommand create-preview!
  {:hystrix/group-key   "Attachment"
   :hystrix/command-key "Create preview"
   :hystrix/init-fn     (fn fetch-request-init [_ setter]
                          (.andCommandPropertiesDefaults setter
                                                         (.withExecutionTimeoutInMilliseconds
                                                           (HystrixCommandProperties/Setter)
                                                           (* 2 60 1000)))
                          setter)
   :hystrix/fallback-fn (constantly nil)}
  [file-id filename content-type application-id & [db-name]]
  (try
    (when (commons-preview/converter content-type)
      (let [preview-file-id (str file-id "-preview")
            preview-filename (str (fs/name filename) ".jpg")]
        (mongo/with-db (or db-name mongo/default-db-name)
          (file-upload/save-file {:fileId   preview-file-id
                                  :filename preview-filename
                                  :content  (commons-preview/placeholder-image)}
                                 :application application-id)
          (if-let [preview-content (util/timing (format "Creating preview: id=%s, type=%s file=%s" file-id content-type filename)
                                                (with-open [content ((:content (mongo/download file-id)))]
                                                  (commons-preview/create-preview content content-type)))]
            (do (debugf "Saving preview: id=%s, type=%s file=%s" file-id content-type filename)
                (file-upload/save-file {:fileId   preview-file-id
                                        :filename preview-filename
                                        :content  preview-content}
                                       :application application-id))
            (error "Preview generation failed: id=%s, type=%s file=%s" file-id content-type filename)))))
    (catch Throwable t
      (error "Preview generation failed" t))))

(defn preview-image!
  "Creates a preview image in own thread pool."
  [application-id fileId filename contentType]
  (.submit preview-threadpool #(create-preview! fileId filename contentType application-id mongo/*db-name*)))
