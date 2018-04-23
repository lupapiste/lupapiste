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
       (filter (fn [{:keys [fileId originalFileId]}]
                 (#{fileId originalFileId} id)))
       first))

(defn ^{:perfmon-exclude true} download
  "Downloads file from Mongo GridFS or S3"
  ([file-id]
   (if (env/feature? :s3)
     (s3/download nil file-id)
     (mongo/download-find {:_id file-id})))
  ([application-id file-id attachment]
   (let [{:keys [storageSystem]} (find-by-file-id file-id attachment)]
     (if (and (env/feature? :s3) (= (keyword storageSystem) :s3))
       (s3/download application-id file-id)
       (mongo/download-find {:_id file-id})))))

(defn link-files-to-application [app-id fileIds]
  {:pre [(seq fileIds) (not-any? ss/blank? (conj fileIds app-id))]}
  (if (env/feature? :s3)
    (doseq [file-id fileIds]
      (s3/link-file-object-to-application app-id file-id))
    (mongo/update-by-query :fs.files {:_id {$in fileIds}} {$set {:metadata.application app-id
                                                                 :metadata.linked true}})))
