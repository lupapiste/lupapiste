(ns lupapalvelu.attachment
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn warnf error errorf fatal]]
            [clojure.java.io :as io]
            [clojure.set :refer [rename-keys]]
            [monger.operators :refer :all]
            [schema.core :refer [defschema] :as sc]
            [sade.core :refer :all]
            [sade.files :as files]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :refer [=as-kw not=as-kw] :as util]
            [lupapalvelu.action :refer [update-application application->command]]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.attachment.conversion :as conversion]
            [lupapalvelu.attachment.tags :as att-tags]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.attachment.accessibility :as access]
            [lupapalvelu.attachment.metadata :as metadata]
            [lupapalvelu.attachment.preview :as preview]
            [lupapalvelu.domain :refer [get-application-as get-application-no-access-checking]]
            [lupapalvelu.states :as states]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.tiedonohjaus :as tos]
            [lupapalvelu.file-upload :as file-upload])
  (:import [java.util.zip ZipOutputStream ZipEntry]
           [java.io File InputStream]))

;;
;; Metadata
;;

(def attachment-meta-types [:size :scale :group :op :contents])

(def attachment-scales
  [:1:20 :1:50 :1:100 :1:200 :1:500
   :muu])

(def attachment-sizes
  [:A0 :A1 :A2 :A3 :A4 :A5
   :B0 :B1 :B2 :B3 :B4 :B5
   :muu])

