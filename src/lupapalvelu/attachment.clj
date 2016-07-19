(ns lupapalvelu.attachment
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn warnf error errorf fatal]]
            [com.netflix.hystrix.core :as hystrix]
            [clojure.java.io :as io]
            [clojure.set :refer [rename-keys]]
            [monger.operators :refer :all]
            [schema.core :refer [defschema] :as sc]
            [sade.schemas :as ssc]
            [sade.util :refer [=as-kw not=as-kw] :as util]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.core :refer :all]
            [sade.http :as http]
            [lupapalvelu.action :refer [update-application application->command]]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.attachment.accessibility :as access]
            [lupapalvelu.attachment.metadata :as metadata]
            [lupapalvelu.domain :refer [get-application-as get-application-no-access-checking]]
            [lupapalvelu.states :as states]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.tiedonohjaus :as tos]
            [lupapalvelu.pdf.libreoffice-conversion-client :as libreoffice-client]
            [lupapiste-commons.preview :as preview]
            [lupapalvelu.pdf.pdfa-conversion :as pdf-conversion]
            [lupapalvelu.tiff-validation :as tiff-validation])
  (:import [java.util.zip ZipOutputStream ZipEntry]
           [java.io File FilterInputStream]
           [org.apache.commons.io FilenameUtils]
           [java.util.concurrent Executors ThreadFactory]
           [com.netflix.hystrix HystrixCommandProperties]))

(defn thread-factory []
  (let [security-manager (System/getSecurityManager)
        thread-group (if security-manager
                       (.getThreadGroup security-manager)
                       (.getThreadGroup (Thread/currentThread)))]
    (reify
      ThreadFactory
      (newThread [this runnable]
        (doto (Thread. thread-group runnable "preview-worker")
          (.setDaemon true)
          (.setPriority Thread/NORM_PRIORITY))))))

(defonce preview-threadpool (Executors/newFixedThreadPool 1 (thread-factory)))


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

(def archivability-errors #{:invalid-mime-type :invalid-pdfa :invalid-tiff :libre-conversion-error})

(def attachment-groups #{:operation :parties :building-site})

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
   (sc/optional-key :archivabilityError) (sc/maybe (apply sc/enum archivability-errors))
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
   :applicationState                     (apply sc/enum states/all-states) ;; state of the application when attachment is created
   :state                                (apply sc/enum attachment-states) ;; attachment state
   (sc/optional-key :approved)           {:value (sc/enum :approved :rejected) ; Key name and value structure are the same as in document meta data.
                                          :user {:id sc/Str, :firstName sc/Str, :lastName sc/Str}
                                          :timestamp ssc/Timestamp
                                          :fileId ssc/ObjectIdStr }
   :target                               (sc/maybe Target)  ;;
   (sc/optional-key :source)             Source             ;;
   (sc/optional-key :ramLink)            AttachmentId       ;; reference from ram attachment to base attachment
   :required                             sc/Bool            ;;
   :requestedByAuthority                 sc/Bool            ;;
   :notNeeded                            sc/Bool            ;;
   :forPrinting                          sc/Bool            ;; see kopiolaitos.clj
   :op                                   (sc/maybe Operation)
   (sc/optional-key :groupType)          (apply sc/enum attachment-groups)
   :signatures                           [Signature]
   :versions                             [Version]
   (sc/optional-key :latestVersion)      (sc/maybe Version) ;; last item of the versions array
   (sc/optional-key :contents)           (sc/maybe sc/Str)  ;; content description
   (sc/optional-key :scale)              (apply sc/enum attachment-scales)
   (sc/optional-key :size)               (apply sc/enum attachment-sizes)
   :auth                                 [AttachmentAuthUser]
   (sc/optional-key :metadata)           {sc/Keyword sc/Any}})

;;
;; Utils
;;

