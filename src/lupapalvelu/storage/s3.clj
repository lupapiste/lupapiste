(ns lupapalvelu.storage.s3
  (:require [sade.env :as env]
            [sade.core :refer [now]]
            [taoensso.timbre :as timbre]
            [clojure.java.io :as io]
            [clojure.walk :refer [keywordize-keys]]
            [sade.strings :as ss]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.object-storage :as object-storage])
  (:import [java.io InputStream ByteArrayOutputStream File FileInputStream ByteArrayInputStream]
           [com.amazonaws.services.s3.model PutObjectRequest CreateBucketRequest CannedAccessControlList
                                            ObjectMetadata AmazonS3Exception S3ObjectSummary]
           [com.amazonaws.client.builder AwsClientBuilder$EndpointConfiguration]
           [com.amazonaws.auth AWSStaticCredentialsProvider BasicAWSCredentials]
           [com.amazonaws.services.s3 AmazonS3ClientBuilder AmazonS3]
           [com.amazonaws Protocol ClientConfiguration SDKGlobalConfiguration]
           [java.util Date]))

(defn- create-client [access-key secret-key endpoint]
  (let [credentials (BasicAWSCredentials. access-key secret-key)
        client-configuration (doto (ClientConfiguration.)
                               (.setProtocol Protocol/HTTPS)
                               (.setSignerOverride "S3SignerType")
                               (.setCacheResponseMetadata false)
                               (.setMaxConnections 100)
                               (.setConnectionTimeout 5000)
                               (.setConnectionTTL (or (env/value :s3 :connection-ttl) 300000)))] ; 5 minutes
    (-> (doto (AmazonS3ClientBuilder/standard)
          (.withCredentials (AWSStaticCredentialsProvider. credentials))
          (.withClientConfiguration client-configuration)
          (.withEndpointConfiguration (AwsClientBuilder$EndpointConfiguration. endpoint nil))
          (.withPathStyleAccessEnabled true))
        (.build))))

; Allow use of expired cert
;(System/setProperty SDKGlobalConfiguration/DISABLE_CERT_CHECKING_SYSTEM_PROPERTY "true")

(defonce ^AmazonS3 s3-client
  (when (env/feature? :s3)
    (create-client (env/value :s3 :access-key)
                   (env/value :s3 :secret-key)
                   (env/value :s3 :endpoint))))

