(ns lupapalvelu.attachment-api
  (:require [clojure.java.io :as io]
            [clojure.set :refer [intersection union]]
            [taoensso.timbre :refer [trace debug debugf info infof warn warnf error errorf fatal]]
            [monger.operators :refer :all]
            [swiss.arrows :refer [-<> -<>>]]
            [sade.core :refer [ok fail fail! now def-]]
            [sade.files :as files]
            [sade.strings :as ss]
            [sade.util :refer [fn->] :as util]
            [schema.core :as sc]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.action :refer [defquery defcommand defraw update-application application->command notify boolean-parameters] :as action]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.application :as app]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.attachment.tags :as att-tags]
            [lupapalvelu.attachment.tag-groups :as att-tag-groups]
            [lupapalvelu.attachment.metadata :as attachment-meta]
            [lupapalvelu.attachment.accessibility :as access]
            [lupapalvelu.attachment.ram :as ram]
            [lupapalvelu.attachment.stamping :as stamping]
            [lupapalvelu.attachment.conversion :as conversion]
            [lupapalvelu.building :as building]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.operations :as op]
            [lupapalvelu.pdftk :as pdftk]
            [lupapalvelu.statement :as statement]
            [lupapalvelu.states :as states]
            [lupapalvelu.tiedonohjaus :as tos]))

;; Action category: attachments

(defn- build-attachment-query-params [{application-id :id} {attachment-id :id latest-version :latestVersion}]
  {:id           application-id
   :attachmentId attachment-id
   :fileId       (:fileId latest-version)})

(defmethod action/allowed-actions-for-category :attachments
  [command]
  (action/allowed-actions-for-collection :attachments build-attachment-query-params command))

;; Validators and pre-checks

(defn- attachment-is-not-locked [{{:keys [attachmentId]} :data application :application}]
  (when (-> (attachment/get-attachment-info application attachmentId) attachment/attachment-is-locked?)
    (fail :error.attachment-is-locked)))

