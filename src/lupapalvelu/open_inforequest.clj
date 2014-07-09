(ns lupapalvelu.open-inforequest
  (:require [taoensso.timbre :as timbre :refer [infof info error]]
            [monger.operators :refer :all]
            [noir.session :as session]
            [noir.response :as resp]
            [sade.env :as env]
            [lupapalvelu.core :refer [now fail fail!]]
            [lupapalvelu.action :refer [defraw]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.security :refer [random-password]]
            [lupapalvelu.notifications :as notifications]))

(defn not-open-inforequest-user-validator [{user :user} _]
  (when (:oir user)
    (fail :error.not-allowed-for-oir)))

(defn- base-email-model [{{token :token-id} :data} _]
  (let  [link-fn (fn [lang] (str (env/value :host) "/api/raw/openinforequest?token-id=" token "&lang=" (name lang)))
         info-fn (fn [lang] (env/value :oir :wanna-join-url))]
    {:link-fi (link-fn :fi)
     :link-sv (link-fn :sv)
     :info-fi (info-fn :fi)
     :info-sv (info-fn :sv)}))

(def base-email-conf
  {:recipients-fn  notifications/from-data
   :subject-key    "applications.inforequest"
   :model-fn       base-email-model})

(notifications/defemail :open-inforequest-invite (assoc base-email-conf :template "open-inforequest-invite.html"))
(notifications/defemail :open-inforequest-commented (assoc base-email-conf :template "new-comment.md"))

(defn notify-on-comment [{application :application user :user} _]
  (when (:openInfoRequest application)
    (if-let [token (mongo/select-one :open-inforequest-token {:application-id (:id application)})]
      (when (not= (:email user) (:email token))
        (notifications/notify! :open-inforequest-commented {:data {:email (:email token) :token-id (:id token)} :application application}))
      (error "Open inforequest token not found! Application ID=" (:id application)))))

(defn new-open-inforequest! [{application-id :id organization-id :organization municipality :municipality permit-type :permitType :as application}]
  {:pre [application-id organization-id municipality permit-type]}
  (let [organization    (organization/get-organization organization-id)
        scope           (organization/resolve-organization-scope organization municipality permit-type)
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

(defn- make-user [token]
  (let [organization-id (:organization-id token)
        email (:email token)]
    {:id (str "oir-" organization-id "-" email)
     :email email
     :enabled true
     :role "authority"
     :oir true
     :organizations [organization-id]
     :firstName ""
     :lastName email
     :phone ""
     :username email}))

(defraw openinforequest
  [{{token-id :token-id} :data lang :lang}]
  (info "open-inforequest: open:" token-id lang)
  (let [token (mongo/by-id :open-inforequest-token token-id)
        url   (str (env/value :host) "/app/" (name lang) "/oir#!/inforequest/" (:application-id token))]
    (when-not token (fail! :error.unknown-open-inforequest-token))
    (mongo/update-by-id :open-inforequest-token token-id {$set {:last-used (now)}})
    (session/put! :user (make-user token))
    (resp/redirect url)))
