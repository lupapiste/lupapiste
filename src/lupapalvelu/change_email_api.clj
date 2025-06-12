(ns lupapalvelu.change-email-api
  (:require [lupapalvelu.action :refer [defcommand some-pre-check] :as action]
            [lupapalvelu.change-email :as change-email]
            [lupapalvelu.company :as com]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.user :as usr]
            [lupapalvelu.vetuma :as vetuma]
            [sade.core :refer [fail ok]]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn change-email-link [system lang token]
  (str (components/system->host system) "/app/" (name lang) "/welcome#!/email/" token))

(defn change-email-simple [system lang token]
  (str (components/system->host system) "/app/" (name lang) "/welcome#!/change-email-simple/" token))

(notifications/defemail :change-email
  {:recipients-fn notifications/from-user
   :model-fn (fn [{:keys [data system]} _ recipient]
               {:pre [(map? system)]}
               (let [{:keys [id expires]} (:token data)]
                 (merge
                   (select-keys data [:old-email :new-email])
                   {:user    recipient
                    :expires (date/finnish-datetime expires :zero-pad)
                    :link    #(change-email-link system % id)})))})

(defn simple-email-model [{:keys [data system]} _ _]
  {:pre [(map? system)]}
  (let [{:keys [id expires]} (:token data)]
    (merge
      (select-keys data [:old-email :new-email])
      {:expires (date/finnish-datetime expires :zero-pad)
       :link    #(change-email-simple system % id)})))

(notifications/defemail :change-email-for-company-user
  {:recipients-fn notifications/from-user
   :subject-key "change-email"
   :model-fn simple-email-model})

(notifications/defemail :change-email-for-financial-authority
                        {:recipients-fn notifications/from-user
                         :subject-key "change-email"
                         :template "change-email-for-company-user.md"
                         :model-fn simple-email-model})

(notifications/defemail :email-changed
  {:recipients-fn (fn [{user :user}]
                    (if-let [company-id (get-in user [:company :id])]
                      (->> (com/find-company-admins company-id)
                           (remove #(= (:id %) (:id user)))
                           (cons user))
                      [user]))
   :model-fn (fn [{:keys [user data]} _ _]
               {:old-email (:email user)
                :new-email (:new-email data)})})

(defn- has-verified-person-id? [user]
  (if-let [user-id (:id user)]
    (-> (if (and (ss/not-blank? (:personIdSource user)) (ss/not-blank? (:personId user)))
          user
          (usr/get-user-by-id! user-id))
        usr/verified-person-id?)
    false))

(defn- validate-has-verified-person-id [{user :user}]
  (when-not (has-verified-person-id? user)
    (fail :error.unauthorized)))

(defcommand change-email-init
  {:parameters [email]
   :user-roles #{:applicant :authority :financialAuthority}
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :notified   true
   :pre-checks [(some-pre-check validate-has-verified-person-id
                                (com/validate-has-company-role :any)
                                (com/validate-is-financial-authority))]
   :description "Starts the workflow for changing user email address"}
  [{:keys [user system]}]
  (change-email/init-email-change system user email))

(defcommand change-email-init-suomifi
  {:parameters       [email userId]
   :user-roles       #{:anonymous}
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :notified         true
   :description      "Starts the workflow for changing user email address after strong authentication"}
  [{:keys [system]}]
  (if-let [person-id (some-> (vetuma/vetuma-session) :user :userid)]
    (if-let [user (->> (usr/get-users-by-person-id person-id)
                       (util/find-by-id userId))]
      (change-email/init-email-change system user email)
      (fail :error.user-not-found))
    (fail :error.invalid-vetuma-user)))

(defcommand change-email
  {:parameters [tokenId stamp]
   :input-validators [(partial action/non-blank-parameters [:tokenId :stamp])]
   :notified   true
   :user-roles #{:anonymous}}
  [{:keys [created system]}]
  (change-email/change-email system tokenId stamp created))

(defcommand change-email-simple
  {:parameters [tokenId]
   :input-validators [(partial action/non-blank-parameters [:tokenId])]
   :notified   true
   :description "Simplified email change for known roles such as company user and financial authority."
   :user-roles #{:anonymous}}
  [{:keys [created system]}]
  (change-email/change-email system tokenId nil created))
