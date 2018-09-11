(ns lupapalvelu.pdf.libreoffice-conversion-client
  (:require [clojure.java.io :as io]
            [taoensso.timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.pdf.libreoffice-template :refer :all]
            [lupapalvelu.pdf.libreoffice-template-history :as history]
            [lupapalvelu.pdf.libreoffice-template-verdict :as verdict]
            [lupapalvelu.pdf.libreoffice-template-statement :as statement]
            [lupapalvelu.pdf.pdfa-conversion :as pdfa-conversion]
            [sade.core :refer [def-]]
            [sade.env :as env]
            [sade.files :as files]
            [sade.http :as http]
            [sade.strings :as ss])
  (:import (org.apache.commons.io FilenameUtils)
           (java.io ByteArrayOutputStream ByteArrayInputStream)
           [java.nio.file Files]))


(def- url (cond
            (ss/not-blank? (env/value :libreoffice :url)) (env/value :libreoffice :url)
            (ss/not-blank? (env/value :libreoffice :host)) (str "http://" (env/value :libreoffice :host) ":" (or (env/value :libreoffice :port) 8001))))

(defn enabled? []
  (if-not url
    (do (warn "Danger: Libreoffice PDF/A conversion feature disabled or service host not configured")
        false)
    true))

(defn- convert-to-pdfa-request [filename content]
  (http/post url
             {:as               :stream
              :throw-exceptions true
              :conn-timeout     (* 5 1000)                  ; wait connection for 5 seconds
              :multipart        [{:name      filename
                                  :part-name "file"
                                  :mime-type (mime/mime-type (mime/sanitize-filename filename))
                                  :encoding  "UTF-8"
                                  :content   content}]}))

(defn- success [filename content]
  {:filename   (str (FilenameUtils/removeExtension filename) ".pdf")
   :content    content
   :archivable true
   :autoConversion true
   :archivabilityError nil})

(defn- fallback [filename original-content error-message]
  (error "libreoffice conversion error: " error-message)
  {:filename           filename
   :content            original-content
   :archivable         false
   :archivabilityError :libre-conversion-error})

(defn- convert-and-validate [content filename original-content]
  (files/with-temp-file converted-temp-file
    (try
      (let [converted-content (:body (convert-to-pdfa-request filename content))
            baos (ByteArrayOutputStream.)
            _ (io/copy converted-content baos)
            bytes (.toByteArray baos)
            ; Validate the result with pdf2pdf. If it's not enabled, assume PDF/A compatibility.
            pdf2pdf-result (pdfa-conversion/convert-to-pdf-a (ByteArrayInputStream. bytes) converted-temp-file {:assume-pdfa-compatibility true})]
        (if (:pdfa? pdf2pdf-result)
          (->> converted-temp-file
               (.toPath)
               (Files/readAllBytes)
               (ByteArrayInputStream.)
               (success filename))
          (merge (select-keys pdf2pdf-result [:missing-fonts :conversionLog])
                 {:archivable         false
                  :filename           (str (FilenameUtils/removeExtension filename) ".pdf")
                  :autoConversion     true
                  :content            (ByteArrayInputStream. bytes)
                  :archivabilityError :invalid-pdfa})))
      (catch Throwable t
        ; On LibraOffice connection failure or other unexpected error
        (fallback filename original-content (.getMessage t))))))

(defprotocol PDFAConversion
  (to-pdfa [content filename] "Converts content to PDF/A using LibreOffice"))

(extend-protocol PDFAConversion

  java.io.File
  (to-pdfa [content filename]
    (convert-and-validate content filename content))

  java.io.InputStream
  ; Content input stream can be read only once (see LPK-1596).
  ; Content is read the first time when it is streamed to LibreOffice and
  ; second time if the conversion fails and we fall back to original content.
  (to-pdfa [content filename]
    (with-open [in content, out (ByteArrayOutputStream.)]
      (io/copy in out)
      (let [bytes (.toByteArray out)]
        (convert-and-validate (ByteArrayInputStream. bytes) filename (ByteArrayInputStream. bytes))))))

(defn convert-to-pdfa [filename content]
  (to-pdfa content filename))

(defn generate-casefile-pdfa [application lang libre-file]
  (debugf "Generating PDF/A for application %s in %s " (:id application) lang)
  (let [filename (str (localize lang "caseFile.heading") ".fodt")]
    (history/write-history-libre-doc application lang libre-file)
    (:content (convert-to-pdfa filename libre-file))))

(defn generate-verdict-pdfa [application verdict-id paatos-idx lang dst-file]
  (debugf "Generating PDF/A for verdict %s/paatos %s in %s" verdict-id paatos-idx lang)
  (let [filename (str (localize lang "application.verdict.title") ".fodt")]
    (files/with-temp-file tmp-file
      (verdict/write-verdict-libre-doc application verdict-id paatos-idx lang tmp-file)
      (io/copy (:content (convert-to-pdfa filename tmp-file)) dst-file))))

(defn generate-statement-pdfa-to-file! [application id lang dst-file]
  (debugf "Generating PDF/A statement %s for application %s in %s" id (:id application) lang)
  (let [filename (str (localize lang "application.statement.status") ".fodt")]
    (files/with-temp-file tmp-file
      (statement/write-statement-libre-doc application id lang tmp-file)
      (io/copy (:content (convert-to-pdfa filename tmp-file)) dst-file))))
