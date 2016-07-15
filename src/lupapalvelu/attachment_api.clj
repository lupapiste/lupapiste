(ns lupapalvelu.attachment-api
  (:require [clojure.java.io :as io]
            [clojure.set :refer [intersection union]]
            [taoensso.timbre :refer [trace debug debugf info infof warn warnf error errorf fatal]]
            [monger.operators :refer :all]
            [swiss.arrows :refer [-<> -<>>]]
            [sade.core :refer [ok fail fail! now def-]]
            [sade.strings :as ss]
            [sade.util :refer [fn->] :as util]
            [schema.core :as sc]
            [lupapalvelu.action :refer [defquery defcommand defraw update-application application->command notify boolean-parameters] :as action]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.application :as a]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.attachment.metadata :as attachment-meta]
            [lupapalvelu.attachment.accessibility :as access]
            [lupapalvelu.attachment.stamping :as stamping]
            [lupapalvelu.attachment.notifications :as att-notifications]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.building :as building]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.statement :as statement]
            [lupapalvelu.states :as states]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.application :refer [get-operations]]
            [lupapalvelu.pdf.pdfa-conversion :as pdf-conversion]
            [lupapalvelu.pdftk :as pdftk]
            [lupapalvelu.tiff-validation :as tiff-validation]
            [lupapalvelu.tiedonohjaus :as tiedonohjaus]
            [sade.env :as env])
  (:import [java.io File]))

;; Validators and pre-checks

(defn- attachment-is-not-locked [{{:keys [attachmentId]} :data application :application}]
  (when (-> (attachment/get-attachment-info application attachmentId) :locked true?)
    (fail :error.attachment-is-locked)))