(defn- attachment-id-is-present-in-application-or-not-set [{{:keys [attachmentId]} :data {:keys [attachments]} :application}]
  (when-not (or (ss/blank? attachmentId) (some #(= (:id %) attachmentId) attachments))
    (fail :error.attachment.id)))

(defn attachment-not-readOnly [{{attachmentId :attachmentId} :data :keys [application user]}]
  (let [attachment (attachment/get-attachment-info application attachmentId)
        readonly-after-sent? (op/get-primary-operation-metadata application :attachments-readonly-after-sent)]
    (when (or (attachment/attachment-is-readOnly? attachment)
              (and readonly-after-sent?
                   (not (states/pre-sent-application-states (-> application :state keyword)))
                   (not (auth/application-authority? application user))))
      (fail :error.unauthorized
            :desc "Attachment is read only."))))

(defn- attachment-not-required [{{attachmentId :attachmentId} :data user :user application :application}]
  (when (and (not (usr/authority? user))
             (:required (attachment/get-attachment-info application attachmentId)))
    (fail :error.unauthorized
          :desc "Attachment template is originally bound to the application by operations in application, or have been manually added by authority.")))

(defn- attachment-not-requested-by-authority [{{attachmentId :attachmentId} :data user :user application :application}]
  (when (and (not (usr/authority? user))
             (:requestedByAuthority (attachment/get-attachment-info application attachmentId)))
    (fail :error.unauthorized
          :desc "Attachment is requested by authority.")))

(defn- attachment-is-needed [{{attachmentId :attachmentId} :data application :application}]
  (when (and attachmentId (:notNeeded (attachment/get-attachment-info application attachmentId)))
    (fail :error.attachment.not-needed)))

(defn versions-empty? [application attachmentId]
  (empty? (:versions (attachment/get-attachment-info application attachmentId))))

(defn- no-versions [{{attachmentId :attachmentId} :data application :application}]
  (when (and (ss/not-blank? attachmentId)
             (not (versions-empty? application attachmentId)))
    (fail :error.attachment.versions-not-empty)))

(defn- has-versions [{{attachmentId :attachmentId} :data application :application}]
  (when (and (ss/not-blank? attachmentId)
             (versions-empty? application attachmentId))
    (fail :error.attachment.no-versions)))

(defn any-attachment-has-version [{{attachments :attachments} :application}]
  (when (every? (comp empty? :versions) attachments)
    (fail :error.attachment.no-versions)))

(defn- attachment-editable-by-application-state
  ([command]
   (attachment-editable-by-application-state false command))
  ([authority-sent? {{attachmentId :attachmentId} :data user :user {current-state :state organization :organization :as application} :application}]
   (when-not (ss/blank? attachmentId)
     (let [{create-state :applicationState} (attachment/get-attachment-info application attachmentId)
           cur-state-kw (keyword current-state)]
       (when-not (cond
                   (= cur-state-kw :sent)
                   (or (and authority-sent? (usr/authority? user))
                       (statement/delete-attachment-allowed? attachmentId application))

                   (states/terminal-states cur-state-kw)
                   (usr/user-is-archivist? user organization)

                   :else
                   (or (not (states/post-verdict-states cur-state-kw))
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
    (when-not ((set att-tags/attachment-groups) group-type)
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

(defn- mime-validator [& allowed-mime-types]
  (fn validate-mime-type [{{attachment-id :attachmentId} :data application :application}]
    (when-let [version (some->> attachment-id (attachment/get-attachment-info application) :latestVersion)]
      (when-not ((set allowed-mime-types) (:contentType version))
        (fail :error.illegal-file-type)))))

(defn- validate-operation-in-application [{data :data application :application}]
  (when-let [op-id (or (get-in data [:meta :op :id]) (get-in data [:group :id]))]
    (when-not (util/find-by-id op-id (app/get-operations application))
      (fail :error.illegal-attachment-operation))))

(defn- foreman-must-be-uploader [{:keys [user application data] :as command}]
  (when (and (auth/has-auth-role? application (:id user) :foreman)
             (ss/not-blank? (:attachmentId data)))
    (access/has-attachment-auth-role :uploader command)))

(defn- is-verdict-attachment? [{target :target}] (= (:type target) "verdict"))
(defn- is-statement-attachment? [{target :target}] (= (:type target) "statement"))

(defn- verdict-attachment-edit-by-authority-only
  [{{attachmentId :attachmentId} :data user :user application :application}]
  (let [attachment-info (attachment/get-attachment-info application attachmentId)]
    (when (and (is-verdict-attachment? attachment-info)
               (not (auth/application-authority? application user)))
      (fail :error.unauthorized))))

(defn- statement-attachment-edit-by-authority-or-statement-giver-only
  [{{attachmentId :attachmentId} :data user :user application :application}]
  (let [attachment-info (attachment/get-attachment-info application attachmentId)]
    (when (is-statement-attachment? attachment-info)
      (when-not (or (auth/application-authority? application user)
                    (auth/has-auth-role? application (:id user) :statementGiver))
        (fail :error.unauthorized)))))

;;
;; Attachments
;;

(defquery attachments
  {:description "Get all attachments in application filtered by user visibility"
   :parameters [:id]
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles
   :user-roles #{:applicant :authority :oirAuthority}
   :states states/all-states}
  [{{attachments :attachments :as application} :application :as command}]
  (ok :attachments (map attachment/enrich-attachment attachments)))

(defquery attachment
  {:description "Get single attachment"
   :parameters [id attachmentId]
   :categories #{:attachments}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles
   :user-roles #{:applicant :authority :oirAuthority}
   :states states/all-states
   :input-validators [(partial action/non-blank-parameters [:id :attachmentId])]}
  [{{attachments :attachments :as application} :application :as command}]
  (let [attachment (attachment/get-attachment-info application attachmentId)]
    (if attachment
      (ok :attachment (attachment/enrich-attachment attachment))
      (fail :error.attachment-not-found))))

(defquery attachment-groups
  {:description "Get all attachment groups for application"
   :parameters [:id]
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles
   :user-roles #{:applicant :authority :oirAuthority}
   :states states/all-states}
  [{application :application}]
  (ok :groups (att-tags/attachment-groups-for-application application)))

(defquery attachments-filters
  {:description "Get all attachments filters for application"
   :parameters [:id]
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles
   :user-roles #{:applicant :authority :oirAuthority}
   :states states/all-application-states}
  [{application :application}]
  (ok :attachmentsFilters (att-tags/attachments-filters application)))

(defquery attachments-tag-groups
  {:description "Get hierarchical attachment grouping by attachment tags."
   :parameters [:id]
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles
   :user-roles #{:applicant :authority :oirAuthority}
   :states states/all-application-states}
  [{application :application}]
  (ok :tagGroups (att-tag-groups/attachment-tag-groups application)))

;;
;; Types
;;

(defquery attachment-types
  {:parameters [:id]
   :user-authz-roles auth/all-authz-roles
   :user-roles #{:applicant :authority :oirAuthority}
   :states     states/all-states}
  [{application :application}]
  (ok :attachmentTypes (att-type/get-attachment-types-for-application application)))

(defcommand set-attachment-type
  {:parameters [id attachmentId attachmentType]
   :categories #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:id :attachmentId :attachmentType])]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles (conj auth/all-authz-writer-roles :foreman)
   :states     (states/all-states-but :answered :sent)
   :pre-checks [app/validate-authority-in-drafts
                foreman-must-be-uploader
                verdict-attachment-edit-by-authority-only
                statement-attachment-edit-by-authority-or-statement-giver-only
                attachment-editable-by-application-state
                attachment-not-readOnly]}
  [{:keys [application user created] :as command}]

  (let [attachment (attachment/get-attachment-info application attachmentId)
        attachment-type (att-type/parse-attachment-type attachmentType)]
    (if (att-type/allowed-attachment-type-for-application? attachment-type application)
      (let [metadata (merge (:metadata attachment)
                            (-> (tos/metadata-for-document (:organization application) (:tosFunction application) attachment-type)
                                (tos/update-end-dates (:verdicts application))))]
        (attachment/update-attachment-data! command attachmentId {:type attachment-type :metadata metadata} created))
      (do
        (errorf "attempt to set new attachment-type: [%s] [%s]: %s" id attachmentId attachment-type)
        (fail :error.illegal-attachment-type)))))

;;
;; RAM link
;;

(defquery ram-linked-attachments
  {:parameters [id attachmentId]
   :categories #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:attachmentId])]
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles
   :user-roles #{:applicant :authority :oirAuthority}
   :states     states/all-states}
  [{{attachments :attachments} :application}]
  (->> (ram/resolve-ram-links attachments attachmentId)
       (map #(select-keys % [:id :latestVersion :ramLink]))
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
  (ok :operations (app/get-operations application)))

;;
;; States
;;

(defcommand approve-attachment
  {:description "Authority can approve attachment. Updates approval map."
   :parameters  [id fileId]
   :categories  #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:fileId])]
   :user-roles #{:authority}
   :states      (states/all-states-but (conj states/terminal-states :answered :sent))
   :pre-checks  [has-versions, app/validate-authority-in-drafts]}
  [command]
  (attachment/set-attachment-state! command fileId :ok))

(defcommand reject-attachment
  {:description "Authority can reject attachment. Updates approval map."
   :parameters  [id fileId]
   :categories  #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:fileId])]
   :user-roles #{:authority}
   :states      (states/all-states-but (conj states/terminal-states :answered :sent))
   :pre-checks  [has-versions, app/validate-authority-in-drafts]}
  [command]
  (attachment/set-attachment-state! command fileId :requires_user_action))

(defcommand reject-attachment-note
  {:description "Like reject-doc-note but for attachments."
   :parameters  [id fileId note]
   :categories  #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:fileId])]
   :user-roles #{:authority}
   :states      (states/all-states-but (conj states/terminal-states :answered :sent))
   :pre-checks  [has-versions, app/validate-authority-in-drafts]}
  [command]
  (attachment/set-attachment-reject-note! command fileId note))

;;
;; Create
;;

(defcommand create-attachments
  {:description      "Authority can set a placeholder for an attachment"
   :parameters       [id attachmentTypes]
   :optional-parameters [group]
   :pre-checks       [(fn [{{attachment-types :attachmentTypes} :data application :application}]
                        (when (and attachment-types
                                   (not-every? #(att-type/allowed-attachment-type-for-application? % application) attachment-types))
                          (fail :error.unknown-attachment-type)))
                      app/validate-authority-in-drafts]
   :input-validators [(partial action/vector-parameters [:attachmentTypes])
                      (fn-> (get-in [:data :group]) validate-group-op)
                      (fn-> (get-in [:data :group]) validate-group-type)]
   :user-roles       #{:authority :oirAuthority}
   :states           (states/all-states-but (conj states/terminal-states :answered :sent))}
  [{application :application {attachment-types :attachmentTypes} :data created :created}]
  (if-let [attachment-ids (attachment/create-attachments! application attachmentTypes group created false true true)]
    (ok :applicationId id :attachmentIds attachment-ids)
    (fail :error.attachment-placeholder)))

(defcommand create-ram-attachment
  {:description      "Create RAM attachment based on existing attachment"
   :parameters       [id attachmentId]
   :categories       #{:attachments}
   :pre-checks       [(fn [{{attachment-id :attachmentId} :data {:keys [attachments]} :application}]
                        (when-not (util/find-by-id attachment-id attachments)
                          (fail :error.attachment.id)))
                      foreman-must-be-uploader
                      ram/ram-not-linked
                      ram/attachment-status-ok
                      ram/attachment-type-allows-ram]
   :input-validators [(partial action/non-blank-parameters [:attachmentId])]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles (conj auth/default-authz-writer-roles :foreman)
   :states           states/post-verdict-states}
  [{application :application {attachment-id :attachmentId} :data created :created}]
  (if-let [attachment-id (ram/create-ram-attachment! application attachment-id created)]
    (do (ram/notify-new-ram-attachment! application attachment-id created)
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
   :categories  #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:attachmentId])]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles (conj auth/all-authz-writer-roles :foreman)
   :states      (states/all-states-but (conj states/terminal-states :answered))
   :pre-checks  [app/validate-authority-in-drafts
                 foreman-must-be-uploader
                 attachment-not-readOnly
                 attachment-not-required
                 attachment-editable-by-application-state
                 verdict-attachment-edit-by-authority-only
                 statement-attachment-edit-by-authority-or-statement-giver-only
                 ram/ram-status-not-ok
                 ram/ram-not-linked]}
  [{:keys [application user]}]
  (attachment/delete-attachments! application [attachmentId])
  (ok))

(defcommand delete-attachment-version
  {:description   "Delete attachment version. Is not atomic: first deletes file, then removes application reference."
   :parameters  [:id attachmentId fileId originalFileId]
   :categories  #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:attachmentId :fileId])]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles (conj auth/all-authz-writer-roles :foreman)
   :states      (states/all-states-but (conj states/terminal-states :answered :sent))
   :pre-checks  [app/validate-authority-in-drafts
                 foreman-must-be-uploader
                 attachment-not-readOnly
                 attachment-editable-by-application-state
                 verdict-attachment-edit-by-authority-only
                 statement-attachment-edit-by-authority-or-statement-giver-only
                 ram/ram-status-not-ok
                 ram/ram-not-linked]}
  [{:keys [application user]}]
  (cond
    (not (and (attachment/file-id-in-application? application attachmentId fileId)
              (attachment/file-id-in-application? application attachmentId originalFileId))) (fail :file_not_linked_to_the_document)
    (not (attachment/can-delete-version? user application attachmentId fileId)) (fail :error.unauthorized)
    :else (attachment/delete-attachment-version! application attachmentId fileId originalFileId)))

;;
;; Download
;;

(defraw "preview-attachment"
  {:parameters       [:attachment-id]  ; Note that this is actually file id
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:attachment-id])]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles}
  [{{:keys [attachment-id]} :data user :user}]
  (attachment/output-attachment-preview! attachment-id (partial attachment/get-attachment-file-as! user)))

(defraw "view-attachment"
  {:parameters       [:attachment-id]  ; Note that this is actually file id
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:attachment-id])]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles}
  [{{:keys [attachment-id]} :data user :user}]
  (attachment/output-attachment attachment-id false (partial attachment/get-attachment-file-as! user)))

(defraw view-file
  {:description      "Fetch uploaded file from MongoDB. Fetching is session bound."
   :parameters       [fileId]
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:fileId])]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles}
  [{{:keys [fileId]} :data session :session}]
  (attachment/output-file fileId (:id session)))

(defraw "download-attachment"
  {:parameters       [:attachment-id]  ; Note that this is actually file id
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:attachment-id])]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles}
  [{{:keys [attachment-id]} :data user :user}]
  (attachment/output-attachment attachment-id true (partial attachment/get-attachment-file-as! user)))

(defraw "latest-attachment-version"
  {:parameters       [:attachment-id]  ; Note that this is actually file id
   :categories       #{:attachments}
   :optional-parameters [:download]
   :input-validators [(partial action/non-blank-parameters [:attachment-id])]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles}
  [{{:keys [attachment-id download]} :data user :user}]
  (attachment/output-attachment (attachment/get-attachment-latest-version-file user attachment-id) (= download "true")))

(defraw "download-bulletin-attachment"
  {:parameters       [attachment-id]  ; Note that this is actually file id
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:attachment-id])]
   :user-roles       #{:anonymous}}
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
          application (app/with-masked-person-ids application user)]
      {:status 200
        :headers {"Content-Type" "application/octet-stream"
                  "Content-Disposition" (str "attachment;filename=\"" (i18n/loc "attachment.zip.filename") "\"")}
        :body (files/temp-file-input-stream (attachment/get-all-attachments! attachments application lang))})
    {:status 404
     :headers {"Content-Type" "text/plain"}
     :body "404"}))

