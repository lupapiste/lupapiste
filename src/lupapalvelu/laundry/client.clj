(ns lupapalvelu.laundry.client
  (:require [clojure.java.io :as io]
            [hato.client :as hc]
            [integrant.core :as ig]
            [lupapalvelu.laundry.client-schema :as client-schema]
            [lupapalvelu.laundry.gcp-iap-client :as gcp-iap-client]
            [lupapalvelu.laundry.proto :as proto]
            [lupapalvelu.logging :as log]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc :refer [defschema]])
  (:import [clojure.lang ExceptionInfo]
           [java.io ByteArrayInputStream File InputStream]))

(set! *warn-on-reflection* true)

;; Inherit this concrete implementation from the generic `:laundry/client`.
(derive ::client-impl :laundry/client)

(defmethod ig/assert-key ::client-impl [_ args]
  (client-schema/valid-args! args))

(defn- handle-stream-exception [msg ^Exception e]
  (let [ed   (ex-data e)
        body (some-> ed :body slurp)]
    (log/error msg "status:" (:status ed) "message:" (or body (.getMessage e)))))

(defrecord Client [url iap-oauth-client-id]
  proto/LaundryClient
  (get-pdf-package [_ content]
    (try
      (log/debug "Requesting /api/get-pdf-package from Laundry...")
      (let [start (System/currentTimeMillis)
            resp  (http/post-transit (str url "/api/get-pdf-package")
                                     content
                                     (util/assoc-when {:as :byte-array}
                                                      :oauth-token (when iap-oauth-client-id
                                                                     (gcp-iap-client/get-id-token iap-oauth-client-id))))]
        (log/debug "Laundry /api/get-pdf-package request took" (- (System/currentTimeMillis) start) "ms")
        (if (and (= (:status resp) 200) (:body resp))
          {:ok              true
           :pdf-file-stream (ByteArrayInputStream. ^bytes (:body resp))}
          {:ok   false
           :text (:error resp)}))
      (catch Exception ex
        (handle-stream-exception "Exception when requesting html->pdf from laundry" ex)
        {:ok false}))))

(defmethod ig/init-key ::client-impl [k {:keys [url iap-oauth? iap-oauth-client-id]}]
  (log/infof "Starting %s..." k)
  (map->Client (cond-> {:url url}
                 (and iap-oauth? iap-oauth-client-id)
                 (assoc :iap-oauth-client-id iap-oauth-client-id))))

(def url (env/value :laundry :url))
(def jwt-aud-claim (when (env/feature? :iap-oauth-client)
                     (env/value :laundry :iap-oauth-client-id)))

