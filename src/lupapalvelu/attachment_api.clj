(ns lupapalvelu.attachment-api
  (:require [clojure.java.io :as io]
            [clojure.set :refer [intersection union]]
            [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn warnf error errorf fatal]]
            [monger.operators :refer :all]
            [swiss.arrows :refer [-<> -<>>]]
            [sade.core :refer [ok fail fail! now def-]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util :refer [future*]]
            [lupapalvelu.action :refer [defquery defcommand defraw update-application application->command notify boolean-parameters] :as action]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.application :as a]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.attachment-metadata :as attachment-meta]
            [lupapalvelu.attachment-accessibility :as access]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.job :as job]
            [lupapalvelu.stamper :as stamper]
            [lupapalvelu.statement :as statement]
            [lupapalvelu.states :as states]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.application :refer [get-operations]]
            [lupapalvelu.pdf.pdfa-conversion :as pdf-conversion]
            [lupapalvelu.pdftk :as pdftk]
            [lupapalvelu.tiff-validation :as tiff-validation]
            [lupapalvelu.tiedonohjaus :as tiedonohjaus])
  (:import [java.io File]))

;; Validators and pre-checks

(defn- attachment-is-not-locked [{{:keys [attachmentId]} :data :as command} application]
  (when (-> (attachment/get-attachment-info application attachmentId) :locked true?)
    (fail :error.attachment-is-locked)))

