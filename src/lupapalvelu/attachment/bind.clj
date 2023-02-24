(ns lupapalvelu.attachment.bind
  (:require [clojure.set :as set]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.conversion :as conversion]
            [lupapalvelu.attachment.preview :as preview]
            [lupapalvelu.attachment.tags :as att-tags]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.filebank :as filebank]
            [lupapalvelu.job :as job]
            [lupapalvelu.organization :as org]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.user :as usr]
            [lupapiste-commons.attachment-types :as att-types]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [taoensso.timbre :refer [warnf]]))

(sc/defschema NewVersion
  {(sc/required-key :fileId)           sssc/FileId
   (sc/required-key :attachmentId)     sc/Str})

(sc/defschema NewAttachment
  {(sc/required-key :fileId)           sssc/FileId
   (sc/required-key :type)             att/Type
   (sc/required-key :group)            (sc/maybe {:groupType  (apply sc/enum att-tags/attachment-groups)
                                                  (sc/optional-key :operations) [{(sc/optional-key :id)   ssc/ObjectIdStr
                                                                                  (sc/optional-key :name) sc/Str}]})
   (sc/optional-key :target)           (sc/maybe att/Target)
   (sc/optional-key :source)           att/Source
   (sc/optional-key :contents)         (sc/maybe sc/Str)
   (sc/optional-key :drawingNumber)    sc/Str
   (sc/optional-key :sign)             sc/Bool
   (sc/optional-key :constructionTime) sc/Bool
   (sc/optional-key :disableResell)    sc/Bool
   (sc/optional-key :backendId)        (sc/maybe sc/Str)})

(sc/defschema NewFilebankFile
  {(sc/required-key :fileId)      sssc/FileId
   (sc/required-key :filebankId)  sc/Str
   (sc/optional-key :keywords)    [sc/Str]})

(sc/defschema BindableFile
  (sc/conditional :attachmentId NewVersion
                  :filebankId   NewFilebankFile
                  :else         NewAttachment))

(sc/defschema FileInfo
  "Basic file information pair that is used when resolving multi-attachment update."
  {:fileId   ssc/NonBlankStr
   :filename ssc/NonBlankStr})

(defn- file-is-to-be-marked-construction-time [{:keys [permitType]} {{typeGroup :type-group typeId :type-id} :type}]
  (let [type-config (att-types/types-marked-being-construction-time-attachments-by-permit-type (keyword permitType))
        config-by-group (get type-config (keyword typeGroup))]
    (util/contains-value? config-by-group (keyword typeId))))

(defn- get-placeholder-attachment [application filedata exclude-ids]
  (->> (or (:attachmentId filedata)
           (att/get-empty-attachment-placeholder-id (:attachments application) (:type filedata) (set exclude-ids)))
       (att/get-attachment-info application)))

(defn- create-bindable-attachment!
  "Refactored from bind-single-attachment! for sanity reasons"
  [application {:keys [type] :as filedata} user created]
  (att/create-attachment! application
                          (assoc (select-keys filedata [:group :contents :target :source :disableResell :backendId])
                                 :requested-by-authority (boolean (auth/application-authority? application user))
                                 :attachment-type        type
                                 :created                created)))

(defn- make-version-options
  "Refactored from bind-single-attachment! for sanity reasons"
  [unlinked-file application {:keys [fileId contents type] :as filedata} user created conversion-data]
  (let [automatic-ok?     (org/get-organization-auto-ok (:organization application))
        is-authority      (usr/user-is-authority-in-organization? user (:organization application))
        construction-time (boolean (and (not is-authority)
                                        type
                                        (file-is-to-be-marked-construction-time application filedata)))]
    (merge
      (select-keys unlinked-file [:fileId :filename :contentType :size])
      (select-keys filedata [:contents :drawingNumber :group :constructionTime :sign :target])
      (util/assoc-when {:created created :original-file-id fileId}
                       :comment-text     contents
                       :state            (when (and is-authority automatic-ok?) :ok)
                       :constructionTime construction-time)
      (:result conversion-data)
      (:file conversion-data))))

(defn bind-single-attachment!
  "Moves a single attachment file from the temporary file bucket to its appropriate place"
  [{:keys [application user created]} unlinked-file filedata exclude-ids]
  (try
    (let [conversion-data         (conversion/archivability-conversion {:uploader-user-id (:id user)} application unlinked-file)
          {:keys [fileId]
           :as   version-options} (make-version-options unlinked-file application filedata user created conversion-data)
          originalFileId          (or (:original-file-id version-options) fileId)
          ;; If linking fails no attachment/version is created
          _                       (storage/link-files-to-application (:id user)
                                                                     (:id application)
                                                                     (cond-> [originalFileId]
                                                                       (not= fileId originalFileId)
                                                                       (conj fileId)))
          attachment              (or (get-placeholder-attachment application filedata exclude-ids)
                                      (create-bindable-attachment! application filedata user created))
          {version-type :type
           :as linked-version} (att/set-attachment-version! application user attachment version-options)]
      (preview/preview-image (:id application) version-options)
      (cond-> linked-version
        (nil? version-type) (assoc :type (:type attachment))))
    (catch Throwable t
      (warnf t "Bind failed for filedata %s" (pr-str filedata))
      (throw t))))

