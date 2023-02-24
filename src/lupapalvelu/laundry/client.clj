(ns lupapalvelu.laundry.client
  (:require [clojure.java.io :as io]
            [hato.client :as hc]
            [lupapalvelu.laundry.gcp-iap-client :as gcp-iap-client]
            [plumbing.core :as p]
            [sade.env :as env]
            [sade.http :as http]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [schema-tools.core :as st]
            [schema.core :as sc]
            [taoensso.timbre :as timbre])
  (:import [java.io ByteArrayInputStream File InputStream]))

(set! *warn-on-reflection* true)

(def url (env/value :laundry :url))
(def jwt-aud-claim (when (env/feature? :iap-oauth-client)
                     (env/value :laundry :iap-oauth-client-id)))

(def archivability-errors #{:invalid-mime-type :invalid-pdfa :not-validated :permanent-archive-disabled})

(sc/defschema ConversionResultAndFile
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
      (merge {:result (p/assoc-when {:archivable         (boolean pdfa?)
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
          (timbre/error t "PDF/A conversion with laundry failed"))
        {:result (p/assoc-when {:archivable         false
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

(defn- handle-stream-exception [msg ^Exception e]
  (let [ed (ex-data e)
        body (some-> ed :body slurp)]
    (timbre/error msg "status:" (:status ed) "message:" (or body (.getMessage e)))))

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
  [input-content]
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
  ([html-content header-content footer-content page-title]
   (html-to-pdf html-content header-content footer-content page-title nil))
  ([html-content header-content footer-content page-title margins]
   (try
     (let [start (System/currentTimeMillis)
           resp  (http/post-transit (str url "/api/get-pdf-package")
                                    (p/assoc-when {:title page-title
                                                   :pages [{:page-type :html
                                                            :title     page-title
                                                            :html      html-content
                                                            :format    "A4"
                                                            :margin    (or margins
                                                                           default-margins)}]}
                                                  :footer-template footer-content
                                                  :header-template header-content)
                                    (merge {:as :byte-array}
                                           (oauth-token-if-required)))]
       (timbre/debug "Laundry /api/get-pdf-package request took" (- (System/currentTimeMillis) start) "ms")
       (if (and (= (:status resp) 200) (:body resp))
         {:ok              true
          :pdf-file-stream (ByteArrayInputStream. ^bytes (:body resp))}
         {:ok   false
          :text (:error resp)}))
     (catch Exception ex
       (handle-stream-exception "Exception when requesting html->pdf from laundry" ex)
       {:ok false}))))

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
      (timbre/error ex "Exception when requesting unzipping from laundry")
      {:ok   false
       :text (.getMessage ex)})))

(defn read-spreadsheet
  "Reads the Excel-compatible file at the given bucket and object-key in vaahtera-laundry. Returns a list of rows
   on the first sheet of the workbook. Each row is a list of strings representing column values."
  [bucket object-key]
  (try
    (-> (http/post-transit (str url "/api/read-sheet-as-strings")
                           {:bucket     bucket
                            :object-key object-key
                            :sheet-ix   0}
                           (oauth-token-if-required))
        http/decode-response-body)
    (catch Exception ex
      (timbre/error ex "Error requesting read-sheet-as-strings from laundry"))))

(sc/defschema StampingRequest {:bucket        sc/Str
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

(sc/defschema UpdatedObjectResponse {:bucket       sc/Str
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
      (timbre/error ex "Error requesting stamp-file from laundry for params:" params)
      (throw ex))))

(sc/defschema RotateRequest {:bucket        sc/Str
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
      (timbre/error ex "Error requesting rotate-pdf from laundry for params:" params)
      (throw ex))))
