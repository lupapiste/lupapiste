(ns lupapalvelu.ddd-map-api
  "3D map view API."
  (:require [monger.operators :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.action :as action :refer [defcommand]]
            [lupapalvelu.organization :as org]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.states :as states]
            [lupapalvelu.ddd-map :as ddd]))

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

(defcommand redirect-to-3d-map
  {:description "Makes a properly formatted (auth, apikey) request to
  the 3D map backend server and returns the location information of
  the redirect response."
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles
   :states           states/all-states
   :pre-checks       [ddd/three-d-map-enabled]}
  [{:keys [application user organization]}]
  (ddd/redirect-to-3d-map user application @organization))
