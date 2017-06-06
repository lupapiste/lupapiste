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
            [taoensso.timbre :as timbre])
  (:import (java.io File InputStream))
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


(defn libreoffice-conversion-required? [{:keys [filename]}]
  "Check if libre office conversion is required for given filename."
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
    (let [pdf-file (files/temp-file "lupapiste-attach-converted-pdf-file" ".pdf")
          original-content (files/temp-file "lupapiste-attach-conversion-input" ".pdf")] ; deleted via temp-file-input-stream, when input was not converted or in catch
      (try
        (io/copy content original-content)
        (let [processing-result (pdf-conversion/convert-to-pdf-a original-content pdf-file {:application application :filename filename})
              {:keys [output-file missing-fonts] auto-conversion :autoConversion :or {missing-fonts []}} processing-result]
          (when-not auto-conversion (io/delete-file pdf-file :silently))
          (cond
            (:already-valid-pdfa? processing-result) {:archivable true :archivabilityError nil}

            ; If we tried with pdf2pdf and failed, try with LibreOffice next
            (and pdf-conversion/pdf2pdf-enabled?
                 (not (:pdfa? processing-result))) (do (timbre/info "File" filename "in application" (:id application) "could not be converted with pdf2pdf, will try libreoffice")
                                                       (->libre-pdfa! filename (files/temp-file-input-stream original-content)))

            ; pdf2pdf tool is not enabled, no checking occurs
            (not (:pdfa? processing-result)) {:archivable false
                                              :archivabilityError :not-validated}

            (:pdfa? processing-result) {:archivable true
                                        :filename (files/filename-for-pdfa filename)
                                        :archivabilityError nil
                                        :content (when output-file (files/temp-file-input-stream output-file))
                                        :autoConversion auto-conversion}))
        (catch Throwable t
          (io/delete-file pdf-file :silently)
          (io/delete-file original-content :silently)
          (throw t))))
    {:archivable false :archivabilityError :permanent-archive-disabled}))

(defmethod convert-file :image/tiff [_ {:keys [content]}]
  (files/with-temp-file tmp-file
    (io/copy content tmp-file)
    (let [valid? (tiff-validation/valid-tiff? tmp-file)]
      {:archivable valid? :archivabilityError (when-not valid? :invalid-tiff)})))

(defmethod convert-file :image/jpeg [application {:keys [content filename]}]
  (if (pdf-conversion/pdf-a-required? (:organization application))
    (files/with-temp-file tmp-file
      (let [pdf-file (files/temp-file "lupapiste-attach-wrapped-jpeg-file" ".pdf") ] ; deleted via temp-file-input-stream
        (try
          (io/copy content tmp-file)
          (pdf-wrapper/wrap! tmp-file pdf-file filename)
          {:archivable true
           :archivabilityError nil
           :autoConversion true
           :content (files/temp-file-input-stream pdf-file)
           :filename (str (FilenameUtils/removeExtension filename) ".pdf")}
          (catch Exception e
            (timbre/error "Could not wrap JPEG" e)
            {:archivable false :archivabilityError :not-validated}))))
    {:archivable false :archivabilityError :permanent-archive-disabled}))

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
