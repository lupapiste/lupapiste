(ns lupapalvelu.storage.file-storage
  (:require [lupapalvelu.storage.s3 :as s3]
            [lupapalvelu.mongo :as mongo]
            [sade.env :as env]
            [sade.strings :as ss]
            [monger.operators :refer :all]))

(defn upload [file-id filename content-type content metadata]
  {:pre [(map? metadata)]}
  (if (env/feature? :s3)
    (s3/put-file-or-input-stream (:application metadata) file-id filename content-type content metadata)
    (mongo/upload file-id filename content-type content metadata)))

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

(defn link-files-to-application [app-id fileIds]
  {:pre [(seq fileIds) (not-any? ss/blank? (conj fileIds app-id))]}
  (if (env/feature? :s3)
    (doseq [file-id fileIds]
      (s3/link-file-object-to-application app-id file-id))
    (mongo/update-by-query :fs.files {:_id {$in fileIds}} {$set {:metadata.application app-id
                                                                 :metadata.linked true}})))
