(ns lupapalvelu.application-options-api
  (:require [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer [fail ok]]
            [sade.util :as util]))

(defn- not-foreman-application
  "Precheck that fails for foreman applications."
  [command]
  (when (some-> command :application foreman-util/foreman-app?)
    (fail :error.foreman-application)))

(defn- only-authority-may-alter-in
  "Give authority users some leeway to alter the setting in additional states"
  [states]
  {:pre [(set? states)]}
  (fn [{user                         :user
        {:keys [state organization]} :application}]
    (when (and user state
               (util/includes-as-kw? states state)
               (not (usr/user-is-authority-in-organization? user organization)))
      (fail :error.command-illegal-state))))

(def ^:private supported-permit-type
  (partial permit/valid-permit-types {:R [:muutoslupa :empty] :P :all}))

(defcommand set-municipality-hears-neighbors
  {:parameters       [:id enabled]
   :user-roles       #{:applicant :authority}
   :states           #{:draft :open :submitted :sent}
   :input-validators [(partial action/boolean-parameters [:enabled])]
   :permissions      [{:required [:application/edit]}]
   :pre-checks       [app/validate-authority-in-drafts
                      supported-permit-type
                      not-foreman-application
                      (only-authority-may-alter-in #{:submitted :sent})]}
  [command]
  (action/update-application command {$set {"options.municipalityHearsNeighbors" enabled}})
  (ok))

(defquery municipality-hears-neighbors-visible
  {:description "Pseudo query for Municipality hears neighbors
  checkbox visibility. The option is availablce only for R and P permit
  types."
   :user-roles #{:applicant :authority}
   :pre-checks [supported-permit-type
                not-foreman-application]}
  [_]
  (ok))
