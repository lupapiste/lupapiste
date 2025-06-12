(ns lupapalvelu.application-tabs-api
  "Pseudo queries to handle application's tabs' visibility in UI"
  (:require [lupapalvelu.action :refer [defquery]]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.ya-extension :as yax]
            [sade.core :refer :all]
            [sade.util :refer [fn->]]))

(defquery tasks-tab-visible
  {:parameters       [id]
   :states           states/post-verdict-states
   :org-authz-roles  roles/reader-org-authz-roles
   :user-authz-roles roles/all-authz-roles
   :user-roles       #{:authority :applicant}
   :pre-checks       [(fn [{:keys [application]}]
                        (cond
                          (foreman-util/foreman-app? application)
                          (fail :error.foreman.no-tasks)
                          (yax/ya-extension-app? application)
                          (fail :error.ya-extension.no-tasks)))
                      (permit/validate-permit-type-is permit/R permit/YA)
                      (app/reject-primary-operations #{:raktyo-aloit-loppuunsaat :jatkoaika})]}
  [_]
  (ok))

(defn- state-before-last-canceled [{{history :history} :application}]
  (->> (app-state/state-history-entries history)
       (map (comp keyword :state))
       (remove #{:canceled})
       last))

(defquery application-info-tab-visible
  {:parameters [id]
   :states states/all-application-or-archiving-project-states
   :user-roles #{:authority :applicant}
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles roles/reader-org-authz-roles
   :pre-checks [(fn-> state-before-last-canceled
                      states/pre-verdict-states
                      (when-not (fail :error.tabs.no-application-info)))]}
  [_]
  (ok))

(defquery application-summary-tab-visible
  {:parameters [id]
   :states states/all-application-or-archiving-project-states
   :user-roles #{:authority :applicant}
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles roles/reader-org-authz-roles
   :pre-checks [(fn-> state-before-last-canceled
                      states/post-verdict-states
                      (when-not (fail :error.tabs.no-application-summary)))]}
  [_]
  (ok))
