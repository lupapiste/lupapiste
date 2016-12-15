(ns lupapalvelu.authorization-messages
  (:require [lupapalvelu.notifications :as notifications]))

(defn- create-invite-email-model [command conf recipient]
  (assoc (notifications/create-app-model command conf recipient)
    :message (get-in command [:data :text])
    :recipient-email (:email recipient)
    :inviter-email (-> command :user :email)))

(notifications/defemail :invite  {:recipients-fn :recipients
                                  :model-fn create-invite-email-model})

(notifications/defemail :guest-invite  {:recipients-fn :recipients
                                        :model-fn create-invite-email-model})

(defn- create-prev-permit-invite-email-model [command conf recipient]
  (assoc (notifications/create-app-model command conf recipient)
    :kuntalupatunnus (get-in command [:data :kuntalupatunnus])
    :recipient-email (:email recipient)))

(notifications/defemail :invite-to-prev-permit  {:recipients-fn :recipients
                                                 :model-fn create-prev-permit-invite-email-model
                                                 :subject-key "invite"})
