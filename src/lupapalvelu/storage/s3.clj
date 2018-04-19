(ns lupapalvelu.storage.s3
  (:require [sade.env :as env]
            [sade.core :refer [now]]
            [taoensso.timbre :as timbre]
            [clojure.java.io :as io]
            [clojure.walk :refer [keywordize-keys]]
            [sade.strings :as str]
            [sade.util :as util])
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
  (str "lupapiste-" env/target-env "-" (or (str/blank-as-nil bucket)
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
            (assoc result (name k) (if (keyword? v) (name v) (str v))))
          {}
          metadata))

(defn- do-put-object [bucket file-id filename content-type input-stream content-length metadata]
  {:pre [(map? metadata)]}
  (create-bucket-if-not-exists (bucket-name bucket))
  (let [user-metadata (-> (generate-user-metadata metadata)
                          (assoc "uploaded" (str (now))
                                 "filename" filename))
        request (->> (doto (ObjectMetadata.)
                       (.setContentLength content-length)
                       (.setContentType content-type)
                       (.setUserMetadata user-metadata))
                     (PutObjectRequest. (bucket-name bucket) file-id input-stream))]
    (-> (.getRequestClientOptions request)
        (.setReadLimit 150001))
    (.putObject s3-client request)))

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

(defn link-file-object-to-application [application-id file-id]
  {:pre [(string? application-id) (string? file-id)]}
  (let [temp-bucket (bucket-name nil)
        target-bucket (bucket-name application-id)]
    (create-bucket-if-not-exists target-bucket)
    (.copyObject s3-client temp-bucket file-id target-bucket file-id)
    (.deleteObject s3-client temp-bucket file-id)))

(defn download [application-id file-id]
  {:pre [(string? file-id)]}
  (try
    (let [object (.getObject s3-client (bucket-name application-id) ^String file-id)
          metadata (.getObjectMetadata object)]
      {:content     (fn [] (.getObjectContent object))
       :contentType (.getContentType metadata)
       :size        (.getContentLength metadata)
       :filename    (get (.getUserMetadata metadata) "filename")
       :fileId      file-id
       :metadata    (-> (into {} (.getUserMetadata metadata))
                        (dissoc "filename")
                        (update "uploaded" util/->int)
                        (keywordize-keys))
       :application application-id})
    (catch AmazonS3Exception ex
      (timbre/error ex "Error occurred when trying to retrieve" file-id "from S3 bucket" (bucket-name application-id)))))
