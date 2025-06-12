(ns lupapalvelu.linked-files
  "Utility functions for linked files.

  Linked files are application attachments that:
  1) are uploaded directly to GCS using upload link. As corollary, GCS is also the only
     supported storage for the linked files.
  2) attachments metadata is stored in a separate collection linked-file-metadata, and not
     nested into application object

  Linked files can be extended outside application context if needed, but the current version
  presumes that each linked file is associated to an application."
  (:require [lupapalvelu.attachment.version :as attachment-version]
            [lupapalvelu.bim.bim-util :as bim-util]
            [lupapalvelu.bim.bim-extracted-data :as bim-extracted-data]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.storage.gcs :as gcs]
            [lupapalvelu.storage.object-storage :as object-storage]
            [lupapalvelu.user :as user]
            [lupapiste-commons.schema-utils :as schema-utils]
            [lupapiste-commons.tos-metadata-schema :as tms]
            [monger.operators :refer [$and $elemMatch $set]]
            [sade.core :refer [fail now ok]]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]
            [swiss.arrows :refer :all]))

(sc/defschema LinkedFileTargetTypes (sc/enum "bim-model-file" "bim-inspection-report" "bim-additional-file"))

(sc/defschema LinkedFileTarget
  "Linked file target is a container for all identifiers needed to attach file to the correct target and support
  different kind of queries. However, if this approach works, this is an extension point needed to handle different
  kinds of file and attachment scenarios."
  {(sc/optional-key :target-type)    LinkedFileTargetTypes
   (sc/optional-key :file-id)        ssc/NonBlankStr
   ;; :parent-file-id is used to link files to each other. Value should be the file-id of the parent linked file.
   (sc/optional-key :parent-file-id) ssc/NonBlankStr
   (sc/optional-key :application-id) ssc/ApplicationId
   (sc/optional-key :operation-id)   ssc/NonBlankStr})

(sc/defschema LinkedFileVersionMetadata
  {(sc/optional-key :name)                ssc/NonBlankStr
   (sc/optional-key :content-type)        ssc/NonBlankStr
   (sc/optional-key :size)                sc/Int
   (sc/optional-key :ryhti)               {:fileKey ssc/UUIDStr}
   (sc/optional-key :document-date)       sc/Str
   (sc/optional-key :document-identifier) sc/Str})

(sc/defschema LinkedFileMetadata
  (st/merge
    ;; Linked-file-specific fields
    {(sc/optional-key :name)        ssc/NonBlankStr
     (sc/optional-key :model-type)  ssc/NonBlankStr
     (sc/optional-key :model-phase) ssc/NonBlankStr
     (sc/optional-key :ryhti)       {:attachmentDocumentKey                         ssc/UUIDStr
                                     (sc/optional-key :buildingInformationModelKey) ssc/UUIDStr}}
    ;; TOS metadata fields from lupapiste-commons, all optional for linked files
    (->> [:henkilotiedot
          :julkisuusluokka
          :kayttajaryhma
          :kayttajaryhmakuvaus
          :kieli
          :nakyvyys
          :salassapidettavat-osat
          :salassapitoperuste
          :salassapitoperuste-kohta
          :suojaustaso
          :tila
          :turvallisuusluokka]
         (map (fn [k]
                ;; NOTE: commons specifies these as exact keyword enumerations
                ;; but at the moment Lupapiste (mostly) deals with plain strings.
                [(sc/optional-key k) (sc/cond-pre ssc/NonBlankStr sc/Keyword)]))
         (into {}))
    {(sc/optional-key :myyntipalvelu)       sc/Bool
     (sc/optional-key :security-period-end) sc/Any
     (sc/optional-key :sailytysaika)        sc/Any
     (sc/optional-key :salassapitoaika)     sc/Any}))

(sc/defschema LinkedFileBimResult
  {(sc/optional-key :bucket)         ssc/NonBlankStr
   (sc/optional-key :result-file-id) ssc/NonBlankStr
   (sc/optional-key :check-failed)   sc/Bool
   :integration                      ssc/NonBlankStr
   :external-job-id                  ssc/NonBlankStr
   :timestamp                        sc/Int
   :tests-count                      sc/Int
   :tests-passed                     sc/Int})

