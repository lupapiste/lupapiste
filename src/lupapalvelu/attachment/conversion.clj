(ns lupapalvelu.attachment.conversion
  (:require [taoensso.timbre :refer [debug]]
            [clojure.java.io :as io]
            [schema.core :as sc]
            [sade.files :as files]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.pdf.pdfa-conversion :as pdf-conversion]
            [lupapalvelu.pdf.libreoffice-conversion-client :as libre-conversion]
            [lupapalvelu.tiff-validation :as tiff-validation]
            [lupapalvelu.attachment.pdf-wrapper :as pdf-wrapper]
            [taoensso.timbre :as timbre]
            [sade.env :as env])
  (:import (java.io File InputStream ByteArrayOutputStream ByteArrayInputStream))
  (:import (org.apache.commons.io FilenameUtils)))


(def archivability-errors #{:invalid-mime-type :invalid-pdfa :invalid-tiff
                            :libre-conversion-error :not-validated :permanent-archive-disabled})

(def libre-conversion-file-types
  #{:application/vnd.openxmlformats-officedocument.presentationml.presentation
    :application/vnd.openxmlformats-officedocument.wordprocessingml.document
    :application/vnd.oasis.opendocument.text
    :application/vnd.ms-powerpoint
    :application/rtf
    :application/msword
    :text/plain})

(def all-convertable-mime-types
  (conj libre-conversion-file-types :image/jpeg :image/tiff :image/png :application/pdf))

(defn libreoffice-conversion-required? 
  "Check if libre office conversion is required for given filename."
  [{:keys [filename]}]
  (boolean (and (libre-conversion/enabled?)
                (libre-conversion-file-types (-> filename
                                                 mime/sanitize-filename
                                                 mime/mime-type
                                                 keyword)))))

(defn ->libre-pdfa!
  "Converts content to PDF/A using Libre Office conversion client.
  Replaces (!) original filename and content with Libre data."
  [filename content]
  (libre-conversion/convert-to-pdfa filename content))

(defmulti convert-file (fn [_ filedata] (keyword (:contentType filedata))))

(defmethod convert-file :application/pdf [application {:keys [content filename]}]
  (if (pdf-conversion/pdf-a-required? (:organization application))
    (let [pdf-file (files/temp-file "lupapiste-attach-converted-pdf-file" ".pdf")] ; This is the output target of pdf2pdf
      (with-open [is (io/input-stream content)
                  bos (ByteArrayOutputStream.)]
        (try
          ; Copy input to a byte array for safe reuse
          (io/copy is bos)
          (let [original-bytes  (.toByteArray bos)
                processing-result (pdf-conversion/convert-to-pdf-a (ByteArrayInputStream. original-bytes)
                                                                   pdf-file
                                                                   {:application application :filename filename})
                ; output-file here is the same file as pdf-file
                {:keys [output-file missing-fonts conversionLog autoConversion] :or {missing-fonts []}} processing-result
                archivability-error (if pdf-conversion/pdf2pdf-enabled? :invalid-pdfa :not-validated)]
            (when-not autoConversion
              ; If the PDF/A was not actually converted, the temp file can be deleted immediately
              (io/delete-file pdf-file :silently))
            (cond
              (:already-valid-pdfa? processing-result) {:archivable true :archivabilityError nil}

              ; If we tried with pdf2pdf and failed, try with LibreOffice next
              (and pdf-conversion/pdf2pdf-enabled?
                   (env/feature? :convert-pdfs-with-libre)
                   (not (:pdfa? processing-result))) (do (timbre/info "File"
                                                                      filename
                                                                      "in application"
                                                                      (:id application)
                                                                      "could not be converted with pdf2pdf, will try libreoffice")
                                                         (->libre-pdfa! filename (ByteArrayInputStream. original-bytes)))

              ; pdf2pdf tool is not enabled, no checking occurs
              (not (:pdfa? processing-result)) {:archivable false
                                                :missing-fonts missing-fonts
                                                :archivabilityError archivability-error
                                                :conversionLog conversionLog}

              (:pdfa? processing-result) {:archivable true
                                          :filename (files/filename-for-pdfa filename)
                                          :archivabilityError nil
                                          ; This is
                                          :content (when output-file (files/temp-file-input-stream output-file))
                                          :autoConversion autoConversion}))
          (catch Throwable t
            (io/delete-file pdf-file :silently)
            (throw t)))))
    {:archivable false :archivabilityError :permanent-archive-disabled}))

(defn- convert-image [image-format application {:keys [content filename]}]
  (if (pdf-conversion/pdf-a-required? (:organization application))
    (files/with-temp-file tmp-file
      (let [pdf-file (files/temp-file "lupapiste-attach-wrapped-image-file" ".pdf") ] ; deleted via temp-file-input-stream
        (try
          (io/copy content tmp-file)
          (pdf-wrapper/wrap! image-format tmp-file pdf-file filename)
          (.close content)
          {:archivable true
           :archivabilityError nil
           :autoConversion true
           :content (files/temp-file-input-stream pdf-file)
           :filename (str (FilenameUtils/removeExtension filename) ".pdf")}
          (catch Exception e
            (timbre/error e "Could not wrap" (name image-format) "image file to PDF/A")
            {:archivable false :archivabilityError :not-validated}))))
    {:archivable false :archivabilityError :permanent-archive-disabled}))

(defmethod convert-file :image/tiff [application filedata]
  (convert-image :tiff application filedata))

(defmethod convert-file :image/jpeg [application filedata]
  (convert-image :jpeg application filedata))

(defmethod convert-file :image/png [application filedata]
  (convert-image :png application filedata))

(defmethod convert-file :default [_ {:keys [content filename] :as filedata}]
  (if (libreoffice-conversion-required? filedata)
    (->libre-pdfa! filename content)
    {:archivable false :archivabilityError :invalid-mime-type}))


(sc/defschema ConversionResult
  {:archivable                       sc/Bool
   :archivabilityError               (sc/maybe (apply sc/enum archivability-errors))
   (sc/optional-key :missing-fonts)  [sc/Str]
   (sc/optional-key :autoConversion) sc/Bool
   (sc/optional-key :content)        (sc/cond-pre File InputStream)
   (sc/optional-key :filename)       sc/Str
   (sc/optional-key :conversionLog)  [sc/Str]})

(defn archivability-conversion
  "Validates file for archivability, and converts content if needed. Content type must be defined correctly.
  Returns ConversionResult map."
  [application filedata]
  {:pre  [(every? (partial contains? filedata) [:filename :contentType :content])]
   :post [(nil? (sc/check ConversionResult %))]}
  (convert-file application filedata))
