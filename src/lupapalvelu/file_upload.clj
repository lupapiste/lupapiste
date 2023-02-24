(ns lupapalvelu.file-upload
  (:require [clj-uuid :as uuid]
            [clojure.set :refer [rename-keys]]
            [lupapalvelu.attachment.tags :as att-tags]
            [lupapalvelu.attachment.type :as lat]
            [lupapalvelu.attachment.unzip-collection :as unzip]
            [lupapalvelu.building :as building]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.storage.file-storage :as storage]
            [me.raynes.fs :as fs]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]])
  (:import (java.io File InputStream)))

(defschema FileData
  {:filename                        sc/Str
   :content                         (sc/cond-pre File InputStream)
   (sc/optional-key :content-type)  sc/Str
   (sc/optional-key :size)          sc/Num
   (sc/optional-key :fileId)        sc/Str
   (sc/optional-key :storageSystem) (sc/cond-pre sc/Str sc/Keyword)})

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
  (let [metadata           (if (map? (first metadata))
                             (first metadata)
                             (apply hash-map metadata))
        file-id            (or (:fileId filedata) (str (uuid/v1)))
        sanitized-filename (mime/sanitize-filename (:filename filedata))
        content-type       (mime/mime-type sanitized-filename)
        result             (storage/upload file-id
                                           sanitized-filename
                                           content-type
                                           (:content filedata)
                                           metadata
                                           (:storageSystem filedata))]
    {:fileId      file-id
     :filename    sanitized-filename
     :size        (or (:size filedata) (:length result))
     :contentType content-type
     :metadata    metadata}))

(defn- op-id-from-buildings-list [{:keys [buildings]} building-id]
  (some
    (fn [b]
      (let [id-set (->> (select-keys b [:buildingId :nationalId :localShortId])
                        vals
                        (remove nil?)
                        (map ss/lower-case)
                        set)]
        (when (id-set building-id)
          (:operationId b))))
    buildings))

(defn- op-id-from-document-tunnus-or-nid [application tunnus-or-national-id]
  (->> (building/building-ids application :include-bldgs-without-nid)
       (some (fn [{:keys [operation-id short-id national-id]}]
               (when (or (= (ss/lower-case short-id) tunnus-or-national-id) ; short-id is 'tunnus'
                         (= (ss/lower-case national-id) tunnus-or-national-id))
                 operation-id)))))

(defn- resolve-attachment-grouping
  [{{:keys [grouping multioperation]} :metadata} application tunnus-or-bid-str]
  (let [tunnus->op-id (comp #(or (op-id-from-document-tunnus-or-nid application %)
                                 (op-id-from-buildings-list application %))
                            ss/lower-case
                            ss/trim)
        op-ids (->> (ss/split tunnus-or-bid-str #",|;")
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

(defn- process-unzip-results [application attachments user-id]
  (some->> attachments
           ;; Allow only the same file mime types as we would for normal uploads
           (filter #(and (->> % :mime (re-matches mime/mime-type-pattern))
                         (->> % :object-key (storage/unlinked-file-exists? user-id))))
           seq
           (map (fn [{:keys [filename object-key size mime localizedType contents drawingNumber operation]}]
                  (let [attachment-type (lat/localisation->attachment-type (or (:permitType application) :R)
                                                                           localizedType)]
                    (merge
                      {:fileId      object-key
                       :filename    (ss/normalize filename)
                       :size        size
                       :contentType mime
                       :metadata    {:uploader-user-id user-id
                                     :linked           false}}
                      (util/strip-nils
                        {:contents      contents
                         :drawingNumber drawingNumber
                         :group         (resolve-attachment-grouping attachment-type application operation)
                         :type          attachment-type})))))))

(defn- is-zip-file? [filedata]
  (-> filedata :filename mime/sanitize-filename mime/mime-type (= "application/zip")))

(defn- unzip? [files]
  (and (empty? (rest files))
       (env/feature? :unzip-attachments)
       (is-zip-file? (first files))))

(defn save-files [application files user-id]
  (if (unzip? files)
    (let [{:keys [attachments error]} (-> files first :tempfile (unzip/unzip-attachment-collection user-id))]
      (if (or (not-empty error) (empty? attachments))
        {:ok false
         :error (or error "error.unzipping-error")}
        (or (some->> (process-unzip-results application attachments user-id)
                     (assoc {:ok true} :files))
            {:ok    false
             :error :error.file-upload.illegal-file-type})))
    {:ok true
     :files (pmap
              #(save-file % :uploader-user-id user-id :linked false)
              (map #(rename-keys % {:tempfile :content}) files))}))

(defn mark-duplicates
  "A file in `result` matches an `application` attachment (latest version) filename, if
  their normalized presentations match. The file extension is ignored in comparison."
  [application result]
  (let [name-fn            #(some-> % :filename ss/blank-as-nil ss/normalize fs/split-ext first)
        existing-files     (->> (:attachments application)
                                (keep #(some-> % :latestVersion name-fn))
                                set)
        find-duplicates-fn (fn [file]
                             (if (some->> (name-fn file) (contains? existing-files))
                               (assoc file :existsWithSameName true)
                               (assoc file :existsWithSameName false)))]
    (assoc result :files (map find-duplicates-fn (remove nil? (:files result))))))
