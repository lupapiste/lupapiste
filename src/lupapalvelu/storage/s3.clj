(ns lupapalvelu.storage.s3
  (:require [sade.env :as env]
            [sade.core :refer [now]]
            [taoensso.timbre :as timbre]
            [clojure.java.io :as io]
            [clojure.walk :refer [keywordize-keys]]
            [sade.strings :as str]
            [sade.util :as util]
            [sade.strings :as ss]
            [sade.crypt :as c]
            [ring.util.codec :as codec])
  (:import [java.io InputStream ByteArrayOutputStream File FileInputStream ByteArrayInputStream]
           [com.amazonaws.services.s3.model PutObjectRequest CreateBucketRequest CannedAccessControlList
                                            SetBucketVersioningConfigurationRequest BucketVersioningConfiguration
                                            ObjectMetadata AmazonS3Exception]
           [com.amazonaws.client.builder AwsClientBuilder$EndpointConfiguration]
           [com.amazonaws.auth AWSStaticCredentialsProvider BasicAWSCredentials]
           [com.amazonaws.services.s3 AmazonS3ClientBuilder AmazonS3]
           [com.amazonaws Protocol ClientConfiguration]))

(defn- create-client [access-key secret-key endpoint]
  (let [credentials (BasicAWSCredentials. access-key secret-key)
        client-configuration (doto (ClientConfiguration.)
                               (.setProtocol Protocol/HTTPS)
                               (.setSignerOverride "S3SignerType")
                               (.setCacheResponseMetadata false)
                               (.setMaxConnections 100)
                               (.setConnectionTimeout 5000))]
    (-> (doto (AmazonS3ClientBuilder/standard)
          (.withCredentials (AWSStaticCredentialsProvider. credentials))
          (.withClientConfiguration client-configuration)
          (.withEndpointConfiguration (AwsClientBuilder$EndpointConfiguration. endpoint nil))
          (.withPathStyleAccessEnabled true))
        (.build))))

(defonce ^AmazonS3 s3-client
  (when (env/feature? :s3)
    (create-client (env/value :s3 :access-key)
                   (env/value :s3 :secret-key)
                   (env/value :s3 :endpoint))))

(defn- ^String bucket-name [bucket]
  (str "lupapiste-" env/target-env "-" (or (ss/lower-case (str/blank-as-nil bucket))
                                           "unlinked-files")))

(defn- create-bucket-if-not-exists [bucket]
  (when-not (.doesBucketExist s3-client bucket)
    (timbre/info "Creating object storage bucket" bucket)
    (->> (doto (CreateBucketRequest. bucket)
           (.setCannedAcl CannedAccessControlList/Private))
         (.createBucket s3-client ))
    (->> (BucketVersioningConfiguration. BucketVersioningConfiguration/ENABLED)
         (SetBucketVersioningConfigurationRequest. bucket)
         (.setBucketVersioningConfiguration s3-client))))

(defn- generate-user-metadata [metadata]
  "Returns a Java Map<String,String> compatible map"
  (reduce (fn [result [k v]]
            (assoc result (name k) (-> (if (keyword? v)
                                         (name v)
                                         (str v))
                                       codec/url-encode)))
          {}
          metadata))

(defn- do-put-object [bucket file-id filename content-type input-stream content-length metadata]
  {:pre [(map? metadata)]}
  (try
    (let [actual-bucket (bucket-name bucket)]
      (create-bucket-if-not-exists actual-bucket)
      (let [user-metadata (-> (generate-user-metadata metadata)
                              (assoc "uploaded" (str (now))
                                     "filename" (codec/url-encode filename)))
            request (->> (doto (ObjectMetadata.)
                           (.setContentLength content-length)
                           (.setContentType content-type)
                           (.setUserMetadata user-metadata))
                         (PutObjectRequest. actual-bucket file-id input-stream))]
        (-> (.getRequestClientOptions request)
            (.setReadLimit 150001))
        (.putObject s3-client request)
        (timbre/debug "Object" file-id "uploaded to bucket" actual-bucket)
        {:length content-length}))
    (catch AmazonS3Exception e
      (timbre/error e "Could not upload object" file-id "to bucket" (bucket-name bucket))
      (throw e))))

