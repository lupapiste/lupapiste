(ns lupapalvelu.application-options-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error errorf]]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [defcommand update-application defquery] :as action]
            [lupapalvelu.application :as a]))

(defcommand set-municipality-hears-neighbors
  {:parameters [:id enabled]
   :user-roles #{:applicant :authority}
   :states     #{:draft :open}
   :input-validators [(partial action/boolean-parameters [:enabled])]
   :pre-checks       [a/validate-authority-in-drafts]}
  [command]
  (update-application command {$set {"options.municipalityHearsNeighbors" enabled}})
  (ok))

(defquery municipality-hears-neighbors-visible
  {:description "Pseudo query for Municipality hears neighbors
  checkbox visibility. The option is available only for R and P permit
  types."
   :user-roles #{:applicant :authority}
   :pre-checks [(partial a/valid-permit-types #{:R :P})]}
  [_])
