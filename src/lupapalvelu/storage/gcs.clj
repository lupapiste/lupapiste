(ns lupapalvelu.storage.gcs
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.object-storage :as object-storage]
            [sade.env :as env]
            [sade.strings :as ss]
            [schema.core :as sc])
  (:import [com.google.auth ServiceAccountSigner]
           [com.google.auth.oauth2 AccessToken GoogleCredentials ServiceAccountCredentials]
           [com.google.cloud.storage Blob
                                     Blob$BlobSourceOption
                                     BlobId
                                     BlobInfo
                                     BucketInfo
                                     Cors
                                     Cors$Origin
                                     HttpMethod
                                     Storage
                                     Storage$BlobListOption
                                     Storage$BlobTargetOption
                                     Storage$BlobWriteOption
                                     Storage$BucketGetOption
                                     Storage$BucketTargetOption
                                     Storage$CopyRequest
                                     Storage$SignUrlOption
                                     StorageClass
                                     StorageOptions
                                     StorageOptions$Builder]
           [java.io ByteArrayOutputStream
                    File
                    IOException
                    InputStream]
           [java.net URLEncoder]
           [java.nio.channels Channels]
           [java.time Instant]
           [java.util Base64 Date List]
           [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)

(defn make-credentials [service-account-file]
  (try
    (if service-account-file
      (with-open [is (io/input-stream service-account-file)]
        (ServiceAccountCredentials/fromStream is))
      (GoogleCredentials/getApplicationDefault))
    (catch IOException e
      (log/warn (.getMessage e) "You may also set gcs.service-account-file path/to/file.json in properties. Storage will not be currently available."))))

(defn- cors-config ^Cors [external-url]
  (-> (Cors/newBuilder)
      (.setOrigins [(Cors$Origin/of external-url)])
      (.setMethods [HttpMethod/PUT HttpMethod/OPTIONS HttpMethod/GET HttpMethod/HEAD HttpMethod/POST])
      (.setResponseHeaders ["Content-Type"])
      (.setMaxAgeSeconds (int 3600))
      (.build)))

(defn- create-client []
  (when-let [creds (make-credentials (env/value :gcs :service-account-file))]
    (let [storage (-> (StorageOptions/newBuilder)
                      ^StorageOptions$Builder (.setCredentials creds)
                      ^StorageOptions$Builder (.setProjectId (env/value :gcs :project))
                      (.build)
                      (.getService))]
      storage)))

(defonce ^Storage storage
  (when (env/feature? :gcs)
    (create-client)))

(defonce existing-buckets (atom #{}))

(defn- empty-bucket-get-options
  ^"[Lcom.google.cloud.storage.Storage$BucketGetOption;" []
  (into-array Storage$BucketGetOption []))

(defn- bucket-exists? [^String bucket]
  (or (contains? @existing-buckets bucket)
      (try
        (when (.get storage bucket (empty-bucket-get-options))
          (swap! existing-buckets conj bucket)
          true)
        (catch Exception e
          (log/error e "Could not check if GCP bucket" bucket "exists")))))

(defn- empty-bucket-target-options
  ^"[Lcom.google.cloud.storage.Storage$BucketTargetOption;" []
  (into-array Storage$BucketTargetOption []))

(defn create-bucket-if-not-exists [^String bucket-name]
  (when-not (bucket-exists? bucket-name)
    (let [external-url (env/value :host)
          cors (cors-config external-url)
          region        (env/value :gcs :region)
          storage-class (or (some-> (env/value :gcs :storage-class)
                                    (StorageClass/valueOf))
                            StorageClass/STANDARD)]
      (log/infof "Creating Cloud Storage bucket %s to region %s with storage class %s" bucket-name region storage-class)
      (.create storage
               ^BucketInfo
               (-> (BucketInfo/newBuilder bucket-name)
                   (.setStorageClass storage-class)
                   (.setLocation region)
                   (.setCors [cors])
                   (.build))
               (empty-bucket-target-options)))))

(defn actual-bucket-name
  "Adds a prefix for the bucket so the different local and dev environments don't get mixed up"
  [bucket]
  (if (and (some? bucket)
           (= bucket object-storage/sftp-bucket))
    ;; Keep SFTP bucket as-is because it is not controlled by Lupapiste
    bucket
    (str "lp-"
         env/target-env
         "-"
         (when (ss/starts-with? (mongo/current-db-name) "test")
           "test-")
         (or (ss/lower-case (ss/blank-as-nil bucket))
             object-storage/unlinked-bucket))))

(defn generic-bucket-name
  "Removes the environment and test prefixes from the actual bucket name to get the generic one"
  [bucket]
  (when (string? bucket)
    (if-let [parts (re-matches #"(lp-[a-z]+-(test-)?)(.*)" bucket)]
      (last parts)
      bucket)))

(defn- empty-blob-write-options
  ^"[Lcom.google.cloud.storage.Storage$BlobWriteOption;" []
  (into-array Storage$BlobWriteOption []))

(defn- empty-blob-target-options
  ^"[Lcom.google.cloud.storage.Storage$BlobTargetOption;" []
  (into-array Storage$BlobTargetOption []))

(defn- upload [bucket object-key filename content-type content metadata-map]
  (try
    (let [actual-bucket (actual-bucket-name bucket)
          _             (create-bucket-if-not-exists actual-bucket)
          blob          (BlobId/of actual-bucket object-key)
          metadata      (object-storage/generate-user-metadata metadata-map filename)
          blob-info     (-> (BlobInfo/newBuilder blob)
                            (.setContentType content-type)
                            (.setMetadata metadata)
                            (.build))
          blob          (if (instance? File content)
                          (.createFrom storage
                                       blob-info
                                       (.toPath ^File content)
                                       (empty-blob-write-options))
                          (let [content-bytes (if (instance? InputStream content)
                                                (let [bos (ByteArrayOutputStream.)]
                                                  (with-open [is ^InputStream content]
                                                    (io/copy is bos))
                                                  (.toByteArray bos))
                                                content)]
                            (.create storage
                                     blob-info
                                     ^bytes content-bytes
                                     (empty-blob-target-options))))]
      (log/debug "Object" object-key "uploaded to GCS bucket" actual-bucket)
      {:length     (.getSize ^Blob blob)
       :bucket     actual-bucket
       :object-key object-key})
    (catch Exception e
      (log/error e "Could not upload object" object-key "to GCS bucket" (actual-bucket-name bucket))
      (throw e))))

(defn- actually-copy-object [from-bucket to-bucket old-id new-id]
  (let [from-bucket (actual-bucket-name from-bucket)
        to-bucket   (actual-bucket-name to-bucket)
        source      (BlobId/of from-bucket old-id)
        target      (BlobId/of to-bucket new-id)]
    (create-bucket-if-not-exists to-bucket)
    (-> (.copy storage (-> (Storage$CopyRequest/newBuilder)
                           (.setSource source)
                           (.setTarget target)
                           (.build)))
        (.getResult))))

(defn- copy-object [from-bucket to-bucket old-id new-id]
  (try
    (actually-copy-object from-bucket to-bucket old-id new-id)
    (log/debug "Object" old-id "copied from" from-bucket "to" to-bucket "as" new-id)
    (catch Throwable ex
      (log/error ex "Could not copy" old-id "from" from-bucket "to" to-bucket "as" new-id)
      (throw ex))))

(defn move-object [from-bucket to-bucket old-id new-id]
  (try
    (actually-copy-object from-bucket to-bucket old-id new-id)
    (.delete storage (BlobId/of (actual-bucket-name from-bucket) old-id))
    (log/debug "Object" old-id "moved from" from-bucket "to" to-bucket "as" new-id)
    (catch Throwable ex
      (log/error ex "Could not move" old-id "from" from-bucket "to" to-bucket "as" new-id)
      (throw ex))))

(defn blob->file-data-map [^Blob blob]
  (let [metadata (object-storage/process-retrieved-metadata (.getMetadata blob))]
    {:content     (fn []
                    (-> (.reader blob (into-array Blob$BlobSourceOption []))
                        (Channels/newInputStream)))
     :contentType (.getContentType blob)
     :size        (.getSize blob)
     :filename    (:filename metadata)
     :fileId      (-> (.getBlobId blob) (.getName) (ss/split #"/") last)
     :modified    (.getUpdateTime blob)
     :metadata    (dissoc metadata :filename)}))

(defn- download-object [bucket object-key]
  (let [actual-bucket (actual-bucket-name bucket)
        blob-id       (BlobId/of actual-bucket object-key)]
    (try
      (if-let [blob ^Blob (.get storage blob-id)]
        (blob->file-data-map blob)
        (log/warn "Tried to retrieve non-existing" object-key "from GCS bucket" actual-bucket))
      (catch Exception e
        (log/error e "Error occurred when trying to retrieve" object-key "from GCS bucket" actual-bucket)))))

(defn list-objects [bucket prefix]
  (->> (.list storage bucket (into-array Storage$BlobListOption [(Storage$BlobListOption/prefix prefix)]))
       (.iterateAll)
       (map blob->file-data-map)))

(defn- does-object-exist? [bucket object-key]
  (->> (BlobId/of (actual-bucket-name bucket) object-key)
       (.get storage)
       boolean))

(defn- delete-object [bucket id]
  (let [actual-bucket (actual-bucket-name bucket)]
    (.delete storage (BlobId/of actual-bucket id))
    (log/debug "Object" id "deleted from" actual-bucket)))

(defn- delete-unlinked-objects [^Date older-than]
  (let [older-than-ms (.getTime older-than)
        bucket        (actual-bucket-name object-storage/unlinked-bucket)]
    (doseq [^Blob blob (-> (.list storage bucket (into-array Storage$BlobListOption []))
                           (.iterateAll))]
      (when (< (.getCreateTime blob) older-than-ms)
        (.delete storage (.getBlobId blob))))))

(defn- create-signed-download-link
  "Link is only valid for ttl-seconds"
  [^Storage storage bucket object-key ^long ttl-seconds]
  (-> (.signUrl storage
                (-> (actual-bucket-name bucket)
                    (BlobId/of object-key)
                    (BlobInfo/newBuilder)
                    .build)
                ttl-seconds
                TimeUnit/SECONDS
                (into-array Storage$SignUrlOption [(Storage$SignUrlOption/withV4Signature)]))
      str))

(defn- get-metadata [^Storage storage bucket object-key]
  (let [actual-bucket (actual-bucket-name bucket)
        blob-id       (BlobId/of actual-bucket object-key)]
    (try
      (if-let [blob ^Blob (.get storage blob-id)]
        (object-storage/process-retrieved-metadata (.getMetadata blob))
        (log/warn "Tried to retrieve non-existing" object-key "metadata from GCS bucket" actual-bucket))
      (catch Exception e
        (log/error e "Error occurred when trying to retrieve" object-key "metadata from GCS bucket" actual-bucket)))))

(defrecord GCStorage []
  object-storage/ObjectStorage
  (put-input-stream [_ bucket file-id filename content-type input-stream _ metadata-map]
    (upload bucket file-id filename content-type input-stream metadata-map))
  (put-file-or-input-stream [_ bucket file-id filename content-type is-or-file metadata-map]
    (upload bucket file-id filename content-type is-or-file metadata-map))
  (copy-file-object [_ from-bucket to-bucket old-id new-id]
    (copy-object from-bucket to-bucket old-id new-id))
  (move-file-object [_ from-bucket to-bucket old-id new-id]
    (move-object from-bucket to-bucket old-id new-id))
  (download [_ bucket file-id]
    (download-object bucket file-id))
  (list-file-objects [_ bucket prefix]
    (list-objects bucket prefix))
  (object-exists? [_ bucket id]
    (does-object-exist? bucket id))
  (delete [_ bucket id]
    (delete-object bucket id))
  (delete-unlinked-files [_ older-than]
    (delete-unlinked-objects older-than))
  (create-download-link [_ bucket object-key ttl-seconds]
    (create-signed-download-link storage bucket object-key ttl-seconds))
  (get-metadata [_ bucket object-key]
    (get-metadata storage bucket object-key)))

(sc/defschema FileUploadOptions
  {:content-type              sc/Str
   (sc/optional-key :file-id) sc/Str
   :md5-digest                sc/Str
   :user-id                   sc/Str})

(sc/defschema FileUploadResponse
  {:content-type    sc/Str
   :file-id         sc/Str
   :file-version-id sc/Str
   :upload-url      sc/Str})


(def ^List credential-scopes ["https://www.googleapis.com/auth/devstorage.read_write"])

(defn ->scoped-creadential [^GoogleCredentials credentials]
      (.createScoped credentials credential-scopes))

(def access-token (atom nil))

(defn get-access-token [^GoogleCredentials credentials]
  (if (and @access-token
           (.after (.getExpirationTime ^AccessToken @access-token) (Date.)))
    @access-token
    (reset! access-token (.refreshAccessToken credentials))))

(sc/defn create-signed-url [credentials
                bucket-object-path
                {:keys [content-type md5-digest]} :- FileUploadOptions]
  (let [expiration (-> (Instant/now)
                       ; 8 hours
                       (.plusSeconds 28800)
                       (.getEpochSecond))
        signable (str "POST\n"
                      md5-digest "\n"
                      content-type "\n"
                      expiration "\n"
                      "x-goog-resumable:start\n"
                      bucket-object-path)
        signed (-> (->> (.sign ^ServiceAccountSigner credentials (.getBytes signable))
                        (.encodeToString (Base64/getEncoder)))
                   (URLEncoder/encode "UTF-8"))]
    (str "https://storage.googleapis.com" bucket-object-path
           "?GoogleAccessId=" (URLEncoder/encode (.getAccount ^ServiceAccountSigner credentials) "UTF-8")
           "&Expires=" expiration
           "&Signature=" signed)))

(sc/defn init-resumable-upload :- FileUploadResponse
  [credentials :- GoogleCredentials
   bucket :- sc/Str
   origin :- sc/Str
   {:keys [content-type md5-digest user-id file-id] :as opt} :- FileUploadOptions]
  (log/debug "Initialize resumable upload")
  (try
    (let [file-id (or file-id (str (random-uuid)))
          file-version-id (str (random-uuid))
          object (str "/" bucket "/" user-id "/" file-version-id)

          url (create-signed-url credentials object opt)

          resp (http/post url
                          {:headers     {"content-type"     content-type
                                         "content-md5"      md5-digest
                                         "x-goog-resumable" "start"
                                         "Origin"           origin}
                           :oauth-token (get-access-token credentials)})]
      {:file-id         file-id
       :file-version-id file-version-id
       :content-type    content-type
       :upload-url      (get-in resp [:headers "Location"])})
    (catch Exception e
      (log/error e "Could not initialize url for upload")
      (throw e))))
