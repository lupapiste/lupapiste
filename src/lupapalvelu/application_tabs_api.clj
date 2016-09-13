(ns lupapalvelu.application-tabs-api
  "Pseudo queries to handle application's tabs' visibility in UI"
  (:require [sade.core :refer :all]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.action :as action :refer [defquery]]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]))

(defquery tasks-tab-visible
  {:parameters [id]
   :states states/post-verdict-states
   :org-authz-roles auth/reader-org-authz-roles
   :user-roles #{:authority :applicant}
   :pre-checks [(fn [{:keys [application]}]
                    (when (foreman/foreman-app? application)
                      (fail :error.foreman.no-tasks)))
                (permit/validate-permit-type-is permit/R permit/YA)]}
  [_])

