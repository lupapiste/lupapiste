(ns lupapalvelu.open-inforequest
  (:require [lupapalvelu.authorization :as auth]
            [lupapalvelu.logging :refer [error infof]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permissions :refer [defcontext]]
            [lupapalvelu.security :refer [random-password]]
            [lupapalvelu.system.util-components :as components]
            [monger.operators :refer :all]
            [sade.core :refer [fail! now]]
            [sade.strings :as ss]))

(defcontext inforequest-context [{{user-id :id :as user} :user application :application}]
  ;; Some permissions are added in inforequests by user application role
  (when (:infoRequest application)
    {:context-scope :inforequest
     :context-roles (->> (auth/get-auths application user-id)
                         (concat (auth/get-company-auths-by-role application user))
                         (map :role))}))

(defn- base-email-model [{{token :token-id} :data
                          :keys             [system]
                          :as               command} _ recipient]
  (assoc
    (notifications/create-app-model command nil recipient)
    :link (fn [lang] (str (components/system->host system) "/api/raw/openinforequest?token-id=" token "&lang=" (name lang)))))

(def base-email-conf
  {:recipients-fn  notifications/from-data
   :subject-key    "email.title.inforequest-invite"
   :model-fn       base-email-model})

(notifications/defemail :open-inforequest-invite (assoc base-email-conf :template "inforequest-invite.md"))
(notifications/defemail :open-inforequest-commented (assoc base-email-conf :template "inforequest-commented.md"))

(defn notify-on-comment [{:keys [application user data system]} _]
  (when (and (:openInfoRequest application) (not (ss/blank? (:text data))))
    (if-let [token (mongo/select-one :open-inforequest-token {:application-id (:id application)})]
      (when (not= (:email user) (:email token))
        (notifications/notify! :open-inforequest-commented {:data        {:email (:email token) :token-id (:id token)}
                                                            :application application
                                                            :system      system}))
      (error "Open inforequest token not found! Application ID=" (:id application)))))

(defn new-open-inforequest! [system
                             {application-id  :id
                              organization-id :organization
                              municipality    :municipality
                              permit-type     :permitType :as application}]
  {:pre [application-id organization-id municipality permit-type]}
  (let [organization (organization/get-organization organization-id)
        scope        (organization/resolve-organization-scope municipality permit-type organization)
        email        (:open-inforequest-email scope)
        token-id     (random-password 48)]
    (when-not organization (fail! :error.unknown-organization))
    (when-not email (fail! :error.missing-organization-email))
    (infof "open-inforequest: new-inforequest: application=%s, organization=%s, email=%s, token=%s" application-id organization-id email token-id)
    (mongo/insert :open-inforequest-token {:id              token-id
                                           :application-id  application-id
                                           :organization-id organization-id
                                           :email           email
                                           :created         (now)
                                           :last-used       nil})
    (notifications/notify! :open-inforequest-invite {:data        {:email email :token-id token-id}
                                                     :application application
                                                     :system      system})
    true))
