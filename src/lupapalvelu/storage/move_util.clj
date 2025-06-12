(ns lupapalvelu.storage.move-util
  (:require [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.storage.gridfs :as gfs]
            [lupapalvelu.storage.object-storage :as object-storage]
            [lupapiste-commons.threads :as threads]
            [monger.operators :refer :all]
            [sade.env :as env])
  (:import [java.util.concurrent ThreadPoolExecutor]))

(set! *warn-on-reflection* true)

(def *gcs (delay (:gcs storage/object-storage-impls)))

(def thread-count (or (when-let [v (env/value :gcs :move-thread-count)]
                        (try (Integer/parseInt v)
                             (catch NumberFormatException _)))
                      64))

(defonce ^ThreadPoolExecutor thread-pool (threads/threadpool thread-count "move-to-gcs-worker"))

(defn- fix-application-attachments [application-id]
  (try
    (doseq [{:keys [versions] att-id :id} (->> (mongo/by-id :applications application-id [:attachments])
                                               :attachments)
            {:keys [fileId originalFileId storageSystem]} versions
            file-id [fileId originalFileId]
            :let [bucket     object-storage/application-bucket
                  object-key (storage/actual-object-id application-id file-id)]
            :when (and (some? file-id)
                       (not (.isInterrupted (Thread/currentThread)))
                       (not (object-storage/object-exists? (storage/get-storage-impl storageSystem)
                                                           bucket
                                                           object-key)))]
      (if-let [file-data (or (gfs/download-find {:_id file-id})
                             (object-storage/download @*gcs bucket object-key))]
        (let [{:keys [content contentType filename metadata]} file-data]
          (log/info "Copying" application-id "/" att-id "/" file-id "to" storageSystem)
          (object-storage/put-file-or-input-stream (storage/get-storage-impl storageSystem)
                                                   bucket
                                                   object-key
                                                   filename
                                                   contentType
                                                   (content)
                                                   metadata))
        (log/error "Could not rescue" application-id "/" att-id "/" file-id "from any storage system")))
      (catch Throwable t
        (log/error t "Could not move files for application" application-id))))

(defn rescue-files-from-wrong-storage [modified-since]
  {:pre [(integer? modified-since)]}
  (let [apps (mongo/select :applications
                           {$or [{:modified {$gte modified-since}}
                                 {:attachments.latestVersion.modified {$gte modified-since}}]}
                           [:_id]
                           {:modified 1})]
    (log/info "Rescuing orphaned application attachment files")
    (->> apps
         (mapv (fn [{:keys [id]}]
                 (threads/submit thread-pool (fix-application-attachments id))))
         threads/wait-for-threads)
    (log/info "Application attachments rescued")))
