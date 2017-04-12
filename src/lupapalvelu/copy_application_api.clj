(ns lupapalvelu.copy-application-api
  (:require [lupapalvelu.action :refer [defcommand] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.copy-application :as copy-app]
            [lupapalvelu.logging :as logging]
            [sade.coordinate :as coord]
            [sade.core :refer :all]
            [lupapalvelu.copy-application :as copy-app]))

(defcommand copy-application
  {:parameters [:source-application-id :x :y :address :propertyId :auth-invites]
   :user-roles #{:applicant :authority} ; TODO: only for company applicants
   :input-validators [(partial action/non-blank-parameters [:address :propertyId])
                      (partial action/property-id-parameters [:propertyId])
                      coord/validate-x coord/validate-y]}
  [command]
  (let [copied-application (copy-app/copy-application command)]
    (logging/with-logging-context {:applicationId (:id copied-application)}
      (app/insert-application copied-application)
      (ok :id (:id copied-application)))))
