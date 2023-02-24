(ns lupapalvelu.storage.move-util
  (:require [taoensso.timbre :as timbre]
            [lupapalvelu.storage.gcs :as gcs]
            [lupapalvelu.storage.object-storage :as object-storage]
            [sade.env :as env]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapiste-commons.threads :as threads]
            [lupapalvelu.storage.gridfs :as gfs])
  (:import [com.google.cloud.storage BlobId Storage$CopyRequest]
           [java.util.concurrent TimeUnit]))

(def *s3 (delay (:s3 storage/object-storage-impls)))
(def *gcs (delay (:gcs storage/object-storage-impls)))

(def gcs-backup-bucket (env/value :gcs :backup-bucket))

(def thread-count (or (when-let [v (env/value :gcs :move-thread-count)]
                        (try (Integer/parseInt v)
                             (catch NumberFormatException _)))
                      64))

(defonce thread-pool (threads/threadpool thread-count "move-to-gcs-worker"))

(defn- copy-file-from-ceph [bucket id]
  (let [{:keys [content contentType filename metadata]} (object-storage/download @*s3 bucket id)]
    (if content
      (do (timbre/info (str "Copying " bucket " / " id " from Ceph to GCS"))
          (object-storage/put-file-or-input-stream @*gcs bucket id filename contentType (content) metadata))
      (throw (Exception. (str bucket " / " id " not found from Ceph"))))))

(defn move-file-to-operative-gcs [bucket id]
  (let [actual-bucket  (gcs/actual-bucket-name bucket)
        backup-blob-id (when gcs-backup-bucket (BlobId/of gcs-backup-bucket (str actual-bucket "/" id)))
        target-blob-id (BlobId/of actual-bucket id)]
    (gcs/create-bucket-if-not-exists actual-bucket)
    (cond
      (and backup-blob-id
           (.get gcs/storage backup-blob-id)) ; Object is in GCS backup bucket
      (-> (.copy gcs/storage (-> (Storage$CopyRequest/newBuilder)
                                 (.setSource backup-blob-id)
                                 (.setTarget target-blob-id)
                                 (.build)))
          (.getResult))

      (object-storage/object-exists? @*gcs bucket id)
      true

      ;; Missing from backup
      :else
      (copy-file-from-ceph bucket id))))

(defn- move-attachment-version-files [application-id {:keys [fileId originalFileId]} att-id version-idx]
  (doseq [file-id (if (= fileId originalFileId)
                    [fileId]
                    [fileId originalFileId])]
    (try
      (move-file-to-operative-gcs object-storage/application-bucket (storage/actual-object-id application-id file-id))
      (catch Throwable t
        (timbre/error t "Could not move file" file-id "from" application-id "/" att-id "version" version-idx))))
  (try
    ;; Preview is never in backup
    (copy-file-from-ceph object-storage/application-bucket (storage/actual-object-id application-id fileId :preview))
    (catch Exception _)))

(defn- move-application-attachments [app-ix total-count application-id]
  (try
    (doseq [[att-idx {:keys [versions latestVersion] att-id :id}] (->> (mongo/by-id :applications application-id [:attachments])
                                                                       :attachments
                                                                       (map-indexed vector))
            [version-idx {:keys [fileId storageSystem] :as version}] (map-indexed vector versions)
            :when (and (= (keyword storageSystem) :s3)
                       (some? fileId)
                       (not (.isInterrupted (Thread/currentThread))))]
      (try
        (move-attachment-version-files application-id version att-id version-idx)
        (mongo/update-by-id :applications
                            application-id
                            {$set (cond-> {(str "attachments." att-idx ".versions." version-idx ".storageSystem") :gcs}
                                    (= fileId (:fileId latestVersion))
                                    (assoc (str "attachments." att-idx ".latestVersion.storageSystem") :gcs))})
        (timbre/info "Marked" application-id "/" att-id "version" version-idx "storage system as GCS")
        (catch Throwable t
          (timbre/error t "Could not change" application-id "/" att-id "version" version-idx "storage system"))))
    (timbre/info "Moved application" application-id "[" app-ix "/" total-count "]")
    (catch Throwable t
      (timbre/error t "Could not move files for application" application-id))))

(defn- move-applications []
  (let [query     {:attachments.versions {$elemMatch {:storageSystem "s3"
                                                      :fileId        {$ne nil}}}}
        app-count (mongo/count :applications
                               query)
        apps      (mongo/select :applications
                                query
                                [:_id]
                                {:modified -1})]
    (timbre/info "Moving" app-count "applications from Ceph to GCS")
    (->> apps
         (map-indexed (fn [ix {:keys [id]}]
                        (threads/submit thread-pool (move-application-attachments (inc ix) app-count id))))
         vec
         threads/wait-for-threads)
    (timbre/info "Application attachments moved")))

