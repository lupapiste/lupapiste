(ns lupapalvelu.sftp-itest-util
  (:require [babashka.fs :as fs]
            [lupapalvelu.storage.gcs :as gcs]
            [lupapalvelu.storage.object-storage :as object-storage]
            [sade.env :as env]
            [sade.strings :as ss])
  (:import [com.google.cloud.storage Storage$BlobListOption Blob$BlobSourceOption]))

(defn gcs-remove-test-folder
  "Removes root `dir` and its every descendant. The directory must be relative and end in
  `_test`. Another safety feature is `n`, the maximum number of files to be deleted."
  [dir n]
  {:pre [(fs/relative? dir)
         (ss/ends-with dir "_test")]}
  (let [blobs (->> (.list gcs/storage
                          object-storage/sftp-bucket
                          (into-array Storage$BlobListOption
                                      [(Storage$BlobListOption/prefix (str dir "/"))]))
                   (.iterateAll)
                   (map identity))
        bc    (count blobs)]
    (assert (<= bc n) (str "Too many blobs: " bc))
    (doseq [blob blobs]
      (.delete blob (into-array Blob$BlobSourceOption [])))))

(defn fs-remove-test-dirs
  "Every removable directory must be relative (and resolved as subdir for SFTP target
  directory) and end with `_test`."
  [& dirs]
  {:pre [(every? #(and (fs/relative? %) (ss/ends-with % "_test")) dirs)]}
  (doseq [dir dirs]
    (fs/delete-tree (ss/join-file-path (env/value :outgoing-directory) dir))))
