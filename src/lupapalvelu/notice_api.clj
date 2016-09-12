(ns lupapalvelu.notice-api
  (:require [sade.core :refer [ok fail fail!]]
            [lupapalvelu.action :refer [defquery defcommand update-application notify] :as action]
            [lupapalvelu.organization :as org]
            [lupapalvelu.application :as app]
            [lupapalvelu.states :as states]
            [monger.operators :refer :all]))

(defn validate-urgency [{{urgency :urgency} :data}]
  (when-not (#{"normal" "urgent" "pending"} urgency)
    (fail :error.unknown-urgency-state)))

(defn update-notice-data
  "Updates application notice data and bookkeeping. Data is map."
  [{:keys [user created] :as command} data]
  (update-application command {$set (merge data
                                           {:authorityNoticeEdited created}
                                           (app/mark-collection-seen-update user
                                                                            created
                                                                            "authority-notices"))}))

(defcommand change-urgency
  {:parameters [id urgency]
   :user-roles #{:authority}
   :states (states/all-states-but [:draft])
   :user-authz-roles #{:statementGiver}
   :input-validators [validate-urgency]}
  [command]
  (update-notice-data command {:urgency urgency}))

(defcommand add-authority-notice
  {:parameters [id authorityNotice]
   :input-validators [(partial action/string-parameters [:authorityNotice])]
   :states (states/all-states-but [:draft])
   :user-authz-roles #{:statementGiver}
   :user-roles #{:authority}}
  [command]
  (update-notice-data command {:authorityNotice authorityNotice}))

(defcommand add-application-tags
  {:parameters [id tags]
   :states (states/all-states-but [:draft])
   :input-validators [(partial action/vector-parameters-with-non-blank-items [:tags])]
   :user-authz-roles #{:statementGiver}
   :user-roles #{:authority}}
  [{organization :organization :as command}]
  (let [org-tag-ids (map :id (:tags @organization))]
    (if (every? (set org-tag-ids) tags)
      (update-notice-data command {:tags tags})
      (fail :error.unknown-tags))))

(defquery authority-notice
  {:user-roles #{:authority}
   :user-authz-roles #{:statementGiver}}
  (ok))
