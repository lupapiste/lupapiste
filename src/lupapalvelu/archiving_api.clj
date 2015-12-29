(ns lupapalvelu.archiving-api
  (:require [lupapalvelu.states :as states]
            [lupapalvelu.action :refer [defquery defcommand non-blank-parameters] :as action]
            [lupapalvelu.archiving :as archiving]))



(defcommand archive-documents
  {:parameters       [:id attachmentIds archiveAppliction]
   :input-validators [(partial non-blank-parameters [:id])]
   :user-roles       #{:authority}
   :states           states/post-verdict-states
   :feature          :arkistointi}
  [{:keys [application user] :as command}]
  (if (contains? (get-in user [:orgAuthz (keyword (:organization application))]) :archivist)
    (archiving/send-to-archive application attachmentIds user archiveApplication)))