(defraw "download-attachments"
  {:parameters [:id ids]
   :user-roles #{:applicant :authority :oirAuthority}
   :states states/all-states
   :user-authz-roles auth/all-authz-roles
   :input-validators [(partial action/non-blank-parameters [:ids])]
   :org-authz-roles auth/reader-org-authz-roles}
  [{:keys [application user lang]}]
  (let [attachments (:attachments application)
        ids (ss/split ids #",")
        atts (filter (fn [att] (some (partial = (:id att)) ids)) attachments)]
      {:status 200
       :headers {"Content-Type" "application/octet-stream"
                 "Content-Disposition" (str "attachment;filename=\"" (i18n/loc "attachment.zip.filename") "\"")}
       :body (attachment/get-attachments-for-user! user atts)}))

;;
;; Upload
;;

(defcommand upload-attachment
  {:parameters [id attachmentId attachmentType group filename tempfile size]
   :categories #{:attachments}
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles (conj auth/all-authz-writer-roles :foreman)
   :pre-checks [attachment-id-is-present-in-application-or-not-set
                attachment-is-not-locked
                attachment-not-readOnly
                statement/upload-attachment-allowed
                foreman-must-be-uploader
                (partial attachment-editable-by-application-state true)
                validate-attachment-type
                app/validate-authority-in-drafts
                validate-operation-in-application
                attachment-is-needed]
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

  (let [file-options       {:filename filename :size size :content tempfile}
        attachment-options {:attachment-id attachmentId     ; options for attachment creation (not version)
                            :attachment-type  attachmentType
                            :group group
                            :created created
                            :target target
                            :locked locked
                            :required false
                            :comment-text text}]
    (if-let [{id :id} (attachment/upload-and-attach! command attachment-options file-options)]
      (ok :attachmentId id)
      (fail! :error.unknown))))

;;
;; Rotate
;;

(defcommand rotate-pdf
  {:parameters  [id attachmentId rotation]
   :categories  #{:attachments}
   :user-roles  #{:applicant :authority}
   :user-authz-roles (conj auth/all-authz-writer-roles :foreman)
   :input-validators [(partial action/number-parameters [:rotation])
                      (fn [{{rotation :rotation} :data}] (when-not (#{-90, 90, 180} rotation) (fail :error.illegal-number)))]
   :pre-checks  [(partial attachment/if-not-authority-state-must-not-be #{:sent})
                 foreman-must-be-uploader
                 attachment-editable-by-application-state
                 validate-attachment-type
                 (mime-validator "application/pdf")
                 app/validate-authority-in-drafts
                 attachment-id-is-present-in-application-or-not-set]
   :states      (conj (states/all-states-but states/terminal-states) :answered)
   :description "Rotate PDF by -90, 90 or 180 degrees (clockwise). Replaces old file, doesn't create new version. Uploader is not changed."}
  [{:keys [application]}]
  (if-let [attachment (attachment/get-attachment-info application attachmentId)]
    (files/with-temp-file temp-pdf
      (let [{:keys [contentType fileId originalFileId filename user created autoConversion] :as latest-version} (last (:versions attachment))
           attachment-options (util/assoc-when {:comment-text nil
                                                :required false
                                                :original-file-id originalFileId
                                                :attachment-id attachmentId
                                                :attachment-type (:type attachment)
                                                :created created
                                                :user user}
                                               :autoConversion autoConversion)]
        (when-not (= "application/pdf" (:contentType latest-version)) (fail! :error.not-pdf))
        (with-open [content ((:content (mongo/download fileId)))]
          (pdftk/rotate-pdf content (.getAbsolutePath temp-pdf) rotation)
          (attachment/upload-and-attach! {:application application :user user} ; NOTE: user is user from attachment version
                                         attachment-options
                                         {:content temp-pdf
                                          :filename filename
                                          :content-type contentType
                                          :size (.length temp-pdf)}))
        (ok)))
    (fail :error.unknown)))

(defcommand stamp-attachments
  {:parameters [:id timestamp text organization files xMargin yMargin page extraInfo includeBuildings kuntalupatunnus section lang]
   :categories #{:attachments}
   :input-validators [(partial action/vector-parameters-with-non-blank-items [:files])
                      (partial action/number-parameters [:xMargin :yMargin])
                      (partial action/non-blank-parameters [:page])]
   :pre-checks [any-attachment-has-version]
   :user-roles #{:authority}
   :states     states/post-submitted-states
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
                          :stamp-created stamp-timestamp
                          :created  (:created command)
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
  {:parameters [:jobId :version]
   :categories #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:jobId :version])]
   :user-roles #{:authority}
   :description "Returns state of stamping job"}
  [{{job-id :jobId version :version timeout :timeout :or {version "0" timeout "10000"}} :data}]
  (ok (stamping/status job-id version timeout)))

(defquery signing-possible
  {:description "Pseudo-query that succeeds
  if the user is allowed to sign and there are signable
  attachments."
   :user-roles  #{:applicant :authority}
   :pre-checks  [domain/validate-owner-or-write-access
                 (fn [{application :application}]
                   (when-not (pos? (count (:attachments application)))
                     (fail :application.attachmentsEmpty)))
                 app/validate-authority-in-drafts
                 any-attachment-has-version]
   :states      (states/all-application-states-but states/terminal-states)
   :categories  #{:attachments}}
  [_])

(defcommand sign-attachments
  {:description "Designers can sign blueprints and other attachments. LUPA-1241"
   :parameters [:id attachmentIds password]
   :categories #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:password])
                      (partial action/vector-parameters-with-non-blank-items [:attachmentIds])]
   :states     (states/all-application-states-but states/terminal-states)
   :pre-checks [domain/validate-owner-or-write-access
                app/validate-authority-in-drafts]
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
   :categories #{:attachments}
   :user-roles #{:applicant :authority}
   :user-authz-roles (conj auth/all-authz-writer-roles :foreman)
   :states     (states/all-states-but :answered :sent)
   :input-validators [(partial action/non-blank-parameters [:attachmentId])
                      validate-meta validate-scale validate-size validate-operation validate-group]
   :pre-checks [app/validate-authority-in-drafts
                foreman-must-be-uploader
                attachment-editable-by-application-state
                attachment-not-readOnly
                verdict-attachment-edit-by-authority-only
                statement-attachment-edit-by-authority-or-statement-giver-only
                validate-operation-in-application]}
  [{:keys [created] :as command}]
  (let [data (attachment/meta->attachment-data meta)]
    (attachment/update-attachment-data! command attachmentId data created))
  (ok))

(defcommand set-attachment-contents
  {:parameters [id attachmentId contents]
   :categories #{:attachments}
   :user-roles #{:authority}
   :org-authz-roles #{:archivist}
   :states     states/all-application-states
   :input-validators [(partial action/non-blank-parameters [:attachmentId])]}
  [{:keys [created] :as command}]
  (attachment/update-attachment-data! command attachmentId {:contents contents} created)
  (ok))

(defcommand set-attachment-not-needed
  {:parameters [id attachmentId notNeeded]
   :categories #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:attachmentId])
                      (partial action/boolean-parameters [:notNeeded])]
   :user-roles #{:applicant :authority}
   :user-authz-roles (conj auth/default-authz-writer-roles :foreman)
   :states     #{:draft :open :submitted :complementNeeded}
   :pre-checks [app/validate-authority-in-drafts
                foreman-must-be-uploader
                attachment-not-requested-by-authority
                access/has-attachment-auth
                no-versions]}
  [{:keys [created] :as command}]
  (attachment/update-attachment-data! command attachmentId {:notNeeded notNeeded} created :set-app-modified? true :set-attachment-modified? false)
  (ok))

(defcommand set-attachments-as-verdict-attachment
  {:parameters [:id selectedAttachmentIds unSelectedAttachmentIds]
   :categories #{:attachments}
   :user-roles #{:authority}
   :states     states/all-but-draft-or-terminal
   :input-validators [(partial action/vector-parameters-with-non-blank-items [:selectedAttachmentIds :unSelectedAttachmentIds])
                      (fn [{{:keys [selectedAttachmentIds unSelectedAttachmentIds]} :data}]
                        (when (seq (intersection (set selectedAttachmentIds) (set unSelectedAttachmentIds)))
                          (error "setting verdict attachments, overlapping ids in: " selectedAttachmentIds unSelectedAttachmentIds)
                          (fail :error.select-verdict-attachments.overlapping-ids)))]
   :pre-checks [attachment-not-readOnly
                any-attachment-has-version]}
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

(defcommand set-attachment-as-construction-time
  {:description "Sets attachment which is added on application time as construction time attachment"
   :parameters [id attachmentId value]
   :categories #{:attachments}
   :user-roles #{:authority}
   :states     states/pre-verdict-states
   :pre-checks [attachment-id-is-present-in-application-or-not-set
                app/validate-authority-in-drafts
                (fn [{{value :value} :data :as command}]
                  (when (false? value)
                    (attachment/validate-attachment-manually-set-construction-time command)))]
   :input-validators [(partial action/non-blank-parameters [:id :attachmentId])
                      (partial action/boolean-parameters [:value])]}
  [command]
  (attachment/set-attachment-construction-time! command attachmentId value)
  (ok))

(defcommand set-attachment-visibility
  {:parameters       [id attachmentId value]
   :categories       #{:attachments}
   :user-roles       #{:authority :applicant}
   :user-authz-roles (conj auth/default-authz-writer-roles :foreman)
   :input-validators [(fn [{{nakyvyys-value :value} :data}]
                        (when-not (some (hash-set (keyword nakyvyys-value)) attachment-meta/visibilities)
                          (fail :error.invalid-nakyvyys-value)))]
   :pre-checks       [app/validate-authority-in-drafts
                      foreman-must-be-uploader
                      (fn [{{attachment-id :attachmentId} :data app :application}]
                        (when attachment-id
                          (when-let [{versions :versions} (util/find-by-id attachment-id (:attachments app))]
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
   :categories       #{:attachments}
   :user-roles       #{:authority}
   :input-validators [(partial action/non-blank-parameters [:id :attachmentId])]
   :pre-checks       [(fn [{{attachment-id :attachmentId} :data {:keys [attachments]} :application}]
                        (let [attachment (util/find-first #(= (:id %) attachment-id) attachments)
                              {:keys [archivable contentType]} (last (:versions attachment))]
                          (when (or archivable (not ((conj conversion/libre-conversion-file-types :image/jpeg :application/pdf) (keyword contentType))))
                            (fail :error.attachment.content-type))))]
   :states           (states/all-application-states-but :draft)}
  [{:keys [application]}]
  (if-let [attachment (attachment/get-attachment-info application attachmentId)]
    (let [{:keys [fileId filename user created stamped]} (last (:versions attachment))]
      (files/with-temp-file temp-pdf
        (with-open [content ((:content (mongo/download fileId)))]
          (io/copy content temp-pdf)
          (attachment/upload-and-attach! {:application application :user user}
                                         {:attachment-id attachmentId
                                          :comment-text nil
                                          :required false
                                          :created created
                                          :stamped stamped
                                          :original-file-id fileId}
                                         {:content temp-pdf :filename filename})))
      (ok))))
