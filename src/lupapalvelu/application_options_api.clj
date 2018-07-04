(ns lupapalvelu.application-options-api
  (:require [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.permit :as permit]
            [monger.operators :refer :all]
            [sade.core :refer :all]))

(defn- not-foreman-application
  "Precheck that fails for foreman applications."
  [command]
  (when (some-> command :application foreman/foreman-app?)
    (fail :error.foreman-application)))

(defcommand set-municipality-hears-neighbors
  {:parameters       [:id enabled]
   :user-roles       #{:applicant :authority}
   :states           #{:draft :open}
   :input-validators [(partial action/boolean-parameters [:enabled])]
   :pre-checks       [app/validate-authority-in-drafts
                      (partial permit/valid-permit-types {:R [] :P :all})
                      not-foreman-application]}
  [command]
  (action/update-application command {$set {"options.municipalityHearsNeighbors" enabled}})
  (ok))

(defquery municipality-hears-neighbors-visible
  {:description "Pseudo query for Municipality hears neighbors
  checkbox visibility. The option is availablce only for R and P permit
  types."
   :user-roles #{:applicant :authority}
   :pre-checks [(partial permit/valid-permit-types {:R [] :P :all})
                not-foreman-application]}
  [_])
