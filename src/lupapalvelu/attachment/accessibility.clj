(ns lupapalvelu.attachment.accessibility
  (:require [lupapalvelu.attachment.metadata :as metadata]
            [lupapalvelu.authorization :as auth]
            [sade.core :refer :all]
            [sade.util :as util]
            [sade.strings :as ss]))


(defn visibility-check [user {app-auth :auth org :organization} {:keys [auth] :as attachment}]
  (case (keyword (metadata/get-visibility attachment))
    :asiakas-ja-viranomainen (or
                               (or (auth/has-auth? {:auth auth} (:id user)) ; attachment auth
                                   (auth/has-auth? {:auth app-auth} (:id user)) ; application auth
                                   (auth/has-auth-via-company? {:auth app-auth} user))
                               (auth/org-authz org user))
    :viranomainen (or (auth/has-auth? {:auth auth} (:id user)) (auth/org-authz org user)) ; attachment auth
    :julkinen true
    nil true))

(defn publicity-check [user {org :organization} {:keys [auth] :as attachment}]
  (case (keyword (metadata/get-publicity-class attachment)) ; TODO check cases
    :osittain-salassapidettava (or (auth/has-auth-role? {:auth auth} (:id user) :uploader) (auth/org-authz org user))
    :salainen (or (auth/has-auth-role? {:auth auth} (:id user) :uploader) (auth/org-authz org user))
    :julkinen true
    nil true))

(defn can-access-attachment?
  "Checks user's access right to attachment from application auth and attachment auth"
  [user {:as application} {:keys [latestVersion auth] :as attachment}]
  {:pre [(map? attachment)]}
  (assert (if latestVersion (seq auth) true))
  (boolean
    (or
      (nil? latestVersion)
      (metadata/public-attachment? attachment)
      (and (seq auth) (publicity-check user application attachment) (visibility-check user application attachment)))))

(defn auth-from-version [{user :user stamped? :stamped}]
  (assoc user :role (if stamped? :stamper :uploader)))

(defn- populate-auth
  "If attachment doesn't auth array, assocs auth array on runtime"
  [{auth :auth versions :versions :as attachment}]
  (if (or (empty? versions) (seq auth))
    attachment
    (assoc attachment :auth (distinct (map auth-from-version versions)))))

(defn can-access-attachment-file? [user file-id {attachments :attachments :as application}]
  (let [file-id (if (ss/ends-with file-id "preview")
                  (first (ss/split file-id #"-preview$"))
                  file-id)]
    (boolean
      (when-let [attachment (util/find-first
                              (fn [{versions :versions}]
                                (->> (mapcat (juxt :fileId :originalFileId) versions)
                                     (util/find-first #{file-id})))
                              attachments)]
        (can-access-attachment? user application attachment)))))

(defn filter-attachments-for [user application attachments]
  {:pre [(map? user) (sequential? attachments)]}
  (let [attachments-with-auth (map populate-auth attachments)]
    (filter (partial can-access-attachment? user application) attachments-with-auth)))

(defn has-attachment-auth [{user :user {attachment-id :attachmentId} :data app :application}]
  (when (and attachment-id (not (auth/application-authority? app user)))
    (if-let [attachment (util/find-by-id attachment-id (:attachments app))]
      (when (and (seq (:auth attachment)) (not (auth/has-auth? attachment (:id user))))
        (fail :error.attachment.no-auth))
      (fail :error.unknown-attachment))))

(defn has-attachment-auth-role [role {user :user {attachment-id :attachmentId} :data app :application}]
  (when (and attachment-id (not (auth/application-authority? app user)))
    (if-let [attachment (util/find-by-id attachment-id (:attachments app))]
      (when (and (seq (:auth attachment)) (not (auth/has-auth-role? attachment (:id user) role)))
        (fail :error.attachment.no-auth))
      (fail :error.unknown-attachment))))
