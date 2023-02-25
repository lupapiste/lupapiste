(ns lupapalvelu.attachment-api
  (:require [clojure.set :refer [intersection difference]]
            [lupapalvelu.action :refer [defquery defcommand defraw update-application notify] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.accessibility :as access]
            [lupapalvelu.attachment.conversion :as conversion]
            [lupapalvelu.attachment.metadata :as attachment-meta]
            [lupapalvelu.attachment.ram :as ram]
            [lupapalvelu.attachment.stamp-schema :refer [StampTemplate JSONStampSchema]]
            [lupapalvelu.attachment.stamping :as stamping]
            [lupapalvelu.attachment.stamps :as stamps]
            [lupapalvelu.attachment.tag-groups :as att-tag-groups]
            [lupapalvelu.attachment.tags :as att-tags]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.automatic-assignment.factory :as factory]
            [lupapalvelu.building :as building]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.pate.verdict :as pate-verdict]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.tiedonohjaus :as tos]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer [ok fail fail! now]]
            [sade.env :as env]
            [sade.files :as files]
            [sade.strings :as ss]
            [sade.util :as util]
            [taoensso.timbre :refer [error errorf warn]]))

;; Action category: attachments

(defn- build-attachment-query-params [{application-id :id} {attachment-id :id latest-version :latestVersion}]
  {:id              application-id
   :attachmentId    attachment-id
   :fileId          (:fileId latest-version)
   :ignoreBulletins true})

(defmethod action/allowed-actions-for-category :attachments
  [command]
  (action/allowed-actions-for-collection :attachments build-attachment-query-params command))

;; Validators and pre-checks

