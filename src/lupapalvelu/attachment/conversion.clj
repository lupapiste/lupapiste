(ns lupapalvelu.attachment.conversion
  (:require [taoensso.timbre :refer [debug]]
            [schema.core :as sc]
            [clojure.java.io :as io]
            [lupapalvelu.attachment.file :as file]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.pdf.pdfa-conversion :as pdf-conversion]
            [lupapalvelu.pdf.libreoffice-conversion-client :as libre-conversion]
            [lupapalvelu.tiff-validation :as tiff-validation]
            [sade.env :as env])
  (:import (java.io File InputStream)))


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
  {:pre [(map? attachment-type)]}
  (let [mime-type (mime/mime-type (mime/sanitize-filename filename))
        {:keys [type-group type-id]} attachment-type]
    (and (libre-conversion/enabled?)
         (or (env/feature? :convert-all-attachments)
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
    (let [temp (File/createTempFile "lupapiste-attach-file" ".pdf")]
      (try
        (io/copy content temp)
        (let [processing-result (pdf-conversion/convert-to-pdf-a temp {:application application :filename filename})]
          (cond
            (:already-valid-pdfa? processing-result) {:archivable true :archivabilityError nil}
            (not (:pdfa? processing-result)) {:archivable false :missing-fonts (or (:missing-fonts processing-result) []) :archivabilityError :invalid-pdfa}
            (:pdfa? processing-result) {:archivable true
                                        :filename (file/filename-for-pdfa filename)
                                        :archivabilityError nil
                                        :content (:output-file processing-result)
                                        :autoConversion (:autoConversion processing-result)}))
        (finally
          (io/delete-file temp :silently))))
    {:archivable false :archivabilityError :not-validated}))

(defmethod convert-file :image/tiff [_ {:keys [content]}]
  (let [valid? (tiff-validation/valid-tiff? content)]
    {:archivable valid? :archivabilityError (when-not valid? :invalid-tiff)}))

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
  "Validates file for archivability, and converts content if needed.
  Returns ConversionResult map."
  [application filedata]
  {:pre  [(contains? filedata :contentType) (contains? filedata :content)]
   :post [(nil? (sc/check ConversionResult %))]}
  (convert-file application filedata))