(defn- attachment-id-is-present-in-application-or-not-set [{{:keys [attachmentId]} :data} {:keys [attachments]}]
  (when-not (or (ss/blank? attachmentId) (some #(= (:id %) attachmentId) attachments))
    (fail :error.attachment.id)))

(defn- if-not-authority-state-must-not-be [state-set {user :user} {state :state}]
  (when (and (not (user/authority? user))
             (state-set (keyword state)))
    (fail :error.non-authority-viewing-application-in-verdictgiven-state)))

(defn- attachment-not-readOnly [{{attachmentId :attachmentId} :data} application]
  (when (-> (attachment/get-attachment-info application attachmentId) :readOnly true?)
    (fail :error.unauthorized
          :desc "Readonly attachments cannot be removed.")))

(defn- attachment-not-required [{{attachmentId :attachmentId} :data user :user} application]
  (when (and (-> (attachment/get-attachment-info application attachmentId) :required true?)
             (not (user/authority? user)))
    (fail :error.unauthorized
          :desc "Only authority can delete attachment templates that are originally bound to the application, or have been manually added by authority.")))

(defn- attachment-editable-by-application-state [{{attachmentId :attachmentId} :data user :user} {current-state :state :as application}]
  (when-not (ss/blank? attachmentId)
    (let [{create-state :applicationState} (attachment/get-attachment-info application attachmentId)]
      (when-not (or (not (states/post-verdict-states (keyword current-state)))
                    (states/post-verdict-states (keyword create-state))
                    (user/authority? user))
        (fail :error.pre-verdict-attachment)))))

(defn- validate-meta [{{meta :meta} :data}]
  (doseq [[k v] meta]
    (when (not-any? #{k} attachment/attachment-meta-types)
      (fail :error.illegal-meta-type :parameters k))))

(defn- validate-operation [{{meta :meta} :data}]
  (let [op (:op meta)]
    (when-let [missing (if op (util/missing-keys op [:id :name]) false)]
      (fail :error.missing-parameters :parameters missing))))

(defn- validate-scale [{{meta :meta} :data}]
  (let [scale (:scale meta)]
    (when (and scale (not (contains? (set attachment/attachment-scales) (keyword scale))))
      (fail :error.illegal-attachment-scale :parameters scale))))

(defn- validate-size [{{meta :meta} :data}]
  (let [size (:size meta)]
    (when (and size (not (contains? (set attachment/attachment-sizes) (keyword size))))
      (fail :error.illegal-attachment-size :parameters size))))

(defn- allowed-attachment-type-for-application? [attachment-type application]
  {:pre [(map? attachment-type)]}
  (let [allowed-types (attachment/get-attachment-types-for-application application)]
    (attachment/allowed-attachment-types-contain? allowed-types attachment-type)))

(defn- validate-attachment-type [{{attachment-type :attachmentType} :data} application]
  (when attachment-type
    (when-not (allowed-attachment-type-for-application? attachment-type application)
      (fail :error.illegal-attachment-type))))

;;
;; Types
;;

(defquery attachment-types
  {:parameters [:id]
   :user-authz-roles auth/all-authz-roles
   :user-roles #{:applicant :authority :oirAuthority}
   :states     states/all-states}
  [{application :application}]
  (ok :attachmentTypes (attachment/get-attachment-types-for-application application)))

(defcommand set-attachment-type
  {:parameters [id attachmentId attachmentType]
   :input-validators [(partial action/non-blank-parameters [:id :attachmentId :attachmentType])]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-writer-roles
   :states     (states/all-states-but (conj states/terminal-states :answered :sent))
   :pre-checks [a/validate-authority-in-drafts attachment-editable-by-application-state]}
  [{:keys [application user created] :as command}]

  (let [attachment-type (attachment/parse-attachment-type attachmentType)]
    (if (allowed-attachment-type-for-application? attachment-type application)
      (attachment/update-attachment-key! command attachmentId :type attachment-type created :set-app-modified? true :set-attachment-modified? true)
      (do
        (errorf "attempt to set new attachment-type: [%s] [%s]: %s" id attachmentId attachment-type)
        (fail :error.illegal-attachment-type)))))
;;
;; Operations
;;

(defquery attachment-operations
  {:parameters [:id]
   :user-authz-roles auth/all-authz-roles
   :user-roles #{:applicant :authority :oirAuthority}
   :states states/all-states}
  [{application :application}]
  (ok :operations (get-operations application)))

;;
;; States
;;

(defcommand approve-attachment
  {:description "Authority can approve attachment, moves to ok"
   :parameters  [id attachmentId]
   :input-validators [(partial action/non-blank-parameters [:attachmentId])]
   :user-roles #{:authority}
   :states      (states/all-states-but (conj states/terminal-states :answered :sent))
   :pre-checks  [a/validate-authority-in-drafts]}
  [{:keys [created] :as command}]
  (attachment/update-attachment-key! command attachmentId :state :ok created :set-app-modified? true :set-attachment-modified? false))

(defcommand reject-attachment
  {:description "Authority can reject attachment, requires user action."
   :parameters  [id attachmentId]
   :input-validators [(partial action/non-blank-parameters [:attachmentId])]
   :user-roles #{:authority}
   :states      (states/all-states-but (conj states/terminal-states :answered :sent))
   :pre-checks  [a/validate-authority-in-drafts]}
  [{:keys [created] :as command}]
  (attachment/update-attachment-key! command attachmentId :state :requires_user_action created :set-app-modified? true :set-attachment-modified? false))

;;
;; Create
;;

(defcommand create-attachments
  {:description "Authority can set a placeholder for an attachment"
   :parameters  [id attachmentTypes]

   :pre-checks [(fn [{{attachment-types :attachmentTypes} :data} application]
                  (when (and attachment-types (not (every? #(allowed-attachment-type-for-application? % application) attachment-types)))
                    (fail :error.unknown-attachment-type)))
                a/validate-authority-in-drafts]
   :input-validators [(partial action/vector-parameters [:attachmentTypes])]
   :user-roles #{:authority :oirAuthority}
   :states      (states/all-states-but (conj states/terminal-states :answered :sent))}
  [{application :application {attachment-types :attachmentTypes} :data created :created}]
  (if-let [attachment-ids (attachment/create-attachments! application attachmentTypes created false true true)]
    (ok :applicationId id :attachmentIds attachment-ids)
    (fail :error.attachment-placeholder)))

;;
;; Delete
;;

(defcommand delete-attachment
  {:description "Delete attachement with all it's versions. Does not delete comments. Non-atomic operation: first deletes files, then updates document."
   :parameters  [id attachmentId]
   :input-validators [(partial action/non-blank-parameters [:attachmentId])]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-writer-roles
   :states      (states/all-states-but (conj states/terminal-states :answered :sent))
   :pre-checks  [a/validate-authority-in-drafts 
                 attachment-not-readOnly 
                 attachment-not-required
                 attachment-editable-by-application-state]}
  [{:keys [application user]}]
  (attachment/delete-attachment! application attachmentId)
  (ok))

(defcommand delete-attachment-version
  {:description   "Delete attachment version. Is not atomic: first deletes file, then removes application reference."
   :parameters  [:id attachmentId fileId originalFileId]
   :input-validators [(partial action/non-blank-parameters [:attachmentId :fileId])]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-writer-roles
   :states      (states/all-states-but (conj states/terminal-states :answered :sent))
   :pre-checks  [a/validate-authority-in-drafts
                 attachment-not-readOnly
                 attachment-editable-by-application-state]}
  [{:keys [application user]}]
  
  (if (and (attachment/file-id-in-application? application attachmentId fileId)
           (attachment/file-id-in-application? application attachmentId originalFileId))
    (attachment/delete-attachment-version! application attachmentId fileId originalFileId)
    (fail :file_not_linked_to_the_document)))

;;
;; Download
;;

(defraw "preview-attachment"
        {:parameters [:attachment-id]
         :input-validators [(partial action/non-blank-parameters [:attachment-id])]
         :user-roles #{:applicant :authority :oirAuthority}
         :user-authz-roles auth/all-authz-roles
         :org-authz-roles auth/reader-org-authz-roles}
        [{{:keys [attachment-id]} :data user :user}]
        (attachment/output-attachment-preview! attachment-id (partial attachment/get-attachment-file-as! user)))

(defraw "view-attachment"
        {:parameters [:attachment-id]
         :input-validators [(partial action/non-blank-parameters [:attachment-id])]
         :user-roles #{:applicant :authority :oirAuthority}
         :user-authz-roles auth/all-authz-roles
         :org-authz-roles auth/reader-org-authz-roles}
        [{{:keys [attachment-id]} :data user :user}]
        (attachment/output-attachment attachment-id false (partial attachment/get-attachment-file-as! user)))

(defraw "download-attachment"
  {:parameters [:attachment-id]
   :input-validators [(partial action/non-blank-parameters [:attachment-id])]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles}
  [{{:keys [attachment-id]} :data user :user}]
  (attachment/output-attachment attachment-id true (partial attachment/get-attachment-file-as! user)))

(defraw "download-bulletin-attachment"
  {:parameters [attachment-id]
   :input-validators [(partial action/non-blank-parameters [:attachment-id])]
   :user-roles #{:anonymous}}
  [_]
  (attachment/output-attachment attachment-id true bulletins/get-bulletin-attachment))

(defraw "download-all-attachments"
  {:parameters [:id]
   :user-roles #{:applicant :authority :oirAuthority}
   :states     states/all-states
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles}
  [{:keys [application user lang]}]
  (if application
    (let [attachments (:attachments application)
          application (a/with-masked-person-ids application user)]
      {:status 200
        :headers {"Content-Type" "application/octet-stream"
                  "Content-Disposition" (str "attachment;filename=\"" (i18n/loc "attachment.zip.filename") "\"")}
        :body (attachment/temp-file-input-stream (attachment/get-all-attachments! attachments application lang))})
    {:status 404
     :headers {"Content-Type" "text/plain"}
     :body "404"}))

;;
;; Upload
;;

(def attachment-modification-prechecks
  [attachment-is-not-locked
   (partial if-not-authority-state-must-not-be #{:sent})
   attachment-editable-by-application-state
   validate-attachment-type
   a/validate-authority-in-drafts
   attachment-id-is-present-in-application-or-not-set])

(def- base-upload-options
  {:comment-text nil
   :required false
   :archivable false
   :archivabilityError :invalid-mime-type
   :upload-pdfa-only false
   :missing-fonts []})

(defn- attach-or-fail! [application attachment-data]
  (when-not (attachment/attach-file! application attachment-data)
    (fail :error.unknown)))

(defn- convert-pdf-and-upload! [application {:keys [pdfa? output-file missing-fonts]}
                                {:keys [attachment-id filename upload-pdfa-only] :as attachment-data}]
  (if pdfa?
    (let [attach-file-result (or upload-pdfa-only (attachment/attach-file! application attachment-data) (fail! :error.unknown))
          new-filename (ss/replace filename #"(-PDFA)?\.(?i)pdf$" "-PDFA.pdf" )
          new-id       (or (:id attach-file-result) attachment-id)
          application  (domain/get-application-no-access-checking (:id application)) ; Refresh attachment versions
          pdfa-attachment-data (assoc attachment-data
                                 :attachment-id new-id
                                 :content output-file
                                 :filename new-filename
                                 :comment? false
                                 :archivable true
                                 :archivabilityError nil)]
      (if (attachment/attach-file! application pdfa-attachment-data)
        (do (io/delete-file output-file :silently)
            nil)
        (fail :error.unknown)))
    (let [missing-fonts (or missing-fonts [])]
      (attach-or-fail! application (assoc attachment-data :missing-fonts missing-fonts :archivabilityError :invalid-pdfa)))))

(defn- upload! [application {:keys [filename content] :as attachment-data}]
  (case (mime/mime-type filename)
    "application/pdf" (if (pdf-conversion/pdf-a-required? (:organization application))
                        (let [processing-result (pdf-conversion/convert-to-pdf-a content)]
                          (if (:already-valid-pdfa? processing-result)
                            (attach-or-fail! application (assoc attachment-data :archivable true :archivabilityError nil))
                            (convert-pdf-and-upload! application processing-result attachment-data)))
                        (attach-or-fail! application attachment-data))
    "image/tiff"      (let [valid? (tiff-validation/valid-tiff? content)
                            attachment-data (assoc attachment-data :archivable valid? :archivabilityError (when-not valid? :invalid-tiff))]
                        (attach-or-fail! application attachment-data))
    (attach-or-fail! application attachment-data)))

(defcommand upload-attachment
  {:parameters [id attachmentId attachmentType op filename tempfile size]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-writer-roles
   :pre-checks attachment-modification-prechecks
   :input-validators [(partial action/non-blank-parameters [:id :filename])
                      (partial action/map-parameters-with-required-keys [:attachmentType] [:type-id :type-group])
                      (fn [{{size :size} :data}] (when-not (pos? size) (fail :error.select-file)))
                      (fn [{{filename :filename} :data}] (when-not (mime/allowed-file? filename) (fail :error.file-upload.illegal-file-type)))]
   :states     (conj (states/all-states-but states/terminal-states) :answered)
   :notified   true
   :on-success [(notify :new-comment)
                open-inforequest/notify-on-comment]
   :description "Reads :tempfile parameter, which is a java.io.File set by ring"}
  [{:keys [created user application] {:keys [text target locked]} :data :as command}]

  (when (= (:type target) "statement")
    (when-let [validation-error (statement/statement-owner (assoc-in command [:data :statementId] (:id target)) application)]
      (fail! (:text validation-error))))

  (upload! application
           (merge
             base-upload-options
             {:filename filename
              :size size
              :content tempfile
              :attachment-id attachmentId
              :attachment-type attachmentType
              :op op
              :comment-text text
              :target target
              :locked locked
              :user user
              :created created})))

;;
;; Rotate
;;

(defcommand rotate-pdf
  {:parameters  [id attachmentId rotation]
   :user-roles  #{:applicant :authority}
   :user-authz-roles auth/all-authz-writer-roles
   :input-validators [(partial action/number-parameters [:rotation])
                      (fn [{{rotation :rotation} :data}] (when-not (#{-90, 90, 180} rotation) (fail :error.illegal-number)))]
   :pre-checks  attachment-modification-prechecks
   :states      (conj (states/all-states-but states/terminal-states) :answered)
   :description "Rotate PDF by -90, 90 or 180 degrees (clockwise)."}
  [{:keys [application]}]
  (if-let [attachment (attachment/get-attachment-info application attachmentId)]
    (let [{:keys [contentType fileId originalFileId filename user created] :as latest-version} (last (:versions attachment))
          temp-pdf (File/createTempFile fileId ".tmp")
          upload-options (merge
                           base-upload-options
                           {:content temp-pdf
                            :original-file-id originalFileId
                            :upload-pdfa-only true
                            :attachment-id attachmentId
                            :filename filename
                            :content-type contentType
                            :created created
                            :user user})]
      (try
        (when-not (= "application/pdf" (:contentType latest-version)) (fail! :error.not-pdf))
        (with-open [content ((:content (mongo/download fileId)))]
          (pdftk/rotate-pdf content (.getAbsolutePath temp-pdf) rotation)
          (upload! application (assoc upload-options :size (.length temp-pdf))))
        (finally
          (io/delete-file temp-pdf :silently))))
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
  (zipmap (map f coll) coll))

(defn ->long [v]
  (if (string? v) (Long/parseLong v) v))

(defn- ->file-info [attachment]
  (let [versions   (-> attachment :versions reverse)
        re-stamp?  (:stamped (first versions))
        source     (if re-stamp? (second versions) (first versions))]
    (assoc (select-keys source [:contentType :fileId :filename :size :archivable])
           :re-stamp? re-stamp?
           :attachment-id (:id attachment))))


(defn- stamp-attachment! [stamp file-info {:keys [application user now x-margin y-margin transparency]}]
  (let [{:keys [attachment-id contentType fileId filename re-stamp?]} file-info
        file (File/createTempFile "lupapiste.stamp." ".tmp")
        new-file-id (mongo/create-id)]
    (with-open [out (io/output-stream file)]
      (stamper/stamp stamp fileId out x-margin y-margin transparency))
    (let [is-pdf-a? (pdf-conversion/ensure-pdf-a-by-organization file (:organization application))]
      (debug "uploading stamped file: " (.getAbsolutePath file))
      (mongo/upload new-file-id filename contentType file :application (:id application))
      (if re-stamp? ; FIXME these functions should return updates, that could be merged into comment update
        (attachment/update-latest-version-content! user application attachment-id new-file-id (.length file) now)
        (attachment/set-attachment-version! application
                                           (attachment/get-attachment-info application attachment-id)
                                           {:attachment-id  attachment-id
                                            :file-id new-file-id :original-file-id new-file-id
                                            :filename filename
                                            :content-type contentType :size (.length file)
                                            :comment-text nil :now now :user user
                                            :archivable is-pdf-a?
                                            :archivabilityError (when-not is-pdf-a? :invalid-pdfa)
                                            :stamped true :comment? false :state :ok}))
      (io/delete-file file :silently)
      (tiedonohjaus/mark-attachment-final! application now attachment-id))
    new-file-id))

(defn- stamp-attachments!
  [file-infos {:keys [text created transparency job-id application info-fields] :as context}]
  {:pre [text (pos? created)]}
  (let [stamp (stamper/make-stamp
                (ss/limit text 100)
                created
                transparency
                (map #(ss/limit % 100) info-fields))]
    (doseq [file-info (vals file-infos)]
      (try
        (debug "Stamping" (select-keys file-info [:attachment-id :contentType :fileId :filename :re-stamp?]))
        (job/update job-id assoc (:attachment-id file-info) {:status :working :fileId (:fileId file-info)})
        (let [new-file-id (stamp-attachment! stamp file-info context)]
          (job/update job-id assoc (:attachment-id file-info) {:status :done :fileId new-file-id}))
        (catch Throwable t
          (errorf t "failed to stamp attachment: application=%s, file=%s" (:id application) (:fileId file-info))
          (job/update job-id assoc (:attachment-id file-info) {:status :error :fileId (:fileId file-info)}))))))

(defn- stamp-job-status [data]
  (if (every? #{:done :error} (map #(get-in % [:status]) (vals data))) :done :running))

(defn- make-stamp-job [file-infos context]
  (let [job (job/start (zipmap (keys file-infos) (map #(assoc % :status :pending) (vals file-infos))) stamp-job-status)]
    (future* (stamp-attachments! file-infos (assoc context :job-id (:id job))))
    job))

(defcommand stamp-attachments
  {:parameters [:id timestamp text organization files xMargin yMargin extraInfo buildingId kuntalupatunnus section]
   :input-validators [(partial action/vector-parameters-with-non-blank-items [:files])]
   :user-roles #{:authority}
   :states     (conj states/post-submitted-states :submitted)
   :description "Stamps all attachments of given application"}
  [{application :application {transparency :transparency} :data :as command}]
  (let [parsed-timestamp (cond
                           (number? timestamp) (long timestamp)
                           (ss/blank? timestamp) (:created command)
                           :else (->long timestamp))
        stamp-timestamp (if (zero? parsed-timestamp) (:created command) parsed-timestamp)]
    (ok :job (make-stamp-job
              (key-by :attachment-id (map ->file-info (attachment/get-attachments-infos application files)))
              {:application application
               :user (:user command)
               :text (if-not (ss/blank? text) text (i18n/loc "stamp.verdict"))
               :created  stamp-timestamp
               :now      (:created command)
               :x-margin (->long xMargin)
               :y-margin (->long yMargin)
               :transparency (->long (or transparency 0))
               :info-fields [(str buildingId)
                             (str kuntalupatunnus)
                             (str section)
                             (str extraInfo)
                             (if-not (ss/blank? organization)
                               organization
                               (let [org (organization/get-organization (:organization application))]
                                 (organization/get-organization-name org)))]
               }))))

(defquery stamp-attachments-job
  {:parameters [:job-id :version]
   :input-validators [(partial action/non-blank-parameters [:job-id :version])]
   :user-roles #{:authority}
   :user-authz-roles auth/default-authz-writer-roles
   :description "Returns state of stamping job"}
  [{{job-id :job-id version :version timeout :timeout :or {version "0" timeout "10000"}} :data}]
  (assoc (job/status job-id (->long version) (->long timeout)) :ok true))

(defcommand sign-attachments
  {:description "Designers can sign blueprints and other attachments. LUPA-1241"
   :parameters [:id attachmentIds password]
   :input-validators [(partial action/non-blank-parameters [:password])
                      (partial action/vector-parameters-with-non-blank-items [:attachmentIds])]
   :states     (states/all-application-states-but states/terminal-states)
   :pre-checks [domain/validate-owner-or-write-access
                (fn [_ application]
                  (when-not (pos? (count (:attachments application)))
                    (fail :application.attachmentsEmpty)))
                a/validate-authority-in-drafts]
   :user-roles #{:applicant :authority}}
  [{application :application u :user :as command}]
  (when (seq attachmentIds)
    (if (user/get-user-with-password (:username u) password)
     (let [attachments (attachment/get-attachments-infos application attachmentIds)
           signature {:user (user/summary u)
                      :created (:created command)}
           updates (reduce (fn [m {attachment-id :id {version :version file-id :fileId} :latestVersion}]
                             (merge m (mongo/generate-array-updates
                                        :attachments
                                        (:attachments application)
                                        #(= (:id %) attachment-id)
                                        :signatures (assoc signature :version version :fileId file-id))))
                     {} attachments)]

       ; Indexes are calculated on the fly so there is a small change of
       ; a concurrency issue.
       ; FIXME should implement optimistic locking
       (update-application command {$push updates}))
     (do
       ; Throttle giving information about incorrect password
       (Thread/sleep 2000)
       (fail :error.password)))))

;;
;; Attachment metadata
;;

(defcommand set-attachment-meta
  {:parameters [id attachmentId meta]
   :user-roles #{:applicant :authority}
   :user-authz-roles auth/all-authz-writer-roles
   :states     (states/all-states-but (conj states/terminal-states :answered :sent))
   :input-validators [(partial action/non-blank-parameters [:attachmentId])
                      validate-meta validate-scale validate-size validate-operation]
   :pre-checks [a/validate-authority-in-drafts attachment-editable-by-application-state]}
  [{:keys [application user created] :as command}]
  ; FIXME yhdella updatella!
  (doseq [[k v] meta]
    (attachment/update-attachment-key! command attachmentId k v created :set-app-modified? true :set-attachment-modified? true))
  (ok))

(defcommand set-attachment-not-needed
  {:parameters [id attachmentId notNeeded]
   :input-validators [(partial action/non-blank-parameters [:attachmentId])
                      (partial action/boolean-parameters [:notNeeded])]
   :user-roles #{:applicant :authority}
   :states     #{:draft :open :submitted}
   :pre-checks [a/validate-authority-in-drafts]}
  [{:keys [created] :as command}]
  (attachment/update-attachment-key! command attachmentId :notNeeded notNeeded created :set-app-modified? true :set-attachment-modified? false)
  (ok))

(defcommand set-attachments-as-verdict-attachment
  {:parameters [:id selectedAttachmentIds unSelectedAttachmentIds]
   :user-roles #{:authority}
   :states     states/all-but-draft-or-terminal
   :input-validators [(partial action/vector-parameters-with-non-blank-items [:selectedAttachmentIds :unSelectedAttachmentIds])
                      (fn [{{:keys [selectedAttachmentIds unSelectedAttachmentIds]} :data}]
                        (when (seq (intersection (set selectedAttachmentIds) (set unSelectedAttachmentIds)))
                          (error "setting verdict attachments, overlapping ids in: " selectedAttachmentIds unSelectedAttachmentIds)
                          (fail :error.select-verdict-attachments.overlapping-ids)))]}
  [{:keys [application created] :as command}]
  (let [updates-fn  (fn [ids k v] (mongo/generate-array-updates :attachments (:attachments application) #((set ids) (:id %)) k v))]
    (when (or (seq selectedAttachmentIds) (seq unSelectedAttachmentIds))
      (update-application command {$set (merge
                                          (when (seq selectedAttachmentIds)
                                            (updates-fn selectedAttachmentIds   :forPrinting true))
                                          (when (seq unSelectedAttachmentIds)
                                            (updates-fn unSelectedAttachmentIds :forPrinting false)))}))
    (ok)))

(defcommand set-attachment-visibility
  {:parameters       [id attachmentId value]
   :user-roles       #{:authority :applicant}
   :input-validators [(fn [{{nakyvyys-value :value} :data}]
                        (when-not (some (hash-set (keyword nakyvyys-value)) attachment-meta/visibilities)
                          (fail :error.invalid-nakyvyys-value)))]
   :pre-checks       [a/validate-authority-in-drafts
                      (fn [{user :user {attachment-id :attachmentId} :data} {attachments :attachments}]
                        (when attachment-id
                          (when-let [{versions :versions} (util/find-first #(= (:id %) attachment-id) attachments)]
                            (when (empty? versions)
                              (fail :error.attachment.no-versions)))))
                      access/has-attachment-auth]
   :states           (lupapalvelu.states/all-application-states-but lupapalvelu.states/terminal-states)}
  [command]
  (update-application command
                      {:attachments {$elemMatch {:id attachmentId}}}
                      {$set {:attachments.$.metadata.nakyvyys value}}))
