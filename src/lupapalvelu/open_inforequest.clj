(ns lupapalvelu.open-inforequest
  (:require [taoensso.timbre :as timbre :refer [infof info]]
            [lupapalvelu.core :refer [now fail! defraw]]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [lupapalvelu.organization :as organization]
            [sade.security :refer [random-password]]
            [lupapalvelu.notifications :as notifications]
            [noir.session :as session]
            [noir.response :as resp]))

(defn new-open-inforequest! [{application-id :id organization-id :organization} host]
  (assert application-id)
  (assert organization-id)
  (let [organization    (organization/get-organization organization-id)
        admin-email     (:open-inforequest-email organization)
        token-id        (random-password 48)]
    (when-not organization (fail! :error.unknown-organization))
    (when-not admin-email (fail! :error.missing-organization-email))
    (infof "open-inforequest: new-inforequest: application=%s, organization=%s, email=%s, token=%s" application-id organization-id admin-email token-id)
    (mongo/insert :open-inforequest-token {:id              token-id
                                           :application-id  application-id
                                           :created         (now)
                                           :last-used       nil})
    (notifications/send-open-inforequest-invite! admin-email token-id application-id host)
    true))

(defn make-user [token]
  {:id "777777777777777777000023"
    :email "sonja.sibbo@sipoo.fi"
    :enabled true
    :role :authority
    :organizations ["433-R"]
    :firstName "Sonja"
    :lastName "Sibbo"
    :phone "03121991"
    :username "sonja"})

(defraw openinforequest
  [{{token-id :token-id} :data lang :lang {:keys [host]} :web}]
  (info "open-inforequest: open:" token-id lang)
  (let [token (mongo/by-id :open-inforequest-token token-id)
        url   (str host "/app/" (name lang) "/authority#!/inforequest/" (:application-id token))]
    (when-not token (fail! :error.unknown-open-inforequest-token))
    (mongo/update-by-id :open-inforequest-token token-id {$set {:last-used (now)}})
    (session/put! :user (make-user token))
    (resp/redirect url :permanent)))
