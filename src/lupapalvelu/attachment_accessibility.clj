(ns lupapalvelu.attachment-accessibility
  (:require [lupapalvelu.attachment-metadata :as metadata]
            [lupapalvelu.user :as user]
            [sade.util :as util]))


(defn owns-latest-version? [user {latest :latestVersion}]
  (= (-> latest :user :id) (:id user)))


(defn can-access-attachment?
  [user {meta :metadata latest :latestVersion :as attachment}]
  (or
    (nil? latest)
    (user/authority? user)
    (owns-latest-version? user attachment)
    (metadata/public-attachment? attachment)))

(defn can-access-attachment-file? [user file-id {attachments :attachments}]
  (boolean
    (when-let [attachment (util/find-first
                            (fn [{versions :versions :as attachment}]
                              (util/find-first #{file-id} (map :fileId versions)))
                            attachments)]
      (can-access-attachment? user attachment))))

(defn filter-attachments-for [user attachments]
  {:pre [(map? user) (sequential? attachments)]}
  (filter (partial can-access-attachment? user) attachments))