(defn- attachment-id-is-present-in-application-or-not-set [{{:keys [attachmentId]} :data {:keys [attachments]} :application}]
  (when-not (or (ss/blank? attachmentId) (some #(= (:id %) attachmentId) attachments))
    (fail :error.attachment.id)))

(defn- attachment-not-required [{{attachmentId :attachmentId} :data user :user application :application}]
  (when (and (not (usr/authority? user))
             (:required (att/get-attachment-info application attachmentId)))
    (fail :error.unauthorized
          :desc "Attachment template is originally bound to the application by operations in application, or have been manually added by authority.")))

(defn- attachment-not-requested-by-authority [{{attachmentId :attachmentId} :data user :user application :application}]
  (when (and (not (auth/application-authority? application user))
             (:requestedByAuthority (att/get-attachment-info application attachmentId)))
    (fail :error.unauthorized
          :desc "Attachment is requested by authority.")))

(defn versions-empty? [application attachmentId]
  (empty? (:versions (att/get-attachment-info application attachmentId))))

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

(defn- validate-meta [{{meta :meta} :data}]
  (some->> (not-empty (remove (set att/attachment-meta-types) (keys meta)))
           (fail :error.illegal-meta-type :parameters)))

(defn- validate-scale [{{meta :meta} :data}]
  (let [scale (:scale meta)]
    (when (and scale (not (contains? (set att/attachment-scales) (keyword scale))))
      (fail :error.illegal-attachment-scale :parameters scale))))

(defn- validate-size [{{meta :meta} :data}]
  (let [size (:size meta)]
    (when (and size (not (contains? (set att/attachment-sizes) (keyword size))))
      (fail :error.illegal-attachment-size :parameters size))))

(defn- mime-validator [& allowed-mime-types]
  (fn validate-mime-type [{{attachment-id :attachmentId} :data application :application}]
    (when-let [version (some->> attachment-id (att/get-attachment-info application) :latestVersion)]
      (when-not ((set allowed-mime-types) (:contentType version))
        (fail :error.illegal-file-type)))))

(defn- no-attachment-is-archived [{{attachments :attachments} :application {:keys [files]} :data}]
  (when (and (seq files)
             (some (fn [{:keys [id metadata]}]
                     (and (contains? (set files) id)
                          (= :arkistoitu (keyword (:tila metadata)))))
                   attachments))
    (fail :error.attachment-is-locked)))

;;
;; Attachments
;;

(defn- maybe-assignments [assignments-delay user]
  (and (usr/authority? user)
       assignments-delay
       @assignments-delay))

(defquery attachments
  {:description      "Get all attachments in application filtered by user visibility"
   :parameters       [:id]
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/reader-org-authz-roles
   :user-roles       #{:applicant :authority :oirAuthority}
   :states           states/all-states}
  [{user :user assignments :application-assignments :as command}]
  (ok :attachments (map (partial att/enrich-attachment-with-trigger-tags (maybe-assignments assignments user))
                        (att/sorted-attachments command))))

(defquery attachment
  {:description      "Get single attachment"
   :parameters       [id attachmentId]
   :categories       #{:attachments}
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/reader-org-authz-roles
   :user-roles       #{:applicant :authority :oirAuthority}
   :states           states/all-states
   :input-validators [(partial action/non-blank-parameters [:id :attachmentId])]}
  [{application :application user :user assignments :application-assignments}]
  (let [attachment (att/get-attachment-info application attachmentId)]
    (if attachment
      (ok :attachment (att/enrich-attachment-with-trigger-tags (maybe-assignments assignments user) attachment))
      (fail :error.attachment-not-found))))

(defquery attachment-groups
  {:description      "Get all attachment groups for application"
   :parameters       [:id]
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/reader-org-authz-roles
   :user-roles       #{:applicant :authority :oirAuthority}
   :states           states/all-states}
  [{application :application}]
  (ok :groups (att-tags/attachment-groups-for-application application)))

(defquery attachments-filters
  {:description      "Get all attachments filters for application"
   :parameters       [:id]
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/reader-org-authz-roles
   :user-roles       #{:applicant :authority :oirAuthority}
   :states           states/all-application-or-archiving-project-states}
  [{application :application user :user assignments :application-assignments}]
  (ok :attachmentsFilters
      (att-tags/attachments-filters application
                                    (and (usr/authority? user)
                                         assignments
                                         @assignments))))

(defquery attachments-tag-groups
  {:description      "Get hierarchical attachment grouping by attachment tags."
   :parameters       [:id]
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/reader-org-authz-roles
   :user-roles       #{:applicant :authority :oirAuthority}
   :states           states/all-application-or-archiving-project-states}
  [{application :application}]
  (ok :tagGroups (att-tag-groups/attachment-tag-groups application)))

;;
;; Types
;;

(defquery attachment-types
  {:parameters          [:id]
   :optional-parameters [:lang]
   :input-validators    [(partial action/non-blank-parameters [:id])]
   :permissions         [{:required [:application/read]}]
   :states              states/all-states}
  [{application :application
    lang        :lang
    user        :user}]
  (let [permit-type (:permitType application)
        language    (or lang
                        (:language user)
                        :fi)
        types       (-> (:organization application)
                        (organization/get-organization)
                        att-type/organization->organization-attachment-settings
                        (att-type/get-attachment-types-for-application application)
                        (att-type/sort-attachment-types permit-type language))]
    (ok :attachmentTypes types)))

(defn- on-set-attachment-type-success [{:keys [application user data organization created]} result]
  (when (:ok result)
    (let [{:keys [attachmentId attachmentType]} data]
      {:user             user
       :organization     @organization
       :application      application
       :targets          [{:id attachmentId :trigger-type attachmentType}]
       :assignment-group "attachments"
       :timestamp        created})))

(defcommand set-attachment-type
  {:parameters       [id attachmentId attachmentType]
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:id :attachmentId :attachmentType])]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles (conj roles/all-authz-writer-roles :foreman)
   :states           {:applicant    (conj (states/all-states-but states/terminal-states) :answered)
                      :authority    (states/all-states-but :canceled)
                      :oirAuthority (states/all-states-but :canceled)}
   :pre-checks       [app/validate-authority-in-drafts
                      att/foreman-must-be-uploader
                      att/edit-allowed-by-target
                      att/attachment-editable-by-application-state
                      att/attachment-not-archived
                      pate-verdict/attachment-not-in-published-verdict
                      (action/some-pre-check usr/precheck-user-is-archivist
                                             att/attachment-not-readOnly)
                      att/attachment-matches-application
                      (action/some-pre-check att/allowed-only-for-authority-when-application-sent
                                             (permit/validate-permit-type-is :YI :YL :YM :VVVL :MAL))
                      att/validate-not-included-in-published-bulletin]
   :on-success       [(factory/attachment-assignment-processor on-set-attachment-type-success)]}
  [{:keys [application created organization] :as command}]
  (let [attachment      (att/get-attachment-info application attachmentId)
        attachment-type (att-type/parse-attachment-type attachmentType)
        attachment-type-setting (att-type/organization->organization-attachment-settings @organization :with-group-node-index true)]
    (if (att-type/allowed-attachment-type-for-application? attachment-type-setting attachment-type application)
      (let [metadata (merge (:metadata attachment)
                            (-> (tos/metadata-for-document (:organization application) (:tosFunction application) attachment-type)
                                (tos/update-end-dates (:verdicts application))))]
        (att/update-attachment-data! command attachmentId {:type attachment-type :metadata metadata} created))
      (do
        (errorf "attempt to set new attachment-type: [%s] [%s]: %s" id attachmentId attachment-type)
        (fail :error.illegal-attachment-type)))))

;;
;; RAM link
;;

