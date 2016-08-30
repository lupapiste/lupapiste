(ns lupapalvelu.xml.disk-writer
  (:require [taoensso.timbre :as timbre :refer [info error]]
            [me.raynes.fs :as fs]
            [clojure.data.xml :as data-xml]
            [clojure.java.io :as io]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.xml.validator :as validator]))

(defn get-file-name-on-server [file-id file-name]
  (str file-id "_" (ss/encode-filename file-name)))

(defn get-submitted-filename [application-id]
  (str  application-id "_submitted_application.pdf"))

(defn get-current-filename [application-id]
  (str application-id "_current_application.pdf"))

(defn- write-attachments [attachments output-dir]
  (doseq [attachment attachments]
    (when-let [filename (:filename attachment)]
      (let [file-id (:fileId attachment)
            attachment-file (mongo/download file-id)
            content (:content attachment-file)
            attachment-file-name (str output-dir "/" filename)
            attachment-file (io/file attachment-file-name)]
        (if (nil? content)
          (do
            (info "Content for attachment file-id " file-id " is nil")
            (fail! :error.attachment.no-content))
          (do
            (with-open [out (io/output-stream attachment-file)
                        in (content)]
              (io/copy in out))
            (fs/chmod "+rw" attachment-file)))))))

(defn- write-application-pdf-versions [output-dir application submitted-application lang]
  (let [id (:id application)
        submitted-file (io/file (str output-dir "/" (get-submitted-filename id)))
        current-file (io/file (str output-dir "/" (get-current-filename id)))]
    (pdf-export/generate submitted-application lang submitted-file)
    (pdf-export/generate application lang current-file)
    (fs/chmod "+rw" submitted-file)
    (fs/chmod "+rw" current-file)))

(defn write-to-disk
  "Writes XML string to disk and copies attachments from database. XML is validated before writing.
   Returns a sequence of attachment fileIds that were written to disk."
  [application attachments xml schema-version output-dir & [submitted-application lang file-suffix]]
  {:pre [(string? output-dir)]
   :post [%]}

  (let [file-name  (if file-suffix
                     (str output-dir "/" (:id application) "_" (now) "_" file-suffix)
                     (str output-dir "/" (:id application) "_" (now)))
        tempfile   (io/file (str file-name ".tmp"))
        outfile    (io/file (str file-name ".xml"))
        xml-s      (data-xml/emit-str xml)]

    (try
      (validator/validate xml-s (permit/permit-type application) schema-version)
      (catch org.xml.sax.SAXParseException e
        (fail! :error.integration.create-message :details (.getMessage e))))

    (fs/mkdirs output-dir)

    (try
      (spit tempfile xml-s)
      (catch java.io.FileNotFoundException e
        (when (fs/exists? tempfile)
          (fs/delete tempfile))
        (error e (.getMessage e))
        (fail! :error.sftp.user.does.not.exist :details (.getMessage e))))

    (write-attachments attachments output-dir)

    (when (and submitted-application lang)
      (write-application-pdf-versions output-dir application submitted-application lang))

    (when (fs/exists? outfile) (fs/delete outfile))
    (fs/rename tempfile outfile)
    (fs/chmod "+rw" outfile))

  (->>
    attachments
    (map :fileId)
    (remove nil?)))
