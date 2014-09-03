(ns lupapalvelu.attachment-api
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn warnf error errorf fatal]]
            [monger.operators :refer :all]
            [swiss-arrows.core :refer [-<> -<>>]]
            [sade.strings :as ss]
            [sade.util :refer [future*]]
            [lupapalvelu.core :refer [ok fail fail!]]
            [lupapalvelu.action :refer [defquery defcommand defraw update-application application->command notify]]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.attachment :as a]
            [lupapalvelu.user :as user]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.job :as job]
            [lupapalvelu.stamper :as stamper]
            [lupapalvelu.ke6666 :as ke6666]
            [lupapalvelu.statement :as statement]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp])
  (:import [java.util.zip ZipOutputStream ZipEntry]
           [java.io File OutputStream FilterInputStream]))

;; Validators

(defn- attachment-is-not-locked [{{:keys [attachmentId]} :data :as command} application]
  (when (-> (a/get-attachment-info application attachmentId) :locked (= true))
    (fail :error.attachment-is-locked)))

(defn- if-not-authority-states-must-match [state-set {user :user} {state :state}]
  (when (and
          (not (user/authority? user))
          (state-set (keyword state)))
    (fail :error.non-authority-viewing-application-in-verdictgiven-state)))

(def post-verdict-states #{:verdictGiven :constructionStarted :closed})

(defn- attachment-editable-by-applicationState? [application attachmentId userRole]
  (or (ss/blank? attachmentId)
      (let [attachment (a/get-attachment-info application attachmentId)
            attachmentApplicationState (keyword (:applicationState attachment))
            currentState (keyword (:state application))]
        (or (not (post-verdict-states currentState))
            (post-verdict-states attachmentApplicationState)
            (= (keyword userRole) :authority)))))

;;
;; KRYSP
;;

(defcommand move-attachments-to-backing-system
  {:parameters [id lang]
   :roles      [:authority]
   :pre-checks [(partial if-not-authority-states-must-match #{:verdictGiven})
                (permit/validate-permit-type-is permit/R)]
   :states     [:verdictGiven :constructionStarted]
   :description "Sends such attachments to backing system that are not yet sent."}
  [{:keys [created application] :as command}]

  (let [attachments-wo-sent-timestamp (filter
                                        #(and
                                           (pos? (-> % :versions count))
                                           (or
                                             (not (:sent %))
                                             (> (-> % :versions last :created) (:sent %)))
                                           (not= "statement" (-> % :target :type))
                                           (not= "verdict" (-> % :target :type)))
                                        (:attachments application))]
    (if (pos? (count attachments-wo-sent-timestamp))
      (let [organization  (organization/get-organization (:organization application))
            sent-file-ids (mapping-to-krysp/save-unsent-attachments-as-krysp (assoc application :attachments attachments-wo-sent-timestamp) lang organization)
            data-argument (attachment/create-sent-timestamp-update-statements (:attachments application) sent-file-ids created)]
        (update-application command {$set data-argument})
        (ok))
      (fail :error.sending-unsent-attachments-failed))))

;;
;; Types
;;

(defquery attachment-types
  {:parameters [:id]
   :extra-auth-roles [:statementGiver]
   :roles      [:applicant :authority]}
  [{application :application}]
  (ok :attachmentTypes (attachment/get-attachment-types-for-application application)))

(defcommand set-attachment-type
  {:parameters [id attachmentId attachmentType]
   :roles      [:applicant :authority]
   :extra-auth-roles [:statementGiver]
   :states     [:draft :info :open :submitted :complement-needed :verdictGiven :constructionStarted]}
  [{:keys [application user] :as command}]

  (when-not (attachment-editable-by-applicationState? application attachmentId (:role user))
    (fail! :error.pre-verdict-attachment))

  (let [attachment-type (a/parse-attachment-type attachmentType)]
    (if (a/allowed-attachment-type-for-application? application attachment-type)
      (update-application command
        {:attachments {$elemMatch {:id attachmentId}}}
        {$set {:attachments.$.type attachment-type}})
      (do
        (errorf "attempt to set new attachment-type: [%s] [%s]: %s" id attachmentId attachment-type)
        (fail :error.attachmentTypeNotAllowed)))))

;;
;; States
;;

(defcommand approve-attachment
  {:description "Authority can approve attachment, moves to ok"
   :parameters  [id attachmentId]
   :roles       [:authority]
   :states      [:draft :info :open :submitted :complement-needed :verdictGiven :constructionStarted]}
  [{:keys [created] :as command}]
  (update-application command
    {:attachments {$elemMatch {:id attachmentId}}}
    {$set {:modified created
           :attachments.$.state :ok}}))

(defcommand reject-attachment
  {:description "Authority can reject attachment, requires user action."
   :parameters  [id attachmentId]
   :roles       [:authority]
   :states      [:draft :info :open :submitted :complement-needed :verdictGiven :constructionStarted]}
  [{:keys [created] :as command}]
  (update-application command
    {:attachments {$elemMatch {:id attachmentId}}}
    {$set {:modified created
           :attachments.$.state :requires_user_action}}))

;;
;; Create
;;

(defcommand create-attachments
  {:description "Authority can set a placeholder for an attachment"
   :parameters  [:id :attachmentTypes]
   :roles       [:authority]
   :states      [:draft :info :open :submitted :complement-needed :verdictGiven :constructionStarted]}
  [{application :application {attachment-types :attachmentTypes} :data created :created}]
  (if-let [attachment-ids (a/create-attachments application attachment-types created)]
    (ok :applicationId (:id application) :attachmentIds attachment-ids)
    (fail :error.attachment-placeholder)))

;;
;; Delete
;;

(defcommand delete-attachment
  {:description "Delete attachement with all it's versions. Does not delete comments. Non-atomic operation: first deletes files, then updates document."
   :parameters  [id attachmentId]
   :roles       [:applicant :authority]
   :extra-auth-roles [:statementGiver]
   :states      [:draft :info :open :submitted :complement-needed :verdictGiven :constructionStarted]}
  [{:keys [application user]}]

  (when-not (attachment-editable-by-applicationState? application attachmentId (:role user))
    (fail! :error.pre-verdict-attachment))

  (a/delete-attachment application attachmentId)
  (ok))

(defcommand delete-attachment-version
  {:description   "Delete attachment version. Is not atomic: first deletes file, then removes application reference."
   :parameters  [:id attachmentId fileId]
   :roles       [:applicant :authority]
   :extra-auth-roles [:statementGiver]
   :states      [:draft :info :open :submitted :complement-needed :verdictGiven :constructionStarted]}
  [{:keys [application user]}]

  (when-not (attachment-editable-by-applicationState? application attachmentId (:role user))
    (fail! :error.pre-verdict-attachment))

  (if (a/file-id-in-application? application attachmentId fileId)
    (a/delete-attachment-version application attachmentId fileId)
    (fail :file_not_linked_to_the_document)))

;;
;; Download
;;

(defraw "view-attachment"
  {:parameters [:attachment-id]
   :roles      [:applicant :authority]
   :extra-auth-roles [:statementGiver]}
  [{{:keys [attachment-id]} :data user :user}]
  (a/output-attachment attachment-id false (partial a/get-attachment-as user)))

(defraw "download-attachment"
  {:parameters [:attachment-id]
   :roles      [:applicant :authority]
   :extra-auth-roles [:statementGiver]}
  [{{:keys [attachment-id]} :data user :user}]
  (a/output-attachment attachment-id true (partial a/get-attachment-as user)))

(defn- append-gridfs-file [zip file-name file-id]
  (when file-id
    (.putNextEntry zip (ZipEntry. (ss/encode-filename (str file-id "_" file-name))))
    (with-open [in ((:content (mongo/download file-id)))]
      (io/copy in zip))))

(defn- append-stream [zip file-name in]
  (when in
    (.putNextEntry zip (ZipEntry. (ss/encode-filename file-name)))
    (io/copy in zip)))

(defn- append-attachment [zip {:keys [filename fileId]}]
  (append-gridfs-file zip filename fileId))

(defn- get-all-attachments [application lang]
  (let [temp-file (File/createTempFile "lupapiste.attachments." ".zip.tmp")]
    (debugf "Created temporary zip file for attachments: %s" (.getAbsolutePath temp-file))
    (with-open [out (io/output-stream temp-file)]
      (let [zip (ZipOutputStream. out)]
        ; Add all attachments:
        (doseq [attachment (:attachments application)]
          (append-attachment zip (-> attachment :versions last)))
        ; Add submitted PDF, if exists:
        (when-let [submitted-application (mongo/by-id :submitted-applications (:id application))]
          (append-stream zip (i18n/loc "attachment.zip.pdf.filename.submitted") (ke6666/generate submitted-application lang)))
        ; Add current PDF:
        (append-stream zip (i18n/loc "attachment.zip.pdf.filename.current") (ke6666/generate application lang))
        (.finish zip)))
    temp-file))

(defn- temp-file-input-stream [^File file]
  (let [i (io/input-stream file)]
    (proxy [FilterInputStream] [i]
      (close []
        (proxy-super close)
        (when (= (io/delete-file file :could-not) :could-not)
          (warnf "Could not delete temporary file: %s" (.getAbsolutePath file)))))))

(defraw "download-all-attachments"
  {:parameters [:id]
   :roles      [:applicant :authority]
   :extra-auth-roles [:statementGiver]}
  [{:keys [application lang]}]
  (if application
    {:status 200
       :headers {"Content-Type" "application/octet-stream"
                 "Content-Disposition" (str "attachment;filename=\"" (i18n/loc "attachment.zip.filename") "\"")}
       :body (temp-file-input-stream (get-all-attachments application lang))}
    {:status 404
     :headers {"Content-Type" "text/plain"}
     :body "404"}))

;;
;; Upload
;;


(defcommand upload-attachment
  {:parameters [id attachmentId attachmentType filename tempfile size]
   :roles      [:applicant :authority]
   :extra-auth-roles [:statementGiver]
   :pre-checks [attachment-is-not-locked
                (partial if-not-authority-states-must-match #{:sent})]
   :input-validators [(fn [{{size :size} :data}] (when-not (pos? size) (fail :error.select-file)))
                      (fn [{{filename :filename} :data}] (when-not (mime/allowed-file? filename) (fail :error.illegal-file-type)))]
   :states     [:draft :info :answered :open :sent :submitted :complement-needed :verdictGiven :constructionStarted]
   :notified   true
   :on-success [(notify :new-comment)
                open-inforequest/notify-on-comment]
   :description "Reads :tempfile parameter, which is a java.io.File set by ring"}
  [{:keys [created user application] {:keys [text target locked]} :data :as command}]

  (when-not (a/allowed-attachment-type-for-application? application attachmentType)
    (fail! :error.illegal-attachment-type))

  (when-not (attachment-editable-by-applicationState? application attachmentId (:role user))
    (fail! :error.pre-verdict-attachment))

  (when (= (:type target) "statement")
    (when-let [validation-error (statement/statement-owner (assoc-in command [:data :statementId] (:id target)) application)]
      (fail! (:text validation-error))))

  (when-not (a/attach-file! {:application application
                             :filename filename
                             :size size
                             :content tempfile
                             :attachment-id attachmentId
                             :attachment-type attachmentType
                             :comment-text text
                             :target target
                             :locked locked
                             :user user
                             :created created})
    (fail :error.unknown)))


;;
;; Stamping:
;;

(defn- stampable? [attachment]
  (let [latest       (-> attachment :versions last)
        content-type (:contentType latest)
        stamped      (:stamped latest)]
    (and (not stamped) (or (= "application/pdf" content-type) (ss/starts-with content-type "image/")))))

(defn- key-by [f coll]
  (into {} (for [e coll] [(f e) e])))

(defn ->long [v]
  (if (string? v) (Long/parseLong v) v))

(defn- ->file-info [attachment]
  (let [versions   (-> attachment :versions reverse)
        re-stamp?  (:stamped (first versions))
        source     (if re-stamp? (second versions) (first versions))]
    (assoc (select-keys source [:contentType :fileId :filename :size])
           :re-stamp? re-stamp?
           :attachment-id (:id attachment))))

(defn- stamp-attachment! [stamp file-info {:keys [application user now x-margin y-margin transparency]}]
  (let [{:keys [attachment-id contentType fileId filename re-stamp?]} file-info
        temp-file (File/createTempFile "lupapiste.stamp." ".tmp")
        new-file-id (mongo/create-id)]
    (debug "created temp file for stamp job:" (.getAbsolutePath temp-file))
    (with-open [out (io/output-stream temp-file)]
      (stamper/stamp stamp fileId out x-margin y-margin transparency))
    (mongo/upload new-file-id filename contentType temp-file :application (:id application))
    (let [new-version (if re-stamp? ; FIXME these functions should return updates, that could be merged into comment update
                        (a/update-latest-version-content application attachment-id new-file-id (.length temp-file) now)
                        (a/set-attachment-version application attachment-id new-file-id filename contentType (.length temp-file) nil now user true 5 false))])
    (try (.delete temp-file) (catch Exception _))))

(defn- stamp-attachments! [file-infos {:keys [text created organization transparency job-id application] :as context}]
  {:pre [text organization (pos? created)]}
  (let [stamp (stamper/make-stamp (ss/limit text 100) created (ss/limit organization 100) transparency)]
    (doseq [file-info (vals file-infos)]
      (try
        (debug "Stamping" (select-keys file-info [:attachment-id :contentType :fileId :filename :re-stamp?]))
        (job/update job-id assoc (:attachment-id file-info) :working)
        (stamp-attachment! stamp file-info context)
        (job/update job-id assoc (:attachment-id file-info) :done)
        (catch Throwable t
          (errorf t "failed to stamp attachment: application=%s, file=%s" (:id application) (:fileId file-info))
          (job/update job-id assoc (:attachment-id file-info) :error))))))

(defn- stamp-job-status [data]
  (if (every? #{:done :error} (vals data)) :done :runnig))

(defn- make-stamp-job [file-infos context]
  (let [job (job/start (zipmap (keys file-infos) (repeat :pending)) stamp-job-status)]
    (future* (stamp-attachments! file-infos (assoc context :job-id (:id job))))
    job))

(defcommand stamp-attachments
  {:parameters [:id timestamp text organization files xMargin yMargin]
   :roles      [:authority]
   :states     [:submitted :sent :complement-needed :verdictGiven :constructionStarted :closed]
   :description "Stamps all attachments of given application"}
  [{application :application {transparency :transparency} :data :as command}]
  (ok :job (make-stamp-job
             (key-by :attachment-id (map ->file-info (a/get-attachments-infos application files)))
             {:application application
              :user (:user command)
              :text (if-not (ss/blank? text) text (i18n/loc "stamp.verdict"))
              :organization (if-not (ss/blank? organization)
                              organization
                              (let [org (organization/get-organization (:organization application))]
                                (organization/get-organization-name org)))
              :created (cond
                         (number? timestamp) (long timestamp)
                         (ss/blank? timestamp) (:created command)
                         :else (->long timestamp))
              :now      (:created command)
              :x-margin (->long xMargin)
              :y-margin (->long yMargin)
              :transparency (->long (or transparency 0))})))

(defquery stamp-attachments-job
  {:parameters [:job-id :version]
   :roles      [:authority]
   :description "Returns state of stamping job"}
  [{{job-id :job-id version :version timeout :timeout :or {version "0" timeout "10000"}} :data}]
  (assoc (job/status job-id (->long version) (->long timeout)) :ok true))

(defcommand sign-attachments
  {:description "Designers can sign blueprints and other attachments. LUPA-1241"
   :parameters [:id attachmentIds password]
   :feature :attachmentsignature
   :states     [:draft :open :submitted :sent :complement-needed :verdictGiven :constructionStarted]
   :roles [:applicant]}
  [{application :application u :user :as command}]
  (when (seq attachmentIds)
    (if (user/get-user-with-password (:username u) password)
     (let [attachments (a/get-attachments-infos application attachmentIds)
           signature {:user (user/summary u)
                      :created (:created command)}
           updates (reduce (fn [m {attachment-id :id {version :version} :latestVersion}]
                             (merge m (mongo/generate-array-updates
                                        :attachments
                                        (:attachments application)
                                        #(= (:id %) attachment-id)
                                        :signatures (assoc signature :version version))))
                     {} attachments)]

       ; Indexes are calculated on the fly so there is a small change of
       ; a concurrency issue.
       ; FIXME should implement optimistic locking
       (update-application command {$push updates}))
     (do
       ; Throttle giving information about incorrect password
       (Thread/sleep 2000)
       (fail :error.password)))))
