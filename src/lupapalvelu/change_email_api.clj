(ns lupapalvelu.change-email-api
  (:require [taoensso.timbre :as timbre :refer [debug info infof warn warnf error]]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.action :refer [defquery defcommand defraw email-validator] :as action]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as usr]
            [lupapalvelu.company :as com]
            [lupapalvelu.vetuma :as vetuma]))

(defn change-email-link [lang token]
  (str (env/value :host) "/app/" lang "/welcome#!/email/" token))

(notifications/defemail :change-email
  {:recipients-fn notifications/from-user
   :model-fn (fn [{data :data} conf recipient]
               (let [{:keys [id expires]} (:token data)]
                 (merge
                   (select-keys data [:old-email :new-email])
                   {:name    (:firstName recipient)
                    :expires (util/to-local-datetime expires)
                    :link-fi (change-email-link "fi" id)
                    :link-sv (change-email-link "sv" id)})))})

(notifications/defemail :email-changed
  {:recipients-fn (fn [{user :user}]
                    (if-let [company-id (get-in user [:company :id])]
                      (->> (com/find-company-admins company-id) (remove #(= (:id %) (:id user))) (cons user))
                      [user]))
   :model-fn (fn [{:keys [user data]} conf recipient]
               {:name (:firstName recipient) :old-email (:email user) :new-email (:new-email data)})})

(defn- init-email-change [user new-email]
  (let [token-id (token/make-token :change-email user {:new-email new-email} :auto-consume false :ttl ttl/change-email-token-ttl)
        token (token/get-token token-id)]
    (notifications/notify! :change-email {:user (assoc user :email new-email), :data {:old-email (:email user), :new-email new-email, :token token}})))

(defn- has-person-id? [user]
  (if-let [user-id (:id user)]
    (let [full-user (if (contains? user :personId) user (usr/get-user-by-id! user-id))]
      (not (ss/blank? (:personId full-user))))
    false))

(defcommand change-email-init
  {:parameters [email]
   :user-roles #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :pre-checks [(fn [{user :user} _] (when-not (has-person-id? user) (fail :error.unauthorized)))]
   :description "Starts the workflow for changing user password"}
  [{user :user}]
  (let [email (usr/canonize-email email)]
    (if-not (usr/get-user-by-email email)
      (init-email-change user email)
      (fail :error.duplicate-email))))

(defn- change-email [token stamp]
  {:pre [(map? token)]}

  (let [vetuma-data (vetuma/get-user stamp)
        new-email (get-in token [:data :new-email])
        {hetu :personId old-email :email :as user} (usr/get-user-by-id! (:user-id token))]

    (cond
      (not= (:token-type token) :change-email) (fail! :error.token-not-found)
      (not= hetu (:userid vetuma-data)) (fail! :error.personid-mismatch)
      (usr/get-user-by-email new-email) (fail! :error.duplicate-email))

    ; Strictly this atomic update is enough.
    ; Access to applications is determined by user id.
    (usr/update-user-by-email old-email {:personId hetu} {$set {:username new-email :email new-email}})

    ; Update application.auth arrays.
    ; They might have duplicates due to old bugs, ensure everything is updated.
    (loop [n 1]
      (when (pos? n)
        ; loop exists when no applications with the old username were found
        (recur (mongo/update-by-query :applications {:auth {$elemMatch {:id (:id user), :username old-email}}} {$set {:auth.$.username new-email}}))))

    ; Also update emails in invite auths
    (loop [n 1]
      (when (pos? n)
        (recur (mongo/update-by-query :applications
                                      {:auth {$elemMatch {:id (:id user)
                                                          :invite.username old-email}}}
                                      {$set {:auth.$.invite.email new-email
                                             :auth.$.invite.user.username new-email}}))))

    ; Cleanup tokens
    (vetuma/consume-user stamp)
    (token/get-token (:id token) :consume true)

    ; Send notifications
    (notifications/notify! :email-changed {:user user, :data {:new-email new-email}})

    (ok)))

(defcommand change-email
  {:parameters [tokenId stamp]
   :input-validators [(partial action/non-blank-parameters [:tokenId :stamp])]
   :user-roles #{:anonymous}}
  [_]
  (if-let [token (token/get-token tokenId)]
    (change-email token stamp)
    (fail :error.token-not-found)))
