(ns lupapalvelu.storage.file-storage
  (:require [lupapalvelu.logging :refer [infof]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.gcs :as gcs]
            [lupapalvelu.storage.gridfs :as gfs]
            [lupapalvelu.storage.object-storage :as object-storage]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.env :as env]
            [sade.strings :as ss])
  (:import [java.io ByteArrayInputStream]
           [java.time ZonedDateTime]
           [java.util Date]))

(set! *warn-on-reflection* true)

(defn default-storage-system-id []
  (cond
    (env/feature? :gcs) :gcs
    :else :mongodb))

(def object-storage-impls
  {:gcs (when (env/feature? :gcs) (gcs/->GCStorage))})

(defn default-storage []
  (get object-storage-impls (default-storage-system-id)))

(defn get-storage-impl [storage-system]
  (get object-storage-impls (keyword storage-system)))

;; UPLOAD

(defn bucket-name [{:keys [application user-id uploader-user-id sessionId]}]
  (cond
    application object-storage/application-bucket
    user-id object-storage/user-bucket
    uploader-user-id object-storage/unlinked-bucket
    sessionId object-storage/unlinked-bucket))

(defn actual-object-id [metadata-or-id file-id & [preview?]]
  (if (map? metadata-or-id)
    (let [{:keys [application user-id uploader-user-id sessionId]} metadata-or-id
          context-id (or application user-id uploader-user-id sessionId)]
      (if (and (string? context-id) (>= (count context-id) 5))
        (str context-id "/" file-id (when preview? "-preview"))
        (throw (ex-info "The metadata map for upload must contain either application, user-id, uploader-user-id or sessionId"
                        metadata-or-id))))
    (str metadata-or-id "/" file-id (when preview? "-preview"))))

(defn upload
  ([file-id filename content-type content metadata]
   (upload file-id filename content-type content metadata (default-storage-system-id)))
  ([file-id filename content-type content metadata storage-system-id]
   (upload file-id filename content-type content metadata storage-system-id (bucket-name metadata)))
  ([file-id filename content-type content metadata storage-system-id bucket-name]
   {:pre [(map? metadata)]}
   (if-let [storage (get-storage-impl (or storage-system-id (default-storage-system-id)))]
     (object-storage/put-file-or-input-stream storage
                                              bucket-name
                                              (actual-object-id metadata file-id)
                                              filename
                                              content-type
                                              content
                                              metadata)
     (gfs/upload file-id filename content-type content metadata))))

(defn upload-signed-company-contract [process-id filename content-type file metadata]
  {:pre [(map? metadata)]}
  (if-let [storage (default-storage)]
    (object-storage/put-file-or-input-stream storage
                                             object-storage/company-contracts
                                             process-id
                                             filename
                                             content-type
                                             file
                                             metadata)
    (gfs/upload process-id filename content-type file metadata)))

(defn upload-process-file [process-id filename content-type ^ByteArrayInputStream is metadata]
  {:pre [(map? metadata)]}
  (if-let [storage (default-storage)]
    (object-storage/put-input-stream storage
                                     object-storage/process-bucket
                                     process-id
                                     filename
                                     content-type
                                     is
                                     (.available is)
                                     metadata)
    (gfs/upload process-id filename content-type is metadata)))

(defn upload-sftp-file [output-dir filename content-type file-or-is]
  (when-let [storage (default-storage)]
    (object-storage/put-file-or-input-stream storage
                                             object-storage/sftp-bucket
                                             (str output-dir "/" filename)
                                             filename
                                             content-type
                                             file-or-is
                                             {})))

(defn upload-admin-file [bucket file-id filename content-type content]
  (if-let [storage (default-storage)]
    (object-storage/put-file-or-input-stream storage
                                             bucket
                                             file-id
                                             filename
                                             content-type
                                             content
                                             nil)
    (gfs/upload file-id filename content-type content nil)))

(defn upload-import-data [file-id filename content-type file-or-is]
  (when-let [storage (default-storage)]
    (object-storage/put-file-or-input-stream storage
                                             object-storage/import-bucket
                                             file-id
                                             filename
                                             content-type
                                             file-or-is
                                             nil)))

;; DOWNLOAD

