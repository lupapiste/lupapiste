(ns lupapalvelu.filebank
  (:require [sade.core :refer [unauthorized]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [lupapalvelu.storage.file-storage :as storage]
            [monger.operators :refer :all]
            [sade.files :as files]
            [schema.core :refer [defschema] :as sc]
            [taoensso.timbre :refer [info debugf]]
            [sade.shared-schemas :as sssc])
  (:import [java.io InputStream]))

(defschema File
  {:file-id       sc/Str
   :filename      sc/Str
   :size          sc/Int
   :modified      sc/Int
   :user          usr/SummaryUser
   :keywords      [sc/Str]
   :storageSystem sssc/StorageSystem})

(defschema Filebank
  {:id              sc/Str
   :files           [File]})

(defn create-file!
  "Creates a new file and attaches it to filebank.
   If no file with the given id exists, creates it."
  [filebank-id file-id filename size modified user keywords]
  (mongo/update-by-id :filebank filebank-id
                      {$push {:files {:file-id file-id
                                      :filename filename
                                      :size size
                                      :modified modified
                                      :user user
                                      :keywords keywords
                                      :storageSystem (storage/default-storage-system-id)}}}
                      :upsert true))

(defn find-filebank-file [filebank-id file-id]
  (->> (mongo/select-one :filebank {:_id filebank-id :files.file-id file-id})
       :files
       (filter #(-> % :file-id (= file-id)))
       first))

(defn delete-filebank-file!
  "Deletes filebank file.
   Non-atomic operation: first deletes file, then updates document."
  [filebank-id file-id]
  (when-let [{:keys [storageSystem]} (find-filebank-file filebank-id file-id)]
    (storage/delete-app-file-from-storage filebank-id file-id storageSystem)
    (info "1/2 deleted filebank file" file-id)
    (mongo/update-by-id :filebank filebank-id {$pull {:files {:file-id file-id}}})
    (info "2/2 deleted meta-data from filebank" filebank-id)))

(defn- append-files-to-zip!
  "Inserts the given files into the ZIP file input stream"
  [zip filebank-id file-ids filename-prefix]
  (let [{:keys [files]} (mongo/by-id :filebank filebank-id)]
    (doseq [{:keys [file-id storageSystem]} (filter #(contains? (set file-ids) (:file-id %)) files)]
      (when-let [{:keys [filename content]} (storage/download-from-system filebank-id file-id storageSystem)]
        (files/open-and-append! zip (str filename-prefix file-id "_" filename) content)))))

(defn ^InputStream get-filebank-files-for-user!
  "Returns the files as an input stream of a self-destructing ZIP file"
  [filebank-id file-ids piped?]
  (if piped?
    (files/piped-zip-input-stream
      (fn [zip]
        (debugf "Streaming zip file for %d filebank files" (count file-ids))
        (append-files-to-zip! zip filebank-id file-ids (str filebank-id "_"))))
    (files/temp-file-input-stream
      (files/temp-file-zip
        "lupapiste.filebank." ".zip.tmp"
        (fn [zip]
          (append-files-to-zip! zip filebank-id file-ids (str filebank-id "_")))))))

(defn validate-filebank-enabled
  "Pre-check for pseudoquery filebank-enabled"
  [{app-org :organization app :application}]
  (when-not (and (:organization app)
                 (:filebank-enabled @app-org))
    unauthorized))
