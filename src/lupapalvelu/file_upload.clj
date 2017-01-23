(ns lupapalvelu.file-upload
  (:require [monger.operators :refer :all]
            [sade.util :as util]
            [schema.core :as sc]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mime :as mime]
            [clojure.set :refer [rename-keys]]
            [lupapalvelu.attachment.muuntaja-client :as muuntaja]
            [lupapalvelu.attachment.type :as lat]
            [sade.strings :as str]
            [lupapalvelu.building :as building])
  (:import (java.io File InputStream)))

(def FileData
  {:filename                        sc/Str
   :content                         (sc/cond-pre File InputStream)
   (sc/optional-key :content-type)  sc/Str
   (sc/optional-key :size)          sc/Num
   (sc/optional-key :fileId)        sc/Str})

(defn save-file
  "Saves file or input stream to mongo GridFS, with metadata (map or kvs). If input stream, caller must close stream.
   Filedata is map (see FileData schema).
   Map of file specific data (fileId, metadata, contentType...) is returned."
  [filedata & metadata]
  {:pre [(sc/validate FileData filedata)]}
  (let [metadata (if (map? (first metadata))
                   (first metadata)
                   (apply hash-map metadata))
        file-id            (or (:fileId filedata) (mongo/create-id))
        sanitized-filename (mime/sanitize-filename (:filename filedata))
        content-type       (mime/mime-type sanitized-filename)

        result (mongo/upload file-id sanitized-filename content-type (:content filedata) metadata)]
    {:fileId file-id
     :filename sanitized-filename
     :size (or (:size filedata) (:length result))
     :contentType content-type
     :metadata metadata}))

(defn- op-id-from-matching-document [tunnus doc]
  (when (= tunnus (str/lower-case (get-in doc [:data :tunnus :value])))
    (get-in doc [:schema-info :op :id])))

(defn- op-id-from-matching-building [application building-id]
  (->> (building/building-ids application :include-bldgs-without-nid)
       (some (fn [{:keys [operation-id short-id national-id]}]
               (when (or (= (str/lower-case short-id) building-id)
                         (= (str/lower-case national-id) building-id))
                 operation-id)))))

(defn- resolve-attachment-grouping [{:keys [metadata]} {:keys [documents] :as application} tunnus-or-bid]
  (if-let [op-id (and
                   (not (str/blank? tunnus-or-bid))
                   (or (some #(op-id-from-matching-document tunnus-or-bid %) documents)
                       (op-id-from-matching-building application tunnus-or-bid)))]
    {:operations [{:id op-id}]}
    {:groupType (:grouping metadata)}))

(defn- download-and-save-files [application attachments session-id]
  (pmap
    (fn [{:keys [filename localizedType contents drawingNumber operation]}]
      (when-let [attachment-type (lat/localisation->attachment-type :R localizedType)]
        (when-let [is (muuntaja/download-file filename)]
          (let [file-data (save-file {:filename filename :content is} :sessionId session-id :linked false)
                tunnus-or-bid (when operation (str/lower-case (str/trim operation)))]
            (.close is)
            (merge file-data
                   {:contents      contents
                    :drawingNumber drawingNumber
                    :group         (resolve-attachment-grouping attachment-type application tunnus-or-bid)
                    :type          (select-keys attachment-type [:type-group :type-id])})))))
    attachments))

(defn- is-zip-file? [filedata]
  (-> filedata :filename mime/sanitize-filename mime/mime-type (= "application/zip")))

(defn save-files [application files session-id]
  (if-let [attachments (and (empty? (rest files))
                            (is-zip-file? (first files))
                            (-> files first :tempfile muuntaja/unzip-attachment-collection :attachments seq))]
    (download-and-save-files application attachments session-id)
    (pmap
      #(save-file % :sessionId session-id :linked false)
      (map #(rename-keys % {:tempfile :content}) files))))

(defn- two-hours-ago []
  ; Matches vetuma session TTL
  (util/get-timestamp-ago :hour 2))

(defn cleanup-uploaded-files []
  (when-not @mongo/connection
    (mongo/connect!))
  (mongo/delete-file {$and [{:metadata.linked {$exists true}}
                            {:metadata.linked false}
                            {:metadata.uploaded {$lt (two-hours-ago)}}]}))
