(ns lupapalvelu.attachment-accessibility
  (:require [lupapalvelu.attachment-metadata :as metadata]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.user :as user]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.util :as util]))


(defn visibility-check [user {app-auth :auth org :organization} {:keys [metadata auth] :as attachment}]
  (case (keyword (metadata/get-visibility attachment))
    :asiakas-ja-viranomainen (or
                               (or (auth/has-auth? {:auth auth} (:id user)) ; attachment auth
                                   (auth/has-auth? {:auth app-auth} (:id user))) ; application auth
                               (auth/org-authz org user))
    :viranomainen (or (auth/has-auth? {:auth auth} (:id user)) (auth/org-authz org user)) ; attachment auth
    :julkinen true
    nil true))

(defn publicity-check [user {app-auth :auth org :organization} {:keys [metadata auth] :as attachment}]
  (case (keyword (metadata/get-publicity-class attachment)) ; TODO check cases
    :osittain-salassapidettava (or (auth/has-auth-role? {:auth auth} (:id user) :uploader) (auth/org-authz org user))
    :salainen (or (auth/has-auth-role? {:auth auth} (:id user) :uploader) (auth/org-authz org user))
    :julkinen true
    nil true))

(defn can-access-attachment?
  "Checks user's access right to attachment from application auth and attachment auth"
  [{authz :orgAuthz :as user} {app-auth :auth :as application} {:keys [latestVersion metadata auth] :as attachment}]
  {:pre [(map? attachment) (sequential? app-auth)]}
  (assert (if latestVersion (seq auth) true))
  (boolean
    (or
      (nil? latestVersion)
      (metadata/public-attachment? attachment)
      (and (seq auth) (publicity-check user application attachment) (visibility-check user application attachment)))))

(defn can-access-attachment-file? [user file-id {attachments :attachments :as application}]
  (boolean
    (when-let [attachment (util/find-first
                            (fn [{versions :versions :as attachment}]
                              (util/find-first #{file-id} (map :fileId versions)))
                            attachments)]
      (can-access-attachment? user application attachment))))

(defn- auth-from-version [{user :user stamped? :stamped}]
  (assoc user :role (if stamped? :stamper :uploader)))

(defn- populate-auth
  "If attachment doesn't auth array, assocs auth array on runtime"
  [{auth :auth versions :versions :as attachment}]
  (if (or (empty? versions) (seq auth))
    attachment
    (assoc attachment :auth (map  versions))))

(defn filter-attachments-for [user application attachments]
  {:pre [(map? user) (sequential? attachments)]}
  (if (env/feature? :attachment-visibility)
    (let [attachments (map populate-auth attachments)]
      (filter (partial can-access-attachment? user application) attachments))
    attachments))

(defn has-attachment-auth [{user :user {attachment-id :attachmentId} :data} {attachments :attachments}]
  (when (and attachment-id (not (user/authority? user)))
    (if-let [{auth :auth} (util/find-first #(= (:id %) attachment-id) attachments)]
      (when (and auth (not (auth/has-auth? {:auth auth} (:id user))))
        (fail :error.attachment.no-auth))
      (fail :error.unknown-attachment))))