(def attachment-states #{:ok :requires_user_action :requires_authority_action})


(defschema AttachmentId
  (ssc/min-length-string 24))

(defschema AttachmentAuthUser
  "User summary for authorized users in attachment.
   Only name and role is used for users without Lupapiste account."
  (let [SummaryAuthUser (assoc usr/SummaryUser :role (sc/enum "stamper" "uploader"))]
    (sc/if :id
      SummaryAuthUser
      (select-keys SummaryAuthUser [:firstName :lastName :role]))))

(defschema VersionNumber
  {:minor                                sc/Int
   :major                                sc/Int})

(defschema Target
  "Refers to part of the application which attachment is targetted.
   Possible types are verdict, statement etc."
  {:id                                   ssc/ObjectIdStr
   :type                                 sc/Keyword
   (sc/optional-key :poytakirjaId)       sc/Str
   (sc/optional-key :urlHash)            sc/Str})

(defschema Source
  "Source for the automatically generated attachment."
  {:id                                   ssc/ObjectIdStr
   :type                                 sc/Str})

(defschema Operation
  "Operation for operation specific attachments"
  {:id                                   ssc/ObjectIdStr    ;;
   (sc/optional-key :name)               sc/Str})           ;;

(defschema Signature
  "Signature for attachment version"
  {:user                                 usr/SummaryUser   ;;
   :created                              ssc/Timestamp      ;;
   :fileId                               sc/Str             ;; used as 'foreign key' to attachment version
   :version                              VersionNumber})    ;; version number of the signed attachment version

(defschema VersionApprovals
  "An approvals are version-specific. Keys are fileIds."
  {sc/Keyword                           {:value (sc/enum :approved :rejected) ; Key name and value structure are the same as in document meta data.
                                         :user {:id sc/Str, :firstName sc/Str, :lastName sc/Str}
                                         :timestamp ssc/Timestamp
                                         :note sc/Str}})

(defschema Version
  "Attachment version"
  {:version                              VersionNumber
   :fileId                               sc/Str             ;; fileId in GridFS
   :originalFileId                       sc/Str             ;; fileId of the unrotated file
   :created                              ssc/Timestamp
   :user                                 (sc/if :id         ;; User who created the version
                                           usr/SummaryUser ;; Only name is used for users without Lupapiste account, eg. neighbours
                                           (select-keys usr/User [:firstName :lastName]))
   :filename                             sc/Str             ;; original filename
   :contentType                          sc/Str             ;; MIME type of the file
   :size                                 (sc/maybe sc/Int)  ;; file size
   (sc/optional-key :stamped)            sc/Bool
   (sc/optional-key :archivable)         (sc/maybe sc/Bool)
   (sc/optional-key :archivabilityError) (sc/maybe (apply sc/enum conversion/archivability-errors))
   (sc/optional-key :missing-fonts)      (sc/maybe [sc/Str])
   (sc/optional-key :autoConversion)     (sc/maybe sc/Bool)})

(defschema Type
  "Attachment type"
  {:type-id                              (apply sc/enum att-type/all-attachment-type-ids)
   :type-group                           (apply sc/enum att-type/all-attachment-type-groups)})

(defschema Attachment
  {:id                                   AttachmentId
   :type                                 Type               ;; Attachment type
   :modified                             ssc/Timestamp      ;; last modified
   (sc/optional-key :sent)               ssc/Timestamp      ;; sent to backing system
   :locked                               sc/Bool            ;;
   (sc/optional-key :readOnly)           sc/Bool            ;;
   :applicationState                     (apply sc/enum states/all-states) ;; state of the application when attachment is created if not forced to any other
   (sc/optional-key :originalApplicationState) (apply sc/enum states/pre-verdict-states) ;; original application state if visible application state if forced to any other
   :state                                (apply sc/enum attachment-states) ;; attachment state
   :target                               (sc/maybe Target)  ;;
   (sc/optional-key :source)             Source             ;;
   (sc/optional-key :ramLink)            AttachmentId       ;; reference from ram attachment to base attachment
   :required                             sc/Bool            ;;
   :requestedByAuthority                 sc/Bool            ;;
   :notNeeded                            sc/Bool            ;;
   :forPrinting                          sc/Bool            ;; see kopiolaitos.clj
   :op                                   (sc/maybe Operation)
   (sc/optional-key :groupType)          (sc/maybe (apply sc/enum att-tags/attachment-groups))
   :signatures                           [Signature]
   :versions                             [Version]
   (sc/optional-key :latestVersion)      (sc/maybe Version) ;; last item of the versions array
   (sc/optional-key :contents)           (sc/maybe sc/Str)  ;; content description
   (sc/optional-key :scale)              (apply sc/enum attachment-scales)
   (sc/optional-key :size)               (apply sc/enum attachment-sizes)
   :auth                                 [AttachmentAuthUser]
   (sc/optional-key :metadata)           {sc/Keyword sc/Any}
   :approvals                            VersionApprovals})

;;
;; Utils
;;

(def attachment-type-coercer (ssc/json-coercer Type))
(def attachment-target-coercer (ssc/json-coercer Target))


(defn if-not-authority-state-must-not-be [state-set {user :user {:keys [state]} :application}]
  (when (and (not (usr/authority? user))
             (state-set (keyword state)))
    (fail :error.non-authority-viewing-application-in-verdictgiven-state)))

(defn attachment-value-is?
  "predicate is invoked for attachment value of key"
  [pred key attachment]
  (pred (get attachment (keyword key))))

(def attachment-is-readOnly? (partial attachment-value-is? true? :readOnly))
(def attachment-is-locked?   (partial attachment-value-is? true? :locked))

(defn- version-approval-path
  [file-id & [subkey]]
  (->>  (if (ss/blank? (str subkey)) [file-id] [file-id (name subkey)])
        (cons "attachments.$.approvals")
        (ss/join ".")
        keyword))

;;
;; Api
;;

(defn link-files-to-application [app-id fileIds]
  {:pre [(string? app-id)]}
  (mongo/update-by-query :fs.files {:_id {$in fileIds}} {$set {:metadata.application app-id
                                                               :metadata.linked true}}))

(defn- by-file-ids [file-ids {versions :versions :as attachment}]
  (some (comp (set file-ids) :fileId) versions))

(defn get-attachments-infos
  "gets attachments from application"
  [application attachment-ids]
  (let [ids (set attachment-ids)] (filter (comp ids :id) (:attachments application))))

(defn get-attachment-info
  "gets an attachment from application or nil"
  [application attachment-id]
  (first (get-attachments-infos application [attachment-id])))

(defn get-attachment-info-by-file-id
  "gets an attachment from application or nil"
  [{:keys [attachments]} file-id]
  (first (filter (partial by-file-ids #{file-id}) attachments)))

(defn get-attachments-by-operation
  [{:keys [attachments] :as application} op-id]
  (filter #(= (:id (:op %)) op-id) attachments))

(defn get-attachments-by-type
  [{:keys [attachments]} type]
  {:pre [(map? type)]}
  (filter #(= (:type %) type) attachments))

(defn create-sent-timestamp-update-statements [attachments file-ids timestamp]
  (mongo/generate-array-updates :attachments attachments (partial by-file-ids file-ids) :sent timestamp))

(defn create-read-only-update-statements [attachments file-ids]
  (mongo/generate-array-updates :attachments attachments (partial by-file-ids file-ids) :readOnly true))

(defn make-attachment
  [created target required? requested-by-authority? locked? application-state group attachment-type metadata & [attachment-id contents read-only? source]]
  {:pre  [(sc/validate Type attachment-type) (keyword? application-state) (or (nil? target) (sc/validate Target target))]
   :post [(sc/validate Attachment %)]}
  (cond-> {:id (if (ss/blank? attachment-id) (mongo/create-id) attachment-id)
           :type attachment-type
           :modified created
           :locked locked?
           :readOnly (boolean read-only?)
           :applicationState (if (and (= :verdict (:type target)) (not (states/post-verdict-states application-state)))
                               :verdictGiven
                               application-state)
           :state :requires_user_action
           :target target
           :required required?       ;; true if the attachment is added from from template along with the operation, or when attachment is requested by authority
           :requestedByAuthority requested-by-authority?  ;; true when authority is adding a new attachment template by hand
           :notNeeded false
           :forPrinting false
           :op (not-empty (select-keys group [:id :name]))
           :signatures []
           :versions []
           :approvals {}
           :auth []
           :contents contents}
          (:groupType group) (assoc :groupType (:groupType group))
          (map? source) (assoc :source source)
          (seq metadata) (assoc :metadata metadata)))

(defn make-attachments
  "creates attachments with nil target"
  [created application-state attachment-types-with-metadata locked? required? requested-by-authority?]
  (map #(make-attachment created nil required? requested-by-authority? locked? (keyword application-state) nil (:type %) (:metadata %)) attachment-types-with-metadata))

(defn- default-tos-metadata-for-attachment-type [type {:keys [organization tosFunction verdicts]}]
  (let [metadata (-> (tos/metadata-for-document organization tosFunction type)
                     (tos/update-end-dates verdicts))]
    (if (seq metadata)
      metadata
      {:nakyvyys :julkinen})))

(defn- required-options-check [options-map]
  (and (map?    (:attachment-type options-map))
       (number? (:created options-map))))

(defn- resolve-group-name [{documents :documents} {op-id :id op-name :name :as group}]
  (if (or (ss/blank? op-id) (ss/not-blank? op-name))
    group
    (->> (map (comp :op :schema-info) documents)
         (util/find-by-id op-id)
         :name
         (util/assoc-when group :name))))

(defn create-attachment-data
  "Returns the attachment data model as map. This attachment data can be pushed to mongo (no versions included)."
  [application {:keys [attachment-id attachment-type group created target locked required requested-by-authority contents read-only source]
                :or {required false locked false requested-by-authority false} :as options}]
  {:pre [(required-options-check options)]}
  (let [metadata (default-tos-metadata-for-attachment-type attachment-type application)]
    (make-attachment created
                     (when target (attachment-target-coercer target))
                     required
                     requested-by-authority
                     locked
                     (-> application :state keyword)
                     (resolve-group-name application group)
                     (attachment-type-coercer attachment-type)
                     metadata
                     attachment-id
                     contents
                     read-only
                     source)))

(defn- create-attachment!
  "Creates attachment data and $pushes attachment to application. Updates TOS process metadata retention period, if needed"
  [application {ts :created :as options}]
  {:pre [(map? application)]}
  (let [attachment-data (create-attachment-data application options)]
    (update-application
     (application->command application)
      {$set {:modified ts}
       $push {:attachments attachment-data}})
    (tos/update-process-retention-period (:id application) ts)
    attachment-data))

(defn create-attachments! [application attachment-types created locked? required? requested-by-authority?]
  {:pre [(map? application)]}
  (let [attachment-types-with-metadata (map (fn [type] {:type     (attachment-type-coercer type)
                                                        :metadata (default-tos-metadata-for-attachment-type type application)})
                                            attachment-types)
        attachments (make-attachments created (:state application) attachment-types-with-metadata locked? required? requested-by-authority?)]
    (update-application
      (application->command application)
      {$set {:modified created}
       $push {:attachments {$each attachments}}})
    (tos/update-process-retention-period (:id application) created)
    (map :id attachments)))

(defn- delete-attachment-file-and-preview! [file-id]
  (mongo/delete-file-by-id file-id)
  (mongo/delete-file-by-id (str file-id "-preview")))

(defn next-attachment-version [{:keys [major minor] :or {major 0 minor 0}} user]
  (if (usr/authority? user)
    {:major major, :minor (inc minor)}
    {:major (inc major), :minor 0}))

(defn- version-number
  [{{:keys [major minor] :or {major 0 minor 0}} :version}]
  (+ (* 1000 major) minor))

(defn- latest-version-after-removing-file [attachment fileId]
  (->> (:versions attachment)
       (remove (comp #{fileId} :fileId))
       (sort-by version-number)
       (last)))

(defn make-version
  [attachment user {:keys [fileId original-file-id replaceable-original-file-id filename contentType size created
                           stamped archivable archivabilityError missing-fonts autoConversion]}]
  (let [version-number (or (->> (:versions attachment)
                                (filter (comp (hash-set original-file-id replaceable-original-file-id) :originalFileId))
                                last
                                :version)
                           (next-attachment-version (get-in attachment [:latestVersion :version]) user))]
    (sc/validate Version
      (util/assoc-when
        {:version        version-number
         :fileId         fileId
         :originalFileId (or original-file-id fileId)
         :created        created
         :user           (usr/summary user)
         ;; File name will be presented in ASCII when the file is downloaded.
         ;; Conversion could be done here as well, but we don't want to lose information.
         :filename       filename
         :contentType    contentType
         :size           size
         :stamped        (boolean stamped)
         :archivable     (boolean archivable)}
        :archivabilityError archivabilityError
        :missing-fonts missing-fonts
        :autoConversion autoConversion))))

(defn- ->approval [state user timestamp]
  {:value (if (= :ok state) :approved :rejected)
   :user (select-keys user [:id :firstName :lastName])
   :timestamp timestamp})

(defn- build-version-updates [user attachment version-model {:keys [created target state stamped replaceable-original-file-id]
                                                             :or   {state :requires_authority_action} :as options}]
  {:pre [(map? attachment) (map? version-model) (number? created) (map? user) (keyword? state)]}

  (let [version-index  (or (-> (map :originalFileId (:versions attachment))
                               (zipmap (range))
                               (some [(or replaceable-original-file-id (:originalFileId version-model))]))
                           (count (:versions attachment)))
        user-role      (if stamped :stamper :uploader)]
    (util/deep-merge
     (when target
       {$set {:attachments.$.target target}})
     (when (->> (:versions attachment) butlast (map :originalFileId) (some #{(:originalFileId version-model)}) not)
       {$set {:attachments.$.latestVersion version-model}})
     (if (and (usr/authority? user) (not= state :requires_authority_action))
       {$set {(version-approval-path (:fileId version-model)) (->approval state user created)}}
       {$unset {(version-approval-path (:fileId version-model)) 1}})
     {$set {:modified created
            :attachments.$.modified created
            :attachments.$.state  state
            :attachments.$.notNeeded false                  ; if uploaded, attachment is needed then, right? LPK-2275 copy-user-attachments
            (ss/join "." ["attachments" "$" "versions" version-index]) version-model}
      $addToSet {:attachments.$.auth (usr/user-in-role (usr/summary user) user-role)}})))

(defn- remove-old-files! [{old-versions :versions} {file-id :fileId original-file-id :originalFileId :as new-version}]
  (some->> (filter (comp #{original-file-id} :originalFileId) old-versions)
           (first)
           ((juxt :fileId :originalFileId))
           (remove (set [file-id original-file-id]))
           (run! delete-attachment-file-and-preview!)))

(defn- attachment-comment-updates [application user attachment {:keys [comment? comment-text created]
                                                                :or   {comment? true}}]
  (let [comment-target {:type :attachment
                        :id (:id attachment)}]
    (when comment?
      (comment/comment-mongo-update (:state application) comment-text comment-target :system false user nil created))))

(defn set-attachment-version!
  "Creates a version from given attachment and options and saves that version to application.
  Returns version model with attachment-id (not file-id) as id."
  ([application user {attachment-id :id :as attachment} options]
    {:pre [(map? application) (map? attachment) (map? options)]}
   (loop [application application attachment attachment retries-left 5]
     (let [version-model (make-version attachment user options)
           mongo-query   {:attachments {$elemMatch {:id attachment-id
                                                    :latestVersion.version.fileId (get-in attachment [:latestVersion :version :fileId])}}}
           mongo-updates (merge (attachment-comment-updates application user attachment options)
                                (build-version-updates user attachment version-model options))
           update-result (update-application (application->command application) mongo-query mongo-updates :return-count? true)]

       (cond (pos? update-result)
             (do (remove-old-files! attachment version-model)
                 (assoc version-model :id attachment-id))

             (pos? retries-left)
             (do (errorf
                  "Latest version of attachment %s changed before new version could be saved, retry %d time(s)."
                  attachment-id retries-left)
                 (let [application (mongo/by-id :applications (:id application) [:attachments :state])
                       attachment  (get-attachment-info application attachment-id)]
                   (recur application attachment (dec retries-left))))

             :else
             (do (error
                  "Concurrency issue: Could not save attachment version meta data.")
                 nil))))))

(defn meta->attachment-data [meta]
  (merge (select-keys meta [:contents :size :scale])
         (when (contains? meta :group)
           {:op (not-empty (select-keys (:group meta) [:id :name]))
            :groupType (get-in meta [:group :groupType])})))

(defn update-attachment-data! [command attachmentId data now & {:keys [set-app-modified? set-attachment-modified?] :or {set-app-modified? true set-attachment-modified? true}}]
  (update-application command
                      {:attachments {$elemMatch {:id attachmentId}}}
                      {$set (merge (zipmap (->> (keys data)
                                                (map name)
                                                (map (partial str "attachments.$."))
                                                (map keyword))
                                           (vals data))
                                   (when set-app-modified? {:modified now})
                                   (when set-attachment-modified? {:attachments.$.modified now}))}))

(defn get-or-create-attachment!
  "If the attachment-id matches any old attachment, it is returned.
   Otherwise a new attachment is created."
  [application user {:keys [attachment-id] :as options}]
  {:pre [(map? application)]}
  (let [requested-by-authority? (and (ss/blank? attachment-id) (usr/authority? user))
        find-application-delay  (delay (mongo/select-one :applications {:_id (:id application) :attachments.id attachment-id} [:attachments]))
        attachment-options (assoc options :requested-by-authority requested-by-authority?)]
    (cond
      (ss/blank? attachment-id) (create-attachment! application attachment-options)
      @find-application-delay   (get-attachment-info @find-application-delay attachment-id)
      :else (create-attachment! application attachment-options)))) ; if given attachment-id didn't match, create new

(defn- attachment-file-ids
  "Gets all file-ids from attachment."
  [attachment]
  (->> (:versions attachment)
       (mapcat (juxt :originalFileId :fileId))
       (distinct)))

(defn attachment-latest-file-id
  "Gets latest file-id from attachment."
  [application attachment-id]
  (last (attachment-file-ids (get-attachment-info application attachment-id))))

(defn file-id-in-application?
  "tests that file-id is referenced from application"
  [application attachment-id file-id]
  (let [file-ids (attachment-file-ids (get-attachment-info application attachment-id))]
    (boolean (some #{file-id} file-ids))))

(defn- get-file-ids-for-attachments-ids [application attachment-ids]
  (reduce (fn [file-ids attachment-id]
            (concat file-ids (attachment-file-ids (get-attachment-info application attachment-id))))
          []
          attachment-ids))

(defn delete-attachments!
  "Delete attachments with all it's versions. does not delete comments.
   Deletes also assignments that are targets of attachments in question.
   Non-atomic operation: first deletes files, then updates document."
  [application attachment-ids]
  (info "1/4 deleting assignments regarding attachments" attachment-ids)
  (run! (partial assignment/remove-assignments-by-target (:id application)) attachment-ids)
  (info "2/4 deleting files of attachments" attachment-ids)
  (run! delete-attachment-file-and-preview! (get-file-ids-for-attachments-ids application attachment-ids))
  (info "3/4 deleted files of attachments" attachment-ids)
  (update-application (application->command application) {$pull {:attachments {:id {$in attachment-ids}}}})
  (info "4/4 deleted meta-data of attachments" attachment-ids))

(defn delete-attachment-version!
  "Delete attachment version. Is not atomic: first deletes file, then removes application reference."
  [application attachment-id file-id original-file-id]
  (let [attachment (get-attachment-info application attachment-id)
        latest-version (latest-version-after-removing-file attachment file-id)]
    (infof "1/3 deleting files [%s] of attachment %s" (ss/join ", " (set [file-id original-file-id])) attachment-id)
    (run! delete-attachment-file-and-preview! (set [file-id original-file-id]))
    (infof "2/3 deleted file %s of attachment %s" file-id attachment-id)
    (update-application
     (application->command application)
     {:attachments {$elemMatch {:id attachment-id}}}
     (util/deep-merge
       (when (= file-id (-> attachment :versions last :fileId))
         ; Deleting latest version resets attachment state
         {$set {:attachments.$.state :requires_authority_action}})
       {$pull {:attachments.$.versions {:fileId file-id}
               :attachments.$.signatures {:fileId file-id}}
        $unset {(version-approval-path file-id) 1}
        $set  (merge
                {:attachments.$.latestVersion latest-version}
                (when (nil? latest-version)
                  ; No latest version: reset auth and state
                  {:attachments.$.auth []
                   :attachments.$.state :requires_user_action}))}))
    (infof "3/3 deleted meta-data of file %s of attachment" file-id attachment-id)))

(defn get-attachment-file-as!
  "Returns the attachment file if user has access to application and the attachment, otherwise nil."
  [user file-id]
  (when-let [attachment-file (mongo/download file-id)]
    (when-let [application (and (:application attachment-file) (get-application-as (:application attachment-file) user :include-canceled-apps? true))]
      (when (and (seq application) (access/can-access-attachment-file? user file-id application)) attachment-file))))

(defn get-attachment-file!
  "Returns the attachment file without access checking, otherwise nil."
  [file-id]
  (when-let [attachment-file (mongo/download file-id)]
    (when-let [application (and (:application attachment-file) (get-application-no-access-checking (:application attachment-file)))]
      (when (seq application) attachment-file))))

(defn get-attachment-latest-version-file
  "Returns the file for the latest attachment version if user has access to application and the attachment, otherwise nil."
  [user attachment-id]
  (let [application (get-application-as {:attachments.id attachment-id} user :include-canceled-apps? true)
        file-id (attachment-latest-file-id application attachment-id)]
    (when (and application file-id (access/can-access-attachment-file? user file-id application))
      (mongo/download file-id))))

(def- not-found {:status 404
                 :headers {"Content-Type" "text/plain"}
                 :body "404"})

(defn output-attachment
  ([attachment download?]
  (if attachment
    (let [filename (ss/encode-filename (:file-name attachment))
          response {:status 200
                    :body ((:content attachment))
                    :headers {"Content-Type" (:content-type attachment)
                              "Content-Length" (str (:size attachment))
                              "Content-Disposition" (format "filename=\"%s\"" filename)}}]
      (if download?
        (assoc-in response [:headers "Content-Disposition"] (format "attachment;filename=\"%s\"" filename))
        (update response :headers merge http/no-cache-headers)))
    not-found))
  ([file-id download? attachment-fn]
   (output-attachment (attachment-fn file-id) download?)))

(defn output-attachment-preview!
  "Outputs attachment preview creating it if is it does not already exist"
  [file-id attachment-fn]
  (let [preview-id (str file-id "-preview")]
    (if-let [attachment (attachment-fn file-id)]
      (do
        (when (zero? (mongo/count :fs.files {:_id preview-id}))
          (let [file-name (:file-name attachment)
                content-type (:content-type attachment)
                application-id (:application attachment)]
            (preview/create-preview! file-id file-name content-type application-id mongo/*db-name*)))
        (output-attachment preview-id false attachment-fn))
      not-found)))

(defn- cleanup-temp-file [conversion-result]
  (if (and (:content conversion-result) (not (instance? InputStream (:content conversion-result))))
    (io/delete-file (:content conversion-result) :silently)))

(defn upload-and-attach!
  "1) Uploads original file to GridFS
   2) Validates and converts for archivability, uploads converted file to GridFS if applicable
   3) Creates and saves attachment model for application, or fetches it if attachment-id is given
   4) Creates preview image in separate thread
   5) Links file as new version to attachment. If conversion was made, converted file is used (originalFileId points to original file)
   Returns attached version."
  [{:keys [application user]} attachment-options file-options]
  (let [initial-filedata   (file-upload/save-file file-options {:application (:id application) :linked false})
        content            (if (instance? File (:content file-options))
                             (:content file-options)
                             ; stream is consumed at this point, load from mongo
                             ((-> initial-filedata :fileId mongo/download :content)))
        conversion-result  (conversion/archivability-conversion application (assoc initial-filedata
                                                                              :attachment-type (:attachment-type attachment-options)
                                                                              :content content))
        converted-filedata (when (:autoConversion conversion-result)
                                 ;; when conversion was made, upload is needed, initial-filedata's fileId will be overwritten
                                 (file-upload/save-file (select-keys conversion-result [:content :filename])
                                                        {:application (:id application) :linked false}))
        options            (merge attachment-options
                                  initial-filedata
                                  conversion-result
                                  {:original-file-id (or (:original-file-id attachment-options)
                                                         (:fileId initial-filedata))}
                                  converted-filedata)
        attachment         (get-or-create-attachment! application user attachment-options)
        linked-version     (set-attachment-version! application user attachment options)]
    (preview/preview-image! (:id application) (:fileId options) (:filename options) (:contentType options))
    (link-files-to-application (:id application) ((juxt :fileId :originalFileId) linked-version))
    (cleanup-temp-file conversion-result)
    linked-version))

(defn- append-stream [zip file-name in]
  (when in
    (.putNextEntry zip (ZipEntry. (ss/encode-filename file-name)))
    (io/copy in zip)
    (.closeEntry zip)))

(defn- append-gridfs-file! [zip {:keys [filename fileId]}]
  (when fileId
    (if-let [content (:content (mongo/download fileId))]
      (with-open [in (content)]
        (append-stream zip (str fileId "_" filename) in))
      (errorf "File '%s' not found in GridFS. Try manually: db.fs.files.find({_id: '%s'})" filename fileId))))

(defn ^java.io.File get-all-attachments!
  "Returns attachments as zip file.
   If application and lang, application and submitted application PDF are included.
   Callers responsibility is to delete the returned file when done with it!"
  [attachments & [application lang]]
  (let [temp-file (files/temp-file "lupapiste.attachments." ".zip.tmp")] ; Must be deleted by caller!
    (debugf "Created temporary zip file for %d attachments: %s" (count attachments) (.getAbsolutePath temp-file))
    (with-open [zip (ZipOutputStream. (io/output-stream temp-file))]
      ; Add all attachments:
      (doseq [attachment attachments]
        (append-gridfs-file! zip (-> attachment :versions last)))

      (when (and application lang)
        ; Add submitted PDF, if exists:
        (when-let [submitted-application (mongo/by-id :submitted-applications (:id application))]
          (append-stream zip (i18n/loc "attachment.zip.pdf.filename.submitted") (pdf-export/generate submitted-application lang)))
        ; Add current PDF:
        (append-stream zip (i18n/loc "attachment.zip.pdf.filename.current") (pdf-export/generate application lang)))
      (.finish zip))
    (debugf "Size of the temporary zip file: %d" (.length temp-file))
    temp-file))

(defn- maybe-append-gridfs-file!
  "Download and add the attachment file if user can access the application"
  [zip user {:keys [filename fileId]}]
  (when fileId
    (when-let [file (get-attachment-file-as! user fileId)]
      (with-open [in ((:content file))]
        (append-stream zip (str (:application file) "_" fileId "_" filename) in)))))

(defn ^java.io.InputStream get-attachments-for-user!
  "Returns the latest corresponding attachment files readable by the user as an input stream of a self-destructing ZIP file"
  [user attachments]
  (let [temp-file (files/temp-file "lupapiste.attachments." ".zip.tmp")] ; deleted via temp-file-input-stream
    (debugf "Created temporary zip file for %d attachments: %s" (count attachments) (.getAbsolutePath temp-file))
    (with-open [zip (ZipOutputStream. (io/output-stream temp-file))]
      (doseq [attachment attachments]
        (maybe-append-gridfs-file! zip user (-> attachment :versions last)))
      (.finish zip))
    (debugf "Size of the temporary zip file: %d" (.length temp-file))
    (files/temp-file-input-stream temp-file)))

(defn- post-process-attachment [attachment]
  (assoc attachment :isPublic (metadata/public-attachment? attachment)))

(defn post-process-attachments [application]
  (update-in application [:attachments] (partial map post-process-attachment)))

(defn attachment-array-updates
  "Generates mongo updates for application attachment array. Gets all attachments from db to ensure proper indexing."
  [app-id pred & kvs]
  (as-> app-id $
    (lupapalvelu.domain/get-application-no-access-checking $ {:attachments true})
    (apply mongo/generate-array-updates :attachments (:attachments $) pred kvs)))

(defn set-attachment-state! [{:keys [created user application] :as command} file-id new-state]
  {:pre [(number? created) (map? user) (map? application) (ss/not-blank? file-id) (#{:ok :requires_user_action} new-state)]}
  (if-let [attachment-id (:id (get-attachment-info-by-file-id application file-id))]
    (let [data (into {:state new-state}
                     (map (fn [[k v]] [(->> [file-id k]
                                            (map name)
                                            (cons "approvals")
                                            (ss/join ".")
                                            keyword)
                                       v])
                          (->approval new-state user created)))]
      (update-attachment-data! command attachment-id data created :set-app-modified? true :set-attachment-modified? false))
    (fail :error.attachment.id)))

(defn set-attachment-reject-note! [{:keys [created user application] :as command} file-id note]
  {:pre [(number? created) (map? user) (map? application) (ss/not-blank? file-id)]}
  (if-let [attachment-id (:id (get-attachment-info-by-file-id application file-id))]
    (update-attachment-data! command attachment-id {(keyword (str "approvals." file-id ".note")) note} created
                             :set-app-modified? true :set-attachment-modified? false)
    (fail :error.attachment.id)))

(defn convert-existing-to-pdfa! [application user attachment]
  {:pre [(map? application) (:id application) (:attachments application)]}
  (let [{:keys [archivable contentType]} (last (:versions attachment))]
    (if (or archivable (not ((conj conversion/libre-conversion-file-types :image/jpeg :application/pdf) (keyword contentType))))
      (fail :error.attachment.content-type)
      ;; else
      (let [{:keys [fileId filename user created stamped]} (last (:versions attachment))
            file-content (mongo/download fileId)]
        (if (nil? file-content)
          (do
            (error "PDF/A conversion: No mongo file for fileId" fileId)
            (fail :error.attachment-not-found))
          (files/with-temp-file temp-pdf
            (with-open [content ((:content file-content))]
              (io/copy content temp-pdf)
              (upload-and-attach! {:application application :user user}
                                  {:attachment-id (:id attachment)
                                   :comment-text nil
                                   :required false
                                   :created created
                                   :stamped stamped
                                   :original-file-id fileId}
                                  {:content temp-pdf :filename filename}))))))))

(defn- manually-set-construction-time [{app-state :applicationState orig-app-state :originalApplicationState :as attachment}]
  (boolean (and (states/post-verdict-states (keyword app-state))
                (states/all-application-states (keyword orig-app-state)))))

(defn validate-attachment-manually-set-construction-time [{{:keys [attachmentId]} :data application :application :as command}]
  (when-not (manually-set-construction-time (get-attachment-info application attachmentId))
    (fail :error.attachment-not-manually-set-construction-time)))

(defn set-attachment-construction-time! [{app :application :as command} attachment-id value]
  (let [{app-state :applicationState orig-app-state :originalApplicationState} (-> (get-attachment-info app attachment-id))]
    (->> (if value
           {$set (attachment-array-updates (:id app) (comp #{attachment-id} :id) :originalApplicationState (or orig-app-state app-state) :applicationState :verdictGiven)}
           {$set   (attachment-array-updates (:id app) (comp #{attachment-id} :id) :applicationState orig-app-state)
            $unset (attachment-array-updates (:id app) (comp #{attachment-id} :id) :originalApplicationState true)})
         (update-application command))))

(defn enrich-attachment [attachment]
  (assoc attachment
         :tags (att-tags/attachment-tags attachment)
         :manuallySetConstructionTime (manually-set-construction-time attachment)))

(defn- attachment-assignment-info
  "Return attachment info as assignment target"
  [{{:keys [type-group type-id]} :type contents :contents id :id :as doc}]
  (util/assoc-when-pred {:id id :type-key (ss/join "." ["attachmentType" type-group type-id])} ss/not-blank?
                        :description contents))

(defn- describe-assignment-targets [application]
  (let [attachments (map enrich-attachment (:attachments application))]
    (->> (att-tags/attachment-tag-groups (assoc application :attachments attachments))
         (att-tags/sort-by-tags attachments)
         (map attachment-assignment-info))))

(assignment/register-assignment-target! :attachments describe-assignment-targets)
