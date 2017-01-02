(ns lupapalvelu.pdf.pdfa-conversion
  (:require [clojure.java.shell :as shell]
            [clojure.java.io :as io]
            [clojure.core.memoize :as memo]
            [taoensso.timbre :refer [trace debug debugf info infof warn warnf error errorf fatal]]
            [com.netflix.hystrix.core :as hystrix]
            [sade.strings :as ss]
            [sade.env :as env]
            [sade.files :as files]
            [lupapalvelu.statistics :as statistics]
            [lupapalvelu.organization :as organization])
  (:import [java.io File IOException FileNotFoundException InputStream]
           [com.lowagie.text.pdf PdfReader]
           [com.netflix.hystrix HystrixCommandProperties HystrixCommand$Setter HystrixThreadPoolProperties]))

(defn- executable-exists? [executable]
  (try
    (do
      (shell/sh executable)
      true)
    (catch IOException e
      false)))

(defn- find-pdf2pdf-executable []
  (let [executable-name "pdf2pdf"]
    (when (executable-exists? executable-name)
      executable-name)))

(def pdf2pdf-executable
  (memo/ttl find-pdf2pdf-executable
    :ttl/threshold 120000))

(defn- pdf2pdf-key []
  (env/value :pdf2pdf :license-key))

(def pdf2pdf-enabled? (and (string? (pdf2pdf-executable)) (string? (pdf2pdf-key))))

(defn- pdf2pdf-command [input-file output-file & options]
  (let [input-file-path  (.getCanonicalPath input-file)
        output-file-path (.getCanonicalPath output-file)]
     (concat [(pdf2pdf-executable) "-lk" (pdf2pdf-key)] options [input-file-path output-file-path])))

(defn- pdftools-pdfa-command
  "Conversion error mask 68 means that the following things will cause the conversion to fail:
   - Visual differences in output file (4)
   - Removal of embedded files (64)"
  [input-file output-file cl]
  (pdf2pdf-command input-file output-file "-ad" "-au" "-rd" "-cl" cl "-cem" "68" "-fd" "/usr/share/fonts/msttcore"))

(defn- pdftools-analyze-command [input-file output-file]
  (pdf2pdf-command input-file output-file "-ma" "-rd" "-cl" "pdfa-2u"))

(defn- parse-log-file [output-file]
  (try
    (let [output-filename (.getCanonicalPath output-file)
          log-filename (str (ss/substring output-filename 0 (- (count output-filename) 4)) "-log.txt")
          lines (with-open [reader (io/reader log-filename)]
                  (vec (line-seq reader)))]
      (io/delete-file log-filename :silently)
      lines)
    (catch FileNotFoundException fnf
      (error "Returning empty log because of" (.getMessage fnf))
      [])))

(defn- parse-errors-from-log-lines [lines]
  (when (seq lines)
    (let [[title-index _] (first (filter (fn [[_ line]] (ss/contains? line "Performing post analysis")) (map-indexed vector lines)))
          [summary-index _] (first (filter (fn [[_ line]] (ss/contains? line "Post analysis errors")) (map-indexed vector lines)))
          start-index (inc (or title-index 0))
          end-index (dec (or summary-index (count lines)))]
      (subvec lines start-index end-index))))

