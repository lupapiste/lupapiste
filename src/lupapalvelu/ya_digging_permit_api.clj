(ns lupapalvelu.ya-digging-permit-api
  (:require [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-api :as app-api]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.operations :as op]
            [lupapalvelu.ya-digging-permit :as ya-digging]
            [sade.core :refer :all]))

(defcommand create-digging-permit
  {:description "Create a digging permit from an existing sijoituslupa application"
   :parameters [:id :operation]
   :user-roles #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:id :operation])
                      app-api/operation-validator]
   :pre-checks [ya-digging/validate-digging-permit-source
                ya-digging/validate-digging-permit-operation]}
  [{:keys [application user created organization] {operation :operation} :data}]
  (when (not (op/selected-operation-for-organization? @organization operation))
    (fail! :error.operations.hidden
           :organization (:id @organization)
           :operation operation))
  (let [digging-permit-app (ya-digging/new-digging-permit application
                                                          user created operation)]
    (logging/with-logging-context {:applicationId (:id digging-permit-app)}
      (app/do-add-link-permit digging-permit-app (:id application))
      (app/insert-application digging-permit-app)
      (ok :id (:id digging-permit-app)))))
