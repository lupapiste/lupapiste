(ns lupapalvelu.ya-digging-permit-api
  (:require [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-api :as app-api]
            [lupapalvelu.copy-application :as copy-app]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.operations :as op]
            [lupapalvelu.organization :as org]
            [lupapalvelu.ya-digging-permit :as ya-digging]
            [sade.core :refer :all]))

(defcommand create-digging-permit
  {:description "Creates a digging permit from an existing sijoituslupa application"
   :parameters [:id :operation]
   :user-roles #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:id :operation])
                      app-api/operation-validator]
   :pre-checks [ya-digging/validate-digging-permit-source
                ya-digging/validate-digging-permit-operation]}
  [{:keys [application user created organization]
    {operation :operation} :data
    :as command}]
  (when (not (op/selected-operation-for-organization? @organization operation))
    (fail! :error.operations.hidden
           :organization (:id @organization)
           :operation operation))
  (let [digging-permit-app (ya-digging/new-digging-permit application
                                                          user created operation
                                                          @organization)]
    (logging/with-logging-context {:applicationId (:id digging-permit-app)}
      (app/do-add-link-permit digging-permit-app (:id application))
      (app/insert-application digging-permit-app)
      (copy-app/send-invite-notifications! digging-permit-app command)
      (ok :id (:id digging-permit-app)))))

(defquery selected-digging-operations-for-organization
  {:description "Returns selected digging operations for the given organization"
   :parameters [:organization]
   :user-roles #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:organization])]}
  [{{:keys [organization]} :data}]
  (if-let [organization (org/get-organization organization)]
    (ok :operations (ya-digging/organization-digging-operations organization))
    (fail! :error.organization-not-found :organization organization)))