(defn- parse-missing-fonts-from-log-lines [lines]
  (->> (map (fn [line] (second (re-find #"\"The font ([^\"]+) must be embedded.\"" line))) lines)
    (remove nil?)
    (seq)))

(defn- pdf-was-already-compliant? [lines]
  (ss/contains? (apply str lines) "will be copied only since it is already conformant"))

(defn- compliance-level [input-file output-file {:keys [application filename]}]
  (apply shell/sh (pdftools-analyze-command input-file output-file))
  (let [log (apply str (parse-log-file output-file))
        required-part (or (last (re-find #"The XMP property 'pdfaid:part' has the invalid value '(\d)'. Required is '\d" log))
                          (when (re-find #"Processing embedded file" log) "3")
                          "2")
        required-conformance (last (re-find #"The XMP property 'pdfaid:conformance' has the invalid value '(\w)'. Required is '\w" log))
        level (ss/lower-case (or required-conformance (if (= required-part "1") "b" "u")))
        cl (str "pdfa-" required-part level)
        not-prints (re-find #"The value of the key . is 'Not Print' but must be 'Print'" log)
        missing-appearances (re-find #"The appearance dictionary doesn't contain an entry" log)]
    (when (or not-prints missing-appearances)
      (warn "PDF has elements with 'not print' or undefined appearance, PDF/A will probably look weird. Application:" (:id application) "file:" filename "Log:" log))
    (debug "Determined required compliance to be:" cl)
     cl))

(defn- run-pdf-to-pdf-a-conversion [input-file output-file opts]
  {:pre [(instance? File input-file) (instance? File output-file)]}
  (let [cl (compliance-level input-file output-file opts)
        {:keys [exit err]} (apply shell/sh (pdftools-pdfa-command input-file output-file cl))
        log-lines (parse-log-file output-file)]
    (cond
      (= exit 0) {:pdfa? true
                  :already-valid-pdfa? (pdf-was-already-compliant? log-lines)
                  :output-file output-file
                  :autoConversion (not (pdf-was-already-compliant? log-lines))}
      (= exit 5) (do (warn "PDF/A conversion failed because it can't be done losslessly")
                     (warn log-lines)
                     {:pdfa? false})
      (#{6 139} exit) (let [error-lines (parse-errors-from-log-lines log-lines)]
                        (if-let [fonts (parse-missing-fonts-from-log-lines error-lines)]
                          {:pdfa? false
                           :missing-fonts fonts}
                          (do (warn "PDF/A conversion failed probably because of missing fonts")
                              (warn error-lines)
                              {:pdfa? false})))
      (= exit 10) (do
                    (error "pdf2pdf - not a valid license")
                    {:pdfa? false})
      :else (do (warnf "pdf2pdf failed with exit status %s, stderr: %s" exit err)
                (warn (parse-errors-from-log-lines log-lines))
                {:pdfa? false}))))

(defn- get-pdf-page-count [input-file-path]
  (try
    (with-open [reader (PdfReader. ^String input-file-path)]
      (.getNumberOfPages reader))
    (catch Exception e
      (warn "Failed to read page count from PDF file - " e)
      0)))

(defn- store-converted-page-count [{:keys [already-valid-pdfa? pdfa? output-file]} original-file]
  (let [original-file-path (.getCanonicalPath original-file)
        db-key (cond
                 already-valid-pdfa? :copied-pages
                 pdfa? :converted-pages
                 :else :invalid-pages)
        file-path (if output-file (.getCanonicalPath output-file) original-file-path)]
    (->> (get-pdf-page-count file-path)
         (statistics/store-pdf-conversion-page-count db-key))))

(defn- analyze-and-convert-to-pdf-a [pdf-file output-file opts]
  {:pre [(or (instance? InputStream pdf-file) (instance? File pdf-file))
         (instance? File output-file)]}
  (if (and (pdf2pdf-executable) (pdf2pdf-key))
    ; run-pdf-to-pdf-a-conversion operates on files.
    ; Content must be copied to temp file if the input is an InputStream (not a File))
    (let [temp-input-file (when (instance? InputStream pdf-file) (files/temp-file "lupapiste-pdfa-stream-conversion" ".pdf"))] ; deleted in finally
      (try
        (when temp-input-file (io/copy pdf-file temp-input-file))
        (let [input-file (or temp-input-file pdf-file)
              original-filename (:filename opts (.getName input-file))
              conversion-result (run-pdf-to-pdf-a-conversion input-file output-file opts)]
          (if (:pdfa? conversion-result)
            (if (pos? (-> conversion-result :output-file .length))
              (do
                (store-converted-page-count conversion-result input-file)
                (infof "Converted '%s' to PDF/A" original-filename))
              (throw (Exception. (format "PDF/A conversion of '%s' resulted in empty file" original-filename))))
            (infof "Could not convert '%s' to PDF/A" original-filename))
          conversion-result)
        (catch Exception e
          (error "Error in PDF/A conversion, using original" e)
          {:pdfa? false})
        (finally
          (when temp-input-file
            (io/delete-file temp-input-file :silently)))))
    (do (warn "Cannot find pdf2pdf executable or license key for PDF/A conversion, using original")
        {:pdfa? false})))

(hystrix/defcommand convert-to-pdf-a
  "Takes a PDF File and returns a File that is PDF/A
  opts is a map possible containing the following keys:
  {:application      \"Application data for logging purposes\"
   :filename         \"Original filename for logging purposes\"}"
  {:hystrix/group-key   "Attachment"
   :hystrix/command-key "Convert to PDF/A with PDF Tools utility"
   :hystrix/thread-pool-key :pdf-tools-thread-pool
   :hystrix/init-fn     (fn [_ ^HystrixCommand$Setter setter]
                          (doto setter
                            (.andCommandPropertiesDefaults
                              (.withExecutionTimeoutInMilliseconds (HystrixCommandProperties/Setter) (* 5 60 1000)))
                            (.andThreadPoolPropertiesDefaults
                              (.withMaxQueueSize (HystrixThreadPoolProperties/Setter) Integer/MAX_VALUE))))}
  [pdf-file output-file & [opts]]
  (analyze-and-convert-to-pdf-a pdf-file output-file opts))

(defn file-is-valid-pdfa? [pdf-file]
  {:pre [(or (instance? InputStream pdf-file) (instance? File pdf-file))]}
  (if (and (pdf2pdf-executable) (pdf2pdf-key))
    (files/with-temp-file temp-file
      (files/with-temp-file output-file
        (let [input-file (if (instance? InputStream pdf-file)
                           (do (io/copy pdf-file temp-file)
                               temp-file)
                           pdf-file)
              {:keys [exit]} (apply shell/sh (pdftools-analyze-command input-file output-file))]
          (= exit 0))))
    (do (warn "Cannot find pdf2pdf executable or license key for PDF/A conversion, cannot validate file")
        false)))

(defn convert-file-to-pdf-in-place [pdf-file]
  {:pre [(or (instance? InputStream pdf-file) (instance? File pdf-file))]}
  "Convert a PDF file to PDF/A in place. Fail-safe, if conversion fails returns false otherwise true.
   Original file is overwritten."
  (files/with-temp-file temp-file
    (try
      (let [conversion-result (convert-to-pdf-a pdf-file temp-file)]
        (cond
          (:already-valid-pdfa? conversion-result) (debug "File was valid PDF/A, no conversion")
          (:pdfa? conversion-result) (do
                                       (io/copy temp-file pdf-file)
                                       (debug "File converted to PDF/A"))
          :else (warn "PDF/A conversion failed, file is not PDF/A" ))
        (:pdfa? conversion-result))
      (catch Exception e
        (do
          (error "Unknown exception in PDF/A conversion, file was not converted." e)
          false)))))

(defn pdf-a-required? [organization-or-id]
  (cond
    (string? organization-or-id) (organization/some-organization-has-archive-enabled? #{organization-or-id})
    (map? organization-or-id)    (true? (:permanent-archive-enabled organization-or-id))
    (delay? organization-or-id)  (true? (:permanent-archive-enabled @organization-or-id))
    :else (throw (IllegalArgumentException. (str "Not an organization: " organization-or-id)))))

(defn ensure-pdf-a-by-organization
  "Ensures PDF file PDF/A compatibility status if the organization uses permanent archive"
  [src-file organization-id]
  (if-not (pdf-a-required? organization-id)
    (do
      (debug "PDF/A conversion not required") false)
    (convert-file-to-pdf-in-place src-file)))
