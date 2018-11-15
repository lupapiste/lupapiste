(ns lupapalvelu.file-upload
  (:require [monger.operators :refer :all]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]]
            [lupapalvelu.mime :as mime]
            [clojure.set :refer [rename-keys]]
            [lupapalvelu.attachment.muuntaja-client :as muuntaja]
            [lupapalvelu.attachment.type :as lat]
            [sade.core :refer :all]
            [sade.strings :as str]
            [sade.env :as env]
            [lupapalvelu.building :as building]
            [lupapalvelu.attachment.tags :as att-tags]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.storage.file-storage :as storage]
            [clj-uuid :as uuid])
  (:import (java.io File InputStream)))

(defschema FileData
  {:filename                        sc/Str
   :content                         (sc/cond-pre File InputStream)
   (sc/optional-key :content-type)  sc/Str
   (sc/optional-key :size)          sc/Num
   (sc/optional-key :fileId)        sc/Str})

(defschema SavedFileData
  {:fileId sc/Str
   :filename sc/Str
   :size sc/Num
   :contentType sc/Str
   :metadata {sc/Any sc/Any}})

(defn file-size-legal [{{files :files} :data {role :role} :user}]
  (let [max-size (env/value :file-upload :max-size (if (contains? roles/all-authenticated-user-roles (keyword role)) :logged-in :anonymous))]
    (when-not (every? #(<= % max-size) (map :size files))
      (fail :error.file-upload.illegal-upload-size :errorParams (/ max-size 1000 1000)))))

(sc/defn ^:always-validate save-file :- SavedFileData
  "Saves file or input stream to mongo GridFS, with metadata (map or kvs). If input stream, caller must close stream.
   Filedata is map (see FileData schema).
   Map of file specific data (fileId, metadata, contentType...) is returned."
  [filedata :- FileData & metadata]
  (let [metadata (if (map? (first metadata))
                   (first metadata)
                   (apply hash-map metadata))
        file-id            (or (:fileId filedata) (str (uuid/v1)))
        sanitized-filename (mime/sanitize-filename (:filename filedata))
        content-type       (mime/mime-type sanitized-filename)
        result (storage/upload file-id sanitized-filename content-type (:content filedata) metadata)]
    {:fileId file-id
     :filename sanitized-filename
     :size (or (:size filedata) (:length result))
     :contentType content-type
     :metadata metadata}))

(defn- op-id-from-buildings-list [{:keys [buildings]} building-id]
  (some
    (fn [b]
      (let [id-set (->> (select-keys b [:buildingId :nationalId :localShortId])
                        vals
                        (remove nil?)
                        (map str/lower-case)
                        set)]
        (when (id-set building-id)
          (:operationId b))))
    buildings))

(defn- op-id-from-document-tunnus-or-nid [application tunnus-or-national-id]
  (->> (building/building-ids application :include-bldgs-without-nid)
       (some (fn [{:keys [operation-id short-id national-id]}]
               (when (or (= (str/lower-case short-id) tunnus-or-national-id) ; short-id is 'tunnus'
                         (= (str/lower-case national-id) tunnus-or-national-id))
                 operation-id)))))

(defn- resolve-attachment-grouping
  [{{:keys [grouping multioperation]} :metadata} application tunnus-or-bid-str]
  (let [tunnus->op-id (comp #(or (op-id-from-document-tunnus-or-nid application %)
                                 (op-id-from-buildings-list application %))
                            str/lower-case
                            str/trim)
        op-ids (->> (str/split tunnus-or-bid-str #",|;")
                    (map tunnus->op-id)
                    set)
        groups (att-tags/attachment-groups-for-application application)
        op-groups (filter #(= (:groupType %) :operation) groups)]
    (cond
      (seq op-ids) {:groupType :operation
                    :operations (filter #(op-ids (:id %)) op-groups)}
      ; This logic should match getDefaultGroupingForType in attachment-batch-model.js
      multioperation {:groupType :operation
                      :operations op-groups}

      (= grouping :operation) {:groupType :operation
                               :operations (take 1 op-groups)}

      grouping (first (filter #(= grouping (:groupType %)) groups)))))

(defn- download-and-save-files [application attachments user-id]
  (pmap
    (fn [{:keys [filename uri localizedType contents drawingNumber operation]}]
      (let [attachment-type (lat/localisation->attachment-type :R localizedType)]
        (when-let [is (muuntaja/download-file uri)]
          (let [file-data (save-file {:filename filename :content is} :uploader-user-id user-id :linked false)]
            (.close is)
            (merge
              file-data
              (util/strip-nils
                {:contents      contents
                 :drawingNumber drawingNumber
                 :group         (resolve-attachment-grouping attachment-type application operation)
                 :type          attachment-type}))))))
    attachments))

(defn- is-zip-file? [filedata]
  (-> filedata :filename mime/sanitize-filename mime/mime-type (= "application/zip")))

(defn- unzip? [files]
  (and (empty? (rest files))
       (env/feature? :unzip-attachments)
       (is-zip-file? (first files))))

(defn save-files [application files user-id]
  (if (unzip? files)
    (let [{:keys [attachments error]} (-> files first :tempfile muuntaja/unzip-attachment-collection)]
      (if (or (not-empty error) (empty? attachments))
        {:ok false
         :error (or error "error.unzipping-error")}
        {:ok true
         :files (download-and-save-files application attachments user-id)}))
    {:ok true
     :files (pmap
              #(save-file % :uploader-user-id user-id :linked false)
              (map #(rename-keys % {:tempfile :content}) files))}))

(defn mark-duplicates [application result]
  (let [existing-files (mapv (fn [attachment] (first (str/split (get-in attachment [:latestVersion :filename]) #"\."))) (:attachments application))
        find-duplicates-fn (fn [file]
                             (if (some #(= % (first (str/split (:filename file) #"\."))) existing-files)
                               (assoc file :existsWithSameName true)
                               (assoc file :existsWithSameName false)))]
    (assoc result :files (map find-duplicates-fn (remove nil? (:files result))))))
