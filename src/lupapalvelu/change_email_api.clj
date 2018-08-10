(ns lupapalvelu.change-email-api
  (:require [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :refer [fn->] :as util]
            [lupapalvelu.action :refer [defquery defcommand defraw email-validator some-pre-check] :as action]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.user :as usr]
            [lupapalvelu.company :as com]
            [lupapalvelu.change-email :as change-email]))

(defn change-email-link [lang token]
  (str (env/value :host) "/app/" (name lang) "/welcome#!/email/" token))

(defn change-email-simple [lang token]
  (str (env/value :host) "/app/" (name lang) "/welcome#!/change-email-simple/" token))

(notifications/defemail :change-email
  {:recipients-fn notifications/from-user
   :model-fn (fn [{data :data} _ recipient]
               (let [{:keys [id expires]} (:token data)]
                 (merge
                   (select-keys data [:old-email :new-email])
                   {:user    recipient
                    :expires (util/to-local-datetime expires)
                    :link    #(change-email-link % id)})))})

(defn simple-email-model [{data :data} _ _]
  (let [{:keys [id expires]} (:token data)]
    (merge
      (select-keys data [:old-email :new-email])
      {:expires (util/to-local-datetime expires)
       :link    #(change-email-simple % id)})))

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
   :description "Starts the workflow for changing user password"}
  [{user :user}]
  (change-email/init-email-change user email))

(defcommand change-email
  {:parameters [tokenId stamp]
   :input-validators [(partial action/non-blank-parameters [:tokenId :stamp])]
   :notified   true
   :user-roles #{:anonymous}}
  [_]
  (change-email/change-email tokenId stamp))

(defcommand change-email-simple
  {:parameters [tokenId]
   :input-validators [(partial action/non-blank-parameters [:tokenId])]
   :notified   true
   :description "Simplified email change for known roles such as company user and financial authority."
   :user-roles #{:anonymous}}
  [_]
  (change-email/change-email tokenId nil))
