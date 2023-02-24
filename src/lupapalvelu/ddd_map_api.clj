(ns lupapalvelu.ddd-map-api
  "3D map view API."
  (:require [lupapalvelu.action :as action :refer [defcommand defquery]]
            [lupapalvelu.ddd-map :as ddd]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.organization :as org]
            [lupapalvelu.states :as states]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [taoensso.timbre :as timbre]))

(defcommand update-3d-map-server-details
  {:description      "3D map backend server details. If user is blank (when
  trimmed), then the basic authentication will not be used."
   :parameters       [organizationId url username password]
   :input-validators [(partial action/non-blank-parameters [:organizationId])
                      (partial action/validate-optional-https-url :url)]
   :user-roles       #{:admin}}
  [_]
  (org/update-organization-3d-map-server organizationId
                                         (ss/trim url) username password))

(defcommand set-3d-map-enabled
  {:description      "Toggle 3D map support for the organization."
   :parameters       [organizationId flag]
   :input-validators [(partial action/non-blank-parameters [:organizationId])
                      (partial action/boolean-parameters [:flag])]
   :user-roles       #{:admin}}
  [_]
  (org/update-organization organizationId {$set {:3d-map.enabled flag}}))

(defquery redirect-to-3d-map
  {:description      "Makes a properly formatted (auth, apikey) request to
  the 3D map backend server and returns the location information of
  the redirect response."
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :permissions      [{:required [:application/read]}]
   :states           states/all-states
   :pre-checks       [ddd/three-d-map-enabled]}
  [{:keys [application] :as command}]
  (logging/with-logging-context {:applicationId (:id application)}
    (timbre/debug "redirect-to-3d-map request start")
    (ddd/redirect-to-3d-map command)))