(defquery ram-linked-attachments
  {:parameters       [id attachmentId]
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:attachmentId])]
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/reader-org-authz-roles
   :user-roles       #{:applicant :authority :oirAuthority}
   :states           states/all-states}
  [{{attachments :attachments} :application}]
  (->> (ram/resolve-ram-links attachments attachmentId)
       (map #(select-keys % [:id :latestVersion :ramLink]))
       (ok :ram-links)))

(defquery ram-disabled-message
  {:description         "Returns the message that is shown to the user if the
  RAM functionality is disabled in the organization. If the `lang`
  parameter is not given, the user's default language is used."
   :parameters          [:id :attachmentId]
   :optional-parameters [:lang]
   :input-validators    [(partial action/non-blank-parameters [:id :attachmentId])]
   :categories          #{:attachments}
   :user-roles          #{:applicant :authority :oirAuthority}
   :states              (difference states/post-verdict-but-terminal #{:foremanVerdictGiven})
   :pre-checks          [(action/not-pre-check ram/organization-allows-ram
                                               :error.ram-enabled-in-organization)
                         att/attachment-matches-application
                         att/foreman-must-be-uploader
                         ram/ram-not-linked
                         ram/attachment-status-ok
                         ram/attachment-type-allows-ram
                         att/validate-not-included-in-published-bulletin]}
  [{:keys [lang organization]}]
  (->> (get-in @organization [:ram :message (keyword lang)])
       ss/trim
       not-empty
       (ok :message)))

;;
;; Operations
;;

(defquery attachment-operations
  {:parameters       [:id]
   :user-authz-roles roles/all-authz-roles
   :user-roles       #{:applicant :authority :oirAuthority}
   :states           states/all-states}
  [{application :application}]
  (ok :operations (app-utils/get-operations application)))

;;
;; States
;;

(defcommand approve-attachment
  {:description      "Authority can approve attachment. Updates approval map."
   :parameters       [id fileId]
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:fileId])]
   :user-roles       #{:authority}
   :states           (states/all-states-but (conj states/terminal-states :answered :sent))
   :pre-checks       [has-versions
                      app/validate-authority-in-drafts
                      att/attachment-matches-application
                      att/validate-not-included-in-published-bulletin]}
  [command]
  (att/set-attachment-state! command fileId :ok))

(defcommand reject-attachment
  {:description      "Authority can reject attachment. Updates approval map."
   :parameters       [id fileId]
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:fileId])]
   :user-roles       #{:authority}
   :states           (states/all-states-but (conj states/terminal-states :answered :sent))
   :pre-checks       [has-versions
                      app/validate-authority-in-drafts
                      att/attachment-matches-application
                      att/validate-not-included-in-published-bulletin]}
  [command]
  (att/set-attachment-state! command fileId :requires_user_action))

(defcommand reject-attachment-note
  {:description      "Like reject-doc-note but for attachments."
   :parameters       [id fileId note]
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:fileId])]
   :user-roles       #{:authority}
   :states           (states/all-states-but (conj states/terminal-states :answered :sent))
   :pre-checks       [has-versions
                      app/validate-authority-in-drafts
                      att/attachment-matches-application
                      att/validate-not-included-in-published-bulletin]}
  [command]
  (att/set-attachment-reject-note! command fileId (ss/trim note)))

(defcommand reset-attachment
  {:description      "Reverts attachment status to neutral (`:requires_authority_action`). Does
  not clear the rejection note."
   :parameters       [id fileId]
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:fileId])]
   :user-roles       #{:authority}
   :states           (states/all-states-but (conj states/terminal-states :answered :sent))
   :pre-checks       [has-versions
                      app/validate-authority-in-drafts
                      att/attachment-matches-application
                      att/validate-not-included-in-published-bulletin]}
  [command]
  (att/set-attachment-state! command fileId :requires_authority_action true))

;;
;; Create
;;