(defn- ^String bucket-name [bucket]
  (str "lp-"
       env/target-env
       "-"
       (when (ss/starts-with mongo/*db-name* "test")
         "test-")
       (or (ss/lower-case (ss/blank-as-nil bucket))
           object-storage/unlinked-bucket)))

(defn- create-bucket-if-not-exists [bucket]
  (when-not (.doesBucketExist s3-client bucket)
    (timbre/info "Creating object storage bucket" bucket)
    (->> (doto (CreateBucketRequest. bucket)
           (.setCannedAcl CannedAccessControlList/Private))
         (.createBucket s3-client))))

(defn- do-put-object [bucket file-id filename content-type input-stream content-length metadata]
  {:pre [(map? metadata)]}
  (try
    (let [actual-bucket (bucket-name bucket)]
      (create-bucket-if-not-exists actual-bucket)
      (let [user-metadata (object-storage/generate-user-metadata metadata filename)
            request (->> (doto (ObjectMetadata.)
                           (.setContentLength content-length)
                           (.setContentType content-type)
                           (.setUserMetadata user-metadata))
                         (PutObjectRequest. actual-bucket file-id input-stream))]
        (-> (.getRequestClientOptions request)
            ; Maximum buffer size 150 MB (when uploading a stream)
            (.setReadLimit 157286401))
        (.putObject s3-client request)
        (timbre/debug "Object" file-id "uploaded to bucket" actual-bucket)
        {:length content-length}))
    (catch AmazonS3Exception e
      (timbre/error e "Could not upload object" file-id "to bucket" (bucket-name bucket))
      (throw e))))

(defn do-put-input-stream [bucket file-id filename content-type input-stream content-length metadata]
  {:pre [(string? bucket) (string? file-id) (string? filename) (string? content-type)
         (instance? InputStream input-stream)
         (or (nil? metadata) (map? metadata))]}
  (do-put-object bucket file-id filename content-type input-stream content-length metadata))

(defn do-put-file-or-input-stream [bucket file-id filename content-type is-or-file metadata]
  {:pre [(string? bucket) (string? file-id) (string? filename) (string? content-type)
         (or (instance? File is-or-file) (instance? InputStream is-or-file))
         (or (nil? metadata) (map? metadata))]}
  (if (instance? InputStream is-or-file)
    (let [bos (ByteArrayOutputStream. 5242880)]
      (with-open [is ^InputStream is-or-file]
        (io/copy is bos))
      (let [content-bytes (.toByteArray bos)]
        (do-put-object bucket
                       file-id
                       filename
                       content-type
                       (ByteArrayInputStream. content-bytes)
                       (alength content-bytes)
                       metadata)))
    (do-put-object bucket
                   file-id
                   filename
                   content-type
                   (FileInputStream. ^File is-or-file)
                   (.length ^File is-or-file)
                   metadata)))

(defn do-move-file-object [from-bucket to-bucket old-id new-id]
  {:pre [(every? string? [to-bucket old-id new-id])]}
  (let [from-bucket (bucket-name from-bucket)
        target-bucket (bucket-name to-bucket)]
    (try
      (create-bucket-if-not-exists target-bucket)
      (.copyObject s3-client from-bucket old-id target-bucket new-id)
      (.deleteObject s3-client from-bucket old-id)
      (timbre/debug "Object" old-id "moved from" from-bucket "to" target-bucket "as" new-id)
      (catch Throwable ex
        (timbre/error ex "Could not move" old-id "from" from-bucket "to" target-bucket "as" new-id)
        (throw ex)))))

(defn do-download [bucket file-id]
  {:pre [(string? file-id)]}
  (try
    (let [metadata (.getObjectMetadata s3-client (bucket-name bucket) ^String file-id)
          user-metadata (object-storage/process-retrieved-metadata (.getUserMetadata metadata))]
      {:content     (fn []
                      (-> (.getObject s3-client (bucket-name bucket) ^String file-id)
                          (.getObjectContent)))
       :contentType (.getContentType metadata)
       :size        (.getContentLength metadata)
       :filename    (:filename user-metadata)
       :fileId      (last (ss/split file-id #"/"))
       :metadata    (dissoc user-metadata :filename)})
    (catch AmazonS3Exception ex
      (if (= 404 (.getStatusCode ex))
        (timbre/warn "Tried to retrieve non-existing" file-id "from S3 bucket" (bucket-name bucket) ":" (.getMessage ex))
        (timbre/error ex "Error occurred when trying to retrieve" file-id "from S3 bucket" (bucket-name bucket))))))

(defn does-object-exist? [bucket id]
  (.doesObjectExist s3-client (bucket-name bucket) id))

(defn do-delete [bucket id]
  (.deleteObject s3-client (bucket-name bucket) id)
  (timbre/debug "Object" id "deleted from" (bucket-name bucket)))

(defn do-delete-unlinked-files [^Date older-than]
  (doseq [^S3ObjectSummary object (-> (.listObjects s3-client ^String (bucket-name object-storage/unlinked-bucket))
                                      (.getObjectSummaries))]
    (when (.before (.getLastModified object) older-than)
      (.deleteObject s3-client (bucket-name object-storage/unlinked-bucket) (.getKey object)))))


(defrecord S3Storage []
  object-storage/ObjectStorage
  (put-input-stream [_ bucket file-id filename content-type input-stream content-length metadata-map]
    (do-put-input-stream bucket file-id filename content-type input-stream content-length metadata-map))
  (put-file-or-input-stream [_ bucket file-id filename content-type is-or-file metadata-map]
    (do-put-file-or-input-stream bucket file-id filename content-type is-or-file metadata-map))
  (copy-file-object [_ _from-bucket _to-bucket _old-id _new-id]
    (throw (UnsupportedOperationException. "Copy not implemented")))
  (move-file-object [_ from-bucket to-bucket old-id new-id]
    (do-move-file-object from-bucket to-bucket old-id new-id))
  (download [_ bucket file-id]
    (do-download bucket file-id))
  (list-file-objects [_ _bucket _prefix]
    (throw (UnsupportedOperationException. "List objects not implemented")))
  (object-exists? [_ bucket id]
    (does-object-exist? bucket id))
  (delete [_ bucket id]
    (do-delete bucket id))
  (delete-unlinked-files [_ older-than]
    (do-delete-unlinked-files older-than)))
