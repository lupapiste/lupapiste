(ns lupapalvelu.archiving-api
  (:require [lupapalvelu.states :as states]
            [lupapalvelu.action :refer [defquery defcommand non-blank-parameters] :as action]
            [lupapalvelu.archiving :as archiving]
            [sade.core :refer [ok]]))

(defcommand archive-documents
  {:parameters       [:id attachmentIds archiveApplication]
   :input-validators [(partial non-blank-parameters [:id])]
   :user-roles       #{:authority}
   :states           states/post-verdict-states
   :feature          :arkistointi}
  [{:keys [application user] :as command}]
  (when (contains? (get-in user [:orgAuthz (keyword (:organization application))]) :archivist)
    (archiving/send-to-archive command (set attachmentIds) archiveApplication)
    (ok)))