(defcommand create-attachments
            {:description         "Authority can set a placeholder for an attachment"
             :parameters          [id attachmentTypes]
             :optional-parameters [group]
             :pre-checks          [(fn [{{attachment-types :attachmentTypes} :data
                                         application                         :application
                                         organization                        :organization}]
                                     (let [att-settings (att-type/organization->organization-attachment-settings (force organization))]
                                       (when (and attachment-types
                                                  (not-every? #(att-type/allowed-attachment-type-for-application? att-settings % application) attachment-types))
                                         (fail :error.unknown-attachment-type))))
                                   app/validate-authority-in-drafts
                                   att/validate-group]
             :input-validators    [(partial action/vector-parameters [:attachmentTypes])]
             :user-roles          #{:authority :oirAuthority}
             :states              (states/all-states-but (conj states/terminal-states :answered :sent))}
  [{application :application created :created}]
  (if-let [attachment-ids (att/create-attachments! application attachmentTypes group created false true true)]
    (ok :applicationId id :attachmentIds attachment-ids)
    (fail :error.attachment-placeholder)))

(defcommand create-ram-attachment
  {:description      "Create RAM attachment based on existing attachment"
   :parameters       [id attachmentId]
   :categories       #{:attachments}
   :pre-checks       [att/attachment-matches-application
                      att/foreman-must-be-uploader
                      ram/ram-not-linked
                      ram/organization-allows-ram
                      ram/attachment-status-ok
                      ram/attachment-type-allows-ram
                      att/validate-not-included-in-published-bulletin]
   :input-validators [(partial action/non-blank-parameters [:id :attachmentId])]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles roles/writer-roles-with-foreman
   :states           (difference states/post-verdict-but-terminal #{:foremanVerdictGiven})}
  [{application :application {attachment-id :attachmentId} :data created :created}]
  (if-let [attachment-id (ram/create-ram-attachment! application attachment-id created)]
    (ok :applicationId id :attachmentId attachment-id)
    (fail :error.attachment-placeholder)))

;;
;; Delete
;;

(defcommand delete-attachment
  {:description      "Delete attachment and its versions. Does not
  delete comments. Non-atomic operation: first deletes files, then
  updates document."
   :parameters       [id attachmentId]
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:attachmentId])]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles (conj roles/all-authz-writer-roles :foreman)
   :states           {:applicant    (states/all-states-but states/terminal-states)
                      :authority    (states/all-states-but states/terminal-states)
                      :oirAuthority states/all-inforequest-states}
   :pre-checks       [app/validate-authority-in-drafts
                      att/foreman-must-be-uploader
                      att/attachment-matches-application
                      att/attachment-not-readOnly
                      pate-verdict/attachment-not-in-published-verdict
                      attachment-not-required
                      att/attachment-editable-by-application-state
                      att/delete-allowed-by-target
                      att/edit-allowed-by-target
                      att/attachment-not-stamped
                      att/attachment-approval-check
                      ram/ram-status-not-ok
                      ram/ram-not-linked
                      attachment-not-requested-by-authority
                      att/validate-not-included-in-published-bulletin]}
  [{:keys [application]}]
  (att/delete-attachments! application [attachmentId])
  (ok))

(defcommand delete-attachment-version
  {:description      "Delete attachment version. Is not atomic: first deletes file, then removes application reference."
   :parameters       [:id attachmentId fileId originalFileId]
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:attachmentId :fileId])]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles (conj roles/all-authz-writer-roles :foreman)
   :states           {:applicant    (states/all-states-but states/terminal-states)
                      :authority    (states/all-states-but :canceled)
                      :oirAuthority states/all-inforequest-states}
   :pre-checks       [app/validate-authority-in-drafts
                      att/foreman-must-be-uploader
                      att/attachment-matches-application
                      (action/some-pre-check att/attachment-not-readOnly
                                             att/stamped-removable-version)
                      pate-verdict/attachment-not-in-published-verdict
                      att/attachment-editable-by-application-state
                      att/delete-allowed-by-target
                      att/edit-allowed-by-target
                      ram/ram-status-not-ok
                      ram/ram-not-linked
                      att/validate-not-included-in-published-bulletin]}
  [{:keys [application user]}]
  (cond
    (not (and (att/file-id-in-application? application attachmentId fileId)
              (att/file-id-in-application? application attachmentId originalFileId))) (fail :file_not_linked_to_the_document)
    (not (att/can-delete-version? user application attachmentId fileId)) (fail :error.unauthorized)
    :else (att/delete-attachment-version! application attachmentId fileId originalFileId)))

;;
;; Download
;;

(defraw view-file
  {:description      "Fetch uploaded file from MongoDB. Fetching is session bound."
   :parameters       [fileId]
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:fileId])]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles roles/all-authz-roles}
  [{{:keys [fileId]} :data session :session user :user}]
  (att/output-file fileId (or (:id user) (:id session))))

(defraw download-attachment
  {:parameters          [:file-id :id]
   :optional-parameters [view]
   :categories          #{:attachments}
   :input-validators    [(partial action/non-blank-parameters [:file-id :id])]
   :user-roles          #{:applicant :authority :oirAuthority :financialAuthority}
   :states              states/all-application-or-archiving-project-states
   :user-authz-roles    roles/all-authz-roles}
  [{{:keys [file-id]} :data user :user application :application}]
  (att/output-attachment file-id (not view) (partial att/get-attachment-file-as! user application)))

