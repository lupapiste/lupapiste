(ns lupapalvelu.change-email-api
  (:require [taoensso.timbre :as timbre :refer [debug info infof warn warnf error]]
            [sade.core :refer :all]
            [sade.env :as env]
            [lupapalvelu.action :refer [defquery defcommand defraw email-validator] :as action]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as usr]))

(defn change-email-link [lang token]
  (str (env/value :host) "/app/" lang "/welcome#!/email/" token))

(notifications/defemail :change-email
  {:recipients-fn notifications/from-user
   :model-fn (fn [{{token :token} :data} conf recipient]
               {:link-fi (change-email-link "fi" token), :link-sv (change-email-link "sv" token)})})

(defn- init-email-change [user new-email]
  (let [token (token/make-token :change-email user {:new-email new-email} :auto-consume false :ttl ttl/change-email-token-ttl)]
    (notifications/notify! :change-email {:user user, :data {:token token}})))

(defcommand change-email
  {:parameters [email]
   :user-roles #{:applicant}
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :description "Starts the workflow for changing user password"}
  [{user :user}]
  (let [email (usr/canonize-email email)]
    (if-not (usr/get-user-by-email email)
      (init-email-change user email)
      (fail :error.duplicate-email))))