(defn- move-users-files [user-ix total-count user-id]
  (try
    (doseq [{:keys [attachment-id storageSystem]} (-> (mongo/by-id :users
                                                                   user-id
                                                                   [:attachments])
                                                      :attachments)
            :when (and (= (keyword storageSystem) :s3)
                       (some? attachment-id)
                       (not (.isInterrupted (Thread/currentThread))))]
      (move-file-to-operative-gcs object-storage/user-bucket (storage/actual-object-id user-id attachment-id))
      (mongo/update :users
                    {:_id         user-id
                     :attachments {$elemMatch {:attachment-id attachment-id}}}
                    {$set {:attachments.$.storageSystem :gcs}})
      (timbre/info "Moved user attachment id" attachment-id "from Ceph to GCS"))
    (timbre/info "Moved user" user-id "[" user-ix "/" total-count "]")
    (catch Throwable t
      (timbre/error t "Could not move files for user" user-id))))

(defn- move-all-user-files []
  (let [user-count (mongo/count :users
                                {:attachments.storageSystem "s3"})
        users      (mongo/select :users
                                 {:attachments.storageSystem "s3"}
                                 [:_id]
                                 {:_id 1})]
    (timbre/info "Moving" user-count "users' files from Ceph to GCS")
    (->> users
         (map-indexed (fn [ix {:keys [id]}]
                        (threads/submit thread-pool (move-users-files (inc ix) user-count id))))
         vec
         threads/wait-for-threads)
    (timbre/info "User files moved")))

(defn- move-bulletin-comment-files [bulletin-ix total-count bulletin-comment-id]
  (try
    (let [{:keys [attachments bulletinId]} (mongo/by-id :application-bulletin-comments
                                                        bulletin-comment-id
                                                        [:attachments :bulletinId])]
      (doseq [{:keys [fileId storageSystem]} attachments
              :when (and (= (keyword storageSystem) :s3)
                         (some? fileId)
                         (not (.isInterrupted (Thread/currentThread))))]
        (move-file-to-operative-gcs object-storage/bulletin-bucket (storage/actual-object-id bulletinId fileId))
        (mongo/update :application-bulletin-comments
                      {:_id         bulletin-comment-id
                       :attachments {$elemMatch {:fileId fileId}}}
                      {$set {:attachments.$.storageSystem :gcs}})
        (timbre/info "Moved bulletin comment attachment id" fileId "from Ceph to GCS")))
    (timbre/info "Moved bulletin comment" bulletin-comment-id "[" bulletin-ix "/" total-count "]")
    (catch Throwable t
      (timbre/error t "Could not move files for bulletin comment" bulletin-comment-id))))

(defn- move-all-bulletin-comments-files []
  (let [comment-count (mongo/count :application-bulletin-comments
                                   {:attachments.storageSystem "s3"})
        comments      (mongo/select :application-bulletin-comments
                                    {:attachments.storageSystem "s3"}
                                    [:_id])]
    (timbre/info "Moving" comment-count "bulletin comments' files from Ceph to GCS")
    (->> comments
         (map-indexed (fn [ix {:keys [id]}]
                        (threads/submit thread-pool (move-bulletin-comment-files (inc ix) comment-count id))))
         vec
         threads/wait-for-threads)
    (timbre/info "Bulletin comment attachments moved")))

(defn- move-filebank-files [idx total-count fb-id]
  (try
    (doseq [{:keys [file-id storageSystem]} (-> (mongo/by-id :filebank
                                                             fb-id
                                                             [:files])
                                                :files)
            :when (and (= (keyword storageSystem) :s3)
                       (not (.isInterrupted (Thread/currentThread))))]
      (move-file-to-operative-gcs object-storage/application-bucket (storage/actual-object-id fb-id file-id))
      (mongo/update :filebank
                    {:_id   fb-id
                     :files {$elemMatch {:file-id file-id}}}
                    {$set {:files.$.storageSystem :gcs}})
      (timbre/info "Moved filebank file id" file-id "from Ceph to GCS"))
    (timbre/info "Moved filebank" fb-id "[" idx "/" total-count "]")
    (catch Throwable t
      (timbre/error t "Could not move files for filebank" fb-id))))

(defn- move-all-filebank-files []
  (let [fb-count  (mongo/count :filebank
                               {:files.storageSystem "s3"})
        filebanks (mongo/select :filebank
                                {:files.storageSystem "s3"}
                                [:_id]
                                {:_id 1})]
    (timbre/info "Moving" fb-count "filebanks' files from Ceph to GCS")
    (->> filebanks
         (map-indexed (fn [ix {:keys [id]}]
                        (threads/submit thread-pool (move-filebank-files (inc ix) fb-count id))))
         vec
         threads/wait-for-threads)
    (timbre/info "Filebank files moved")))

