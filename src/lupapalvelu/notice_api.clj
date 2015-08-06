(ns lupapalvelu.notice-api
  (:require [sade.core :refer [ok fail fail!]]
            [lupapalvelu.action :refer [defquery defcommand update-application notify] :as action]
            [lupapalvelu.states :as states]
            [monger.operators :refer :all]))

(defn validate-urgency [{{urgency :urgency} :data}]
  (when-not (#{"normal" "urgent" "pending"} urgency)
    (fail :error.unknown-urgency-state)))

(defcommand change-urgency
  {:parameters [id urgency]
   :user-roles #{:authority}
   :states (states/all-states-but [:draft])
   :user-authz-roles #{:statementGiver}
   :input-validators [validate-urgency]}
  [command]
  (update-application command {$set {:urgency urgency}}))

(defcommand add-authority-notice
  {:parameters [id authorityNotice]
   :states (states/all-states-but [:draft])
   :user-authz-roles #{:statementGiver}
   :user-roles #{:authority}
   :org-authz-roles action/reader-org-authz-roles}
  [command]
  (update-application command {$set {:authorityNotice authorityNotice}}))

(defcommand add-application-tags
  {:parameters [id tags]
   :states (states/all-states-but [:draft])
   :user-authz-roles #{:statementGiver}
   :user-roles #{:authority}}
  [command]
  (update-application command {$set {:tags tags}}))
