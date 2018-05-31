(ns lupapalvelu.storage.file-storage
  (:require [lupapalvelu.storage.s3 :as s3]
            [lupapalvelu.mongo :as mongo]
            [sade.env :as env]
            [sade.strings :as ss]
            [monger.operators :refer :all]
            [sade.util :as util])
  (:import [java.io ByteArrayInputStream]
           [java.time ZonedDateTime ZoneId]
           [java.util Date]))

;; UPLOAD

(def process-bucket "sign-process")
(def application-bucket "application-files")
(def user-bucket "user-files")
(def unlinked-bucket s3/unlinked-bucket)
(def bulletin-bucket "bulletin-files")

(defn bucket-name [{:keys [application user-id sessionId]}]
  (cond
    application application-bucket
    user-id user-bucket
    sessionId unlinked-bucket))

(defn s3-id [metadata-or-id file-id & [preview?]]
  (if (map? metadata-or-id)
    (let [{:keys [application user-id sessionId]} metadata-or-id]
      (str (or application user-id sessionId) "/" file-id (when preview? "-preview")))
    (str metadata-or-id "/" file-id (when preview? "-preview"))))

(defn upload [file-id filename content-type content metadata]
  {:pre [(map? metadata)]}
  (if (env/feature? :s3)
    (s3/put-file-or-input-stream (bucket-name metadata) (s3-id metadata file-id) filename content-type content metadata)
    (mongo/upload file-id filename content-type content metadata)))

(defn upload-process-file [process-id filename content-type ^ByteArrayInputStream is metadata]
  {:pre [(map? metadata)]}
  (if (env/feature? :s3)
    (s3/put-input-stream process-bucket process-id filename content-type is (.available is) metadata)
    (mongo/upload process-id filename content-type is metadata)))

;; DOWNLOAD

