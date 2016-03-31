(ns lupapalvelu.archiving-api
  (:require [lupapalvelu.states :as states]
            [lupapalvelu.action :refer [defquery defcommand non-blank-parameters vector-parameters]]
            [lupapalvelu.archiving :as archiving]
            [sade.core :refer [ok unauthorized]]
            [lupapalvelu.user :as user]
            [clojure.set :as set]
            [lupapalvelu.organization :as organization]))

(defcommand archive-documents
  {:parameters       [:id attachmentIds documentIds]
   :input-validators [(partial non-blank-parameters [:id])]
   :user-roles       #{:authority}
   :states           states/post-verdict-states
   :feature          :arkistointi
   :pre-checks       [(fn [{:keys [user]} {:keys [organization]}]
                        (let [org-set (set/intersection #{organization} (user/organization-ids-by-roles user #{:archivist}))]
                          (when (or (empty? org-set) (not (organization/some-organization-has-archive-enabled? org-set)))
                            unauthorized)))]}
  [{:keys [application user] :as command}]
  (when (contains? (get-in user [:orgAuthz (keyword (:organization application))]) :archivist)
    (archiving/send-to-archive command (set attachmentIds) (set documentIds))
    (ok)))

(defcommand document-states
  {:parameters       [:id documentIds]
   :input-validators [(partial non-blank-parameters [:id])
                      (partial vector-parameters [:documentIds])]
   :user-roles       #{:authority}
   :states           (states/all-application-states-but :draft)
   :feature          :arkistointi}
  [{:keys [application] :as command}]
  (let [id-set (set documentIds)
        app-doc-id (str (:id application) "-application")
        case-file-doc-id (str (:id application) "-case-file")
        attachment-map (->> (:attachments application)
                            (filter #(id-set (:id %)))
                            (map (fn [{:keys [id metadata]}] [id (:tila metadata)]))
                            (into {}))
        state-map (cond-> attachment-map
                          (id-set app-doc-id) (assoc app-doc-id (get-in application [:metadata :tila]))
                          (id-set case-file-doc-id) (assoc case-file-doc-id (get-in application [:processMetadata :tila])))]
    (ok :state state-map)))
