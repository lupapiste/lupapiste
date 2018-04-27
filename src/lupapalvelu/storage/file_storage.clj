(ns lupapalvelu.storage.file-storage
  (:require [lupapalvelu.storage.s3 :as s3]
            [lupapalvelu.mongo :as mongo]
            [sade.env :as env]
            [sade.strings :as ss]
            [monger.operators :refer :all])
  (:import [java.io ByteArrayInputStream]))

;; UPLOAD

(defn upload [file-id filename content-type content {:keys [application user-id] :as metadata}]
  {:pre [(map? metadata)]}
  (if (env/feature? :s3)
    (s3/put-file-or-input-stream (or application user-id) file-id filename content-type content metadata)
    (mongo/upload file-id filename content-type content metadata)))

(def process-bucket "sign-process")

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
       (s3/download (:id application) file-id)
       (mongo/download-find {:_id file-id}))))
  ([application-id file-id attachment]
   (let [{:keys [storageSystem]} (find-by-file-id file-id attachment)]
     (if (and (env/feature? :s3) (= (keyword storageSystem) :s3))
       (s3/download application-id file-id)
       (mongo/download-find {:_id file-id})))))

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
     (s3/download user-id file-id)
     (mongo/download-find {:_id file-id :metadata.user-id user-id}))))

(defn ^{:perfmon-exclude true} download-preview
  "Downloads preview file from Mongo GridFS or S3"
  [application-id file-id attachment]
  (let [{:keys [storageSystem]} (find-by-file-id file-id attachment)]
    (if (and (env/feature? :s3) (= (keyword storageSystem) :s3))
      (s3/download application-id (str file-id "-preview"))
      (mongo/download-find {:_id (str file-id "-preview")}))))

(defn ^{:perfmon-exclude true} download-session-file
  [session-id file-id]
  (if (env/feature? :s3)
    (s3/download session-id file-id)
    (mongo/download-find {:_id file-id :metadata.sessionId session-id})))

(defn ^{:perfmon-exclude true} download-process-file
  [process-id]
  (if (env/feature? :s3)
    (s3/download process-bucket process-id)
    (mongo/download-find {:_id process-id})))

;; LINK

(defn link-files-to-application [app-id fileIds]
  {:pre [(seq fileIds) (not-any? ss/blank? (conj fileIds app-id))]}
  (if (env/feature? :s3)
    (doseq [file-id fileIds]
      (s3/link-file-object-to-application app-id file-id))
    (mongo/update-by-query :fs.files {:_id {$in fileIds}} {$set {:metadata.application app-id
                                                                 :metadata.linked true}})))

;; DELETE

(defn ^{:perfmon-exclude true} delete-user-attachment
  "Downloads user attachment file from Mongo GridFS or S3"
  ([user-id file-id]
   (->> (find-user-attachment-storage-system user-id file-id)
        (delete-user-attachment user-id file-id)))
  ([user-id file-id storage-system]
   (if (and (env/feature? :s3) (= (keyword storage-system) :s3))
     (s3/delete user-id file-id)
     (mongo/delete-file {:id file-id :metadata.user-id user-id}))))

(defn ^{:perfmon-exclude true} delete-process-file
  [process-id]
  (if (env/feature? :s3)
    (s3/delete process-bucket process-id)
    (mongo/delete-file {:_id process-id})))
