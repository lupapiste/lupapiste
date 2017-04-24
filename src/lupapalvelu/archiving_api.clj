(ns lupapalvelu.archiving-api
  (:require [clojure.set :as set]
            [cheshire.core :as json]
            [sade.core :refer [ok unauthorized fail]]
            [lupapalvelu.action :refer [defquery defcommand non-blank-parameters vector-parameters]]
            [lupapalvelu.archiving :as archiving]
            [lupapalvelu.application :as app]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]))

(defn check-user-is-archivist [{user :user {:keys [organization]} :application}]
  (when-not (usr/user-is-archivist? user organization)
    unauthorized))

(defn validate-permanent-archive-enabled [{user-orgs :user-organization app-org :organization {app-org-id :organization} :application}]
  (when-not (if app-org-id
              (:permanent-archive-enabled @app-org)
              (some :permanent-archive-enabled user-orgs))
    unauthorized))

(defcommand archive-documents
  {:parameters       [:id attachmentIds documentIds]
   :input-validators [(partial non-blank-parameters [:id])]
   :user-roles       #{:authority}
   :states           states/post-verdict-states
   :pre-checks       [check-user-is-archivist]}
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
   :states           states/post-verdict-states
   :pre-checks       [check-user-is-archivist]}
  [{:keys [application created]}]
  (archiving/mark-application-archived application created :application)
  (ok))

(defquery archiving-operations-enabled
  {:user-roles #{:authority}
   :states     states/all-application-states
   :pre-checks [check-user-is-archivist]}
  (ok))

(defquery permanent-archive-enabled
  {:user-roles #{:applicant :authority}
   :categories #{:attachments}
   :pre-checks [validate-permanent-archive-enabled]}
  [_])
