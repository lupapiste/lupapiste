(ns lupapalvelu.linked-files
  "Utility functions for linked files.

  Linked files are application attachemnts that:
  1) are uploaded directly to GCS using upload link. As corollary, GCS is also the only
     supported storage for the linked files.
  2) attachemnt metadata is stored in a separate collection linked-file-metadata, and not
     nested into application object

  Linked files can be extended outside application context if needed, but the current version
  presumes that each linked file is associated to an application."
  (:require [lupapalvelu.attachment :as attachment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.storage.gcs :as gcs]
            [lupapalvelu.storage.object-storage :as object-storage]
            [lupapalvelu.user :as user]
            [monger.operators :refer [$and $elemMatch $set]]
            [sade.core :refer [fail now ok]]
            [sade.env :as env]
            [sade.schemas :as schemas]
            [schema.core :as sc]
            [sade.util :as util]))

(sc/defschema LinkedFileTargetTypes (sc/enum "operations-location-file"))

(sc/defschema LinkedFileTarget
  "Linked file target is a container for all identifiers needed to attach file to the correct target and support
  different kind of queries. Currently, there is only one target-type, and hence there is only one set of identifiers.
  However, if this approach works, this is an extension point needed to handle different kinds of file and attachment
  scenarios."
  {(sc/optional-key :target-type)    LinkedFileTargetTypes
   (sc/optional-key :file-id)        sc/Str
   (sc/optional-key :application-id) sc/Str
   (sc/optional-key :operation-id)   sc/Str})

(sc/defschema LinkedFileVersionMetadata
  {(sc/optional-key :name)          sc/Str
   (sc/optional-key :content-type)  sc/Str
   (sc/optional-key :size)          sc/Int})

(sc/defschema LinkedFileMetadata
  {(sc/optional-key :name)       sc/Str
   (sc/optional-key :model-type) sc/Str})

(sc/defschema LinkedFileVersion
  "Linked file metadata contains following fields

  * storage system (always gcs): This must be here, or otherwise it's hard to reuse old file upload and download logic.
  * file-version-id: Each unique identifier for file version. File-id is unique identifier for the all file versions.
    When only it is used to reference the file, the lastest version is returned.
  * bucket and target-file-path redundant data and store just in case. File download logics presumes that file is in
    application folder. This is not necessarily case in the future.
  * uploaded-by is reference to the user who uploaded the file.
  * uploaded-at it time stamp of upload.
  * metadata contains version related metadata. Size is neede so that it's possible to show file size warning when
    the file is realy big."
  {(sc/optional-key :storage-system)   (sc/enum :gcs "gcs")
   (sc/optional-key :file-version-id)  sc/Str
   (sc/optional-key :version)          attachment/VersionNumber
   (sc/optional-key :bucket)           sc/Str
   (sc/optional-key :target-file-path) sc/Str
   (sc/optional-key :uploaded-by)      user/SummaryUser
   (sc/optional-key :uploaded-at)      schemas/Timestamp
   (sc/optional-key :metadata)         LinkedFileVersionMetadata})

(sc/defschema LinkedFileEntity
  "MongoDB representatio of LinkedFileMetadata"
  {(sc/optional-key :_id)            sc/Any
   :target-entity LinkedFileTarget
   :metadata      LinkedFileMetadata
   :versions      [LinkedFileVersion]})

(sc/defn ^:always-validate init-resumable-upload-for-application
  [uid :- String
   content-type :- String
   md5-digest :- String
   file-id]
  (let [credentials (-> (gcs/make-credentials (env/value :gcs :service-account-file))
                        (gcs/->scoped-creadential))
        origin (env/value :host)
        bucketName (gcs/actual-bucket-name object-storage/unlinked-bucket)
        file-upload-options {:content-type content-type
                             :md5-digest   md5-digest
                             :user-id      uid
                             :file-id      file-id}]
    (gcs/create-bucket-if-not-exists bucketName)
    (gcs/init-resumable-upload credentials bucketName origin file-upload-options)))