(defn- fix-bulletin-storage-system [bulletin-id]
  (try
    (doseq [version (:versions (mongo/by-id :application-bulletins bulletin-id [:versions]))
            :let [version-id     (:id version)
                  application-id (:application-id version)]
            [idx {{:keys [storageSystem fileId]} :latestVersion att-id :id}] (map-indexed vector (:attachments version))
            :when (and (= (keyword storageSystem) :s3)
                       (some? fileId)
                       (not (.isInterrupted (Thread/currentThread))))]
      (if (or (object-storage/object-exists? @*gcs object-storage/application-bucket (storage/actual-object-id application-id fileId))
              (try
                (move-file-to-operative-gcs object-storage/application-bucket (storage/actual-object-id application-id fileId))
                true
                (catch Throwable _
                  false)))
        ; Bulletin files are the original attachments from application, so we just update the storage system
        (do (mongo/update :application-bulletins
                          {:_id      bulletin-id
                           :versions {$elemMatch {:id version-id}}}
                          {$set {(str "versions.$.attachments." idx ".latestVersion.storageSystem") :gcs}})
            (timbre/info "Updated bulletin" bulletin-id "version" version-id "attachment" idx "storage system to GCS"))
        (timbre/error "File" fileId "not found in bulletin" bulletin-id "attachment" att-id)))
    (catch Throwable t
      (timbre/error t "Could not update storage for bulletin" bulletin-id))))

(defn update-bulletin-attachments-storage-system []
  (let [bulletin-count (mongo/count :application-bulletins
                                    {:versions.attachments.latestVersion.storageSystem :s3})
        bulletins      (mongo/select :application-bulletins
                                     {:versions.attachments.latestVersion.storageSystem :s3}
                                     [:_id])]
    (timbre/info "Updating" bulletin-count "bulletins storage system from Ceph to GCS")
    (->> bulletins
         (mapv (fn [{:keys [id]}]
                 (threads/submit thread-pool (fix-bulletin-storage-system id))))
         threads/wait-for-threads)
    (timbre/info "Bulletin attachments updated")))

(defn- move-sign-process-files []
  (timbre/info "Moving sign-process files from Ceph to GCS")
  (->> (mongo/select :sign-processes {} [:_id])
       (mapv (fn [{:keys [id]}]
               ; sign-process files have no storageSystem reference in Mongo, they just use the current default system
               (threads/submit thread-pool
                               (try
                                 (move-file-to-operative-gcs object-storage/process-bucket id)
                                 (catch Throwable t
                                   (timbre/error t "Could not move sign-process" id))))))
       threads/wait-for-threads)
  (timbre/info "Sign-process files moved"))

(defn move-all-files []
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread.
                      ^Runnable
                      (fn []
                        (println "Interrupt received, shutting down.")
                        (.shutdownNow thread-pool)
                        (.awaitTermination thread-pool 60 TimeUnit/SECONDS)
                        (println "Threads finished"))))
  (timbre/info "Moving all files from Ceph to GCS")
  (move-all-filebank-files)
  (move-sign-process-files)
  (move-applications)
  (update-bulletin-attachments-storage-system)
  (move-all-user-files)
  (move-all-bulletin-comments-files)
  (timbre/info "All files moved from Ceph to GCS"))


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
                             (object-storage/download @*s3 bucket object-key)
                             (object-storage/download @*gcs bucket object-key))]
        (let [{:keys [content contentType filename metadata]} file-data]
          (timbre/info "Copying" application-id "/" att-id "/" file-id "to" storageSystem)
          (object-storage/put-file-or-input-stream (storage/get-storage-impl storageSystem)
                                                   bucket
                                                   object-key
                                                   filename
                                                   contentType
                                                   (content)
                                                   metadata))
        (timbre/error "Could not rescue" application-id "/" att-id "/" file-id "from any storage system")))
      (catch Throwable t
        (timbre/error t "Could not move files for application" application-id))))

(defn rescue-files-from-wrong-storage [modified-since]
  {:pre [(integer? modified-since)]}
  (let [apps (mongo/select :applications
                           {$or [{:modified {$gte modified-since}}
                                 {:attachments.latestVersion.modified {$gte modified-since}}]}
                           [:_id]
                           {:modified 1})]
    (timbre/info "Rescuing orphaned application attachment files")
    (->> apps
         (mapv (fn [{:keys [id]}]
                 (threads/submit thread-pool (fix-application-attachments id))))
         threads/wait-for-threads)
    (timbre/info "Application attachments rescued")))
