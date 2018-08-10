(ns lupapalvelu.attachment.util
  (:require [sade.util :as util]))

(defn get-operation-ids [{op :op}]
  (mapv :id op))

(defn get-original-file-id
  "Returns original file id of the attachment version that matches
  file-id. File-id can be either fileId or originalFileId."
  [attachment file-id]
  (some->> (:versions attachment)
           (util/find-first (fn [{:keys [fileId originalFileId]}]
                              (or (= fileId file-id)
                                  (= originalFileId file-id))))
           :originalFileId))

(defn attachment-version-state [attachment file-id]
  (when-let [file-key (keyword (get-original-file-id attachment file-id))]
    (some-> attachment :approvals file-key :state keyword)))

(defn attachment-state [attachment]
  (attachment-version-state attachment (some-> attachment
                                                        :latestVersion
                                                        :originalFileId)))