(defn bind-single-filebank-file!
  "Moves a single filebank file from the temporary file bucket to its appropriate place"
  [{:keys [user created]} unlinked-file {:keys [fileId filebankId keywords] :as filedata} _]
  (storage/link-files-to-application (:id user) filebankId [fileId])
  (filebank/create-file! filebankId
                         fileId
                         (:filename unlinked-file)
                         (:size unlinked-file)
                         created
                         (select-keys user [:id :username :firstName :lastName :role])
                         (or keywords []))
  filedata)

(defn bind-single-file!
  "Dispatches the filebinding according to destination type.
   Use case is simple enough that a multimethod would be overkill."
  [command destination unlinked-file filedata exclude-ids]
  (let [fun (case destination
              :attachment  bind-single-attachment!
              :filebank    bind-single-filebank-file!)]
    (fun command unlinked-file filedata exclude-ids)))

(defn- bind-files!
  "Moves several files from temporary to permanent storage and attaches them to a collection in the database"
  [{:keys [user] :as command} destination file-infos job-id]
  (reduce
    (fn [results {:keys [fileId type] :as filedata}]
      (job/update-by-id job-id fileId {:status :working :fileId fileId})
      (if-let [unlinked-file (storage/download-unlinked-file (:id user) fileId)]
        ;; Unlinked file found, bind it
        (try
          (let [bound-id  (keyword (str (name destination) "-id")) ;e.g. :attachment -> :attachment-id
                result    (bind-single-file! command destination unlinked-file filedata (map bound-id results))]
            (job/update-by-id job-id fileId {:status :done :fileId fileId})
            (conj results {:original-file-id  fileId
                           :fileId            (:fileId result)
                           bound-id           (:id result)
                           :type              (or type (:type result))
                           :status            :done}))
          (catch Throwable _
            (job/update-by-id job-id fileId {:status :error :fileId fileId})
            (conj results {:fileId fileId :type type :status :error})))
        ;; No unlinked file, produce error
        (do (warnf "no file with file-id %s in storage" fileId)
            (job/update-by-id job-id fileId {:status :error :fileId fileId})
            (conj results {:fileId fileId :type type :status :error}))))
    []
    file-infos))

(defn- cancel-job [job-id {:keys [status text]}]
  (warnf "canceling bind job %s due '%s'" job-id text)
  (job/update job-id #(util/map-values (fn [{file-id :fileId}] {:fileId file-id :status status :text text}) %)))

(defn- coerce-bindable-file
  "Coerces bindable file data"
  [destination file]
  (if (or (not= destination :attachment) (:attachmentId file))
    file
    (-> file
        (update :type   att/attachment-type-coercer)
        (update :target #(and % (att/attachment-target-coercer %)))
        (update :group (fn [{group-type :groupType operations :operations}]
                         (util/assoc-when nil
                                          :groupType  (and group-type (att/group-type-coercer group-type))
                                          :operations (and operations (map att/->attachment-operation operations))))))))

(defn make-bind-job
  "The postprocess-fn parameter can be either a function or a list of functions.
   The destination tells the job what type of file to treat the bound file as and where to move it to.
   Possible values for destination are :attachment or :filebank"
  [command destination file-infos & {:keys [preprocess-ref postprocess-fn]
                         :or   {preprocess-ref (delay (ok))
                                postprocess-fn identity}}]
  (let [coerced-file-infos (->> (map (partial coerce-bindable-file destination) file-infos)
                                (sc/validate [BindableFile]))
        job                (-> (zipmap (map :fileId coerced-file-infos)
                                       (map #(assoc % :status :pending) coerced-file-infos))
                               (job/start))]
    (util/future*
     (if (ok? @preprocess-ref)
       (let [results (bind-files! command destination coerced-file-infos (:id job))]
         (doseq [fun (flatten [postprocess-fn])]
           (fun results)))
       (cancel-job (:id job) (assoc @preprocess-ref :status :error))))
    job))

(defn- uniq-filenames [xs]
  (->> (map :filename xs)
       (group-by identity)
       vals
       (filter #(= 1 (count %)))
       (map first)
       set))

(sc/defn ^:always-validate resolve-attachment-update-candidates
  :- [{:attachmentId ssc/NonBlankStr
       :fileId       ssc/NonBlankStr}]
  [application files :- [FileInfo]]
  (let [files     (map #(update % :filename ss/normalize) files)
        atts      (->> (:attachments application)
                       (filter :latestVersion)
                       (map (fn [a]
                              {:id       (:id a)
                               :filename (-> a :latestVersion :filename ss/normalize)})))
        infonames (uniq-filenames files)
        att-names (uniq-filenames atts)]
    (map (fn [filename]
           {:attachmentId (:id (util/find-by-key :filename filename atts))
            :fileId       (:fileId (util/find-by-key :filename filename files))})
         (set/intersection infonames att-names))))