(defraw latest-attachment-version
  {:parameters          [:attachment-id]
   :categories          #{:attachments}
   :optional-parameters [:download :preview]
   :input-validators    [(partial action/non-blank-parameters [:attachment-id])]
   :user-roles          #{:applicant :authority :oirAuthority :financialAuthority}
   :user-authz-roles    roles/all-authz-roles}
  [{{:keys [attachment-id download preview]} :data user :user}]
  (att/output-attachment (att/get-attachment-latest-version-file user attachment-id (= preview "true")) (= download "true")))

(defraw mass-download
  {:description "A document mass download endpoint for Lupadoku. Note that this is not used by Lupapiste front end. The `docs` parameter is Transit JSON encoded."
   :parameters [:docs]
   :user-roles #{:authority}
   :input-validators [(partial action/non-blank-parameters [:docs])]}
  [{{:keys [docs]} :data user :user}]
  {:status  200
   :headers {"Content-Type" "application/octet-stream"
             "Content-Disposition" (str "attachment; filename=\"LP-dokumentit-" (now) ".zip\"")}
   :body (att/mass-download user docs)})

(defraw download-bulletin-attachment
  {:parameters       [bulletin-id attachment-id]  ; Note that this is actually file id
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:attachment-id])]
   :user-roles       #{:anonymous}}
  [_]
  (att/output-attachment attachment-id true (partial bulletins/get-bulletin-attachment bulletin-id)))

(defn use-tempfile-for-attachments? [attachments]
  (<= (count attachments)
      (env/value :attachments :download :max-tempfile-number)))

(defraw download-all-attachments
  {:parameters       [:id]
   :user-roles       #{:applicant :authority :oirAuthority}
   :states           states/all-states
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/reader-org-authz-roles}
  [{:keys [application user lang]}]
  (if application
    {:status  200
     :headers {"Content-Type"        "application/octet-stream"
               "Content-Disposition" (str "attachment;filename=\"" (i18n/loc "attachment.zip.filename") "\"")}
     :body    (if (use-tempfile-for-attachments? (:attachments application))
                (-> (:attachments application)
                    (att/get-all-attachments! application user lang)
                    (files/temp-file-input-stream))
                (-> (:attachments application)
                    (att/get-all-attachments-as-input-stream! application user lang)))}
    {:status  404
     :headers {"Content-Type" "text/plain"}
     :body    "404"}))

