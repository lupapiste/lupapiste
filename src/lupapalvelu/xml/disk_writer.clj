(ns lupapalvelu.xml.disk-writer
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [lupapalvelu.logging :refer [debugf info warn]]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.storage.file-storage :as storage]
            [sade.core :refer [fail!]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [java.io InputStream]))

(defn- download-attachment-content
  "Downloads attachment content, handling both regular attachments and linked files (BIM models)."
  [application attachment]
  (let [file-id (:fileId attachment)]
    (if-let [linked-file (:linked-file attachment)]
      ;; For linked files, use the linked-files download function
      (linked-files/get-linked-attachment-version-file linked-file file-id)
      ;; For regular attachments, use the standard storage download
      (storage/download application file-id))))

(defn write-attachments [application attachments output-dir]
  (doseq [attachment attachments]
    (when-let [filename (:filename attachment)]
      (let [file-id              (:fileId attachment)
            attachment-file      (download-attachment-content application attachment)
            content              (:content attachment-file)
            attachment-file-name (str output-dir "/" filename)
            attachment-file      (io/file attachment-file-name)]
        (debugf "write-attachment: %s" attachment-file-name)
        (if (nil? content)
          (do
            (info "Content for attachment file-id" file-id "is nil")
            (fail! :error.attachment.no-content))
          (do
            (with-open [out             (io/output-stream attachment-file)
                        ^InputStream in (content)]
              (io/copy in out))
            (if (env/dev-mode?)
              (fs/set-posix-file-permissions attachment-file "rwxrwxrwx")
              (fs/set-posix-file-permissions attachment-file "rw-rw-rw-"))))))))

(defn write-file
  "Writes `input` to `filepath`. The directories are created if needed. `input` can be
  anything supported by `io/copy`"
  [filepath input]
  {:pre [(ss/not-blank? (fs/file-name filepath))
         (not (fs/directory? filepath))]}
  (debugf "write-file: %s" filepath)
  (some-> filepath fs/parent fs/create-dirs)
  (io/copy input (io/file filepath))
  (if (env/dev-mode?)
    (try
      (fs/set-posix-file-permissions filepath "rwxrwxrwx")
      (catch Exception _
        (warn "Failed to alter permissions for" filepath)))
    (fs/set-posix-file-permissions filepath "rw-rw-rw-")))

(defn krysp-xml-files
  "Returns list of file paths that have XML extension and match the
  given application."
  [application-id output-dir]
  (let [pattern (re-pattern (str "(?i)" application-id "_.*\\.xml$"))]
    (->> (util/get-files-by-regex output-dir pattern)
         (map str))))

(defn cleanup-output-dir
  "Removes the old KRYSP message files from the output folder. Only
  the messages with the removable kayttotapaus value will be removed."
  [application-id output-dir should-remove?]
  (doseq [file (krysp-xml-files application-id output-dir)
          :when (should-remove? file)]
    (debugf "Remove deprecated %s" file)
    (io/delete-file file true)))