(sc/defn ^:validate-always linked-file-target->file-link-strategy
  [{:keys [target-type application-id]} :- LinkedFileTarget]
  (case target-type
    "operations-location-file"
    {:target-bucket   object-storage/application-bucket
     :target-file-dir application-id}
    (throw (Exception. "No target bucket specified for target-type: " target-type))))

(sc/defn version-exists? :- Boolean
  [file-doc :- LinkedFileEntity
   file-version-id :- String ]
  (->> (get file-doc :versions)
       (some #(= (:file-version-id %) file-version-id))))

(sc/defn get-in-version
  [file-doc file-version-id & path]
  (let [version-doc
        (->> (get file-doc :versions)
             (filter #(= (:file-version-id %) file-version-id))
             first)]
    (get-in version-doc path)))

(sc/defn ->file-version :- LinkedFileVersion
  [file-version-id
   {:keys [versions] :as file}
   target-file-path :- String
   target-bucket :- String
   user
   version-metadata :- LinkedFileVersionMetadata]
  (let [last-version (->> versions
                          (sort-by (comp (juxt :major :minor) :version))
                          last
                          :version)
        exists? (version-exists? file file-version-id)
        version (if exists?
                  (get-in-version file file-version-id :version)
                  (attachment/next-attachment-version last-version user))]
    {:storage-system   :gcs
     :file-version-id  file-version-id
     :version          version
     :bucket           target-bucket
     :target-file-path target-file-path
     :uploaded-by      (user/summary user)
     :metadata         (or version-metadata {})
     :uploaded-at      (now)}))

(def linked-file-metadata-collection "linked-file-metadata")

(defn gen-linked-file-mongo-query
  "Return linked file metadata object with file-id or nil if not found

  Following query targeted are supported

  * :file-id (also :target-entity/file-id)
    In mongo target-entity.file-id must match the key's value.
  * :application-id (also :target-entity/application-id)
    In mongo target-entity.file-id must match value the key's value.
  * :target-type (also :target-entity/target-type)
    In mongo target-entity.file-id must match value the key's value.
  * :operation-id (also :target-entity/operation-id)
    In mongo operation id must much the key's value.
  * :versions/file-version-id
    In mongo document nested version array must contain and object where file-version-id matches"

  [query]
  (let [targetEntityQueries
        (->> query
             (filter (fn [[k _]] (or (nil? (namespace k))
                                     (= "target-entity" (namespace k)))))
             (map (fn [[k v]] {(util/kw-path :target-entity k) v})))

        versionQuery
        (->> query
             (filter (fn [[k _]] (= "versions" (namespace k))))
             (map (fn [[k v]] {(keyword (name k)) v}))
             (into {}))

        queries (keep identity
                      [(when (seq targetEntityQueries) {$and targetEntityQueries})
                       (when (seq versionQuery) {$elemMatch versionQuery})])]
    (case (count queries)
      0 nil
      1 (first queries)
      {$and queries})))

(defn find-one-linked-file-with-metadata
  "Return a linked-file-metadata object that match query or nil

  See function `gen-linked-file-mongo-query` for more extensive information on the query
  parameters.

  E.g.
  (find-one-linked-file-with-metadata {:file-id \"file-id\" :application-id \"file-id\"})
  will return linked metadata object where target-type.file-id = file-id and
  target-type.application-id = application-id."
  [query & {:keys [include-query]}]
  (let [query (gen-linked-file-mongo-query query)
        result (when (seq query) (mongo/select-one linked-file-metadata-collection query))]
    (if include-query
      {:query query :result result}
      result)))

(defn find-many-linked-files-with-metadata
  "Return all linked-file-metadata object that match query or nil

  See function `gen-linked-file-mongo-query` for more extensive information on the supported query
  parameters.

  E.g.
  (find-one-linked-file-with-metadata {:file-id \"file-id\" :application-id \"file-id\"})
  will return linked metadata object where target-type.file-id = file-id and
  target-type.application-id = application-id."
  [query & {:keys [include-query only-fields]}]
  (let [query (gen-linked-file-mongo-query query)
        result (when (seq query) (mongo/select linked-file-metadata-collection query only-fields))]
    (if include-query
      {:query query :result result}
      result)))

(defn get-linked-application-files-for
  "Queries metadatas from mongo and wrap results into a ok response"
  [target]
  (ok :data (find-many-linked-files-with-metadata target :include-query false :only-fields [:target-entity :metadata :versions])))

(defn update-metadata-field [target-entity field value]
  (let [query (gen-linked-file-mongo-query target-entity)]
    (mongo/update-one-and-return linked-file-metadata-collection
                                 query
                                 {$set {(str "metadata." field) value}})))

(sc/defn ^:validate-always upsert-linked-file
  [query-with-file-target
   doc :- LinkedFileEntity]
  (mongo/update-one-and-return linked-file-metadata-collection
                               query-with-file-target
                               doc
                               :upsert true))

(defn ensure-file-version
  [file-version-id linked-file-target user {:keys [version-metadata file-metadata]}]
  (let [uid (:id user)
        {:keys [target-bucket
                target-file-dir]} (linked-file-target->file-link-strategy linked-file-target)
        {move-result      :result
         to-bucket-name   :bucket
         target-file-path :file-path} (storage/move-unlinked-file-to target-bucket uid target-file-dir file-version-id)
        {query-with-file-target :query
         file-doc               :result} (find-one-linked-file-with-metadata linked-file-target :include-query true)

        metadata-for-file-exists? (some? file-doc)
        version-exists (version-exists? file-doc file-version-id)
        version-obj (->file-version file-version-id file-doc target-file-path to-bucket-name user version-metadata)

        file-metadata-default {:name (:name version-metadata)}
        file-metadata (-> (get file-doc :metadata file-metadata-default)
                          (merge file-metadata))
        versions-list (concat (:versions file-doc) [version-obj])
        upsert-linked-file #(upsert-linked-file query-with-file-target
                                                {:target-entity linked-file-target
                                                 :metadata      file-metadata
                                                 :versions      versions-list})]

    (cond
      (= :not-found move-result)
      (fail "error.file-with-id-not-found")

      (and (= :already-moved move-result) (not metadata-for-file-exists?))
      (fail "error.file-was-already-linked-but-metadata-not-found")

      (and metadata-for-file-exists? (not version-exists))
      (do (upsert-linked-file) (ok :result :added-version-to-existing-doc))

      (and metadata-for-file-exists? version-exists)
      (ok :result :version-was-already-linked)

      (not metadata-for-file-exists?)
      (do (upsert-linked-file) (ok :result :added-doc-and-version)))))

(defn get-linked-attachment-version-file
  "Get requested version of the file from storage system.

  It is similar to get-attachment-version-file except:
  - Linked files do not support file level access checks unlike get-attachment-version-file
  - Linked files do not support auto generated preview files unlike get-attachment-version-file"
  [{:keys [target-entity] :as linked-file-metadata} file-version-id]
  (when (and file-version-id (:application-id target-entity))
    (storage/download (:application-id target-entity) file-version-id linked-file-metadata)))

(defn get-linked-file-content
  "Returns the file for the latest linked attachment version if user has access to application and the attachment,
   otherwise nil.

   The difference to get-attachment-latest-version-file is that attachment metadata is stored in and
   fetched form linked-file-metadata collection and not nested into the application document."
  ([user linked-file-id & {:keys [linked-file-version-id]}]
   (->> (find-one-linked-file-with-metadata {:target-entity/file-id linked-file-id})
        ((fn [linked-file]
           (when-let [version (seq (:versions linked-file))]
             (let [application-id (get-in linked-file [:target-entity :application-id])
                   related-application (domain/get-application-as {:_id application-id} user :include-canceled-apps? true)
                   filename (get-in linked-file [:metadata :name])
                   lastest-file-version-first (comp > :uploaded-at)
                   adjust-file-name #(when % (assoc % :filename filename))
                   matches-with-file-version-id-when-given-in-query?
                   #(or (nil? linked-file-version-id)
                        (= linked-file-version-id (:file-version-id %)))]
               (when (and related-application (seq version))
                 (->> (:versions linked-file)
                      (filter matches-with-file-version-id-when-given-in-query?)
                      (sort lastest-file-version-first)
                      first
                      :file-version-id
                      (get-linked-attachment-version-file linked-file)
                      adjust-file-name)))))))))
