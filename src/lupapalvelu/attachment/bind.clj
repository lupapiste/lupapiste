(ns lupapalvelu.attachment.bind
  (:require [taoensso.timbre :refer [info warnf]]
            [sade.core :refer :all]
            [sade.util :as util]
            [schema.core :as sc]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.tags :as att-tags]
            [lupapalvelu.attachment.preview :as preview]
            [lupapalvelu.job :as job]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [sade.schemas :as ssc]
            [sade.strings :as ss]))

(sc/defschema BindableFile
  {(sc/required-key :fileId)           ssc/ObjectIdStr
   (sc/required-key :type)             att/Type
   (sc/required-key :group)            {:groupType              (sc/maybe (apply sc/enum att-tags/attachment-groups))
                                        (sc/optional-key :id)   ssc/ObjectIdStr
                                        (sc/optional-key :name) sc/Str}
   (sc/optional-key :attachmentId)     sc/Str
   (sc/optional-key :contents)         (sc/maybe sc/Str)
   (sc/optional-key :sign)             sc/Bool
   (sc/optional-key :constructionTime) sc/Bool})

(defn- bind-attachments! [{:keys [application user created]} file-infos job-id]
  (reduce
    (fn [results {:keys [fileId type attachmentId contents] :as filedata}]
      (job/update job-id assoc fileId {:status :working :fileId fileId})
      (if-let [mongo-file (mongo/download fileId)]
        (let [conversion-data    (att/conversion application (assoc mongo-file :attachment-type type :content ((:content mongo-file))))
              placeholder-id     (or attachmentId
                                     (att/get-empty-attachment-placeholder-id (:attachments application) type (set (map :attachment-id results))))
              attachment         (or
                                   (att/get-attachment-info application placeholder-id)
                                   (att/create-attachment! application
                                                           (assoc (select-keys filedata [:group :contents])
                                                             :requested-by-authority (usr/authority? user)
                                                             :created         created
                                                             :attachment-type type)))
              version-options (merge
                                (select-keys mongo-file [:fileId :filename :contentType :size])
                                (select-keys filedata [:contents :group :constructionTime :sign])
                                (util/assoc-when {:created          created
                                                  :original-file-id fileId}
                                                 :comment-text contents)
                                (:result conversion-data)
                                (:file conversion-data))
              linked-version (att/set-attachment-version! application user attachment version-options)]
          (preview/preview-image! (:id application) (:fileId version-options) (:filename version-options) (:contentType version-options))
          (att/link-files-to-application (:id application) ((juxt :fileId :originalFileId) linked-version))
          (att/cleanup-temp-file (:result conversion-data))
          (job/update job-id assoc fileId {:status :done :fileId fileId})
          (conj results {:original-file-id fileId
                         :fileId (:fileId linked-version)
                         :attachment-id (:id linked-version)
                         :type type
                         :status :done}))
        (do
          (warnf "no file with file-id %s in mongo" (:fileId))
          (job/update job-id assoc fileId {:status :error :fileId fileId})
          {:fileId fileId :type type :status :error})))
    []
    file-infos))

(defn- bind-job-status [data]
  (if (every? #{:done :error} (map #(get-in % [:status]) (vals data))) :done :running))

(defn- bindable-file-check
  "Coerces target and type and then checks against schema"
  [file]
  (sc/check BindableFile (-> file
                             (update :target att/attachment-target-coercer)
                             (update :type att/attachment-type-coercer)
                             (update-in [:group :groupType] att/group-type-coercer))))

(defn make-bind-job
  [command file-infos]
  {:pre [(every? bindable-file-check file-infos)]}
  (let [job (-> (zipmap (map :fileId file-infos) (map #(assoc % :status :pending) file-infos))
                (job/start bind-job-status))]
    (util/future* (bind-attachments! command file-infos (:id job)))
    job))
