(ns lupapalvelu.attachment
  (:use [monger.operators]
        [lupapalvelu.core]
        [lupapalvelu.log]
        [clojure.java.io :only [reader file]]
        [clojure.string :only [split join trim]])
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.strings :as strings]))

;;
;; Constants
;;
(def default-version {:major 0, :minor 0})

;;
;; Helpers
;;

(defn equal-versions? [ver1 ver2]
  (and ver1 ver2 (= (:major ver1) (:major ver2)) (= (:minor ver1) (:minor ver2))))

;;
;; Metadata
;;

(defn attachment-types-for
  ""
  [application-id]
  [:muu])


;; Reads mime.types file provided by Apache project.
;; Ring has also some of the most common file extensions mapped, but is missing
;; docx and other MS Office formats.
(def mime-types
  (into {} (for [line (line-seq (reader (file "resources/private/mime.types")))
                 :let [l (trim line)
                       type-and-exts (split l #"\s+")
                       mime-type (first type-and-exts)]
                 :when (and (not (.isEmpty l)) (not (.startsWith l "#")))]
             (into {} (for [ext (rest type-and-exts)] [ext mime-type])))))

(def mime-type-pattern
  (re-pattern
    (join "|" [
          "(image/.+)"
          "(text/(plain|rtf))"
          (str "(application/("
               (join "|" [
                     "pdf" "postscript"
                     "rtf" "msword" "vnd\\.ms-excel" "vnd\\.ms-powerpoint"
                     "vnd\\.oasis\\.opendocument\\..+"
                     "vnd\\.openxmlformats-officedocument\\..+"]) "))")])))


(defn mime-type [filename]
  (when filename
    (get mime-types (.toLowerCase (strings/suffix filename ".")))))

(defn allowed-file? [filename]
  (when-let [type (mime-type filename)]
      (re-matches mime-type-pattern type)))

;;
;; Upload commands
;;
(defcommand "create-attachment"
  {:parameters [:id]
   :roles      [:applicant :authority]
   :states     [:draft :open]}
  [{{application-id :id} :data created :created}]
  (let [attachment-id (mongo/create-id)
        attachment-model {:id attachment-id
                          :type nil
                          :state :none
                          :latestVersion   {:version default-version}
                          :versions []
                          :comments []}]
    (mongo/update-by-id mongo/applications application-id
      {$set {:modified created, (str "attachments." attachment-id) attachment-model}})
    (ok :applicationId application-id :attachmentId attachment-id)))

(defn- next-attachment-version [current-version user]
  (if (= (keyword (:role user)) :authority)
    {:major (:major current-version), :minor (inc (:minor current-version))}
    {:major (inc (:major current-version)), :minor 0}))

(defn- set-attachment-version
  [application-id attachment-id file-id type filename content-type size now user]
  (when-let [application (mongo/by-id mongo/applications application-id)]
    (let [latest-version (-> application :attachments (get (keyword attachment-id)) :latestVersion :version)
          next-version (next-attachment-version latest-version user)
          version-model {
                  :version  next-version
                  :fileId   file-id
                  :created  now
                  :accepted nil
                  :user    (security/summary user)
                  ; File name will be presented in ASCII when the file is downloaded.
                  ; Conversion could be done here as well, but we don't want to lose information.
                  :filename filename
                  :contentType content-type
                  :size size}
          type-key (str "attachments." attachment-id ".type")
          attachment-model {:modified now
                 (str "attachments." attachment-id ".modified") now
                 (str "attachments." attachment-id ".state")  :added
                 (str "attachments." attachment-id ".latestVersion") version-model}]

        ; TODO check return value and try again with new version number
        (mongo/update-by-query
          mongo/applications
          {:_id application-id
           (str "attachments." attachment-id ".latestVersion.version.major") (:major latest-version)
           (str "attachments." attachment-id ".latestVersion.version.minor") (:minor latest-version)}
          {$set (if (equal-versions? default-version latest-version)
                  (assoc attachment-model type-key type)
                  attachment-model)
           $push {(str "attachments." attachment-id ".versions") version-model}}))))

(defcommand "upload-attachment"
  {:parameters [:id :attachmentId :type :filename :tempfile :size]
   :roles      [:applicant :authority]
   :states     [:draft :open]}
  [{created :created
    user    :user
    {:keys [id attachmentId type filename tempfile size]} :data}]
  (debug "Create GridFS file: %s %s %s %s %s %d" id attachmentId type filename tempfile size)
  (let [file-id (mongo/create-id)
        sanitazed-filename (strings/suffix (strings/suffix filename "\\") "/")]
    (if (allowed-file? sanitazed-filename)
      (let [content-type (mime-type sanitazed-filename)]
        (mongo/upload file-id file-id sanitazed-filename content-type tempfile created)
        (set-attachment-version id attachmentId file-id type sanitazed-filename content-type size created user)
        (.delete (file tempfile))
        (ok))
      (fail "Illegal file type"))))

;;
;; Delete
;;

(defcommand "delete-empty-attachment"
  {:parameters [:id :attachmentId]
   :roles      [:applicant :authority]
   :states     [:draft :open]}
  [{{:keys [id attachmentId]} :data}]
  (mongo/update-by-query
          mongo/applications
          {:_id id
           (str "attachments." attachmentId ".latestVersion.version.major") (:major default-version)
           (str "attachments." attachmentId ".latestVersion.version.minor") (:minor default-version)}
          {$unset {(str "attachments." attachmentId) 1}})

  (ok))

;;
;; Download
;;

(defn- get-attachment [attachmentId]
  ;; FIXME access rights
  (mongo/download attachmentId))

(def windows-filename-max-length 255)

(defn encode-filename
  "Replaces all non-ascii chars and other that the allowed punctuation with dash.
   UTF-8 support would have to be browser specific, see http://greenbytes.de/tech/tc2231/"
  [unencoded-filename]
  (when-let [de-accented (strings/de-accent unencoded-filename)]
      (clojure.string/replace
        (strings/last-n windows-filename-max-length de-accented)
        #"[^a-zA-Z0-9\.\-_ ]" "-")))

(defn output-attachment [attachmentId download]
  (debug "file download: attachmentId=%s" attachmentId)
  (if-let [attachment (get-attachment attachmentId)]
    (let [response
          {:status 200
           :body ((:content attachment))
           :headers {"Content-Type" (:content-type attachment)
                     "Content-Length" (str (:content-length attachment))}}]
        (if download
          (assoc-in response [:headers "Content-Disposition"]
            (format "attachment;filename=\"%s\"" (encode-filename (:file-name attachment))) )
          response))
    {:status 404}))
