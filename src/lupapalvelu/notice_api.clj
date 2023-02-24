(ns lupapalvelu.notice-api
  (:require [lupapalvelu.action :refer [defquery defcommand update-application notify] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.organization :as org]
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
   :states (states/all-states-but [:draft])
   :contexts    [org/organization-statement-giver-context]
   :permissions [{:required [:application/set-urgency]}]
   :input-validators [validate-urgency]}
  [command]
  (update-notice-data command {:urgency urgency}))

(defcommand add-authority-notice
  {:parameters [id authorityNotice]
   :input-validators [(partial action/string-parameters [:authorityNotice])]
   :states (states/all-states-but [:draft])
   :contexts    [org/organization-statement-giver-context]
   :permissions [{:required [:application/set-authority-notice]}]}
  [command]
  (update-notice-data command {:authorityNotice authorityNotice}))

(defcommand add-application-tags
  {:parameters [id tags]
   :states (states/all-states-but [:draft])
   :input-validators [(partial action/vector-parameters-with-non-blank-items [:tags])]
   :contexts    [org/organization-statement-giver-context]
   :permissions [{:required [:application/set-authority-notice]}]}
  [{organization :organization :as command}]
  (let [org-tag-ids (map :id (:tags @organization))]
    (if (every? (set org-tag-ids) tags)
      (update-notice-data command {:tags tags})
      (fail :error.unknown-tags))))

(defquery authority-notice
  {:description      "Notice is shown to authorities and organization statement givers."
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :contexts    [org/organization-statement-giver-context]
   :permissions [{:required [:application/read-authority-notice]}]
   :states           states/all-but-draft}
  [_])
