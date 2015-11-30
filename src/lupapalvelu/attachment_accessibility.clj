(ns lupapalvelu.attachment-accessibility
  (:require [lupapalvelu.attachment-metadata :as metadata]
            [lupapalvelu.user :as user]
            [sade.util :as util]
            [lupapalvelu.authorization :as auth]))


(defn visibility-check [user app-auth {:keys [metadata auth] :as attachment}]
  (case (keyword (metadata/get-visibility attachment))
    :asiakas-ja-viranomainen (or (auth/has-auth? {:auth app-auth} (:id user)) (user/authority? user))
    :viranomainen (or (auth/has-auth? {:auth auth} (:id user)) (user/authority? user)) ; attachment auth
    :julkinen true
    nil true))

(defn publicity-check [user app-auth {:keys [metadata auth] :as attachment}]
  (case (keyword (metadata/get-publicity-class attachment)) ; TODO check cases
    :osittain-salassapidettava (or (auth/has-auth-role? {:auth auth} (:id user) :uploader) (user/authority?))
    :salainen (or (auth/has-auth-role? {:auth auth} (:id user) :uploader) (user/authority?))
    :julkinen true
    nil true))

(defn can-access-attachment?
  [user app-auth {:keys [latestVersion metadata auth] :as attachment}]
  (boolean
    (or
      (nil? latestVersion)
      (metadata/public-attachment? attachment)
      (if auth                                                ; TODO remove when auth migration is done
        (and (publicity-check user app-auth attachment) (visibility-check user app-auth attachment))
        true))))

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