(defn- attachment-id-is-present-in-application-or-not-set [{{:keys [attachmentId]} :data {:keys [attachments]} :application}]
  (when-not (or (ss/blank? attachmentId) (some #(= (:id %) attachmentId) attachments))
    (fail :error.attachment.id)))

(defn attachment-not-readOnly [{{attachmentId :attachmentId} :data application :application}]
  (when (-> (attachment/get-attachment-info application attachmentId) :readOnly true?)
    (fail :error.unauthorized
          :desc "Read-only attachments cannot be modified.")))

(defn- attachment-not-required [{{attachmentId :attachmentId} :data user :user application :application}]
  (when (and (-> (attachment/get-attachment-info application attachmentId) :required true?)
             (not (usr/authority? user)))
    (fail :error.unauthorized
          :desc "Only authority can delete attachment templates that are originally bound to the application, or have been manually added by authority.")))

(defn- has-versions [{{attachmentId :attachmentId} :data application :application}]
  (when (and (ss/not-blank? attachmentId)
             (empty? (:versions (attachment/get-attachment-info application attachmentId))))
    (fail :error.attachment.no-versions)))

(defn- attachment-editable-by-application-state
  ([command]
   (attachment-editable-by-application-state false command))
  ([authority-sent? {{attachmentId :attachmentId} :data user :user {current-state :state :as application} :application}]
   (when-not (ss/blank? attachmentId)
     (let [{create-state :applicationState} (attachment/get-attachment-info application attachmentId)]
       (when-not (if (= (keyword current-state) :sent)
                   (or (and authority-sent? (usr/authority? user))
                       (statement/delete-attachment-allowed? attachmentId application))
                   (or (not (states/post-verdict-states (keyword current-state)))
                       (states/post-verdict-states (keyword create-state))
                       (usr/authority? user)))
         (fail :error.pre-verdict-attachment))))))

(defn- validate-meta [{{meta :meta} :data}]
  (doseq [[k v] meta]
    (when (not-any? #{k} attachment/attachment-meta-types)
      (fail :error.illegal-meta-type :parameters k))))

(defn- validate-group-op [group]
  (when-let [op (not-empty (select-keys group [:id :name]))]
    (when (sc/check attachment/Operation op)
      (fail :error.illegal-attachment-operation))))

(defn- validate-group-type [group]
  (when-let [group-type (keyword (:groupType group))]
    (when-not (attachment/attachment-groups group-type)
      (fail :error.illegal-attachment-group-type))))

(defn- validate-group [{{{group :group} :meta} :data}]
  ((some-fn validate-group-op validate-group-type) group))

(defn- validate-operation [{{meta :meta} :data}]
  (when-let [op (:op meta)]
    (when (sc/check attachment/Operation op)
      (fail :error.illegal-attachment-operation))))

(defn- validate-scale [{{meta :meta} :data}]
  (let [scale (:scale meta)]
    (when (and scale (not (contains? (set attachment/attachment-scales) (keyword scale))))
      (fail :error.illegal-attachment-scale :parameters scale))))

(defn- validate-size [{{meta :meta} :data}]
  (let [size (:size meta)]
    (when (and size (not (contains? (set attachment/attachment-sizes) (keyword size))))
      (fail :error.illegal-attachment-size :parameters size))))

(defn- validate-attachment-type [{{attachment-type :attachmentType} :data application :application}]
  (when attachment-type
    (when-not (att-type/allowed-attachment-type-for-application? attachment-type application)
      (fail :error.illegal-attachment-type))))

(defn- validate-operation-in-application [{data :data application :application}]
  (when-let [op-id (or (get-in data [:meta :op :id]) (get-in data [:group :id]))]
    (when-not (util/find-by-id op-id (a/get-operations application))
      (fail :error.illegal-attachment-operation))))

;;
;; Attachments
;;

(defquery attachments
  {:description "Get all attachments in application filtered by user visibility"
   :parameters [:id]
   :user-authz-roles auth/all-authz-roles
   :user-roles #{:applicant :authority :oirAuthority}
   :states states/all-application-states}
  [{{attachments :attachments} :application}]
  (ok :attachments (map #(assoc % :group (attachment/attachment-grouping %)) attachments)))

(defquery attachment-groups
  {:description "Get all attachment groups for application"
   :parameters [:id]
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles
   :user-roles #{:applicant :authority :oirAuthority}
   :states states/all-states}
  [{application :application}]
  (ok :groups (attachment/attachment-groups-for-application application)))

;;
;; Types
;;

(defquery attachment-types
  {:parameters [:id]
   :user-authz-roles auth/all-authz-roles
   :user-roles #{:applicant :authority :oirAuthority}
   :states     states/all-states}
  [{application :application}]
  (ok :attachmentTypes (->> (att-type/get-attachment-types-for-application application)
                            (att-type/->grouped-array))))

(defcommand set-attachment-type
  {:parameters [id attachmentId attachmentType]
   :input-validators [(partial action/non-blank-parameters [:id :attachmentId :attachmentType])]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-writer-roles
   :states     (states/all-states-but (conj states/terminal-states :answered :sent))
   :pre-checks [a/validate-authority-in-drafts attachment-editable-by-application-state attachment-not-readOnly]}
  [{:keys [application user created] :as command}]

  (let [attachment-type (att-type/parse-attachment-type attachmentType)]
    (if (att-type/allowed-attachment-type-for-application? attachment-type application)
      (let [metadata (-> (tiedonohjaus/metadata-for-document (:organization application) (:tosFunction application) attachment-type)
                         (tiedonohjaus/update-end-dates (:verdicts application)))]
        (attachment/update-attachment-data! command attachmentId {:type attachment-type :metadata metadata} created))
      (do
        (errorf "attempt to set new attachment-type: [%s] [%s]: %s" id attachmentId attachment-type)
        (fail :error.illegal-attachment-type)))))

;;
;; RAM link
;;

(defquery ram-linked-attachments
  {:parameters [id attachmentId]
   :input-validators [(partial action/non-blank-parameters [:attachmentId])]
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles
   :user-roles #{:applicant :authority :oirAuthority}
   :states     states/all-states}
  [{{attachments :attachments} :application}]
  (->> (attachment/resolve-ram-links attachments attachmentId)
       (map #(select-keys % [:id :latestVersion :approved :ramLink]))
       (ok :ram-links)))

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
   :parameters  [id fileId]
   :input-validators [(partial action/non-blank-parameters [:fileId])]
   :user-roles #{:authority}
   :states      (states/all-states-but (conj states/terminal-states :answered :sent))
   :pre-checks  [has-versions, a/validate-authority-in-drafts]}
  [command]
  (attachment/set-attachment-state! command fileId :ok))

(defcommand reject-attachment
  {:description "Authority can reject attachment, requires user action."
   :parameters  [id fileId]
   :input-validators [(partial action/non-blank-parameters [:fileId])]
   :user-roles #{:authority}
   :states      (states/all-states-but (conj states/terminal-states :answered :sent))
   :pre-checks  [has-versions, a/validate-authority-in-drafts]}
  [command]
  (attachment/set-attachment-state! command fileId :requires_user_action))

;;
;; Create
;;

(defcommand create-attachments
  {:description      "Authority can set a placeholder for an attachment"
   :parameters       [id attachmentTypes]
   :pre-checks       [(fn [{{attachment-types :attachmentTypes} :data application :application}]
                        (when (and attachment-types
                                   (not-every? #(att-type/allowed-attachment-type-for-application? % application) attachment-types))
                          (fail :error.unknown-attachment-type)))
                      a/validate-authority-in-drafts]
   :input-validators [(partial action/vector-parameters [:attachmentTypes])]
   :user-roles       #{:authority :oirAuthority}
   :states           (states/all-states-but (conj states/terminal-states :answered :sent))}
  [{application :application {attachment-types :attachmentTypes} :data created :created}]
  (if-let [attachment-ids (attachment/create-attachments! application attachmentTypes created false true true)]
    (ok :applicationId id :attachmentIds attachment-ids)
    (fail :error.attachment-placeholder)))

(defcommand create-ram-attachment
  {:description      "Create RAM attachment based on existing attachment"
   :parameters       [id attachmentId]
   :pre-checks       [(fn [{{attachment-id :attachmentId} :data {:keys [attachments]} :application}]
                        (when-not (util/find-by-id attachment-id attachments)
                          (fail :error.attachment.id)))
                      (fn [{{attachment-id :attachmentId} :data {:keys [attachments]} :application}]
                        (when (some (comp #{attachment-id} :ramLink) attachments)
                          (fail :error.ram-link-already-exists)))
                      attachment/ram-status-ok]
   :input-validators [(partial action/non-blank-parameters [:attachmentId])]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/default-authz-writer-roles
   :states           states/post-verdict-states}
  [{application :application {attachment-id :attachmentId} :data created :created}]
  (if-let [attachment-id (attachment/create-ram-attachment! application attachment-id created)]
    (do (att-notifications/notify-new-ram-attachment! application attachment-id created)
        (ok :applicationId id :attachmentId attachment-id))
    (fail :error.attachment-placeholder)))

;;
;; Delete
;;

(defcommand delete-attachment
  {:description "Delete attachment with all it's versions. Does not
  delete comments. Non-atomic operation: first deletes files, then
  updates document."
   :parameters  [id attachmentId]
   :input-validators [(partial action/non-blank-parameters [:attachmentId])]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-writer-roles
   :states      (states/all-states-but (conj states/terminal-states :answered))
   :pre-checks  [a/validate-authority-in-drafts
                 attachment-not-readOnly
                 attachment-not-required
                 attachment-editable-by-application-state
                 attachment/ram-status-not-ok
                 attachment/ram-not-root-attachment]}
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
                 attachment-editable-by-application-state
                 attachment/ram-status-not-ok
                 attachment/ram-not-root-attachment]}
  [{:keys [application user]}]

  (if (and (attachment/file-id-in-application? application attachmentId fileId)
           (attachment/file-id-in-application? application attachmentId originalFileId))
    (attachment/delete-attachment-version! application attachmentId fileId originalFileId)
    (fail :file_not_linked_to_the_document)))

;;
;; Download
;;

(defraw "preview-attachment"
          {:parameters [:attachment-id]                       ;; Note that this is actually file id
         :input-validators [(partial action/non-blank-parameters [:attachment-id])]
         :user-roles #{:applicant :authority :oirAuthority}
         :user-authz-roles auth/all-authz-roles
         :org-authz-roles auth/reader-org-authz-roles}
        [{{:keys [attachment-id]} :data user :user}]
        (attachment/output-attachment-preview! attachment-id (partial attachment/get-attachment-file-as! user)))

(defraw "view-attachment"
        {:parameters [:attachment-id]                       ;; Note that this is actually file id
         :input-validators [(partial action/non-blank-parameters [:attachment-id])]
         :user-roles #{:applicant :authority :oirAuthority}
         :user-authz-roles auth/all-authz-roles
         :org-authz-roles auth/reader-org-authz-roles}
        [{{:keys [attachment-id]} :data user :user}]
        (attachment/output-attachment attachment-id false (partial attachment/get-attachment-file-as! user)))

(defraw "download-attachment"
  {:parameters [:attachment-id]                             ;; Note that this is actually file id
   :input-validators [(partial action/non-blank-parameters [:attachment-id])]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles}
  [{{:keys [attachment-id]} :data user :user}]
  (attachment/output-attachment attachment-id true (partial attachment/get-attachment-file-as! user)))

(defraw "latest-attachment-version"
  {:parameters       [:attachment-id]
   :optional-parameters [:download]
   :input-validators [(partial action/non-blank-parameters [:attachment-id])]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles}
  [{{:keys [attachment-id download]} :data user :user}]
  (attachment/output-attachment (attachment/get-attachment-latest-version-file user attachment-id) (= download "true")))

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

(defn- convert-pdf-and-upload! [application
                                {:keys [pdfa? output-file missing-fonts autoConversion]}
                                {:keys [attachment-id filename upload-pdfa-only] :as attachment-data}]
  (if pdfa?
    (let [attach-file-result (or upload-pdfa-only (attachment/attach-file! application attachment-data) (fail! :error.unknown))
          new-filename (attachment/filename-for-pdfa filename)
          new-id       (or (:id attach-file-result) attachment-id)
          application  (domain/get-application-no-access-checking (:id application)) ; Refresh attachment versions
          pdfa-attachment-data (assoc attachment-data
                                 :attachment-id new-id
                                 :content output-file
                                 :filename new-filename
                                 :comment? false
                                 :archivable true
                                 :autoConversion autoConversion
                                 :archivabilityError nil)]
      (if (attachment/attach-file! application pdfa-attachment-data)
        (do (io/delete-file output-file :silently)
            nil)
        (fail :error.unknown)))
    (let [missing-fonts (or missing-fonts [])]
      (attach-or-fail! application (assoc attachment-data :missing-fonts missing-fonts :archivabilityError :invalid-pdfa)))))

(defn- upload! [application {:keys [filename content] :as attachment-data}]
  (let [mt (keyword (mime/mime-type filename))]
    (cond
      (and (= mt :application/pdf)
           (pdf-conversion/pdf-a-required?
             (:organization application))) (let [processing-result (pdf-conversion/convert-to-pdf-a content {:application application :filename filename})]
                                             (if (:already-valid-pdfa? processing-result)
                                               (attach-or-fail! application (assoc attachment-data :archivable true :archivabilityError nil))
                                               (convert-pdf-and-upload! application processing-result attachment-data)))

      (= mt :image/tiff) (let [valid? (tiff-validation/valid-tiff? content)
                               attachment-data (assoc attachment-data :archivable valid? :archivabilityError (when-not valid? :invalid-tiff))]
                           (attach-or-fail! application attachment-data))

      (attachment/libreoffice-conversion-required? attachment-data) (->> (assoc attachment-data :skip-pdfa-conversion true)
                                                                         (attachment/attach-file! application)
                                                                         :id
                                                                         (assoc attachment-data :attachment-id)
                                                                         (attach-or-fail! application))

      :else (attach-or-fail! application (assoc attachment-data :skip-pdfa-conversion true)))))

(defcommand upload-attachment
  {:parameters [id attachmentId attachmentType group filename tempfile size]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-writer-roles
   :pre-checks [attachment-is-not-locked
                statement/upload-attachment-allowed
                (partial attachment-editable-by-application-state true)
                validate-attachment-type
                a/validate-authority-in-drafts
                attachment-id-is-present-in-application-or-not-set
                validate-operation-in-application
                attachment-not-readOnly]
   :input-validators [(partial action/non-blank-parameters [:id :filename])
                      (partial action/map-parameters-with-required-keys [:attachmentType] [:type-id :type-group])
                      (fn [{{size :size} :data}] (when-not (pos? size) (fail :error.select-file)))
                      (fn [{{filename :filename} :data}] (when-not (mime/allowed-file? filename) (fail :error.file-upload.illegal-file-type)))
                      (fn-> (get-in [:data :group]) validate-group-op)
                      (fn-> (get-in [:data :group]) validate-group-type)]
   :states     (conj (states/all-states-but states/terminal-states) :answered)
   :notified   true
   :on-success [(notify :new-comment)
                open-inforequest/notify-on-comment]
   :description "Reads :tempfile parameter, which is a java.io.File set by ring"}
  [{:keys [created user application] {:keys [text target locked]} :data :as command}]

  (when (= (:type target) "statement")
    (when-let [validation-error (statement/statement-owner (assoc-in command [:data :statementId] (:id target)))]
      (fail! (:text validation-error))))

  (upload! application
           (merge
             base-upload-options
             {:filename filename
              :size size
              :content tempfile
              :attachment-id attachmentId
              :attachment-type attachmentType
              :group group
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
   :pre-checks  [(partial attachment/if-not-authority-state-must-not-be #{:sent})
                 attachment-editable-by-application-state
                 validate-attachment-type
                 a/validate-authority-in-drafts
                 attachment-id-is-present-in-application-or-not-set]
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

(defcommand stamp-attachments
  {:parameters [:id timestamp text organization files xMargin yMargin page extraInfo includeBuildings kuntalupatunnus section lang]
   :input-validators [(partial action/vector-parameters-with-non-blank-items [:files])
                      (partial action/number-parameters [:xMargin :yMargin])
                      (partial action/non-blank-parameters [:page])]
   :user-roles #{:authority}
   :states     (conj states/post-submitted-states :submitted)
   :description "Stamps all attachments of given application"}
  [{application :application org :organization {transparency :transparency} :data :as command}]
  (let [parsed-timestamp (cond
                           (number? timestamp) (long timestamp)
                           (ss/blank? timestamp) (:created command)
                           :else (util/->long timestamp))
        stamp-timestamp (if (zero? parsed-timestamp) (:created command) parsed-timestamp)
        org             (if-not (ss/blank? organization)
                          organization
                          (organization/get-organization-name @org))
        job             (stamping/make-stamp-job
                         (attachment/get-attachments-infos application files)
                         {:application application
                          :user (:user command)
                          :lang lang
                          :text (if-not (ss/blank? text) text (i18n/loc "stamp.verdict"))
                          :created  stamp-timestamp
                          :now      (:created command)
                          :x-margin (util/->long xMargin)
                          :y-margin (util/->long yMargin)
                          :page     (keyword page)
                          :transparency (util/->long (or transparency 0))
                          :options      {:include-buildings includeBuildings}
                          :info-fields  {:backend-id   kuntalupatunnus
                                         :section      section
                                         :extra-info   extraInfo
                                         :organization org
                                         :buildings    (building/building-ids application)}})]
    (ok :job job)))

(defquery stamp-attachments-job
  {:parameters [:job-id :version]
   :input-validators [(partial action/non-blank-parameters [:job-id :version])]
   :user-roles #{:authority}
   :user-authz-roles auth/default-authz-writer-roles
   :description "Returns state of stamping job"}
  [{{job-id :job-id version :version timeout :timeout :or {version "0" timeout "10000"}} :data}]
  (ok (stamping/status job-id version timeout)))

(defcommand sign-attachments
  {:description "Designers can sign blueprints and other attachments. LUPA-1241"
   :parameters [:id attachmentIds password]
   :input-validators [(partial action/non-blank-parameters [:password])
                      (partial action/vector-parameters-with-non-blank-items [:attachmentIds])]
   :states     (states/all-application-states-but states/terminal-states)
   :pre-checks [domain/validate-owner-or-write-access
                (fn [{application :application}]
                  (when-not (pos? (count (:attachments application)))
                    (fail :application.attachmentsEmpty)))
                a/validate-authority-in-drafts]
   :user-roles #{:applicant :authority}}
  [{application :application u :user :as command}]
  (when (seq attachmentIds)
    (if (usr/get-user-with-password (:username u) password)
      ; check, if user has access to (at least one of) the requested attachmentIds
      (if-let [attachments (seq (attachment/get-attachments-infos application attachmentIds))]
        ; OK, get all attachments of application so indices are correct
        (let [all-attachments (:attachments (domain/get-application-no-access-checking (:id application) [:attachments]))
              signature {:user (usr/summary u)
                         :created (:created command)}
              updates (reduce (fn [m {attachment-id :id {version :version file-id :fileId} :latestVersion}]
                                (merge m (mongo/generate-array-updates
                                           :attachments
                                           all-attachments
                                           #(= (:id %) attachment-id)
                                           :signatures (assoc signature :version version :fileId file-id))))
                              {} attachments)]
          ; Indexes are calculated on the fly so there is a small change of
          ; a concurrency issue.
          ; FIXME should implement optimistic locking
          (update-application command {$push updates}))
        (fail :error.unknown-attachment))
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
                      validate-meta validate-scale validate-size validate-operation validate-group]
   :pre-checks [a/validate-authority-in-drafts
                attachment-editable-by-application-state
                attachment-not-readOnly
                validate-operation-in-application]}
  [{:keys [created] :as command}]
  (let [data (attachment/meta->attachment-data meta)]
    (attachment/update-attachment-data! command attachmentId data created))
  (ok))

(defcommand set-attachment-not-needed
  {:parameters [id attachmentId notNeeded]
   :input-validators [(partial action/non-blank-parameters [:attachmentId])
                      (partial action/boolean-parameters [:notNeeded])]
   :user-roles #{:applicant :authority}
   :states     #{:draft :open :submitted :complementNeeded}
   :pre-checks [a/validate-authority-in-drafts]}
  [{:keys [created] :as command}]
  (attachment/update-attachment-data! command attachmentId {:notNeeded notNeeded} created :set-app-modified? true :set-attachment-modified? false)
  (ok))

(defcommand set-attachments-as-verdict-attachment
  {:parameters [:id selectedAttachmentIds unSelectedAttachmentIds]
   :user-roles #{:authority}
   :states     states/all-but-draft-or-terminal
   :input-validators [(partial action/vector-parameters-with-non-blank-items [:selectedAttachmentIds :unSelectedAttachmentIds])
                      (fn [{{:keys [selectedAttachmentIds unSelectedAttachmentIds]} :data}]
                        (when (seq (intersection (set selectedAttachmentIds) (set unSelectedAttachmentIds)))
                          (error "setting verdict attachments, overlapping ids in: " selectedAttachmentIds unSelectedAttachmentIds)
                          (fail :error.select-verdict-attachments.overlapping-ids)))]
   :pre-checks [attachment-not-readOnly]}
  [{:keys [application created] :as command}]
  (let [all-attachments (:attachments (domain/get-application-no-access-checking (:id application) [:attachments]))
        updates-fn      (fn [ids k v] (mongo/generate-array-updates :attachments all-attachments #((set ids) (:id %)) k v))]
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
                      (fn [{{attachment-id :attachmentId} :data app :application}]
                        (when attachment-id
                          (when-let [{versions :versions} (util/find-first #(= (:id %) attachment-id) (:attachments app))]
                            (when (empty? versions)
                              (fail :error.attachment.no-versions)))))
                      access/has-attachment-auth
                      attachment-not-readOnly]
   :states           (lupapalvelu.states/all-application-states-but lupapalvelu.states/terminal-states)}
  [command]
  (update-application command
                      {:attachments {$elemMatch {:id attachmentId}}}
                      {$set {:attachments.$.metadata.nakyvyys value}}))

(defcommand convert-to-pdfa
  {:parameters       [id attachmentId]
   :user-roles       #{:authority}
   :input-validators [(partial action/non-blank-parameters [:id :attachmentId])]
   :pre-checks       [(fn [{{attachment-id :attachmentId} :data {:keys [attachments]} :application}]
                        (let [attachment (util/find-first #(= (:id %) attachment-id) attachments)
                              {:keys [archivable contentType]} (last (:versions attachment))]
                          (when (or archivable (not= "application/pdf" contentType))
                            (fail :error.attachment.id))))]
   :states           (lupapalvelu.states/all-application-states-but :draft)}
  [{:keys [application]}]
  (if-let [attachment (attachment/get-attachment-info application attachmentId)]
    (let [{:keys [contentType fileId filename user created stamped]} (last (:versions attachment))
          temp-pdf (File/createTempFile fileId ".tmp")
          upload-options (merge
                           base-upload-options
                           {:content temp-pdf
                            :attachment-id attachmentId
                            :upload-pdfa-only true
                            :filename filename
                            :content-type contentType
                            :created created
                            :user user
                            :stamped stamped})]
      (try
        (with-open [content ((:content (mongo/download fileId)))]
          (io/copy content temp-pdf)
          (upload! application (assoc upload-options :size (.length temp-pdf))))
        (finally
          (io/delete-file temp-pdf :silently))))
    (fail :error.unknown)))
