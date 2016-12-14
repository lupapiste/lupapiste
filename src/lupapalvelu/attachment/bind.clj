(ns lupapalvelu.attachment.bind
  (:require [sade.core :refer :all]
            [sade.util :as util]
            [schema.core :as sc]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.tags :as att-tags]
            [lupapalvelu.job :as job]
            ))

(sc/defschema BindableFile
  {(sc/required-key :fileId)           sc/Str
   (sc/required-key :type)             att/Type
   (sc/required-key :groupType)        (sc/maybe (apply sc/enum att-tags/attachment-groups))
   (sc/optional-key :contents)         (sc/maybe sc/Str)
   (sc/optional-key :sign)             sc/Bool
   (sc/optional-key :constructionTime) sc/Bool})

(defn- bind-attachments! [command file-infos job-id]
  (doseq [filedata file-infos]
    (job/update job-id assoc (:fileId filedata) {:status :working :fileId (:fileId filedata)})
    ; do bind
    (job/update job-id assoc (:fileId filedata) {:status :done :fileId (:fileId filedata)})))

(defn- bind-job-status [data]
  (if (every? #{:done :error} (map #(get-in % [:status]) (vals data))) :done :running))

(defn- bindable-file-check
  "Coerces target and type and then checks against schema"
  [file]
  (sc/check BindableFile (-> file
                             (update :target att/attachment-target-coercer)
                             (update :type att/attachment-type-coercer))))

(defn make-bind-job
  [command file-infos]
  {:pre [(every? bindable-file-check file-infos)]}
  (let [job (-> (zipmap (map :fileId file-infos) (map #(assoc % :status :pending) file-infos))
                (job/start bind-job-status))]
    (util/future* (bind-attachments! command file-infos (:id job)))
    job))
