(ns lupapalvelu.pdf.pdfa-conversion
  (:require [clojure.java.shell :as shell]
            [clojure.pprint :refer [pprint]]
            [clojure.core.memoize :as memo]
            [taoensso.timbre :refer [trace debug debugf info infof warn error fatal]]
            [sade.strings :as ss]
            [clojure.java.io :as io]
            [sade.env :as env]
            [lupapalvelu.statistics :as statistics]
            [lupapalvelu.organization :as organization])
  (:import [java.io File IOException FileNotFoundException]
           [com.lowagie.text.pdf PdfReader]))

(defn- executable-exists? [executable]
  (try
    (do
      (shell/sh executable)
      true)
    (catch IOException e
      false)))

(defn- find-pdf2pdf-executable []
  (let [exetuable-name "pdf2pdf"]
    (when (executable-exists? exetuable-name)
      exetuable-name)))

(def pdf2pdf-executable
  (memo/ttl find-pdf2pdf-executable
    :ttl/threshold 120000))

(defn- pdf2pdf-key []
  (env/value :pdf2pdf :license-key))

(defn- pdftools-pdfa-command [input-file output-file]
  [(pdf2pdf-executable) "-mp" "-rd" "-lk" (pdf2pdf-key) input-file output-file])

(defn- parse-log-file [output-filename]
  (let [log-filename (str (ss/substring output-filename 0 (- (count output-filename) 4)) "-log.txt")]
    (try (with-open [reader (io/reader log-filename)]
           (vec (line-seq reader)))
         (catch FileNotFoundException fnf
           []))))

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

(defn- run-pdf-to-pdf-a-conversion [input-file output-file]
  (let [{:keys [exit err]} (apply shell/sh (pdftools-pdfa-command input-file output-file))
        log-lines (parse-log-file output-file)]
    (case exit
      0 {:pdfa? true
         :already-valid-pdfa? (pdf-was-already-compliant? log-lines)
         :output-file (File. output-file)}
      5 (do (warn "pdf2pdf conversion was not lossless")
            (warn log-lines)
            {:pdfa? true
             :output-file (File. output-file)})
      6 (let [error-lines (parse-errors-from-log-lines log-lines)]
          (if-let [fonts (parse-missing-fonts-from-log-lines error-lines)]
            {:pdfa? false
             :missing-fonts fonts}
            (do (error error-lines)
                {:pdfa? false})))
      (do (error "pdf2pdf error:" err "exit status:" exit)
          (error (parse-errors-from-log-lines log-lines))
          {:pdfa? false}))))

(defn- get-pdf-page-count [input-file]
  (with-open [reader (PdfReader. input-file)]
    (.getNumberOfPages reader)))

(defn- store-converted-page-count [result count]
  (let [db-key (cond
                 (:already-valid-pdfa? result) :copied-pages
                 (:pdfa? result) :converted-pages
                 :else :invalid-pages)]
    (statistics/store-pdf-conversion-page-count db-key count)))

(defn convert-to-pdf-a [pdf-file & [target-file-path]]
  "Takes PDF File and returns a File that is PDF/A"
  (if (and (pdf2pdf-executable) (pdf2pdf-key))
    (try
      (debug "Trying to convert PDF to PDF/A")
      (let [temp-file-path (.getCanonicalPath pdf-file)
            page-count (get-pdf-page-count temp-file-path)
            pdf-a-file-path (or target-file-path (str temp-file-path "-pdfa.pdf"))
            conversion-result (run-pdf-to-pdf-a-conversion temp-file-path pdf-a-file-path)]
        (store-converted-page-count conversion-result page-count)
        (if (:pdfa? conversion-result)
          (debug "Converted to " pdf-a-file-path)
          (debug "Could not convert the file"))
        conversion-result)
      (catch Exception e
        (error "Error in PDF/A conversion, using original" e)
        {:pdfa? false}))
    (do (info "Cannot find pdf2pdf executable or license key for PDF/A conversion, using original")
        {:pdfa? false})))

(defn pdf-a-required? [organization-id]
  (and (env/feature? :arkistointi)
       (organization/some-organization-has-archive-enabled? #{organization-id})))

(defn convert-file-to-pdf-in-place [src-file]
  "Convert a PDF file to PDF/A in place. Fail-safe, if conversion fails returns false otherwie true.
   Original file is overwritten."
  (let [temp-file (File/createTempFile "lupapiste.pdf.a." ".tmp")]
    (try
      (let [conversion-result (convert-to-pdf-a src-file (.getCanonicalPath temp-file))]
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
        (.deleteOnExit temp-file)))))

(defn ensure-pdf-a-by-organization
  "Ensures PDF file PDF/A compatibility status if the organization uses permanent archive"
  [src-file organization-id]
  (if-not (pdf-a-required? organization-id)
    (do
      (debug "PDF/A conversion not required") false)
    (convert-file-to-pdf-in-place src-file)))
