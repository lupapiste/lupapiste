(ns lupapalvelu.authorization-messages
  (:require [clostache.parser :as clostache]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.notifications :as notifications]))

(defn- recognition-of-authorization [description-context]
  (clostache/render (i18n/localize (:lang description-context)
                                   "email.invite-description.default")
                    description-context))

(defn- create-invite-email-model [command conf recipient]
  (merge (notifications/create-app-model command conf recipient)
         {:message (get-in command [:data :text])
          :recipient-email (:email recipient)
          :inviter-email (-> command :user :email)
          :recognition-of-authorization #(i18n/localize %
                                                        (str "email.invite.recognition-of-authorization."
                                                             (if (= (:permitType (:application command)) "R")
                                                               "R"
                                                               "default")))}))

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