(sc/defschema LinkedFileBim
  {(sc/optional-key :validation-result) LinkedFileBimResult
   (sc/optional-key :compliance-result) LinkedFileBimResult
   (sc/optional-key :xkt-file)          {:bucket     ssc/NonBlankStr
                                         :object-key ssc/NonBlankStr}
   (sc/optional-key :ifc-metadata)      {:createdAt sc/Int
                                         :schema    sc/Str}})

(sc/defschema LinkedFileVersion
  "Linked file metadata contains following fields

  * storage system (always gcs): This must be here, or otherwise it's hard to reuse old file upload and download logic.
  * file-version-id: Each unique identifier for file version. File-id is unique identifier for the all file versions.
    When only it is used to reference the file, the latest version is returned.
  * bucket and target-file-path are redundant data and stored just in case. The file download logic presumes that the
    file is in the application folder. This is not necessarily the case in the future.
  * uploaded-by is a reference to the user who uploaded the file.
  * uploaded-at is time stamp of upload.
  * metadata contains version related metadata. Size is needed so that it's possible to show file size warning when
    the file is massive.
  * bim contains results for bim validation and compliance checks"
  {(sc/optional-key :storage-system)   (sc/enum :gcs "gcs")
   (sc/optional-key :file-version-id)  sc/Str
   (sc/optional-key :version)          attachment-version/VersionNumber
   (sc/optional-key :bucket)           sc/Str
   (sc/optional-key :target-file-path) sc/Str
   (sc/optional-key :uploaded-by)      user/SummaryUser
   (sc/optional-key :uploaded-at)      ssc/Timestamp
   (sc/optional-key :metadata)         LinkedFileVersionMetadata
   (sc/optional-key :bim)              LinkedFileBim})

(sc/defschema LinkedFileEntity
  "MongoDB representation of LinkedFileMetadata"
  {(sc/optional-key :_id) ssc/ObjectIdField
   :target-entity         LinkedFileTarget
   :metadata              LinkedFileMetadata
   :versions              [LinkedFileVersion]})

