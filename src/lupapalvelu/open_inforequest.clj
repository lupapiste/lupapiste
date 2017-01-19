(ns lupapalvelu.open-inforequest
  (:require [taoensso.timbre :as timbre :refer [infof info error]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.core :refer [now fail fail!]]
            [sade.strings :as ss]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.security :refer [random-password]]
            [lupapalvelu.notifications :as notifications]))

(defn- base-email-model [{{token :token-id} :data :as command} _ recipient]
  (assoc
    (notifications/new-email-app-model command nil recipient)
    :link (fn [lang] (str (env/value :host) "/api/raw/openinforequest?token-id=" token "&lang=" (name lang)))))

(def base-email-conf
  {:recipients-fn  notifications/from-data
   :subject-key    "email.title.inforequest-invite"
   :model-fn       base-email-model})

(notifications/defemail :open-inforequest-invite (assoc base-email-conf :template "inforequest-invite.md"))
(notifications/defemail :open-inforequest-commented (assoc base-email-conf :template "inforequest-commented.md"))

(defn notify-on-comment [{application :application user :user data :data} _]
  (when (and (:openInfoRequest application) (not (ss/blank? (:text data))))
    (if-let [token (mongo/select-one :open-inforequest-token {:application-id (:id application)})]
      (when (not= (:email user) (:email token))
        (notifications/notify! :open-inforequest-commented {:data {:email (:email token) :token-id (:id token)} :application application}))
      (error "Open inforequest token not found! Application ID=" (:id application)))))

(defn new-open-inforequest! [{application-id :id organization-id :organization municipality :municipality permit-type :permitType :as application}]
  {:pre [application-id organization-id municipality permit-type]}
  (let [organization    (organization/get-organization organization-id)
        scope           (organization/resolve-organization-scope municipality permit-type organization)
        email           (:open-inforequest-email scope)
        token-id        (random-password 48)]
    (when-not organization (fail! :error.unknown-organization))
    (when-not email (fail! :error.missing-organization-email))
    (infof "open-inforequest: new-inforequest: application=%s, organization=%s, email=%s, token=%s" application-id organization-id email token-id)
    (mongo/insert :open-inforequest-token {:id               token-id
                                           :application-id   application-id
                                           :organization-id  organization-id
                                           :email            email
                                           :created          (now)
                                           :reminder-sent    nil
                                           :last-used        nil})
    (notifications/notify! :open-inforequest-invite {:data {:email email :token-id token-id}
                                                     :application application})
    true))
