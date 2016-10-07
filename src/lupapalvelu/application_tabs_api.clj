(ns lupapalvelu.application-tabs-api
  "Pseudo queries to handle application's tabs' visibility in UI"
  (:require [sade.core :refer :all]
            [sade.util :refer [fn->]]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.action :as action :refer [defquery]]
            [lupapalvelu.application :as app]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]))

(defquery tasks-tab-visible
  {:parameters [id]
   :states states/post-verdict-states
   :org-authz-roles auth/reader-org-authz-roles
   :user-authz-roles auth/all-authz-roles
   :user-roles #{:authority :applicant}
   :pre-checks [(fn [{:keys [application]}]
                    (when (foreman/foreman-app? application)
                      (fail :error.foreman.no-tasks)))
                (permit/validate-permit-type-is permit/R permit/YA)]}
  [_])

(defn- state-before-last-canceled [{{history :history} :application}]
  (->> (app/state-history-entries history)
       (map (comp keyword :state))
       (remove #{:canceled})
       last))

(defquery application-info-tab-visible
  {:parameters [id]
   :states states/all-application-states
   :user-roles #{:authority :applicant}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles
   :pre-checks [(fn-> state-before-last-canceled
                      states/pre-verdict-states
                      (when-not (fail :error.tabs.no-application-info)))]}
  [_])

(defquery application-summary-tab-visible
  {:parameters [id]
   :states states/all-application-states
   :user-roles #{:authority :applicant}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles
   :pre-checks [(fn-> state-before-last-canceled
                      states/post-verdict-states
                      (when-not (fail :error.tabs.no-application-summary)))]}
  [_])
