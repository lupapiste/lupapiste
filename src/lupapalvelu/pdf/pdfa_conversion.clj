(ns lupapalvelu.pdf.pdfa-conversion
  (:require [clojure.java.shell :as shell]
            [clojure.pprint :refer [pprint]]
            [clojure.core.memoize :as memo]
            [taoensso.timbre :refer [trace debug debugf info infof warn error errorf fatal]]
            [com.netflix.hystrix.core :as hystrix]
            [sade.strings :as ss]
            [clojure.java.io :as io]
            [sade.env :as env]
            [lupapalvelu.statistics :as statistics]
            [lupapalvelu.organization :as organization])
  (:import [java.io File IOException FileNotFoundException InputStream]
           [com.lowagie.text.pdf PdfReader]
           [com.netflix.hystrix HystrixCommandProperties]))

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

(defn- pdftools-pdfa-command
  "Conversion error mask 9028 means that the following things will cause the conversion to fail:
   - Visual differences in output file
   - Removal of embedded files
   - Error during linearization of output file
   - Removal of digital signature because of conversion
   - OCR error occurred (OCR not currently in use)"
  [input-file output-file cl]
  [(pdf2pdf-executable) "-mp" "-rd" "-lk" (pdf2pdf-key) "-cl" cl "-cem" "9028" "-fd" "/usr/share/fonts/msttcore" input-file output-file])

(defn- pdftools-analyze-command [input-file output-file]
  [(pdf2pdf-executable) "-ma" "-rd" "-lk" (pdf2pdf-key) "-cl" "pdfa-2b" input-file output-file])

