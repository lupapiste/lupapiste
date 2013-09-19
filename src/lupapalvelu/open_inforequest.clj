(ns lupapalvelu.open-inforequest
  (:require [taoensso.timbre :as timbre :refer [infof]]
            [lupapalvelu.core :refer [now fail!]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]
            [sade.security :refer [random-password]]
            [lupapalvelu.notifications :as notifications]))

(defn new-open-inforequest! [{application-id :id organization-id :organization} host]
  (assert application-id)
  (assert organization-id)
  (let [organization    (organization/get-organization organization-id)
        admin-email     (:open-inforequest-email organization)
        token-id        (random-password 48)]
    (when-not organization (fail! :error.unknown-organization))
    (when-not admin-email (fail! :error.missing-organization-email))
    (infof "open-inforequest: application=%s, organization=%s, email=%s, token=%s" application-id organization-id admin-email token-id)
    (mongo/insert :open-inforequest-token {:id              token-id
                                           :application-id  application-id
                                           :created         (now)
                                           :last-used       nil})
    (notifications/send-open-inforequest-invite! admin-email token-id application-id host)
    true))