(def linked-file-edit-permissions
  [{:context  {:application {:state #{:draft}}}
    :required [:application/edit-draft]}
   {:context  {:application {:state states/all-but-draft-or-terminal}}
    :required [:application/edit]}])

(def linked-file-read-permissions
  [{:context  {:application {:state #{:draft}}}
    :required [:application/edit-draft]}
   {:required [:application/read]}])

(sc/defn ^:always-validate init-resumable-upload-for-application
  ;; NOTE: although this is named `-for-application`, there is nothing application specific here.
  ;; We simply provide an URL for uploading an unlinked file. Binding the upload to an actual
  ;; application happens later (`link-resumable-upload-for-application`).
  [host :- String
   user-id :- String
   content-type :- String
   md5-digest :- String
   file-id]
  (let [credentials         (-> (gcs/make-credentials (env/value :gcs :service-account-file))
                                (gcs/->scoped-creadential))
        origin              host
        bucketName          (gcs/actual-bucket-name object-storage/unlinked-bucket)
        file-upload-options (util/assoc-when {:content-type content-type
                                              :md5-digest   md5-digest
                                              :user-id      user-id}
                                             :file-id      file-id)]
    (gcs/create-bucket-if-not-exists bucketName)
    (gcs/init-resumable-upload credentials bucketName origin file-upload-options)))

(sc/defn ^:always-validate linked-file-target->file-link-strategy
  [{:keys [target-type application-id]} :- LinkedFileTarget]
  (let [application-bucket {:target-bucket   object-storage/application-bucket
                            :target-file-dir application-id}]
    (case target-type
      "bim-model-file"        application-bucket
      "bim-inspection-report" application-bucket
      "bim-additional-file"   application-bucket
      (throw (Exception. "No target bucket specified for target-type: " target-type)))))

(defn latest-version [linked-file]
  (->> linked-file
       :versions
       (apply util/max-key :uploaded-at)))

(sc/defn version-exists? :- Boolean
  [file-doc :- (sc/maybe LinkedFileEntity)
   file-version-id :- String]
  (boolean (and file-doc
                (->> (get file-doc :versions)
                     (some #(= (:file-version-id %) file-version-id))))))

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
        exists?      (version-exists? file file-version-id)
        version      (if exists?
                       (get-in-version file file-version-id :version)
                       (attachment-version/next-attachment-version last-version user))]
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
  (let [target-entity-queries (some->> query
                                       (filter (fn [[k _]] (or (nil? (namespace k))
                                                               (= "target-entity" (namespace k)))))
                                       (map (fn [[k v]] {(util/kw-path :target-entity k) v})))

        version-query         (some->> query
                                       (filter (fn [[k _]] (= "versions" (namespace k))))
                                       (map (fn [[k v]] {(keyword (name k)) v}))
                                       (into {}))

        queries               (keep identity
                                    [(when (seq target-entity-queries) {$and target-entity-queries})
                                     (when (seq version-query) {:versions {$elemMatch version-query}})])]
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
  (let [query  (gen-linked-file-mongo-query query)
        result (when (seq query) (mongo/select-one linked-file-metadata-collection query))]
    (if include-query
      {:query query :result result}
      result)))

(defn linked-file-running-jobs [application-id target-file-path]
  (->> (mongo/select :jobs {:status                "running"
                            :data.application-id   application-id
                            :data.target-file-path target-file-path})
       (mapv #(update % :id str))))

(defn find-many-linked-files-with-metadata
  "Return all linked-file-metadata object that match query or nil

  See function `gen-linked-file-mongo-query` for more extensive information on the supported query
  parameters.

  E.g.
  (find-one-linked-file-with-metadata {:file-id \"file-id\" :application-id \"file-id\"})
  will return linked metadata object where target-type.file-id = file-id and
  target-type.application-id = application-id."
  [query & {:keys [include-query only-fields]}]
  (when (seq query)
    (let [query  (gen-linked-file-mongo-query query)
          result (if (seq only-fields)
                   (mongo/select linked-file-metadata-collection query only-fields)
                   (mongo/select linked-file-metadata-collection query))]
      (if include-query
        {:query query :result result}
        result))))

(defn find-linked-files-by-query
  ([query projection]
   (mongo/select-options linked-file-metadata-collection query {:projection projection}))
  ([query]
   (find-linked-files-by-query query nil)))

(defn get-linked-application-files-for
  "Queries metadatas from mongo"
  [target]
  (into [] (find-many-linked-files-with-metadata target :include-query false :only-fields [:target-entity :metadata :versions])))

(defn update-metadata-field [target-entity field value]
  (let [query (gen-linked-file-mongo-query target-entity)]
    (mongo/update-one-and-return linked-file-metadata-collection
                                 query
                                 {$set {(str "metadata." field) value}})))

(defn update-version-metadata-field
  "Updates a metadata field in a specific version of a linked file.
  The target-entity should include :file-version-id to identify which version to update.
  Example: {:application-id \"LP-123\" :file-version-id \"abc-123\"}."
  [target-entity field value]
  {:pre [(:file-version-id target-entity)
         (:application-id target-entity)]}
  (let [file-version-id    (:file-version-id target-entity)
        query-with-version (gen-linked-file-mongo-query
                             (-> target-entity
                                 (dissoc :file-version-id)
                                 (assoc :versions/file-version-id file-version-id)))
        update-path        (str "versions.$.metadata." field)]
    (mongo/update-one-and-return linked-file-metadata-collection
                                 query-with-version
                                 {$set {update-path value}})))

(sc/defn ^:always-validate upsert-linked-file
  [query-with-file-target
   doc :- LinkedFileEntity]
  (mongo/update-one-and-return linked-file-metadata-collection
                               query-with-file-target
                               doc
                               :upsert true))

(defn linked-file-target-type [linked-file _system]
  (-> linked-file
      (get-in [:target-entity :target-type])
      keyword))

(defmulti post-process-linked-file
  "For actions taken after a given type of linked file is uploaded,
  e.g. validating it or converting it to another file format."
  linked-file-target-type)

(defmethod post-process-linked-file :default [_linked-file _system])

(defn ensure-file-version
  [file-version-id linked-file-target user system {:keys [version-metadata file-metadata]}]
  {:pre [(map? system)]}
  (let [uid                              (:id user)
        {:keys [target-bucket
                target-file-dir]}        (linked-file-target->file-link-strategy linked-file-target)
        {move-result      :result
         to-bucket-name   :bucket
         target-file-path :file-path}    (storage/move-unlinked-file-to target-bucket uid target-file-dir file-version-id)
        {query-with-file-target :query
         file-doc               :result} (find-one-linked-file-with-metadata linked-file-target :include-query true)
        metadata-for-file-exists?        (some? file-doc)
        version-exists                   (version-exists? file-doc file-version-id)
        version-obj                      (->file-version file-version-id file-doc target-file-path to-bucket-name user version-metadata)

        file-metadata-default            {:name (:name version-metadata)}
        file-metadata                    (-> (get file-doc :metadata file-metadata-default)
                                             (merge file-metadata)
                                             (->> (schema-utils/coerce-metadata-to-schema tms/AsiakirjaMetaDataMap)))
        versions-list                    (concat (:versions file-doc) [version-obj])
        upsert-linked-file               #(-> (upsert-linked-file query-with-file-target
                                                                  {:target-entity linked-file-target
                                                                   :metadata      file-metadata
                                                                   :versions      versions-list})
                                              (post-process-linked-file system))]
    (cond
      (= :not-found move-result)
      (fail "error.file-with-id-not-found")

      (and (= :already-moved move-result) (not metadata-for-file-exists?))
      (fail "error.file-was-already-linked-but-metadata-not-found")

      (and metadata-for-file-exists? (not version-exists))
      (ok :result :added-version-to-existing-doc
          :post-process-data (upsert-linked-file))

      (and metadata-for-file-exists? version-exists)
      (ok :result :version-was-already-linked)

      (not metadata-for-file-exists?)
      (ok :result :added-doc-and-version
          :post-process-data (upsert-linked-file)))))

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
             (let [application-id             (get-in linked-file [:target-entity :application-id])
                   related-application        (domain/get-application-as {:_id application-id} user :include-canceled-apps? true)
                   matches-with-file-version-id-when-given-in-query?
                                              #(or (nil? linked-file-version-id)
                                                   (= linked-file-version-id (:file-version-id %)))]
               (when (and related-application (seq version))
                 (let [selected-version (->> (:versions linked-file)
                                             (filter matches-with-file-version-id-when-given-in-query?)
                                             (apply util/max-key :uploaded-at))
                       filename         (get-in selected-version [:metadata :name])]
                   (some-<>> selected-version
                             :file-version-id
                             (get-linked-attachment-version-file linked-file)
                             (assoc <> :filename filename)))))))))))

(defn delete-linked-attachment-version-files
  "Delete requested version of the file from storage system."
  [{:keys [target-entity]} version]
  (when-let [application-id (:application-id target-entity)]
    (run! (fn [file-id]
            (when file-id
              (storage/delete-app-file-from-storage application-id file-id "gcs")))
          [(:file-version-id version)
           (some-> version :bim :compliance-result :result-file-id)
           (some-> version :bim :validation-result :result-file-id)
           (some-> version :bim :xkt-file :object-key (ss/split #"/") second)])))

(defn delete-linked-file [linked-file]
  (mongo/remove-many linked-file-metadata-collection {:_id (:id linked-file)})
  (doseq [v (:versions linked-file)]
    (delete-linked-attachment-version-files linked-file v))
  (case (-> linked-file :target-entity :target-type)
    ;; BIM files have also IFC data snapshots that need to be deleted when the file is deleted
    "bim-model-file" (bim-extracted-data/delete-ifc-data-snapshots! (keep :file-version-id (:versions linked-file)))
    nil))

(defn enriched-linked-file-metadata
  "Returns a version of the linked file with added fields for the frontend"
  [application organization file-id]
  (when-let [linked (find-one-linked-file-with-metadata {:target-entity/file-id        file-id
                                                         :target-entity/application-id (:id application)})]
    (let [doc         (->> linked :target-entity :operation-id (domain/get-document-by-operation application))
          model-phase (-> linked :metadata :model-phase)]
      (cond-> linked
        ;; Add rakennusluokka
        (tools/has-rakennusluokka? application organization)
        (assoc-in [:target-entity :rakennusluokka]
                  (get-in doc [:data :kaytto :rakennusluokka :value]))
        ;; Add valid-model-phase? for BIM models
        model-phase
        (assoc-in [:metadata :valid-model-phase?]
                  (bim-util/valid-model-phase? (:state application) model-phase))))))
