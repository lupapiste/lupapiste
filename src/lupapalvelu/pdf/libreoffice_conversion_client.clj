(ns lupapalvelu.pdf.libreoffice-conversion-client
  (:require [clj-http.client :as http]
            [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.pdf.libreoffice-template :refer :all]
            [lupapalvelu.pdf.libreoffice-template-history :as history]
            [lupapalvelu.pdf.libreoffice-template-verdict :as verdict]
            [lupapalvelu.pdf.libreoffice-template-statement :as statement]
            [sade.core :refer [def-]]
            [sade.strings :as ss]
            [sade.env :as env]
            [clojure.java.io :as io])
  (:import (org.apache.commons.io FilenameUtils)
           (java.io File ByteArrayOutputStream ByteArrayInputStream)))


(def- url (str "http://" (env/value :libreoffice :host) ":" (or (env/value :libreoffice :port) 8001)))

(defn enabled? []
  (boolean (if (or (not (env/feature? :libreoffice)) (ss/blank? (env/value :libreoffice :host)))
             (info "Danger: Libreoffice PDF/A conversion feature disabled or service host not configured")
             true)))

(defn- convert-to-pdfa-request [filename content]
  (http/post url
             {:as               :stream
              :throw-exceptions false
              :multipart        [{:name      filename
                                  :part-name "file"
                                  :mime-type (mime/mime-type (mime/sanitize-filename filename))
                                  :encoding  "UTF-8"
                                  :content   content
                                  }]}))

(defn- fallback [filename original-bytes error-message]
  (error "libreoffice conversion error: " error-message)
  {:filename           filename
   :content            (ByteArrayInputStream. original-bytes)
   :archivabilityError :libre-conversion-error})

(defn convert-to-pdfa [filename content]
  ; Content input stream can be read only once (see LPK-1596).
  ; Content is read the first time when it is streamed to LibreOffice and
  ; second time if the conversion fails and we fall back to original content.
  (with-open [in content, out (ByteArrayOutputStream.)]
    (io/copy in out)
    (let [bytes (.toByteArray out)]
      (try
        (let [{:keys [status body]} (convert-to-pdfa-request filename (ByteArrayInputStream. bytes) )]
          (if (= status 200)
            {:filename   (str (FilenameUtils/removeExtension filename) ".pdf")
             :content    body
             :archivable true}
            (fallback filename bytes (str "response status is" status " with body: " body))))
        (catch Throwable t
          (fallback filename bytes (.getMessage t)))))))

(defn generate-casefile-pdfa [application lang]
  (let [filename (str (localize lang "caseFile.heading") ".fodt")
        tmp-file (File/createTempFile (str "casefile-" (name lang) "-") ".fodt")]
    (history/write-history-libre-doc application lang tmp-file)
    (:content (convert-to-pdfa filename (io/input-stream tmp-file)))))


(defn generate-verdict-pdfa [application verdict-id paatos-idx lang dst-file]
  (debug "Generating PDF/A for verdict: " verdict-id ", paatos: " paatos-idx ", lang: " lang)
  (let [filename (str (localize lang "application.verdict.title") ".fodt")
        tmp-file (File/createTempFile (str "verdict-" (name lang) "-") ".fodt")]
    (verdict/write-verdict-libre-doc application verdict-id paatos-idx lang tmp-file)
    (io/copy (:content (convert-to-pdfa filename (io/input-stream tmp-file))) dst-file)
    (io/delete-file tmp-file :silently)))

(defn generate-statment-pdfa-to-file! [application id lang dst-file]
  (debug "Generating PDF/A statement(" id ") for application: " (:id application) ", lang: " lang)
  (let [filename (str (localize lang "application.statement.status") ".fodt")
        tmp-file (File/createTempFile (str "temp-export-statement-" (name lang) "-") ".fodt")]
    (statement/write-statement-libre-doc application id lang tmp-file)
    (io/copy (:content (convert-to-pdfa filename (io/input-stream tmp-file))) dst-file)
    (io/delete-file tmp-file :silently)))