(defn- find-by-file-id [id {:keys [versions]}]
  (->> versions
       (filter #((set ((juxt :fileId :originalFileId) %)) id))
       first))

(defn- find-by-file-id-from-attachments [id attachments]
  (some
    #(find-by-file-id id %)
    attachments))

(defn ^{:perfmon-exclude true} download
  "Downloads file from Mongo GridFS or S3"
  ([file-id]
   (if (env/feature? :s3)
     (s3/download nil file-id)
     (mongo/download-find {:_id file-id})))
  ([application file-id]
   (let [{:keys [storageSystem]} (find-by-file-id-from-attachments file-id (:attachments application))]
     (if (and (env/feature? :s3) (= (keyword storageSystem) :s3))
       (s3/download application-bucket (s3-id (:id application) file-id) (:id application))
       (mongo/download-find {:_id file-id}))))
  ([application-id file-id attachment]
   (let [{:keys [storageSystem]} (find-by-file-id file-id attachment)]
     (if (and (env/feature? :s3) (= (keyword storageSystem) :s3))
       (s3/download application-bucket (s3-id application-id file-id) application-id)
       (mongo/download-find {:_id file-id})))))

(defn download-many
  "Downloads multiple files from Mongo GridFS or S3"
  [application file-ids]
  (if (env/feature? :s3)
    (pmap #(download application %) file-ids)
    (mongo/download-find-many {:_id {$in file-ids}})))

(defn ^{:perfmon-exclude true} download-from-system
  [application-id file-id storage-system]
  (if (= (keyword storage-system) :s3)
    (s3/download application-bucket (s3-id application-id file-id) application-id)
    (mongo/download-find {:_id file-id})))

(defn- find-user-attachment-storage-system [user-id file-id]
  (->> (mongo/select-one :users
                         {:_id                        user-id
                          :attachments.attachment-id file-id}
                         [:attachments.$])
       :attachments
       first
       :storageSystem))

(defn ^{:perfmon-exclude true} download-user-attachment
  "Downloads user attachment file from Mongo GridFS or S3"
  ([user-id file-id]
   (->> (find-user-attachment-storage-system user-id file-id)
        (download-user-attachment user-id file-id)))
  ([user-id file-id storage-system]
   (if (and (env/feature? :s3) (= (keyword storage-system) :s3))
     (s3/download user-bucket (s3-id user-id file-id))
     (mongo/download-find {:_id file-id :metadata.user-id user-id}))))

(defn ^{:perfmon-exclude true} download-preview
  "Downloads preview file from Mongo GridFS or S3"
  [application-id file-id attachment]
  (let [{:keys [storageSystem]} (find-by-file-id file-id attachment)]
    (if (and (env/feature? :s3) (= (keyword storageSystem) :s3))
      (s3/download application-bucket (s3-id application-id file-id :preview) application-id)
      (mongo/download-find {:_id (str file-id "-preview")}))))

(defn ^{:perfmon-exclude true} download-session-file
  [session-id file-id]
  (if (env/feature? :s3)
    (s3/download unlinked-bucket (s3-id session-id file-id))
    (mongo/download-find {:_id file-id :metadata.sessionId session-id})))

(defn ^{:perfmon-exclude true} download-process-file
  [process-id]
  (if (env/feature? :s3)
    (s3/download process-bucket process-id)
    (mongo/download-find {:_id process-id})))

(defn ^{:perfmon-exclude true} download-bulletin-comment-file
  [bulletin-id file-id storage-system]
  (if (and (env/feature? :s3) (= (keyword storage-system) :s3))
    (s3/download bulletin-bucket (s3-id bulletin-id file-id))
    (mongo/download-find {:_id file-id :metadata.bulletinId bulletin-id})))

;; LINK

(defn link-files-to-application [session-id app-id file-ids]
  {:pre [(seq file-ids) (not-any? ss/blank? (conj file-ids app-id))]}
  (if (env/feature? :s3)
    (doseq [file-id file-ids]
      (s3/move-file-object unlinked-bucket application-bucket (s3-id session-id file-id) (s3-id app-id file-id)))
    (mongo/update-by-query :fs.files {:_id {$in file-ids}} {$set {:metadata.application app-id
                                                                  :metadata.linked true}})))

(defn link-files-to-bulletin [session-id bulletin-id file-ids]
  {:pre [(seq file-ids) (not-any? ss/blank? (conj file-ids bulletin-id))]}
  (if (env/feature? :s3)
    (doseq [file-id file-ids]
      (s3/move-file-object unlinked-bucket bulletin-bucket (s3-id session-id file-id) (s3-id bulletin-id file-id)))
    (mongo/update-by-query :fs.files {:_id {$in file-ids}} {$set {:metadata.bulletinId bulletin-id
                                                                  :metadata.linked true}})))

;; EXISTS

(defn session-files-exist? [session-id file-ids]
  {:pre [(sequential? file-ids) (not-any? ss/blank? (conj file-ids session-id))]}
  (->> (if (env/feature? :s3)
         (map #(s3/object-exists? unlinked-bucket (s3-id session-id %)) file-ids)
         (map #(mongo/any? :fs.files {:_id % :metadata.sessionId session-id}) file-ids))
       (every? true?)))

(defn application-file-exists? [application-id file-id]
  (if (env/feature? :s3)
    (s3/object-exists? application-bucket (s3-id application-id file-id))
    (seq (mongo/file-metadata {:id file-id}))))

;; DELETE

(defn delete [application file-id]
  (let [{:keys [storageSystem]} (find-by-file-id-from-attachments file-id (:attachments application))]
    (if (and (env/feature? :s3) (= (keyword storageSystem) :s3))
      (do (s3/delete application-bucket (s3-id (:id application) file-id))
          (s3/delete application-bucket (s3-id (:id application) file-id :preview)))
      (do (mongo/delete-file-by-id file-id)
          (mongo/delete-file-by-id (str file-id "-preview"))))))

(defn delete-session-file
  "Deletes a file uploaded to temporary storage with session id.
   Guarantees that the file is not linked to anything at the time of deletion."
  [session-id file-id]
  (if (env/feature? :s3)
    (s3/delete unlinked-bucket (s3-id session-id file-id))
    (mongo/delete-file {:_id file-id
                        :metadata.sessionId session-id
                        :metadata.application {$exists false}
                        :metadata.bulletinId {$exists false}
                        :linked false})))

(defn ^{:perfmon-exclude true} delete-user-attachment
  ([user-id file-id]
   (->> (find-user-attachment-storage-system user-id file-id)
        (delete-user-attachment user-id file-id)))
  ([user-id file-id storage-system]
   (if (and (env/feature? :s3) (= (keyword storage-system) :s3))
     (s3/delete user-bucket (s3-id user-id file-id))
     (mongo/delete-file {:id file-id :metadata.user-id user-id}))))

(defn ^{:perfmon-exclude true} delete-process-file
  [process-id]
  (if (env/feature? :s3)
    (s3/delete process-bucket process-id)
    (mongo/delete-file {:_id process-id})))

(defn delete-from-any-system [application-id file-id]
  (when-not @mongo/connection
    (mongo/connect!))
  (mongo/delete-file-by-id file-id)
  (when (env/feature? :s3)
    (s3/delete application-bucket (s3-id application-id file-id))))

(defn- ts-two-hours-ago []
  ; Matches vetuma session TTL
  (util/get-timestamp-ago :hour 2))

(defn- date-two-hours-ago []
  (-> (ZoneId/of "Europe/Helsinki")
      (ZonedDateTime/now)
      (.minusHours 2)
      (.toInstant)
      (Date/from)))

(defn delete-old-unlinked-files []
  (if (env/feature? :s3)
    (s3/delete-unlinked-files (date-two-hours-ago))
    (do
      (when-not @mongo/connection
        (mongo/connect!))
      (mongo/delete-file {$and [{:metadata.linked {$exists true}}
                                {:metadata.linked false}
                                {:metadata.uploaded {$lt (ts-two-hours-ago)}}]}))))
