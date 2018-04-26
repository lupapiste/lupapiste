(ns lupapalvelu.attachment.bind
  (:require [taoensso.timbre :refer [info warnf]]
            [sade.core :refer :all]
            [sade.util :as util]
            [schema.core :as sc]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.tags :as att-tags]
            [lupapalvelu.attachment.preview :as preview]
            [lupapalvelu.job :as job]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [lupapalvelu.authorization :as auth]
            [lupapiste-commons.attachment-types :as att-types]
            [sade.schemas :as ssc]
            [sade.shared-schemas :as sssc]))

(sc/defschema NewVersion
  {(sc/required-key :fileId)           sssc/UUIDStr
   (sc/required-key :attachmentId)     sc/Str})

(sc/defschema NewAttachment
  {(sc/required-key :fileId)           sssc/UUIDStr
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

(sc/defschema BindableFile (sc/if :attachmentId NewVersion NewAttachment))

(defn- file-is-to-be-marked-construction-time [{:keys [permitType]} {{typeGroup :type-group typeId :type-id} :type}]
  (let [type-config (att-types/types-marked-being-construction-time-attachments-by-permit-type (keyword permitType))
        config-by-group (get type-config (keyword typeGroup))]
    (util/contains-value? config-by-group (keyword typeId))))

(defn bind-single-attachment! [{:keys [application user created]} mongo-file {:keys [fileId type attachmentId contents] :as filedata} exclude-ids]
  (let [conversion-data       (att/conversion application (assoc mongo-file :content ((:content mongo-file))))
        is-authority          (usr/user-is-authority-in-organization? user (:organization application))
        automatic-ok-enabled  (org/get-organization-auto-ok (:organization application))
        placeholder-id        (or attachmentId
                               (att/get-empty-attachment-placeholder-id (:attachments application) type (set exclude-ids)))
        attachment            (or
                                (att/get-attachment-info application placeholder-id)
                                (att/create-attachment! application
                                                      (assoc (select-keys filedata [:group :contents :target :source :disableResell :backendId])
                                                              :requested-by-authority (boolean (auth/application-authority? application user))
                                                        :created         created
                                                        :attachment-type type)))
        version-options (merge
                          (select-keys mongo-file [:fileId :filename :contentType :size])
                          (select-keys filedata [:contents :drawingNumber :group :constructionTime :sign :target])
                          (util/assoc-when {:created          created
                                            :original-file-id fileId}
                                           :comment-text contents
                                           :state (when (and is-authority automatic-ok-enabled)
                                                    :ok)
                                           :constructionTime (boolean (and (not is-authority)
                                                                           (:type filedata)
                                                                           (file-is-to-be-marked-construction-time application
                                                                                                                   filedata))))
                          (:result conversion-data)
                          (:file conversion-data))
        linked-version (att/set-attachment-version! application user attachment version-options)
        {:keys [fileId originalFileId]} linked-version]
    (preview/preview-image! (:id application) (:fileId version-options) (:filename version-options) (:contentType version-options))
    (storage/link-files-to-application (:id application) (cond-> [originalFileId]
                                                                 (not= fileId originalFileId) (conj fileId)))
    (att/cleanup-temp-file (:result conversion-data))
    (assoc linked-version :type (or (:type linked-version) (:type attachment)))))

(defn- bind-attachments! [command file-infos job-id]
  (reduce
    (fn [results {:keys [fileId type] :as filedata}]
      (job/update job-id assoc fileId {:status :working :fileId fileId})
      (if-let [unlinked-file (storage/download fileId)]
        (let [result (bind-single-attachment! command unlinked-file filedata (map :attachment-id results))]
          (job/update job-id assoc fileId {:status :done :fileId fileId})
          (conj results {:original-file-id fileId
                         :fileId (:fileId result)
                         :attachment-id (:id result)
                         :type (or type (:type result))
                         :status :done}))
        (do
          (warnf "no file with file-id %s in mongo" fileId)
          (job/update job-id assoc fileId {:status :error :fileId fileId})
          (conj results {:fileId fileId :type type :status :error}))))
    []
    file-infos))

(defn- bind-job-status [data]
  (if (every? #{:done :error} (map #(get-in % [:status]) (vals data))) :done :running))

(defn- cancel-job [job-id {:keys [status text]}]
  (warnf "canceling bind job %s due '%s'" job-id text)
  (job/update job-id #(util/map-values (fn [{file-id :fileId}] {:fileId file-id :status status :text text}) %)))

(defn- coerce-bindable-file
  "Coerces bindable file data"
  [file]
  (if (:attachmentId file)
    file
    (-> file
        (update :type   att/attachment-type-coercer)
        (update :target #(and % (att/attachment-target-coercer %)))
        (update :group (fn [{group-type :groupType operations :operations :as group}]
                         (util/assoc-when nil
                                          :groupType  (and group-type (att/group-type-coercer group-type))
                                          :operations (and operations (map att/->attachment-operation operations))))))))

(defn make-bind-job
  "postprocess-fn can be either a function or a list of functions."
  [command file-infos & {:keys [preprocess-ref postprocess-fn]
                         :or   {preprocess-ref (delay (ok))
                                postprocess-fn identity}}]
  (let [coerced-file-infos (->> (map coerce-bindable-file file-infos)
                                (sc/validate [BindableFile]))
        job                (-> (zipmap (map :fileId coerced-file-infos)
                                       (map #(assoc % :status :pending)
                                            coerced-file-infos))
                               (job/start bind-job-status))]
    (util/future*
     (if (ok? @preprocess-ref)
       (let [results (bind-attachments! command coerced-file-infos (:id job))]
         (doseq [fun (flatten [postprocess-fn])]
           (fun results)))
       (cancel-job (:id job) (assoc @preprocess-ref :status :error))))
    job))
