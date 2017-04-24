(ns lupapalvelu.copy-application-api
  (:require [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.copy-application :as copy-app]
            [lupapalvelu.logging :as logging]
            [sade.coordinate :as coord]
            [sade.core :refer :all]
            [lupapalvelu.copy-application :as copy-app]))

(defcommand copy-application
  {:parameters [:source-application-id :x :y :address :propertyId :auth-invites]
   :user-roles #{:applicant :authority} ; TODO: only for company applicants and authorities
   :input-validators [(partial action/non-blank-parameters [:address :propertyId :source-application-id])
                      (partial action/property-id-parameters [:propertyId])
                      (partial action/vector-parameters [:auth-invites])
                      coord/validate-x coord/validate-y]}
  [command]
  (let [copied-application (copy-app/copy-application command)]
    (logging/with-logging-context {:applicationId (:id copied-application)}
      (app/insert-application copied-application)
      (copy-app/send-invite-notifications! copied-application command)
      (ok :id (:id copied-application)))))

(defquery copy-application-invite-candidates
  {:parameters [:source-application-id]
   :user-roles #{:applicant :authority} ; TODO: only for company applicants and authorities
   :input-validators [(partial action/non-blank-parameters [:source-application-id])]}
  [{{:keys [source-application-id]} :data user :user}]
  (ok :candidates (copy-app/copy-application-invite-candidates user source-application-id)))
