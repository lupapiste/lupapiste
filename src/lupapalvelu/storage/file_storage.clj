(ns lupapalvelu.storage.file-storage
  (:require [lupapalvelu.storage.s3 :as s3]
            [lupapalvelu.mongo :as mongo]
            [sade.env :as env]
            [sade.strings :as ss]
            [monger.operators :refer :all])
  (:import [java.io ByteArrayInputStream]))

;; UPLOAD

(defn session-bucket [id]
  (when id
    (str "unlinked-" id)))

(defn upload [file-id filename content-type content {:keys [application user-id sessionId] :as metadata}]
  {:pre [(map? metadata)]}
  (if (env/feature? :s3)
    (let [bucket (or application user-id (session-bucket sessionId))]
      (s3/put-file-or-input-stream bucket file-id filename content-type content metadata))
    (mongo/upload file-id filename content-type content metadata)))

(def process-bucket "sign-process")

(defn bulletin-bucket [id]
  (str id "-bulletin"))

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

(defn download-many
  "Downloads multiple files from Mongo GridFS or S3"
  [application file-ids]
  (if (env/feature? :s3)
    (pmap #(download application %) file-ids)
    (mongo/download-find-many {:_id {$in file-ids}})))

(defn ^{:perfmon-exclude true} download-from-system
  [application-id file-id storage-system]
  (if (= (keyword storage-system) :s3)
    (s3/download application-id file-id)
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
    (s3/download (session-bucket session-id) file-id)
    (mongo/download-find {:_id file-id :metadata.sessionId session-id})))

(defn ^{:perfmon-exclude true} download-process-file
  [process-id]
  (if (env/feature? :s3)
    (s3/download process-bucket process-id)
    (mongo/download-find {:_id process-id})))

(defn ^{:perfmon-exclude true} download-bulletin-comment-file
  [bulletin-id file-id storage-system]
  (if (and (env/feature? :s3) (= (keyword storage-system) :s3))
    (s3/download (bulletin-bucket bulletin-id) file-id)
    (mongo/download-find {:_id file-id :metadata.bulletinId bulletin-id})))

;; LINK

(defn link-files-to-application [session-id app-id file-ids]
  {:pre [(seq file-ids) (not-any? ss/blank? (conj file-ids app-id))]}
  (if (env/feature? :s3)
    (doseq [file-id file-ids]
      (s3/move-file-object (session-bucket session-id) app-id file-id))
    (mongo/update-by-query :fs.files {:_id {$in file-ids}} {$set {:metadata.application app-id
                                                                  :metadata.linked true}})))

(defn link-files-to-bulletin [session-id bulletin-id file-ids]
  {:pre [(seq file-ids) (not-any? ss/blank? (conj file-ids bulletin-id))]}
  (if (env/feature? :s3)
    (doseq [file-id file-ids]
      (s3/move-file-object (session-bucket session-id) (bulletin-bucket bulletin-id) file-id))
    (mongo/update-by-query :fs.files {:_id {$in file-ids}} {$set {:metadata.bulletinId bulletin-id
                                                                  :metadata.linked true}})))

(defn session-files-exist? [session-id file-ids]
  {:pre [(seq file-ids) (not-any? ss/blank? (conj file-ids session-id))]}
  (->> (if (env/feature? :s3)
         (map #(s3/object-exists? (session-bucket session-id) %) file-ids)
         (map #(mongo/any? :fs.files {:_id % :metadata.sessionId session-id}) file-ids))
       (every? true?)))

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
