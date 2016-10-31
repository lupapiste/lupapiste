(ns lupapalvelu.attachment.conversion
  (:require [taoensso.timbre :refer [debug]]
            [clojure.java.io :as io]
            [schema.core :as sc]
            [sade.env :as env]
            [sade.files :as files]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.pdf.pdfa-conversion :as pdf-conversion]
            [lupapalvelu.pdf.libreoffice-conversion-client :as libre-conversion]
            [lupapalvelu.tiff-validation :as tiff-validation]
            [lupapalvelu.attachment.pdf-wrapper :as pdf-wrapper])
  (:import (java.io File InputStream))
  (:import (org.apache.commons.io FilenameUtils)))


(def archivability-errors #{:invalid-mime-type :invalid-pdfa :invalid-tiff :libre-conversion-error :not-validated})

(def libre-conversion-file-types
  #{:application/vnd.openxmlformats-officedocument.presentationml.presentation
    :application/vnd.openxmlformats-officedocument.wordprocessingml.document
    :application/vnd.oasis.opendocument.text
    :application/vnd.ms-powerpoint
    :application/rtf
    :application/msword
    :text/plain})


(defn libreoffice-conversion-required? [{:keys [filename attachment-type]}]
  "Check if libre office conversion is required.
  Attachment type deprecation warning: attachment-type check is not needed after convert-all-attachments
  feature is enabled in production."
  {:pre [(map? attachment-type)]}
  (let [mime-type (mime/mime-type (mime/sanitize-filename filename))
        {:keys [type-group type-id]} attachment-type]
    (and (libre-conversion/enabled?)
         (or (env/feature? :convert-all-attachments)        ;; TODO remove attachment-type check when feature is used in prod
             (and (= :paatoksenteko (keyword type-group))
                  (#{:paatos :paatosote} (keyword type-id))))
         (libre-conversion-file-types (keyword mime-type)))))

(defn ->libre-pdfa!
  "Converts content to PDF/A using Libre Office conversion client.
  Replaces (!) original filename and content with Libre data.
  Adds :archivable + :autoConversion / :archivabilityError key depending on conversion result."
  [filename content]
  (let [result (libre-conversion/convert-to-pdfa filename content)]
    (if (:archivabilityError result)
      (assoc result :archivable false)
      (assoc result :archivabilityError nil))))

(defmulti convert-file (fn [_ filedata] (keyword (:contentType filedata))))

(defmethod convert-file :application/pdf [application {:keys [content filename]}]
  (if (pdf-conversion/pdf-a-required? (:organization application))
    (let [temp (files/temp-file "lupapiste-attach-file" ".pdf")] ; deleted in finally
      (try
        (io/copy content temp)
        (let [processing-result (pdf-conversion/convert-to-pdf-a temp {:application application :filename filename})]
          (cond
            (:already-valid-pdfa? processing-result) {:archivable true :archivabilityError nil :content (:output-file processing-result)}
            (not (:pdfa? processing-result)) {:archivable false :missing-fonts (or (:missing-fonts processing-result) []) :archivabilityError (if pdf-conversion/pdf2pdf-enabled? :invalid-pdfa :not-validated)}
            (:pdfa? processing-result) {:archivable true
                                        :filename (files/filename-for-pdfa filename)
                                        :archivabilityError nil
                                        :content (:output-file processing-result)
                                        :autoConversion (:autoConversion processing-result)}))
        (finally
          (io/delete-file temp :silently))))
    {:archivable false :archivabilityError :not-validated}))

(defmethod convert-file :image/tiff [_ {:keys [content]}]
  (let [tmp-file (files/temp-file "lupapiste-attach-tif-file" ".tif")] ; deleted in finally
    (try
      (io/copy content tmp-file)
      (let [valid? (tiff-validation/valid-tiff? tmp-file)]
        {:archivable valid? :archivabilityError (when-not valid? :invalid-tiff)})
      (finally
        (io/delete-file tmp-file :silently)))))

(defmethod convert-file :image/jpeg [application {:keys [content filename] :as filedata}]
  (if (and (env/feature? :convert-all-attachments)
           (pdf-conversion/pdf-a-required? (:organization application)))
    (let [tmp-file (files/temp-file "lupapiste-attach-jpg-file" ".jpg") ; deleted in finally
          pdf-file (files/temp-file "lupapiste-attach-wrapped-jpeg-file" ".pdf") ; deleted via temp-file-input-stream
          pdf-title filename]
      (try
        (io/copy content tmp-file)
        (pdf-wrapper/wrap! tmp-file pdf-file pdf-title)
        {:archivable true
         :archivabilityError nil
         :autoConversion true
         :content (files/temp-file-input-stream pdf-file)
         :filename (str (FilenameUtils/removeExtension filename) ".pdf")}
        (catch Exception e
          {:archivable false :archivabilityError :not-validated})
        (finally
          (io/delete-file tmp-file :silently))))
    {:archivable false :archivabilityError :not-validated}))

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
   (sc/optional-key :filename)       sc/Str})

(defn archivability-conversion
  "Validates file for archivability, and converts content if needed. Content type must be defined correctly.
  Returns ConversionResult map."
  [application filedata]
  {:pre  [(every? (partial contains? filedata) [:filename :contentType :content])]
   :post [(nil? (sc/check ConversionResult %))]}
  (convert-file application filedata))