(defn filename-for-pdfa [filename]
  {:pre [(string? filename)]}
  (ss/replace filename #"(-PDFA)?\.(?i)pdf$" "-PDFA.pdf"))

(defn if-not-authority-state-must-not-be [state-set {user :user {:keys [state]} :application}]
  (when (and (not (usr/authority? user))
             (state-set (keyword state)))
    (fail :error.non-authority-viewing-application-in-verdictgiven-state)))

;;
;; Api
;;

(defn attachment-groups-for-application [{primary-op :primaryOperation secondary-ops :secondaryOperations}]
  (let [operations (->> (cons primary-op secondary-ops)
                        (map (partial merge {:groupType :operation})))]
    (->> (remove #{:operation} attachment-groups)
         (map (partial assoc {} :groupType))
         (apply conj operations))))

(defn attachment-grouping [{group-type :groupType operation :op :as attachment}]
  (let [group-type (or group-type (when operation :operation))] ;; Group not set for old attachments.
    {:by-ref  (merge {:groupType group-type}
                     (when (= :operation group-type) operation))
     :by-type (att-type/group-by-type attachment)}))

(defn link-file-to-application [app-id fileId]
  {:pre [(string? app-id)]}
  (mongo/update-by-id :fs.files fileId {$set {:metadata.application app-id
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

(defn create-sent-timestamp-update-statements [attachments file-ids timestamp]
  (mongo/generate-array-updates :attachments attachments (partial by-file-ids file-ids) :sent timestamp))

(defn create-read-only-update-statements [attachments file-ids]
  (mongo/generate-array-updates :attachments attachments (partial by-file-ids file-ids) :readOnly true))

(defn make-attachment [now target required? requested-by-authority? locked? application-state group attachment-type metadata & [attachment-id contents read-only? source]]
  (cond-> {:id (or attachment-id (mongo/create-id))
           :type (select-keys attachment-type [:type-id :type-group])
           :modified now
           :locked locked?
           :readOnly (boolean read-only?)
           :applicationState (if (and (= "verdict" (:type target)) (not (states/post-verdict-states (keyword application-state))))
                               "verdictGiven"
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
           :auth []
           :contents contents}
          (:groupType group) (assoc :groupType (:groupType group))
          (map? source) (assoc :source source)
          (seq metadata) (assoc :metadata metadata)))

(defn make-attachments
  "creates attachments with nil target"
  [now application-state attachment-types-with-metadata locked? required? requested-by-authority?]
  (map #(make-attachment now nil required? requested-by-authority? locked? application-state nil (:type %) (:metadata %)) attachment-types-with-metadata))

(defn- default-tos-metadata-for-attachment-type [type {:keys [organization tosFunction verdicts]}]
  (let [metadata (-> (tos/metadata-for-document organization tosFunction type)
                     (tos/update-end-dates verdicts))]
    (if (seq metadata)
      metadata
      {:nakyvyys :julkinen})))

(defn- create-attachment-data
  "Returns the attachment data model as map. This attachment data can be pushed to mongo (no versions included)."
  [application attachment-type group now target locked? required? requested-by-authority? & [attachment-id contents read-only? source]]
  (let [metadata (default-tos-metadata-for-attachment-type attachment-type application)]
    (make-attachment now
                     target
                     required?
                     requested-by-authority?
                     locked?
                     (:state application)
                     group
                     attachment-type
                     metadata
                     attachment-id
                     contents
                     read-only?
                     source)))

(defn- create-attachment!
  "Creates attachment data and $pushes attachment to application. Updates TOS process metadata retention period, if needed"
  [application attachment-type group now target locked? required? requested-by-authority? & [attachment-id contents read-only? source]]
  {:pre [(map? application)]}
  (let [attachment-data (create-attachment-data application
                                                attachment-type
                                                group
                                                now
                                                target
                                                locked?
                                                required?
                                                requested-by-authority?
                                                attachment-id
                                                contents
                                                read-only?
                                                source)]
    (update-application
     (application->command application)
      {$set {:modified now}
       $push {:attachments attachment-data}})
    (tos/update-process-retention-period (:id application) now)
    attachment-data))

(defn create-attachments! [application attachment-types now locked? required? requested-by-authority?]
  {:pre [(map? application)]}
  (let [attachment-types-with-metadata (map (fn [type] {:type type :metadata (default-tos-metadata-for-attachment-type type application)}) attachment-types)
        attachments (make-attachments now (:state application) attachment-types-with-metadata locked? required? requested-by-authority?)]
    (update-application
      (application->command application)
      {$set {:modified now}
       $push {:attachments {$each attachments}}})
    (tos/update-process-retention-period (:id application) now)
    (map :id attachments)))

;; -------------------------------------------------------------------
;; RAM
;; -------------------------------------------------------------------

(defn ram-status-ok
  "Pre-checker that fails only if the attachment is unapproved RAM attachment."
  [{{attachment-id :attachmentId} :data app :application}]
  (let [{:keys [ramLink state]} (util/find-by-id attachment-id (:attachments app))]
    (when (and (ss/not-blank? ramLink)
               (util/not=as-kw state :ok))
      (fail :error.ram-not-approved))))

(defn ram-status-not-ok
  "Pre-checker that fails only if the attachment is approved RAM attachment."
  [{{attachment-id :attachmentId} :data app :application}]
  (let [{:keys [ramLink state]} (util/find-by-id attachment-id (:attachments app))]
    (when (and (ss/not-blank? ramLink)
               (util/=as-kw state :ok))
      (fail :error.ram-approved))))

(defn- find-by-ram-link [link attachments]
  (util/find-first (comp #{link} :ramLink) attachments))

(defn ram-not-root-attachment
  "Pre-checker that fails if the attachment is the root for RAM
  attachments and the user is applicant (authority can delete the
  root)."
  [{user :user {attachment-id :attachmentId} :data {:keys [attachments]} :application}]
  (when (and (-> attachment-id (util/find-by-id attachments) :ramLink ss/blank?)
             (find-by-ram-link attachment-id attachments)
             (usr/applicant? user))
    (fail :error.ram-cannot-delete-root)))


(defn- make-ram-attachment [{:keys [id op target type contents scale size] :as base-attachment} application now]
  (->> (default-tos-metadata-for-attachment-type type application)
       (make-attachment now target false false false (:state application) op type)
       (#(merge {:ramLink id}
                %
                (when contents {:contents contents})
                (when scale    {:scale scale})
                (when size     {:size size})))))

(defn create-ram-attachment! [{attachments :attachments :as application} attachment-id now]
  {:pre [(map? application)]}
  (let [ram-attachment (make-ram-attachment (util/find-by-id attachment-id attachments) application now)]
    (update-application
     (application->command application)
     {$set {:modified now}
      $push {:attachments ram-attachment}})
    (tos/update-process-retention-period (:id application) now)
    (:id ram-attachment)))

(defn resolve-ram-links [attachments attachment-id]
  (-> []
      (#(loop [res % id attachment-id] ; Backward linking
           (if-let [attachment (and id (not (util/find-by-id id res)) (util/find-by-id id attachments))]
             (recur (cons attachment res) (:ramLink attachment))
             (vec res))))
      (#(loop [res % id attachment-id] ; Forward linking
           (if-let [attachment (and id (not (find-by-ram-link id res)) (find-by-ram-link id attachments))]
             (recur (conj res attachment) (:id attachment))
             (vec res))))))

;; -------------------------------------------------------------------

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

(defn- make-version [attachment {:keys [file-id original-file-id filename content-type size now user stamped archivable archivabilityError missing-fonts autoConversion]}]
  (let [version-number (or (->> (:versions attachment)
                                (filter (comp #{original-file-id} :originalFileId))
                                last
                                :version)
                           (next-attachment-version (get-in attachment [:latestVersion :version]) user))]
    (util/assoc-when {:version        version-number
             :fileId         file-id
             :originalFileId (or original-file-id file-id)
             :created        now
             :user           (usr/summary user)
             ;; File name will be presented in ASCII when the file is downloaded.
             ;; Conversion could be done here as well, but we don't want to lose information.
             :filename       filename
             :contentType    content-type
             :size           size}
      :stamped stamped
      :archivable archivable
      :archivabilityError archivabilityError
      :missing-fonts missing-fonts
      :autoConversion autoConversion)))

(defn- ->approval [state user timestamp file-id]
  {:value (if (= :ok state) :approved :rejected)
   :user (select-keys user [:id :firstName :lastName])
   :timestamp timestamp
   :fileId file-id})

(defn- build-version-updates [application attachment version-model {:keys [now target state user stamped comment? comment-text]
                                                                    :or   {comment? true, state :requires_authority_action} :as options}]
  {:pre [(map? application) (map? attachment) (map? version-model) (number? now) (map? user) (keyword? state)]}

  (let [version-index  (or (-> (map :originalFileId (:versions attachment))
                               (zipmap (range))
                               (some [(:originalFileId version-model)]))
                           (count (:versions attachment)))
        user-role      (if stamped :stamper :uploader)
        comment-target (merge {:type :attachment
                               :id (:id  attachment)}
                              (select-keys version-model [:version :fileId :filename]))]
    (util/deep-merge
     (when comment?
       (comment/comment-mongo-update (:state application) comment-text comment-target :system false user nil now))
     (when target
       {$set {:attachments.$.target target}})
     (when (->> (:versions attachment) butlast (map :originalFileId) (some #{(:originalFileId version-model)}) not)
       {$set {:attachments.$.latestVersion version-model}})
     (if (and (usr/authority? user) (not= state :requires_authority_action))
       {$set {:attachments.$.approved (->approval state user now (:fileId version-model))}}
       {$unset {:attachments.$.approved 1}})
     {$set {:modified now
            :attachments.$.modified now
            :attachments.$.state  state
            (ss/join "." ["attachments" "$" "versions" version-index]) version-model}
      $addToSet {:attachments.$.auth (usr/user-in-role (usr/summary user) user-role)}})))

(defn- remove-old-files! [{old-versions :versions} {file-id :fileId original-file-id :originalFileId :as new-version}]
  (some->> (filter (comp #{original-file-id} :originalFileId) old-versions)
           (first)
           ((juxt :fileId :originalFileId))
           (remove (set [file-id original-file-id]))
           (run! delete-attachment-file-and-preview!)))

(defn set-attachment-version!
  "Creates a version from given attachment and options and saves that version to application.
  Returns version model with attachment-id (not file-id) as id."
  ([application attachment options]
    {:pre [(map? options)]}
    (set-attachment-version! application attachment options 5))
  ([application {attachment-id :id :as attachment} {:keys [stamped] :as options} retry-limit]
    {:pre [(map? application) (map? attachment) (map? options) (not (nil? stamped))]}
    (if (pos? retry-limit)
      (let [latest-version (get-in attachment [:latestVersion :version])
            version-model  (make-version attachment options)]
        ; Check return value and try again with new version number
        (if (pos? (update-application
                   (application->command application)
                   {:attachments {$elemMatch {:id attachment-id
                                              :latestVersion.version.fileId (:fileId latest-version)}}}
                   (build-version-updates application attachment version-model options)
                   true))
          (do
            (remove-old-files! attachment version-model)
            (assoc version-model :id attachment-id))
          (do
            (errorf
              "Latest version of attachment %s changed before new version could be saved, retry %d time(s)."
              attachment-id retry-limit)
            (let [application (mongo/by-id :applications (:id application) [:attachments :state])
                  attachment  (get-attachment-info application attachment-id)]
              (set-attachment-version! application attachment options (dec retry-limit))))))
      (do
        (error "Concurrency issue: Could not save attachment version meta data.")
        nil))))

(defn meta->attachment-data [{group :group :as meta}]
  (merge (select-keys meta [:contents :size :scale])
         (when (:group meta)
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

(defn update-latest-version-content!
  "Updates latest version when version is stamped"
  [user application attachment-id file-id size now]
  (let [attachment (get-attachment-info application attachment-id)
        latest-version-index (-> attachment :versions count dec)
        latest-version-path (str "attachments.$.versions." latest-version-index ".")
        old-file-id (get-in attachment [:latestVersion :fileId])
        user-summary (usr/summary user)]

    (when-not (= old-file-id file-id)
      (delete-attachment-file-and-preview! old-file-id))

    (update-application
      (application->command application)
      {:attachments {$elemMatch {:id attachment-id}}}
      {$set {:modified now
             :attachments.$.modified now
             (str latest-version-path "user") user-summary
             (str latest-version-path "fileId") file-id
             (str latest-version-path "size") size
             (str latest-version-path "created") now
             :attachments.$.latestVersion.user user-summary
             :attachments.$.latestVersion.fileId file-id
             :attachments.$.latestVersion.size size
             :attachments.$.latestVersion.created now}})))

(defn- get-or-create-attachment!
  "If the attachment-id matches any old attachment, a new version will be added.
   Otherwise a new attachment is created."
  [application {:keys [attachment-id attachment-type group created user target locked required contents read-only source] :as options}]
  {:pre [(map? application)]}
  (let [requested-by-authority? (and (ss/blank? attachment-id) (usr/authority? user))
        find-application-delay  (delay (mongo/select-one :applications {:_id (:id application) :attachments.id attachment-id} [:attachments]))]
    (cond
      (ss/blank? attachment-id) (create-attachment! application attachment-type group created target locked required requested-by-authority? nil contents read-only source)
      @find-application-delay   (get-attachment-info @find-application-delay attachment-id)
      :else (create-attachment! application attachment-type group created target locked required requested-by-authority? attachment-id contents read-only source))))

(defn get-attachment-info-by-file-id
  "gets an attachment from application or nil"
  [{:keys [attachments]} file-id]
  (first (filter (partial by-file-ids #{file-id}) attachments)))

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

(defn delete-attachment!
  "Delete attachement with all it's versions. does not delete comments. Non-atomic operation: first deletes files, then updates document."
  [application attachment-id]
  (info "1/3 deleting files of attachment" attachment-id)
  (run! delete-attachment-file-and-preview! (attachment-file-ids (get-attachment-info application attachment-id)))
  (info "2/3 deleted files of attachment" attachment-id)
  (update-application (application->command application) {$pull {:attachments {:id attachment-id}}})
  (info "3/3 deleted meta-data of attachment" attachment-id))

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
         ; Deleting latest versions resets approval
         {$unset {:attachments.$.approved 1}
          $set {:attachments.$.state :requires_authority_action}})
       {$pull {:attachments.$.versions {:fileId file-id}
               :attachments.$.signatures {:fileId file-id}}
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
                              "Content-Length" (str (:content-length attachment))
                              "Content-Disposition" (format "filename=\"%s\"" filename)}}]
      (if download?
        (assoc-in response [:headers "Content-Disposition"] (format "attachment;filename=\"%s\"" filename))
        (update response :headers merge http/no-cache-headers)))
    not-found))
  ([file-id download? attachment-fn]
   (output-attachment (attachment-fn file-id) download?)))

(hystrix/defcommand create-preview!
  {:hystrix/group-key   "Attachment"
   :hystrix/command-key "Create preview"
   :hystrix/init-fn     (fn fetch-request-init [_ setter] (.andCommandPropertiesDefaults setter (.withExecutionTimeoutInMilliseconds (HystrixCommandProperties/Setter) (* 2 60 1000))) setter)
   :hystrix/fallback-fn  (constantly nil)}
  [file-id filename content-type application-id & [db-name]]
  (when (preview/converter content-type)
    (mongo/with-db (or db-name mongo/default-db-name)
      (mongo/upload (str file-id "-preview") (str (FilenameUtils/getBaseName filename) ".jpg") "image/jpeg" (preview/placeholder-image) :application application-id)
      (when-let [preview-content (util/timing (format "Creating preview: id=%s, type=%s file=%s" file-id content-type filename)
                                              (with-open [content ((:content (mongo/download file-id)))]
                                                (preview/create-preview content content-type)))]
        (debugf "Saving preview: id=%s, type=%s file=%s" file-id content-type filename)
        (mongo/upload (str file-id "-preview") (str (FilenameUtils/getBaseName filename) ".jpg") "image/jpeg" preview-content :application application-id)))))

(def file-types
  #{:application/vnd.openxmlformats-officedocument.presentationml.presentation
    :application/vnd.openxmlformats-officedocument.wordprocessingml.document
    :application/vnd.oasis.opendocument.text
    :application/vnd.ms-powerpoint
    :application/rtf
    :application/msword
    :text/plain})

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
            (create-preview! file-id file-name content-type application-id)))
        (output-attachment preview-id false attachment-fn))
      not-found)))

(defn libreoffice-conversion-required? [{:keys [filename attachment-type]}]
  (let [mime-type (mime/mime-type (mime/sanitize-filename filename))
        {:keys [type-group type-id]} attachment-type]
    (and (libreoffice-client/enabled?)
         (or (env/feature? :convert-all-attachments)
             (and (= :paatoksenteko (keyword type-group))
                  (#{:paatos :paatosote} (keyword type-id))))
         (file-types (keyword mime-type)))))

(defn ->libre-pdfa!
  "Converts content to PDF/A using Libre Office conversion client.
  Replaces (!) original filename and content with Libre data.
  Adds :archivable + :autoConversion / :archivabilityError key depending on conversion result.
  Returns given options map with libre data merged (or if conversion failed, the original data).
  If conversion not applicable, returns original given options map."
  [{:keys [filename content skip-pdfa-conversion] :as options}]
  (if (and (not skip-pdfa-conversion)
           (libreoffice-conversion-required? options))
    (merge options (libreoffice-client/convert-to-pdfa filename content))
    options))


(defn- preview-image!
  "Creates a preview image in own thread pool. Returns the given opts."
  [application-id {:keys [file-id filename content-type] :as opts}]
  (.submit preview-threadpool #(create-preview! file-id filename content-type application-id mongo/*db-name*))
  opts)

(defn- upload-file
  "Uploads the file to MongoDB.
   Content can be a file or input-stream.
   Returns given attachment options, with file specific data added."
  [{application-id :id :as application} {:keys [filename content] :as options}]
  {:pre [(map? application)]}
  (let [file-id (mongo/create-id)
        sanitized-filename (mime/sanitize-filename filename)
        content-type (mime/mime-type sanitized-filename)]
    (mongo/upload file-id sanitized-filename content-type content :application application-id)
    (assoc options
      :file-id file-id
      :original-file-id (or (:original-file-id options) file-id)
      :filename sanitized-filename
      :content-type content-type)))

(defn upload-file-through-libre!
  [application options]
  (->> (->libre-pdfa! options)
       (upload-file application)))

(defn attach-file!
  "1) Uploads the original file to MongoDB if conversion is required and :keep-original-file? is true and
   2) converts file to PDF/A, if the file format is convertable and
   3) uploads the file to MongoDB and
   4) creates a preview image and
   5) creates a corresponding attachment structure to application
   Content can be a file or input-stream.
   Returns attachment version."
  [application options]
  (let [temp-file        (File/createTempFile "lupapiste-attach-file" ".pdf")
        _                (io/copy (:content options) temp-file)
        options          (assoc options :content temp-file)
        original-file-id (when (and (:keep-original-file? options)
                                    (libreoffice-conversion-required? options))
                           (->> (assoc options :skip-pdfa-conversion true)
                                (upload-file application)
                                (preview-image! (:id application))
                                :file-id))]
    (try
      (->> (cond-> options
                   original-file-id (assoc :original-file-id original-file-id))
           (upload-file-through-libre! application)
           (preview-image! (:id application))
           (merge options {:now (:created options) :stamped (get options :stamped false)})
           (set-attachment-version! application (get-or-create-attachment! application options)))
      (finally
        (io/delete-file temp-file :silently)))))

(defn get-attachments-by-operation
  [{:keys [attachments] :as application} op-id]
  (filter #(= (:id (:op %)) op-id) attachments))

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

(defn get-all-attachments!
  "Returns attachments as zip file. If application and lang, application and submitted application PDF are included."
  [attachments & [application lang]]
  (let [temp-file (File/createTempFile "lupapiste.attachments." ".zip.tmp")]
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

(defn temp-file-input-stream [^File file]
  (let [i (io/input-stream file)]
    (proxy [FilterInputStream] [i]
      (close []
        (proxy-super close)
        (when (= (io/delete-file file :could-not) :could-not)
          (warnf "Could not delete temporary file: %s" (.getAbsolutePath file)))))))

(defn- post-process-attachment [attachment]
  (assoc attachment :isPublic (metadata/public-attachment? attachment)))

(defn post-process-attachments [application]
  (update-in application [:attachments] (partial map post-process-attachment)))

(defn save-pdfa-file!
  "Save PDF/A file from pdf-conversion processing result to mongo gridfs.
   Returns map with archivability flags (archivable, missing-fonts, archivability error)
   and if valid PDF/A, fileId, filename and content-type of the uploaded file."
  [{app-id :id} {:keys [pdfa? output-file missing-fonts autoConversion]} filename content-type]
  (if pdfa?
    (let [pdfa-file-id  (mongo/create-id)
          pdfa-filename (filename-for-pdfa filename)
          filesize      (.length output-file)]
      (mongo/upload pdfa-file-id pdfa-filename content-type output-file :application app-id)
      {:archivable true
       :archivabilityError nil
       :fileId pdfa-file-id
       :file-name pdfa-filename
       :content-type content-type
       :content-length filesize
       :autoConversion autoConversion})
    {:archivable false :missing-fonts (or missing-fonts []) :archivabilityError :invalid-pdfa}))

(defn archivability-steps!
  "If file is PDF or TIFF, returns map to indicate if file is archivable.
   If not PDF/TIFF nor required by organization, returns nil.
   In case of PDF, PDF/A conversion is made if needed, and new converted file uploaded to mongo.
   If PDF/A conversion was made, additional :fileId, :filename, :content-type and :content-length keys are returned."
  [{application :application} {:keys [content-type content file-name]}]
  (case (name content-type)
    "application/pdf" (when (pdf-conversion/pdf-a-required? (:organization application))
                        (let [processing-result (pdf-conversion/convert-to-pdf-a (content) {:application application :filename file-name})] ; content is a function from mongo.clj
                          (if (:already-valid-pdfa? processing-result)
                            {:archivable true :archivabilityError nil :already-valid true}
                            (save-pdfa-file! application processing-result file-name content-type))))
    "image/tiff"      (let [valid? (tiff-validation/valid-tiff? content)]
                        {:archivable valid? :archivabilityError (when-not valid? :invalid-tiff)})
    nil))

(def- initial-archive-options {:archivable false :archivabilityError :invalid-mime-type})

(defn- version-options
  "Returns version options for subject (a file). This is NOT final version model (see make-version)."
  [subject pdfa-result now user]
  (merge {:file-id          (:fileId subject)
          :original-file-id (:fileId subject)
          :filename         (:file-name subject)
          :content-type     (:content-type subject)
          :size             (:content-length subject)
          :now now
          :user user
          :stamped false}
         (select-keys pdfa-result [:archivable :archivabilityError :missing-fonts :autoConversion])))

(defn- appeal-attachment-versions-options
  "Create options maps for needed versions. Created version(s) are returned in vector."
  [{now :created user :user} pdfa-result original-file]
  (if-not (nil? pdfa-result) ; nil if content-type is not regarded as archivable
    (if (:already-valid pdfa-result)
      [(version-options original-file pdfa-result now user)]
      (let [initial-versions-options [(version-options original-file ; initial version without archive results
                                                       (merge pdfa-result initial-archive-options)
                                                       now
                                                       user)]]
        (if (contains? pdfa-result :fileId) ; if PDF/A file was uploaded to mongo
          (conj initial-versions-options (version-options pdfa-result pdfa-result now user)) ; add PDF/A version
          [(version-options original-file pdfa-result now user)]))) ; just return original file, with pdfa conversion result merged
    [(version-options original-file initial-archive-options now user)]))

(defn- create-appeal-attachment-data!
  "Return attachment model for new appeal attachment with version(s) included.
   If PDF, converts to PDF/A (if applicable) and thus creates a new file to GridFS as side effect."
  [{app :application now :created user :user :as command} appeal-id appeal-type file]
  (let [type                 (att-type/attachment-type-for-appeal appeal-type)
        target               {:id appeal-id
                              :type appeal-type}
        attachment-data      (create-attachment-data app type nil now target true false false nil nil true)
        archivability-result (archivability-steps! command file)
        versions-options     (appeal-attachment-versions-options command archivability-result file)]
    (reduce (fn [attachment version-options] ; reduce attachment over versions, thus version number gets incremented correctly
              (let [version (make-version attachment version-options)]
                (-> attachment
                    (update :versions conj version)
                    (assoc :latestVersion version))))
            attachment-data
            versions-options)))

(defn new-appeal-attachment-updates!
  "Return $push operation for attachments, with attachments created for given fileIds.
   As a side effect, creates converted PDF/A version to mongo for PDF files (if applicable)."
  [command appeal-id appeal-type fileIds]
  (let [file-objects    (seq (mongo/download-find-many {:_id {$in fileIds}}))
        new-attachments (map
                          (partial
                            create-appeal-attachment-data!
                            command
                            appeal-id
                            appeal-type)
                          file-objects)]
    (when (seq new-attachments)
      {$push {:attachments {$each new-attachments}}})))

(defn attachment-array-updates
  "Generates mongo updates for application attachment array. Gets all attachments from db to ensure proper indexing."
  [app-id pred k v]
  {$set (as-> app-id $
          (lupapalvelu.domain/get-application-no-access-checking $ {:attachments true})
          (mongo/generate-array-updates :attachments (:attachments $) pred k v))})

(defn set-attachment-state! [{:keys [created user application] :as command} file-id new-state]
  {:pre [(number? created) (map? user) (map? application) (ss/not-blank? file-id) (#{:ok :requires_user_action} new-state)]}
  (if-let [attachment-id (:id (get-attachment-info-by-file-id application file-id))]
    (let [data {:state new-state,
                :approved (->approval new-state user created file-id)}]
      (update-attachment-data! command attachment-id data created :set-app-modified? true :set-attachment-modified? false))
    (fail :error.attachment.id)))