(defn find-by-file-id [id {:keys [versions]}]
  (->> versions
       (filter #((set ((juxt :file-version-id :fileId :originalFileId) %)) id))
       first))

(defn- find-by-file-id-from-attachments [id attachments]
  (some
    #(find-by-file-id id %)
    attachments))

(defn download-with-user-id
  "Downloads file from Mongo GridFS or GCS with user-id prefix"
  [user-id file-id]
  (if-let [storage (default-storage)]
    (object-storage/download storage nil (actual-object-id user-id file-id))
    (gfs/download-find {:_id file-id})))

(defn ^{:perfmon-exclude true} download
  "Downloads file from Mongo GridFS or GCS"
  ([file-id]
   (if-let [storage (default-storage)]
     (object-storage/download storage nil file-id)
     (gfs/download-find {:_id file-id})))
  ([application file-id]
   {:pre [(map? application) (string? file-id)]}
   (let [{:keys [storageSystem]} (find-by-file-id-from-attachments file-id (:attachments application))]
     (if-let [storage (get-storage-impl storageSystem)]
       (object-storage/download storage object-storage/application-bucket (actual-object-id (:id application) file-id))
       (gfs/download-find {:_id file-id}))))
  ([application-id file-id attachment]
   {:pre [(string? application-id) (string? file-id) (map? attachment)]}
   (let [version (find-by-file-id file-id attachment)
         storageSystem (or (:storageSystem version) (:storage-system version))]
     (if-let [storage (get-storage-impl storageSystem)]
       (object-storage/download storage object-storage/application-bucket (actual-object-id application-id file-id))
       (gfs/download-find {:_id file-id})))))

(defn download-many
  "Downloads multiple files from Mongo GridFS or GCS"
  [application file-ids]
  (pmap #(download application %) file-ids))

(defn linked-file-latest-version [linked-file]
  (->> (get-in linked-file [:versions])
       last))

(defn ^{:perfmon-exclude true} download-bim-xkt-file
  "Downloads XKT file connected to specific IFC file"
  [application-id ifc-file-id linked-file-metadata]
  (let [{:keys [storage-system]} (find-by-file-id ifc-file-id linked-file-metadata)
        xkt-file-id (str ifc-file-id "-xkt-file")]
    (if-let [storage (get-storage-impl storage-system)]
      (object-storage/download storage object-storage/application-bucket (actual-object-id application-id xkt-file-id))
      (gfs/download-find {:_id xkt-file-id}))))

(defn download-many-linked-files [application linked-files]
  (letfn [(download-fn [linked-file]
            (let [latest-version (linked-file-latest-version linked-file)
                  file-id (:file-version-id latest-version)
                  ifc-file? (= (get-in latest-version [:metadata :content-type]) "application/x-step")]
              (cond-> [(download (:id application) file-id linked-file)]
                ifc-file? (conj (download-bim-xkt-file (:id application) file-id linked-file)))))]
    (mapcat download-fn linked-files)))

(defn ^{:perfmon-exclude true} download-from-system
  [application-id file-id storage-system]
  (if-let [storage (get-storage-impl storage-system)]
    (object-storage/download storage object-storage/application-bucket (actual-object-id application-id file-id))
    (gfs/download-find {:_id file-id})))

(defn- find-user-attachment-storage-system [user-id file-id]
  (->> (mongo/select-one :users
                         {:_id                       user-id
                          :attachments.attachment-id file-id}
                         [:attachments.$])
       :attachments
       first
       :storageSystem))

(defn ^{:perfmon-exclude true} download-user-attachment
  "Downloads user attachment file from Mongo GridFS or GCS"
  ([user-id file-id]
   (->> (find-user-attachment-storage-system user-id file-id)
        (download-user-attachment user-id file-id)))
  ([user-id file-id storage-system]
   (if-let [storage (get-storage-impl storage-system)]
     (object-storage/download storage object-storage/user-bucket (actual-object-id user-id file-id))
     (gfs/download-find {:_id file-id :metadata.user-id user-id}))))

(defn ^{:perfmon-exclude true} download-preview
  "Downloads preview file from Mongo GridFS or GCS"
  [application-id file-id attachment]
  (let [{:keys [storageSystem]} (find-by-file-id file-id attachment)]
    (if-let [storage (get-storage-impl storageSystem)]
      (object-storage/download storage object-storage/application-bucket (actual-object-id application-id file-id :preview))
      (gfs/download-find {:_id (str file-id "-preview")}))))

(defn ^{:perfmon-exclude true} download-unlinked-file
  [user-or-session-id file-id]
  (if-let [storage (default-storage)]
    (object-storage/download storage object-storage/unlinked-bucket (actual-object-id user-or-session-id file-id))
    (gfs/download-find {$and [{:_id file-id}
                              {$or [{:metadata.linked false}
                                    {:metadata.linked {$exists false}}]}
                              {$or [{:metadata.sessionId user-or-session-id}
                                    {:metadata.uploader-user-id user-or-session-id}]}]})))

(defn ^{:perfmon-exclude true} download-contract
  [company-id]
  (if-let [storage (default-storage)]
    (object-storage/download storage object-storage/company-contracts company-id)
    (gfs/download-find {:_id company-id})))

(defn ^{:perfmon-exclude true} download-process-file
  [process-id]
  (if-let [storage (default-storage)]
    (object-storage/download storage object-storage/process-bucket process-id)
    (gfs/download-find {:_id process-id})))

(defn ^{:perfmon-exclude true} download-bulletin-comment-file
  [bulletin-id file-id storage-system]
  (let [dl (if-let [storage (get-storage-impl storage-system)]
             (object-storage/download storage object-storage/bulletin-bucket (actual-object-id bulletin-id file-id))
             (gfs/download-find {:_id file-id :metadata.bulletinId bulletin-id}))]
    (-> (assoc dl :bulletin bulletin-id)
        (dissoc :application))))

(defn ^{:perfmon-exclude true} download-import-data
  [file-id]
  (if-let [storage (default-storage)]
    (object-storage/download storage object-storage/import-bucket file-id)
    (gfs/download-find {:_id file-id})))

;; LIST

(defn list-sftp-files [prefix]
  (when-let [storage (default-storage)]
    (object-storage/list-file-objects storage
                                      object-storage/sftp-bucket
                                      prefix)))

;; LINK

(defn link-files-to-application
  ([user-or-session-id app-id file-ids]
   (link-files-to-application user-or-session-id app-id file-ids (default-storage-system-id)))
  ([user-or-session-id app-id file-ids storage-system-id]
   {:pre [(seq file-ids) (not-any? ss/blank? (conj file-ids app-id user-or-session-id))]}
   (if-let [storage (get-storage-impl (or storage-system-id (default-storage-system-id)))]
     (do (doseq [file-id file-ids]
           (object-storage/move-file-object storage
                                            object-storage/unlinked-bucket
                                            object-storage/application-bucket
                                            (actual-object-id user-or-session-id file-id)
                                            (actual-object-id app-id file-id)))
         (count file-ids))
     (mongo/update-by-query :fs.files
                            {$and [{:_id {$in file-ids}}
                                   {$or [{:metadata.linked false}
                                         {:metadata.linked {$exists false}}]}
                                   {$or [{:metadata.sessionId user-or-session-id}
                                         {:metadata.uploader-user-id user-or-session-id}]}]}
                            {$set {:metadata.application app-id
                                   :metadata.linked      true}}))))

(defn link-files-to-bulletin [user-or-session-id bulletin-id file-ids]
  {:pre [(seq file-ids) (not-any? ss/blank? (conj file-ids bulletin-id))]}
  (if-let [storage (default-storage)]
    (do (doseq [file-id file-ids]
          (object-storage/move-file-object storage
                                           object-storage/unlinked-bucket
                                           object-storage/bulletin-bucket
                                           (actual-object-id user-or-session-id file-id)
                                           (actual-object-id bulletin-id file-id)))
        (count file-ids))
    (mongo/update-by-query :fs.files
                           {:_id                {$in file-ids}
                            :metadata.sessionId user-or-session-id}
                           {$set {:metadata.bulletinId bulletin-id
                                  :metadata.linked     true}})))

;; COPY

(defn copy-attachments-to-sftp [application-id output-dir attachments]
  (when-let [storage (get-storage-impl :gcs)]
    (doseq [attachment attachments]
      (when-let [filename (:filename attachment)]
        (let [file-id          (:fileId attachment)
              target-object-id (str output-dir "/" filename)]
          (object-storage/copy-file-object storage
                                           object-storage/application-bucket
                                           object-storage/sftp-bucket
                                           (actual-object-id application-id file-id)
                                           target-object-id))))))

;; EXISTS

(defn unlinked-file-exists? [user-or-session-id file-id]
  {:pre [(string? file-id) (not-any? ss/blank? [file-id user-or-session-id])]}
  (->> (if-let [storage (default-storage)]
         (object-storage/object-exists? storage
                                        object-storage/unlinked-bucket
                                        (actual-object-id user-or-session-id file-id))
         (mongo/any? :fs.files
                     {$and [{:_id file-id}
                            {$or [{:metadata.linked false}
                                  {:metadata.linked {$exists false}}]}
                            {:metadata.bulletinId {$exists false}}
                            {$or [{:metadata.sessionId user-or-session-id}
                                  {:metadata.uploader-user-id user-or-session-id}]}]}))))

(defn file-exists-in?
  ([bucket-name file-path]
   (if-let [storage (get-storage-impl (default-storage-system-id))]
     (object-storage/object-exists? storage bucket-name file-path)
     (throw (Exception. "file-exists-in? not for mongo storage")))))

(defn move-unlinked-file-to
  "Move unlinked file to target bucket. If unlinked file does not exist check if the file is already moved."
  ([to-bucket-name uid-or-session-id target-bucket-dir file-id]
   (if-let [storage (get-storage-impl (default-storage-system-id))]
     (let [target-file-path (actual-object-id target-bucket-dir file-id)
           is-unlinked? (unlinked-file-exists? uid-or-session-id file-id)
           is-moved-already? (or (not is-unlinked?) (file-exists-in? to-bucket-name target-file-path))]
       (cond
         is-unlinked? (do (object-storage/move-file-object storage
                                                           object-storage/unlinked-bucket
                                                           to-bucket-name
                                                           (actual-object-id uid-or-session-id file-id)
                                                           target-file-path)
                          {:result    :did-move
                           :bucket    to-bucket-name
                           :file-path target-file-path})
         is-moved-already? {:result   :already-moved
                            :bucket   to-bucket-name
                            :file-path target-file-path}
         :else {:result :file-not-found}))
     (throw (Exception. "move-unlinked-file-to is not suppored for mongo storage")))))


(defn application-file-exists?
  ([application-id file-id]
   (application-file-exists? (default-storage-system-id) application-id file-id))
  ([storage-system-id application-id file-id]
   (if-let [storage (get-storage-impl (or storage-system-id (default-storage-system-id)))]
     (object-storage/object-exists? storage object-storage/application-bucket (actual-object-id application-id file-id))
     (map? (gfs/file-metadata {:id file-id})))))

(defn user-attachment-exists?
  ([user-id file-id] (user-attachment-exists? user-id file-id (find-user-attachment-storage-system user-id file-id)))
  ([user-id file-id storage-system]
   (if-let [storage (get-storage-impl storage-system)]
     (object-storage/object-exists? storage object-storage/user-bucket (actual-object-id user-id file-id))
     (map? (gfs/file-metadata {:id file-id})))))

;; DELETE

(defn delete-app-file-from-storage [app-id file-id storage-system]
  (if-let [storage (get-storage-impl storage-system)]
    (do (object-storage/delete storage object-storage/application-bucket (actual-object-id app-id file-id))
        (object-storage/delete storage object-storage/application-bucket (actual-object-id app-id file-id :preview)))
    (do (gfs/delete-file-by-id file-id)
        (gfs/delete-file-by-id (str file-id "-preview")))))

(defn delete [application file-id]
  (let [{:keys [storageSystem]} (find-by-file-id-from-attachments file-id (:attachments application))]
    (delete-app-file-from-storage (:id application) file-id storageSystem)))

(defn delete-unlinked-file
  "Deletes a file uploaded to temporary storage with session id.
   Guarantees that the file is not linked to anything at the time of deletion."
  [user-or-session-id file-id]
  (if-let [storage (default-storage)]
    (object-storage/delete storage object-storage/unlinked-bucket (actual-object-id user-or-session-id file-id))
    (gfs/delete-file {$and [{:_id file-id}
                            {$or [{:metadata.sessionId user-or-session-id}
                                  {:metadata.uploader-user-id user-or-session-id}]}
                            {:metadata.application {$exists false}}
                            {:metadata.bulletinId {$exists false}}
                            {$or [{:metadata.linked false}
                                  {:metadata.linked {$exists false}}]}]})))

(defn ^{:perfmon-exclude true} delete-user-attachment
  ([user-id file-id]
   (->> (find-user-attachment-storage-system user-id file-id)
        (delete-user-attachment user-id file-id)))
  ([user-id file-id storage-system]
   (if-let [storage (get-storage-impl storage-system)]
     (object-storage/delete storage object-storage/user-bucket (actual-object-id user-id file-id))
     (gfs/delete-file {:id file-id :metadata.user-id user-id}))))

(defn ^{:perfmon-exclude true} delete-process-file
  [process-id]
  (if-let [storage (default-storage)]
    (object-storage/delete storage object-storage/process-bucket process-id)
    (gfs/delete-file {:_id process-id})))

(defn delete-from-any-system [application-id file-id]
  (gfs/delete-file-by-id file-id)
  (when-let [storage (default-storage)]
    (object-storage/delete storage object-storage/application-bucket (actual-object-id application-id file-id))))

(defn delete-sftp-file [output-dir filename]
  (when-let [storage (default-storage)]
    (object-storage/delete storage
                           object-storage/sftp-bucket
                           (str output-dir "/" filename))))

(defn delete-admin-file [bucket file-id]
  (if-let [storage (default-storage)]
    (object-storage/delete storage bucket file-id)
    (gfs/delete-file {:_id file-id})))

(defn download-admin-file [bucket file-id]
  (when-let [storage (default-storage)]
    (object-storage/download storage bucket file-id)))

(defn ^{:perfmon-exclude true} delete-import-data
  [file-id]
  (if-let [storage (default-storage)]
    (object-storage/delete storage object-storage/import-bucket file-id)
    (gfs/delete-file-by-id file-id)))

(defn delete-with-user-id
  [user-id file-id]
  (if-let [storage (default-storage)]
    (object-storage/delete storage nil (actual-object-id user-id file-id))
    (gfs/delete-file-by-id file-id)))

(defn- ts-two-hours-ago []
  ;; Matches vetuma session TTL
  (-> (date/now) (.minusHours 2) (date/timestamp)))

(defn- date-two-hours-ago []
  (-> (ZonedDateTime/now)
      (.minusHours 2)
      (.toInstant)
      (Date/from)))

(defn delete-old-unlinked-files []
  (if-let [storage (default-storage)]
    (object-storage/delete-unlinked-files storage (date-two-hours-ago))
    (gfs/delete-file {$and [{$or [{:metadata.linked false}
                                  {:metadata.linked {$exists false}}]}
                            {:metadata.application {$exists false}}
                            {:metadata.uploaded {$lt (ts-two-hours-ago)}}]})))


(defn fix-file-links
  "Re-links every attachment file that was not successfully linked earlier. In other words,
  if the file-id is still unlinked, link again."
  [{application-id :id attachments :attachments}]
  (let [versions (->> attachments
                      (mapcat :versions)
                      (remove :onkaloFileId))]
    (doseq [{:keys [fileId originalFileId
                    user]} versions
            :let           [user-id      (:id user)
                            unlinked-ids (->> (distinct [fileId originalFileId])
                                              (remove nil?)
                                              (remove (partial application-file-exists? application-id))
                                              (filter #(unlinked-file-exists? user-id %))
                                              seq)]
            :when unlinked-ids]
      (infof "Link files %s (again) to %s for user %s" unlinked-ids application-id user-id)
      (link-files-to-application user-id application-id unlinked-ids))))

(defn create-download-link
  "Returns link the file can be downloaded from during the next ttl-seconds"
  [bucket-name file-id ttl-seconds]
  (if-let [storage (get-storage-impl (default-storage-system-id))]
    (object-storage/create-download-link storage bucket-name file-id ttl-seconds)
    (throw (Exception. "create-download-link is not supported for configured storage"))))

(defn get-metadata [bucket-name file-id]
  (if-let [storage (get-storage-impl (default-storage-system-id))]
    (object-storage/get-metadata storage bucket-name file-id)
    (throw (Exception. "get-metadata is not supported for configured storage"))))
