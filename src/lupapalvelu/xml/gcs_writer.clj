(ns lupapalvelu.xml.gcs-writer
  (:require [babashka.fs :as fs]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.storage.gcs :as gcs]
            [lupapalvelu.storage.object-storage :as object-storage]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [taoensso.timbre :as timbre])
  (:import [com.google.cloud.storage BlobInfo Storage$BlobTargetOption
            BlobId Storage$BlobListOption]))

(defn write-attachments [application-id attachments output-dir]
  (storage/copy-attachments-to-sftp application-id output-dir attachments))

(defn write-file
  "Writes `input` (string, input stream or file) to `filepath`."
  [filepath input]
  {:pre [(fs/relative? filepath)
         (ss/not-blank? (fs/file-name filepath))
         (not (ss/ends-with filepath "/")) ; Directory naming convention.
         ]}
  (let [upload (partial storage/upload-sftp-file
                        (str (fs/parent filepath))
                        (fs/file-name filepath)
                        (mime/mime-type filepath))]
    (if (string? input)
      (with-open [stream (ss/->inputstream input)]
        (upload stream))
      (upload input))))

(defn krysp-xml-files
  "Returns list of XML files in storage SFTP bucket matching the given application."
  [application-id output-dir]
  (->> (str output-dir "/" application-id)
       storage/list-sftp-files
       (filter #(-> % :contentType #{"text/xml" "application/xml"}))))

(defn cleanup-output-dir
  "Removes the old KRYSP message files from the output path in the SFTP bucket.
   Only the messages / XMLs for which `should-remove?` returns true are removed."
  [application-id output-dir should-remove?]
  (doseq [{:keys [content] :as file} (krysp-xml-files application-id output-dir)
          :when (should-remove? (content))]
    (timbre/debugf "Removing old version of %s from SFTP directory" (:fileId file))
    (storage/delete-sftp-file output-dir (:fileId file))))

(defn file-exists? [filepath]
  {:pre [(fs/relative? filepath)]}
  (object-storage/object-exists? (storage/default-storage)
                                 object-storage/sftp-bucket
                                 filepath))

(defn get-file
  [filepath]
  {:pre [(fs/relative? filepath)]}
  (object-storage/download (storage/default-storage)
                           object-storage/sftp-bucket
                           filepath))

(defn move-file
  "Moves file in relative `filepath` to (relative) `directory`. Throws on failure."
  [filepath directory]
  {:pre [(fs/relative? filepath) (fs/relative? directory)]}
  (object-storage/move-file-object (storage/default-storage)
                                   object-storage/sftp-bucket
                                   object-storage/sftp-bucket
                                   filepath
                                   (ss/join-file-path directory (fs/file-name filepath))))


(defn directorify
  "Returns `object-key` in the directory path format: no extra slashes, ends in slash."
  [object-key]
  {:pre [(ss/not-blank? object-key)]}
  (ss/replace (str (ss/trim object-key) "/") #"//+" "/"))

(defn create-directory
  "Creates `directory`. If `create-all?` is true (default false) also creates the every
  'parent' within the path. In GCS, each _actual_ directory (or rather folder) is an empty
  blob. Adds / to path if needed. Does not overwrite existing blob. Returns `Blob` (either
  existing or created)."
  ([directory create-all?]
   {:pre [(fs/relative? directory)]}
   (if-not create-all?
     (create-directory directory)
     (->> (ss/split directory #"/+")
          (reduce (fn [{:keys [path]} part]
                    (let [path (ss/join-file-path path part)]
                      {:path path
                       :blob (create-directory path)}))
                  {})
          :blob)))
  ([directory]
   {:pre [(fs/relative? directory)]}
   (let [object-key (directorify directory)
         blob-id    (BlobId/of (gcs/actual-bucket-name object-storage/sftp-bucket)
                               object-key)]
    (or (.get gcs/storage blob-id)
        (let [blob-info (-> (BlobInfo/newBuilder blob-id)
                            (.setContentType "text/plain")
                            (.build))]
          (.create gcs/storage
                   blob-info
                   (into-array Storage$BlobTargetOption
                               [(Storage$BlobTargetOption/doesNotExist)])))))))

(defn list-files
  "Immediate directory contents (subdirs and their contents are excluded). Optional
  `fn-or-regex` filter is applied to the results. Function filter is called with blob and
  regex matched against file base name. Returns file-data-maps."
  ([directory fn-or-regex]
   {:pre [(fs/relative? directory)]}
   (let [object-key (directorify directory)
         path-n     (count (ss/split object-key #"/"))]
     (->> (.list gcs/storage
                 (gcs/actual-bucket-name object-storage/sftp-bucket)
                 (into-array Storage$BlobListOption [(Storage$BlobListOption/prefix object-key)]))
          (.iterateAll)
          (filter (fn [blob]
                    (let [blob-name (.getName (.getBlobId blob))
                          parts     (ss/split blob-name #"/")]
                      (cond
                        (not= (count parts) (inc path-n))
                        false

                        (ss/ends-with blob-name "/")
                        false

                        (fn? fn-or-regex)
                        (fn-or-regex blob)

                        (instance? java.util.regex.Pattern fn-or-regex)
                        (re-matches fn-or-regex (last parts))

                        :else true))))
          (map gcs/blob->file-data-map))))
  ([directory]
   (list-files directory nil)))
