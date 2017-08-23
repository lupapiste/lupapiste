(ns lupapalvelu.financial-api
  (:require [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.financial :as financial]
            [lupapalvelu.application :as application]))

(defcommand create-financial-handler
  {:parameters [:email]
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :user-roles #{:admin :authorityAdmin}}
  [{user-data :data user :user}]
    (financial/create-financial-handler user-data user))

(defcommand invite-financial-handler
  {:parameters [:id :documentId :path]
   :categories #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id])
                      action/email-validator]
   :user-roles #{:applicant :authority}
   :pre-checks  [application/validate-authority-in-drafts]
   :notified   true}
  [command]
  (financial/invite-financial-handler command))

(defcommand remove-financial-handler-invitation
  {:parameters [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles #{:applicant :authority}
   :pre-checks  [application/validate-authority-in-drafts]
   :notified true}
  [command]
  (financial/remove-financial-handler-invitation command))