(defn put-input-stream [bucket file-id filename content-type input-stream content-length & metadata]
  {:pre [(string? file-id) (string? filename) (string? content-type)
         (instance? InputStream input-stream)
         (or (nil? metadata) (sequential? metadata))
         (or (even? (count metadata)) (map? (first metadata)))]}
  (->> (if (map? (first metadata))
         (first metadata)
         (apply hash-map metadata))
       (do-put-object bucket file-id filename content-type input-stream content-length)))

(defn put-file-or-input-stream
  "Puts the provided content under the given id in a bucket generated from the application id.
   The content may be a (temp) file handle or an input stream.
   If it's a stream, consider using put-input-stream if the content length in bytes is known, as
   otherwise the stream will be copied to a byte array first, as S3 has to know the content length.

   Bucket should be either an application id, a user id, a session id or nil. If it is nil, the
   object will go to the common temp file bucket from which can be linked to an application later."
  [bucket file-id filename content-type is-or-file & metadata]
  {:pre [(string? file-id) (string? filename) (string? content-type)
         (or (instance? File is-or-file) (instance? InputStream is-or-file))
         (or (nil? metadata) (sequential? metadata))
         (or (even? (count metadata)) (map? (first metadata)))]}
  (let [md (if (map? (first metadata))
             (first metadata)
             (apply hash-map metadata))]
    (if (instance? InputStream is-or-file)
      (let [bos (ByteArrayOutputStream. 5242880)]
        (with-open [is is-or-file]
          (io/copy is bos))
        (let [content-bytes (.toByteArray bos)]
          (do-put-object bucket
                         file-id
                         filename
                         content-type
                         (ByteArrayInputStream. content-bytes)
                         (alength content-bytes)
                         md)))
      (do-put-object bucket
                     file-id
                     filename
                     content-type
                     (FileInputStream. ^File is-or-file)
                     (.length is-or-file)
                     md))))

(defn move-file-object
  "Moves object within the storage system from one bucket to another by copying the object
   and then deleting it from the source bucket.
   Source bucket for the two-arity version is the default temp file buvket."
  ([to-bucket file-id]
   (move-file-object nil to-bucket file-id))
  ([from-bucket to-bucket file-id]
   {:pre [(string? to-bucket) (string? file-id)]}
   (let [from-bucket (bucket-name from-bucket)
         target-bucket (bucket-name to-bucket)]
     (create-bucket-if-not-exists target-bucket)
     (.copyObject s3-client from-bucket file-id target-bucket file-id)
     (.deleteObject s3-client from-bucket file-id)
     (timbre/debug "Object" file-id "moved from" from-bucket "to" target-bucket))))

(defn download [application-id file-id]
  {:pre [(string? file-id)]}
  (try
    (let [object (.getObject s3-client (bucket-name application-id) ^String file-id)
          metadata (.getObjectMetadata object)
          user-metadata (->> (.getUserMetadata metadata)
                             (into {})
                             (map (fn [[k v]]
                                    [(keyword k) (codec/url-decode v)]))
                             (into {}))]
      {:content     (fn [] (.getObjectContent object))
       :contentType (.getContentType metadata)
       :size        (.getContentLength metadata)
       :filename    (:filename user-metadata)
       :fileId      file-id
       :metadata    (-> (dissoc user-metadata :filename)
                        (update :uploaded util/->int))
       :application application-id})
    (catch AmazonS3Exception ex
      (timbre/error ex "Error occurred when trying to retrieve" file-id "from S3 bucket" (bucket-name application-id)))))

(defn object-exists? [bucket id]
  (.doesObjectExist s3-client (bucket-name bucket) id))

(defn delete [bucket id]
  (.deleteObject s3-client (bucket-name bucket) id)
  (timbre/debug "Object" id "deleted from" (bucket-name bucket)))