(defraw download-attachments
  {:parameters       [:id ids]
   :user-roles       #{:applicant :authority :oirAuthority}
   :states           states/all-states
   :user-authz-roles roles/all-authz-roles
   :input-validators [(partial action/non-blank-parameters [:ids])]
   :org-authz-roles  roles/reader-org-authz-roles}
  [{:keys [application user lang]}]
  (let [attachments (:attachments application)
        ids         (ss/split ids #",")
        atts        (filter (fn [att] (some (partial = (:id att)) ids)) attachments)]
    {:status  200
     :headers {"Content-Type"        "application/octet-stream"
               "Content-Disposition" (str "attachment;filename=\"" (i18n/loc "attachment.zip.filename") "\"")}
     :body    (att/get-attachments-for-user! user application atts (not (use-tempfile-for-attachments? atts)))}))

;;
;; Upload
;;

;; DEPRECATED
(defcommand upload-attachment
  {:parameters       [id attachmentId attachmentType group filename tempfile size]
   :categories       #{:attachments}
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles (conj roles/all-authz-writer-roles :foreman)
   :pre-checks       [att/attachment-matches-application
                      att/attachment-not-locked
                      att/attachment-not-readOnly
                      pate-verdict/attachment-not-in-published-verdict
                      att/attachment-is-needed
                      att/attachment-editable-by-application-state
                      att/upload-to-target-allowed
                      att/foreman-must-be-uploader
                      (action/some-pre-check att/allowed-only-for-authority-when-application-sent
                                             (permit/validate-permit-type-is :YI :YL :YM :VVVL :MAL))
                      ; deprecated attachment type validation removed from this deprecated command
                      ; validate-attachment-type

                      app/validate-authority-in-drafts
                      att/validate-group]
   :input-validators [(partial action/non-blank-parameters [:id :filename])
                      (partial action/map-parameters-with-required-keys [:attachmentType] [:type-id :type-group])
                      (fn [{{size :size} :data}] (when-not (pos? size) (fail :error.select-file)))
                      (fn [{{filename :filename} :data}] (when-not (mime/allowed-file? filename) (fail :error.file-upload.illegal-file-type)))]
   :states           {:applicant    (conj (states/all-states-but states/terminal-states) :answered)
                      :authority    (states/all-states-but :canceled)
                      :oirAuthority (states/all-states-but :canceled)}
   :notified         true
   :on-success       [(notify :new-comment)
                      open-inforequest/notify-on-comment]
   :description      "Reads :tempfile parameter, which is a java.io.File set by ring"}
  [{:keys [created] {:keys [text target locked]} :data :as command}]
  ; I think this should only be used currently by some legacy itest
  (warn "Using DEPRECATED upload-attachment command")
  (let [file-options       {:filename filename :size size :content tempfile}
        attachment-options {:attachment-id   attachmentId                       ; options for attachment creation (not version)
                            :attachment-type attachmentType
                            :group           group
                            :created         created
                            :target          target
                            :locked          locked
                            :required        false
                            :comment-text    text}]
    (if-let [{id :id} (att/upload-and-attach! command attachment-options file-options)]
      (ok :attachmentId id)
      (fail! :error.unknown))))

;;
;; Rotate
;;

(defcommand rotate-pdf
  {:parameters       [id attachmentId rotation]
   :categories       #{:attachments}
   :user-roles       #{:applicant :authority}
   :user-authz-roles (conj roles/all-authz-writer-roles :foreman)
   :input-validators [(partial action/number-parameters [:rotation])
                      (fn [{{rotation :rotation} :data}] (when-not (#{-90, 90, 180} rotation) (fail :error.illegal-number)))]
   :pre-checks       [(action/some-pre-check att/allowed-only-for-authority-when-application-sent
                                             (permit/validate-permit-type-is :YI :YL :YM :VVVL :MAL))
                      att/foreman-must-be-uploader
                      att/attachment-not-archived
                      att/attachment-editable-by-application-state
                      (mime-validator "application/pdf")
                      app/validate-authority-in-drafts
                      att/attachment-matches-application
                      att/validate-not-included-in-published-bulletin]
   :states           {:applicant (conj (states/all-states-but states/terminal-states) :answered)
                      :authority (states/all-states-but :canceled)}
   :description      "Rotate PDF by -90, 90 or 180 degrees (clockwise). Replaces old file, doesn't create new version. Uploader is not changed."}
  [{:keys [application] :as command}]
  (if-let [{:keys [signatures]
            :as   attachment} (att/get-attachment-info application attachmentId)]
    (let [old-file-id           (-> attachment :latestVersion :fileId)
          {new-file-id :fileId} (att/rotate command attachment rotation)]
      (when (util/find-by-key :fileId old-file-id signatures)
        (mongo/update-by-query :applications
                               {:_id         id
                                :attachments {$elemMatch {:id attachmentId}}}
                               {$set {:attachments.$.signatures (map (fn [{:keys [fileId] :as m}]
                                                                       (cond-> m
                                                                         (= fileId old-file-id)
                                                                         (assoc :fileId new-file-id)))
                                                                     signatures)}}))
      (ok))
    (fail :error.unknown)))

(defcommand upsert-stamp-template
  {:parameters       [organizationId name position background page qrCode rows]
   :input-validators [(partial action/non-blank-parameters [:name :page])
                      (partial action/number-parameters [:background])
                      (partial action/boolean-parameters [:qrCode])
                      (partial action/parameters-matching-schema [:position] (:position StampTemplate))
                      (partial action/parameters-matching-schema [:rows] (:rows StampTemplate))]
   :permissions      [{:required [:organization/admin]}]}
  [{{stamp-id :stamp-id :as data} :data}]
  (let [stamp (assoc (select-keys data [:name :position :background :page :qrCode :rows]) :id (or stamp-id (mongo/create-id)))]
    (if stamp-id
      (mongo/update :organizations
                    {:_id organizationId :stamps {$elemMatch {:id stamp-id}}}
                    {$set {:stamps.$ stamp}})
      (mongo/update :organizations {:_id organizationId} {$push {:stamps stamp}}))
    (ok :stamp-id (:id stamp))))

(defcommand delete-stamp-template
  {:parameters       [organizationId stamp-id]
   :input-validators [(partial action/non-blank-parameters [:stamp-id])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (organization/update-organization organizationId {$pull {:stamps {:id stamp-id}}})
  (ok))

(defquery stamp-templates
  {:parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (ok :stamps (:stamps (util/find-by-id organizationId user-organizations))))

(defquery custom-stamps
  {:parameters      [id]
   :user-roles      #{:authority}
   :org-authz-roles roles/default-org-authz-roles
   :states          states/all-application-states
   :description     "Stamps based on organization stamp templates and filled with application data"}
  [{user :user application :application app-org :organization}]
  (ok :stamps (stamps/stamps @app-org application user)))

(defcommand stamp-attachments
  {:parameters       [:id timestamp files lang stamp]
   :categories       #{:attachments}
   :input-validators [(partial action/vector-parameters-with-non-blank-items [:files])
                      (partial action/parameters-matching-schema [:stamp] JSONStampSchema)
                      (partial action/supported-lang :lang)]
   :pre-checks       [any-attachment-has-version
                      no-attachment-is-archived]
   :user-roles       #{:authority}
   :states           states/post-submitted-states
   :description      "Stamps all attachments of given application"}
  [{application :application :as command}]
  (let [parsed-timestamp (cond
                           (number? timestamp)   (long timestamp)
                           (ss/blank? timestamp) (:created command)
                           :else                 (util/->long timestamp))
        stamp-timestamp  (if (zero? parsed-timestamp) (:created command) parsed-timestamp)
        job              (stamping/make-stamp-job
                           (att/get-attachments-infos application files)
                           {:application   application
                            :user          (:user command)
                            :lang          lang
                            :qr-code       (:qrCode stamp)
                            :stamp-created stamp-timestamp
                            :created       (:created command)
                            :x-margin      (util/->long (get-in stamp [:position :x]))
                            :y-margin      (util/->long (get-in stamp [:position :y]))
                            :page          (keyword (:page stamp))
                            :transparency  (util/->long (or (:background stamp) 0))
                            :scale         (/ (:scale stamp 100) 100.0)
                            :info-fields   {:fields (:rows stamp) :buildings (building/building-ids application)}})]
    (ok :job job)))

(defquery stamp-attachments-job
  {:parameters       [:jobId :version]
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:jobId :version])]
   :user-roles       #{:authority}
   :description      "Returns state of stamping job"}
  [{{job-id :jobId version :version timeout :timeout :or {version "0" timeout "10000"}} :data}]
  (ok (stamping/status job-id version timeout)))

(defquery signing-possible
  {:description      "Pseudo-query that succeeds
  if the user is allowed to sign and there are signable
  attachments."
   :user-roles       #{:applicant :authority}
   :user-authz-roles roles/writer-roles-with-foreman
   :pre-checks       [(fn [{application :application}]
                        (when-not (pos? (count (:attachments application)))
                          (fail :application.attachmentsEmpty)))
                      action/disallow-impersonation
                      app/validate-authority-in-drafts
                      any-attachment-has-version]
   :states           (states/all-application-states-but states/terminal-states)
   :categories       #{:attachments}}
  [_])

(defquery set-attachment-group-enabled
  {:description      "Pseudo-query for checking that attachment group can be selected for application."
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles (conj roles/all-authz-writer-roles :foreman)
   :pre-checks       [att/validate-group-is-selectable]
   :states           (states/all-application-or-archiving-project-states-but states/terminal-states)
   :categories       #{:attachments}})

(defcommand sign-attachments
  {:description      "Designers can sign blueprints and other attachments. LUPA-1241"
   :parameters       [:id attachmentIds password]
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:password])
                      (partial action/vector-parameters-with-non-blank-items [:attachmentIds])]
   :states           (states/all-application-states-but states/terminal-states)
   :pre-checks       [app/validate-authority-in-drafts
                      permit/is-not-archiving-project]
   :user-roles       #{:applicant :authority}
   :user-authz-roles roles/writer-roles-with-foreman}
  [{application :application u :user :as command}]
  (when (seq attachmentIds)
    (if (usr/get-user-with-password (:username u) password)
      ; check, if user has access to (at least one of) the requested attachmentIds
      (if-let [attachments (seq (att/get-attachments-infos application attachmentIds))]
        ; OK, get all attachments of application so indices are correct
        (let [all-attachments (:attachments (domain/get-application-no-access-checking (:id application) [:attachments]))
              signature       {:user    (usr/summary u)
                               :created (:created command)}
              updates         (reduce (fn [m {attachment-id :id {version :version file-id :fileId} :latestVersion}]
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
  {:parameters       [id attachmentId meta]
   :categories       #{:attachments}
   :user-roles       #{:applicant :authority}
   :user-authz-roles (conj roles/all-authz-writer-roles :foreman)
   :states           {:applicant (conj (states/all-states-but states/terminal-states) :answered)
                      :authority (states/all-states-but :canceled)}
   :input-validators [(partial action/non-blank-parameters [:attachmentId])
                      validate-meta validate-scale validate-size]
   :pre-checks       [app/validate-authority-in-drafts
                      att/foreman-must-be-uploader
                      att/attachment-editable-by-application-state
                      (action/some-pre-check usr/precheck-user-is-archivist
                                             att/attachment-not-readOnly)
                      pate-verdict/attachment-not-in-published-verdict
                      att/attachment-not-archived
                      att/edit-allowed-by-target
                      (action/some-pre-check att/allowed-only-for-authority-when-application-sent
                                             (permit/validate-permit-type-is :YI :YL :YM :VVVL :MAL))
                      (partial att/validate-group [:meta :group])]}
  [{:keys [created] :as command}]
  (let [data (att/meta->attachment-data meta)]
    (att/update-attachment-data! command attachmentId data created))
  (ok))

(defcommand set-attachment-not-needed
  {:parameters       [id attachmentId notNeeded]
   :categories       #{:attachments}
   :input-validators [(partial action/non-blank-parameters [:attachmentId])
                      (partial action/boolean-parameters [:notNeeded])]
   :user-roles       #{:applicant :authority}
   :user-authz-roles roles/writer-roles-with-foreman
   :states           #{:draft :open :submitted :complementNeeded}
   :pre-checks       [app/validate-authority-in-drafts
                      att/foreman-must-be-uploader
                      attachment-not-requested-by-authority
                      access/has-attachment-auth
                      no-versions]}
  [{:keys [created] :as command}]
  (att/update-attachment-data! command attachmentId {:notNeeded notNeeded} created :set-app-modified? true :set-attachment-modified? false)
  (ok))

(defcommand set-attachments-as-verdict-attachment
  {:parameters       [:id selectedAttachmentIds unSelectedAttachmentIds]
   :categories       #{:attachments}
   :user-roles       #{:authority}
   :states           states/all-but-draft-or-terminal
   :input-validators [(partial action/vector-parameters-with-non-blank-items [:selectedAttachmentIds :unSelectedAttachmentIds])
                      (fn [{{:keys [selectedAttachmentIds unSelectedAttachmentIds]} :data}]
                        (when (seq (intersection (set selectedAttachmentIds) (set unSelectedAttachmentIds)))
                          (error "setting verdict attachments, overlapping ids in: " selectedAttachmentIds unSelectedAttachmentIds)
                          (fail :error.select-verdict-attachments.overlapping-ids)))]
   :pre-checks       [any-attachment-has-version
                      att/validate-not-included-in-published-bulletin]}
  [{:keys [application] :as command}]
  (let [all-attachments (:attachments (domain/get-application-no-access-checking (:id application) [:attachments]))
        updates-fn      (fn [ids k v] (mongo/generate-array-updates :attachments all-attachments #((set ids) (:id %)) k v))]
    (when (or (seq selectedAttachmentIds) (seq unSelectedAttachmentIds))
      (update-application command {$set (merge
                                          (when (seq selectedAttachmentIds)
                                            (updates-fn selectedAttachmentIds :forPrinting true))
                                          (when (seq unSelectedAttachmentIds)
                                            (updates-fn unSelectedAttachmentIds :forPrinting false)))}))
    (ok)))

(defcommand set-attachment-as-construction-time
  {:description      "Sets attachment which is added on application time as construction time attachment"
   :parameters       [id attachmentId value]
   :categories       #{:attachments}
   :user-roles       #{:authority}
   :states           states/pre-verdict-states
   :pre-checks       [att/attachment-matches-application
                      app/validate-authority-in-drafts
                      permit/is-not-archiving-project
                      (fn [{{value :value} :data :as command}]
                        (when (false? value)
                          (att/validate-attachment-manually-set-construction-time command)))]
   :input-validators [(partial action/non-blank-parameters [:id :attachmentId])
                      (partial action/boolean-parameters [:value])]}
  [command]
  (att/set-attachment-construction-time! command attachmentId value)
  (ok))

(defcommand set-attachment-visibility
  {:parameters       [id attachmentId value]
   :categories       #{:attachments}
   :user-roles       #{:authority :applicant}
   :user-authz-roles roles/writer-roles-with-foreman
   :input-validators [(fn [{{nakyvyys-value :value} :data}]
                        (when-not (some (hash-set (keyword nakyvyys-value)) attachment-meta/visibilities)
                          (fail :error.invalid-nakyvyys-value)))]
   :pre-checks       [app/validate-authority-in-drafts
                      att/foreman-must-be-uploader
                      has-versions
                      access/has-attachment-auth
                      att/attachment-not-readOnly
                      pate-verdict/attachment-not-in-published-verdict
                      att/attachment-matches-application
                      att/validate-not-included-in-published-bulletin
                      att/validate-not-draft-target]
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
                          (when (or archivable (not (conversion/all-convertable-mime-types (keyword contentType))))
                            (fail :error.attachment.content-type))))]
   :states           (states/all-application-or-archiving-project-states-but :draft)}
  [{:keys [application]}]
  (if-let [attachment (att/get-attachment-info application attachmentId)]
    (let [{:keys [archivable archivabilityError]} (att/convert-existing-to-pdfa! application attachment)]
      (if archivable
        (ok)
        (fail archivabilityError)))))