(def archivability-errors
  "Note: only includes errors which are currently in use.
  Extended schema for all errors is in `lupapalvelu.attachment.conversion/archivability-errors`"
  #{:invalid-mime-type :invalid-pdfa :not-validated :permanent-archive-disabled :tiff-archiving :jpeg-archiving})

(defschema ConversionResultAndFile
  {:result                 (st/merge {:archivable         sc/Bool
                                      :archivabilityError (sc/maybe (apply sc/enum archivability-errors))}
                                     (st/optional-keys {:missing-fonts  [sc/Str]
                                                        :autoConversion sc/Bool
                                                        :conversionLog  [sc/Str]}))
   (sc/optional-key :file) {:fileId                     sssc/FileId
                            :contentType                (sc/eq "application/pdf")
                            :size                       sc/Int
                            (sc/optional-key :filename) sc/Str}})

(defn- oauth-token-if-required []
  (when jwt-aud-claim
    {:oauth-token (gcp-iap-client/get-id-token jwt-aud-claim)}))

(sc/defn ^:always-validate convert-to-pdfa :- ConversionResultAndFile
  "Validates the given object as PDF/A or converts if necessary. If the file is converted, a new object will be placed
   in the same bucket with \"-pdfa\" appended to the object key. This only works with GCS storage."
  [bucket :- sc/Str
   object-key :- sc/Str]
  (try
    (let [{:keys [object-key
                  bucket
                  size
                  already-valid-pdfa?
                  pdfa?
                  conversion-log
                  missing-fonts]} (-> (http/post-transit (str url "/api/convert-to-pdfa")
                                                         {:bucket     bucket
                                                          :object-key object-key}
                                                         (oauth-token-if-required))
                                      http/decode-response-body)]
      (merge {:result (util/assoc-when {:archivable         (boolean pdfa?)
                                        :archivabilityError (when-not pdfa?
                                                              :invalid-pdfa)}
                                       :missing-fonts missing-fonts
                                       :autoConversion (and pdfa?
                                                            (not already-valid-pdfa?))
                                       :conversionLog conversion-log)}
             (when (and bucket object-key)
               {:file {:fileId      (last (ss/split object-key #"/"))
                       :contentType "application/pdf"
                       :size        size}})))
    (catch Throwable t
      (let [body       (:body (ex-data t))
            error-type (cond
                         (false? (:pdfa? body))
                         :invalid-pdfa

                         (and (string? body)
                              (re-find #"MIME type :[\w/]+ is not supported." body))
                         :invalid-mime-type

                         :else
                         :not-validated)]
        (when (= error-type :not-validated)
          (log/error t "PDF/A conversion with laundry failed"))
        {:result (util/assoc-when {:archivable         false
                                   :archivabilityError error-type}
                                  :missing-fonts (:missing-fonts body)
                                  :conversionLog (:conversion-log body))}))))

(defn- convert-to-input-stream ^InputStream [content-type input-content]
  (-> (hc/post (str url "/api/convert-to-pdfa-stream")
               (merge {:http-client  @http/*http-client
                       :content-type content-type
                       :as           :stream
                       :body         input-content}
                      (oauth-token-if-required)))
      :body))



(defn convert-input-to-pdfa-file
  "Converts the provided `input-content`, which should be a File or InputStream of the given `content-type`,
   and writes the output into the provided `output-file`."
  [^String content-type input-content ^File output-file]
  (try
    (with-open [is (convert-to-input-stream content-type input-content)]
      (io/copy is output-file))
    true
    (catch Exception e
      (handle-stream-exception "PDF/A conversion into file failed." e))))

(defn convert-libre-template-to-pdfa-stream
  ^InputStream [input-content]
  (try
    (convert-to-input-stream "application/vnd.oasis.opendocument.text" input-content)
    (catch Exception e
      (handle-stream-exception "Libre template PDF/A conversion failed." e))))

(def default-margins {:top    "18mm"
                      :bottom "18mm"
                      :left   "9mm"
                      :right  "9mm"})

(defn html-to-pdf
  "Sends content, header and footer to vaahtera-laundry.
  Laundry returns PDF file stream, which is stored in returning hashmap
  under :pdf-file-stream key.

  On error/exception, returns sade.core/fail hashmap with error."
  ([client html-content header-content footer-content page-title]
   (html-to-pdf client html-content header-content footer-content page-title nil))
  ([client html-content header-content footer-content page-title margins]
   {:pre [(some? client)]}
   (proto/get-pdf-package client (util/assoc-when {:title page-title
                                                   :pages [{:page-type :html
                                                            :title     page-title
                                                            :html      html-content
                                                            :format    "A4"
                                                            :margin    (or margins
                                                                           default-margins)}]}
                                                  :footer-template footer-content
                                                  :header-template header-content))))

(defn unzip-attachments
  "Tries to unzip the file at the given bucket and object-key in vaahtera-laundry. Returns data about the files
   inside and possibly their metadata mapped from an index file based on column-mapping."
  [bucket object-key column-mapping]
  (try
    (-> (http/post-transit (str url "/api/unzip-attachments")
                           {:bucket     bucket
                            :object-key object-key
                            :columns    column-mapping}
                           (oauth-token-if-required))
        http/decode-response-body)
    (catch Exception ex
      (log/error ex "Exception when requesting unzipping from laundry")
      {:ok   false
       :text (.getMessage ex)})))

(defn read-spreadsheet
  "Reads the Excel-compatible file at the given bucket and object-key in
  Laundry (`read-sheet-as-strings` endpoint). Returns a list of rows on the first sheet of
  the workbook. Each row is a list of strings representing column values."
  [bucket object-key]
  (try
    (-> (http/post-transit (str url "/api/read-sheet-as-strings")
                           {:bucket     bucket
                            :object-key object-key
                            :sheet-ix   0}
                           (oauth-token-if-required))
        http/decode-response-body)
    (catch Exception ex
      (log/error ex "Error requesting read-sheet-as-strings from laundry"))))

(sc/defn read-spreadsheet-fast :- (sc/conditional
                                    :rows {:rows       [[sc/Str]]
                                           :window1904 sc/Bool}
                                    :else {:error                  sc/Keyword
                                           (sc/optional-key :text) ssc/NonBlankStr})
  "Reads the `.xlsx` file at the given `bucket` with the `object-key` in
  Laundry (`read-sheet-rows-fast` endpoint). On success, returns map with the following
  fields:

 `:rows`: The rows as string vectors. Thus, the client code is responsible for
 interpreting the actual types (e.g., dates).
 `:window1904`: True, if the spreadsheet uses 1904 windowing for dates.

  On failure, returns an error map:

  `:error` Error code that can be localized.
  `:text` Human-readable (technical) error message, if any (optional)."
  [bucket object-key]
  (try
    (-> (http/post-transit (str url "/api/read-sheet-rows-fast")
                           {:bucket     bucket
                            :object-key object-key}
                           (oauth-token-if-required))
        http/decode-response-body)
    (catch Exception e
      (let [msg (ex-message e)]
        (log/error e "Laundry failed for read-sheet-rows-fast:" msg)
        (util/assoc-when {:error :error.read-spreadsheet-fast}
                         :text  (ss/blank-as-nil msg))))))

(defschema StampingRequest {:bucket        sc/Str
                            :object-key    sc/Str
                            :target-bucket sc/Str
                            :target-key    sc/Str
                            :pdfa?         sc/Bool
                            :stamp         (st/merge {:page         (sc/enum :all :first :last)
                                                      :x-margin     sc/Int
                                                      :y-margin     sc/Int
                                                      :scale        sc/Num
                                                      :transparency sc/Num
                                                      :info-fields  [sc/Str]}
                                                     (st/optional-keys {:qr-code {:data sc/Str
                                                                                  :size sc/Int}}))})

(defschema UpdatedObjectResponse {:bucket       sc/Str
                                  :object-key   sc/Str
                                  :content-type sc/Str
                                  :size         sc/Int})

(sc/defn stamp-attachment :- UpdatedObjectResponse
  "Request vaahtera-laundry to stamp the file at the provided bucket and object-key and upload the result to
   the target-bucket and target-key. Stamp will be drawn based on the data in `:stamp` key.
   If `:pdfa?` is `true` and the file is originally a PDF, the result will be PDF/A as well."
  [params :- StampingRequest]
  (try
    (-> (http/post-transit (str url "/api/stamp-file")
                           params
                           (oauth-token-if-required))
        http/decode-response-body)
    (catch Exception ex
      (log/error ex "Error requesting stamp-file from laundry for params:" params)
      (throw ex))))

(defschema RotateRequest {:bucket        sc/Str
                          :object-key    sc/Str
                          :target-bucket sc/Str
                          :target-key    sc/Str
                          :degrees       sc/Int})

(sc/defn rotate-pdf :- UpdatedObjectResponse
  "Request vaahtera-laundry to rotate the PDF file at the provided bucket and object-key and upload the result to
   the target-bucket and target-key. Positive number of degrees rotates clockwise."
  [params :- RotateRequest]
  (try
    (-> (http/post-transit (str url "/api/rotate-pdf")
                           params
                           (oauth-token-if-required))
        http/decode-response-body)
    (catch Exception ex
      (log/error ex "Error requesting rotate-pdf from laundry for params:" params)
      (throw ex))))

(defn- do-check-image-properties!
  [bucket object-id]
  (try
    {:properties (-> (http/post-transit (str url "/api/get-image-properties")
                                        {:bucket     bucket
                                         :object-key object-id}
                                        (oauth-token-if-required))
                     http/decode-response-body)}
    (catch ExceptionInfo e
      {:error (ex-data e)})))

(def max-dimension
  "National archives specifies a maximum resolution of 65535 x 65535."
  65535)

(defn verify-image-archivable!
  "Verify (using laundry) that given object is an image matching the content type
  and has valid properties wrt. archiving. Blocks.
  Returns true if valid, throws otherwise."
  [{:keys [bucket object-id expected-content-type]}]
  (log/infof "Verifying if object %s is %s" object-id expected-content-type)
  (let [{:keys [error properties]} (do-check-image-properties! bucket object-id)]
    (cond
      error      (let [{:keys [status body]} error]
                   (log/warnf "Failed to verify file is image, status: %d, error: %s" status body)
                   (throw (ex-info "Failed to verify image" {:status                status
                                                             :body                  body
                                                             :expected-content-type expected-content-type})))
      properties (let [{:keys [width height content-type]} properties
                       valid?                              (and width height content-type
                                                                (<= 1 width max-dimension)
                                                                (<= 1 height max-dimension)
                                                                (= content-type expected-content-type))]
                   (when-not valid?
                     (log/warnf "Invalid image. Content-type %s, w: %d, h:%d" content-type width height)
                     (throw (ex-info "Invalid image" {:content-type          content-type
                                                      :expected-content-type expected-content-type
                                                      :width                 width
                                                      :height                height})))
                   valid?))))
