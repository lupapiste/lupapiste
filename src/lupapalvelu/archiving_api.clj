(ns lupapalvelu.archiving-api
  (:require [cheshire.core :as json]
            [sade.core :refer [ok unauthorized fail]]
            [lupapalvelu.action :refer [defquery defcommand non-blank-parameters vector-parameters]]
            [lupapalvelu.archiving :as archiving]
            [lupapalvelu.application :as app]
            [lupapalvelu.states :as states]))

(defn validate-permanent-archive-enabled [{user-orgs :user-organizations app-org :organization app :application}]
  (when-not (if (:organization app)
              (:permanent-archive-enabled @app-org)
              (some :permanent-archive-enabled user-orgs))
    unauthorized))

(defcommand archive-documents
  {:parameters       [:id attachmentIds documentIds]
   :input-validators [(partial non-blank-parameters [:id])]
   :user-roles       #{:authority}
   :org-authz-roles  #{:archivist}
   :states           states/post-verdict-states}
  [{:keys [application user organization] :as command}]
  (if-let [{:keys [error]} (-> (update command :application app/enrich-application-handlers @organization)
                               (archiving/send-to-archive (set attachmentIds) (set documentIds)))]
    (fail error)
    (ok)))

(defquery document-states
  {:parameters       [:id documentIds]
   :input-validators [(partial non-blank-parameters [:id :documentIds])]
   :user-roles       #{:authority}
   :states           (states/all-application-states-but :draft)}
  [{:keys [application] :as command}]
  (let [id-set (set (json/parse-string documentIds))
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

(defcommand mark-pre-verdict-phase-archived
  {:parameters       [:id]
   :input-validators [(partial non-blank-parameters [:id])]
   :user-roles       #{:authority}
   :org-authz-roles  #{:archivist}
   :states           states/post-verdict-states}
  [{:keys [application created]}]
  (archiving/mark-application-archived application created :application)
  (ok))

(defquery archiving-operations-enabled
  {:user-roles #{:authority}
   :org-authz-roles  #{:archivist}
   :states     states/all-application-states}
  (ok))

(defquery permanent-archive-enabled
  {:user-roles #{:applicant :authority}
   :categories #{:attachments}
   :pre-checks [validate-permanent-archive-enabled]}
  [_])
