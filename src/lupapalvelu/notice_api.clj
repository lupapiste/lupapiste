(ns lupapalvelu.notice-api
  (:require [lupapalvelu.action :refer [defquery defcommand update-application notify] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.organization :as org]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [monger.operators :refer :all]
            [sade.core :refer [ok fail fail!]]))

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
   :user-roles #{:authority :applicant}
   :states (states/all-states-but [:draft])
   :user-authz-roles #{:statementGiver}
   :input-validators [validate-urgency]
   :pre-checks [(action/some-pre-check
                  auth/application-authority-pre-check
                  org/statement-giver-in-organization)]}
  [command]
  (update-notice-data command {:urgency urgency}))

(defcommand add-authority-notice
  {:parameters [id authorityNotice]
   :input-validators [(partial action/string-parameters [:authorityNotice])]
   :states (states/all-states-but [:draft])
   :user-authz-roles #{:statementGiver}
   :user-roles #{:authority :applicant}
   :pre-checks [(action/some-pre-check
                  auth/application-authority-pre-check
                  org/statement-giver-in-organization)]}
  [command]
  (update-notice-data command {:authorityNotice authorityNotice}))

(defcommand add-application-tags
  {:parameters [id tags]
   :states (states/all-states-but [:draft])
   :input-validators [(partial action/vector-parameters-with-non-blank-items [:tags])]
   :user-authz-roles #{:statementGiver}
   :user-roles #{:authority :applicant}
   :pre-checks [(action/some-pre-check
                  auth/application-authority-pre-check
                  org/statement-giver-in-organization)]}
  [{organization :organization :as command}]
  (let [org-tag-ids (map :id (:tags @organization))]
    (if (every? (set org-tag-ids) tags)
      (update-notice-data command {:tags tags})
      (fail :error.unknown-tags))))

(defquery authority-notice
  {:description      "Notice is shown to authorities and organization statement givers."
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles       #{:authority :applicant}
   :user-authz-roles #{:statementGiver}
   :org-authz-roles  roles/reader-org-authz-roles
   :states           states/all-but-draft
   :pre-checks       [(action/some-pre-check
                        auth/application-authority-pre-check
                        org/statement-giver-in-organization)]}
  [_])