(defn- parse-log-file [output-filename]
  (try
    (let [log-filename (str (ss/substring output-filename 0 (- (count output-filename) 4)) "-log.txt")
          lines (with-open [reader (io/reader log-filename)]
                  (vec (line-seq reader)))]
      (io/delete-file log-filename :silently)
      lines)
    (catch FileNotFoundException fnf
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
        required-part (last (re-find #"The XMP property 'pdfaid:part' has the invalid value '(\d)'. Required is '\d'." log))
        level (if (= required-part "1") "b" "u")
        cl (str "pdfa-" (or required-part "2") level)
        not-prints (re-find #"The value of the key . is 'Not Print' but must be 'Print'" log)
        missing-appearances (re-find #"The appearance dictionary doesn't contain an entry" log)]
    (when (or not-prints missing-appearances)
      (warn "PDF has elements with 'not print' or undefined appearance, PDF/A will probably look weird. Application:" (:id application) "file:" filename "Log:" log))
    (debug "Determined required compliance to be:" cl)
     cl))

(defn- run-pdf-to-pdf-a-conversion [input-file output-file opts]
  (let [cl (compliance-level input-file output-file opts)
        {:keys [exit err]} (apply shell/sh (pdftools-pdfa-command input-file output-file cl))
        log-lines (parse-log-file output-file)]
    (cond
      (= exit 0) {:pdfa? true
                  :already-valid-pdfa? (pdf-was-already-compliant? log-lines)
                  :output-file (File. ^String output-file)}
      (= exit 5) (do (warn "PDF/A conversion failed because it can't be done losslessly")
                     (warn log-lines)
                     (io/delete-file output-file :silently)
                     {:pdfa? false})
      (#{6 139} exit) (let [error-lines (parse-errors-from-log-lines log-lines)]
                        (io/delete-file output-file :silently)
                        (if-let [fonts (parse-missing-fonts-from-log-lines error-lines)]
                          {:pdfa? false
                           :missing-fonts fonts}
                          (do (warn "PDF/A conversion failed probably because of missing fonts")
                              (warn error-lines)
                              {:pdfa? false})))
      :else (do (warn "pdf2pdf error:" err "exit status:" exit)
                (warn (parse-errors-from-log-lines log-lines))
                {:pdfa? false}))))

(defn- get-pdf-page-count [input-file]
  (try
    (with-open [reader (PdfReader. ^String input-file)]
      (.getNumberOfPages reader))
    (catch Exception e
      (error "Error occurred when trying to read page count from PDF file" e)
      0)))

(defn- store-converted-page-count [result count]
  (let [db-key (cond
                 (:already-valid-pdfa? result) :copied-pages
                 (:pdfa? result) :converted-pages
                 :else :invalid-pages)]
    (statistics/store-pdf-conversion-page-count db-key count)))

(defn- analyze-and-convert-to-pdf-a [pdf-file {:keys [target-file-path] :as opts}]
  (if (and (pdf2pdf-executable) (pdf2pdf-key))
    (try
      (info "Trying to convert PDF to PDF/A")
      (let [stream? (instance? InputStream pdf-file)
            file-path (if stream?
                        (let [temp-file (File/createTempFile "lupapiste-pdfa-stream-conversion" ".pdf")]
                          (io/copy pdf-file temp-file)
                          (.getCanonicalPath temp-file))
                        (.getCanonicalPath pdf-file))
            pdf-a-file-path (or target-file-path (str file-path "-pdfa.pdf"))
            conversion-result (run-pdf-to-pdf-a-conversion file-path pdf-a-file-path opts)]
        (->> (get-pdf-page-count pdf-a-file-path)
             (store-converted-page-count conversion-result))
        (if (:pdfa? conversion-result)
          (if (pos? (-> conversion-result :output-file .length))
            (info "Converted to PDF/A " pdf-a-file-path)
            (throw (Exception. (str "PDF/A conversion resulted in empty file. Original file: " file-path))))
          (info "Could not convert the file to PDF/A"))
        conversion-result)
      (catch Exception e
        (error "Error in PDF/A conversion, using original" e)
        {:pdfa? false}))
    (do (warn "Cannot find pdf2pdf executable or license key for PDF/A conversion, using original")
        {:pdfa? false})))

(hystrix/defcommand convert-to-pdf-a
  "Takes a PDF File and returns a File that is PDF/A
  opts is a map possible containing the following keys:
  {:target-file-path \"Output conversion to this file\"
   :application      \"Application data for logging purposes\"
   :filename         \"Original filename for logging purposes\"}"
  {:hystrix/group-key   "Attachment"
   :hystrix/command-key "Convert to PDF/A with PDF Tools utility"
   :hystrix/init-fn     (fn [_ setter]
                          (doto setter
                            (.andCommandPropertiesDefaults
                              (.withExecutionTimeoutInMilliseconds (HystrixCommandProperties/Setter) (* 5 60 1000)))))}
  [pdf-file & [opts]]
  (analyze-and-convert-to-pdf-a pdf-file opts))

(defn pdf-a-required? [organization-id]
  (organization/some-organization-has-archive-enabled? #{organization-id}))

(defn convert-file-to-pdf-in-place [src-file]
  "Convert a PDF file to PDF/A in place. Fail-safe, if conversion fails returns false otherwise true.
   Original file is overwritten."
  (let [temp-file (File/createTempFile "lupapiste.pdf.a." ".tmp")]
    (try
      (let [conversion-result (convert-to-pdf-a src-file {:target-file-path (.getCanonicalPath temp-file)})]
        (cond
          (:already-valid-pdfa? conversion-result) (debug "File was valid PDF/A, no conversion")
          (:pdfa? conversion-result) (do
                                       (io/copy temp-file src-file)
                                       (debug "File converted to PDF/A"))
          :else (error "PDF/A conversion failed, file is not PDF/A" ))
        (:pdfa? conversion-result))
      (catch Exception e
        (do
          (error "Unknown exception in PDF/A conversion, file was not converted." e)
          false))
      (finally
        (io/delete-file temp-file :silently)))))

(defn ensure-pdf-a-by-organization
  "Ensures PDF file PDF/A compatibility status if the organization uses permanent archive"
  [src-file organization-id]
  (if-not (pdf-a-required? organization-id)
    (do
      (debug "PDF/A conversion not required") false)
    (convert-file-to-pdf-in-place src-file)))
