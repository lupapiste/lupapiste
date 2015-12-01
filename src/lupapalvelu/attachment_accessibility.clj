(ns lupapalvelu.attachment-accessibility
  (:require [lupapalvelu.attachment-metadata :as metadata]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.user :as user]
            [sade.util :as util]
            [sade.core :refer :all]))


(defn visibility-check [user app-auth {:keys [metadata auth] :as attachment}]
  (case (keyword (metadata/get-visibility attachment))
    :asiakas-ja-viranomainen (or
                               (or (auth/has-auth? {:auth auth} (:id user)) ; attachment auth
                                   (auth/has-auth? {:auth app-auth} (:id user))) ; application auth
                               (user/authority? user))
    :viranomainen (or (auth/has-auth? {:auth auth} (:id user)) (user/authority? user)) ; attachment auth
    :julkinen true
    nil true))

(defn publicity-check [user app-auth {:keys [metadata auth] :as attachment}]
  (case (keyword (metadata/get-publicity-class attachment)) ; TODO check cases
    :osittain-salassapidettava (or (auth/has-auth-role? {:auth auth} (:id user) :uploader) (user/authority? user))
    :salainen (or (auth/has-auth-role? {:auth auth} (:id user) :uploader) (user/authority? user))
    :julkinen true
    nil true))

(defn can-access-attachment?
  [user app-auth {:keys [latestVersion metadata auth] :as attachment}]
  {:pre [(map? attachment)]}
  (boolean
    (or
      (nil? latestVersion)
      (and (metadata/public-attachment? attachment) (not (nil? user)))
      (if auth                                                ; TODO remove when auth migration is done
        (and (publicity-check user app-auth attachment) (visibility-check user app-auth attachment))
        (or (auth/has-auth? {:auth app-auth} (:id user)) (user/authority? user))))))

(defn can-access-attachment-file? [user file-id {attachments :attachments auth :auth}]
  (boolean
    (when-let [attachment (util/find-first
                            (fn [{versions :versions :as attachment}]
                              (util/find-first #{file-id} (map :fileId versions)))
                            attachments)]
      (can-access-attachment? user auth attachment))))

(defn filter-attachments-for [user auths attachments]
  {:pre [(map? user) (sequential? attachments)]}
  (filter (partial can-access-attachment? user auths) attachments))

(defn has-attachment-auth [{user :user {attachment-id :attachmentId} :data} {attachments :attachments}]
  (when (and attachment-id (not (user/authority? user)))
    (if-let [{auth :auth} (util/find-first #(= (:id %) attachment-id) attachments)]
      (when (and auth (not (auth/has-auth? {:auth auth} (:id user))))
        (fail :error.attachment.no-auth))
      (fail :error.unknown-attachment))))
