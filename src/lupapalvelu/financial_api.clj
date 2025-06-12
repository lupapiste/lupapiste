(ns lupapalvelu.financial-api
  (:require [lupapalvelu.action :refer [defcommand] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.financial :as financial]
            [lupapalvelu.organization :as org]
            [lupapalvelu.states :as states]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as usr]
            [sade.core :refer [ok]]))

(defcommand create-financial-handler
  {:parameters       [:email]
   :input-validators [usr/AdminCreateUser]
   :pre-checks       [org/orgAuthz-pre-checker]
   :permissions      [{:required [:users/create-financial-handler]}]
   :feature          :financial}
  [{user-data :data caller :user system :system}]
  (let [user  (usr/create-new-user caller user-data)
        ;; Don't auto-consume; allows reading token for password quality estimation
        token (token/make-token :password-reset caller {:email (:email user)} :ttl ttl/create-user-token-ttl :auto-consume false)
        host  (components/system->host system)]
    (ok :id (:id user)
        :user user
        :linkFi (str host "/app/fi/welcome#!/setpw/" token)
        :linkSv (str host "/app/sv/welcome#!/setpw/" token))))

(defcommand invite-financial-handler
  {:parameters [:id :documentId :path]
   :categories #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id])
                      action/email-validator]
   :user-roles #{:applicant :authority}
   :pre-checks  [application/validate-authority-in-drafts]
   :notified   true
   :feature :financial}
  [command]
  (or (financial/invite-financial-handler command)
      (ok)))

(defcommand remove-financial-handler-invitation
  {:parameters [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles #{:applicant :authority}
   :pre-checks  [application/validate-authority-in-drafts]
   :notified true
   :feature :financial}
  [command]
  (financial/remove-financial-handler-invitation command)
  (ok))

(defcommand notify-organizations-housing-office
  {:parameters [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles #{:applicant :authority}
   :pre-checks [(fn [command]
                  (when-not (contains? states/post-submitted-states (keyword (-> command :application :state)))
                    (ok)))]
   :notified true
   :feature :financial}
  [command]
  (financial/notify-housing-office command)
  (ok))
