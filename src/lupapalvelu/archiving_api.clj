(ns lupapalvelu.archiving-api
  (:require [cheshire.core :as json]
            [sade.core :refer [ok unauthorized fail]]
            [lupapalvelu.action :refer [defquery defcommand non-blank-parameters vector-parameters]]
            [lupapalvelu.archiving :as archiving]
            [lupapalvelu.application :as app]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [lupapalvelu.permit :as permit]))

(defn validate-permanent-archive-enabled [{user-orgs :user-organizations app-org :organization app :application}]
  (when-not (if (:organization app)
              (:permanent-archive-enabled @app-org)
              (some :permanent-archive-enabled user-orgs))
    unauthorized))

(defn application-within-time-limit
  [{app-org :organization {:keys [submitted created] :as app} :application}]
  (if-not (:permanent-archive-enabled @app-org)
    (fail :error.archive-not-enabled)
    (when (or (nil? app)
              (let [archive-in-use-since (:permanent-archive-in-use-since @app-org)]
                (if submitted
                  (< submitted archive-in-use-since)
                  (< created archive-in-use-since))))
      (fail :error.application-too-old-for-archival))))

(defcommand archive-documents
  {:parameters       [:id attachmentIds documentIds]
   :input-validators [(partial non-blank-parameters [:id])]
   :user-roles       #{:authority}
   :org-authz-roles  #{:archivist}
   :states           (conj states/post-verdict-states :underReview)
   :pre-checks       [application-within-time-limit]}
  [{:keys [application user organization] :as command}]
  (if-let [{:keys [error]} (-> (update command :application app/enrich-application-handlers @organization)
                               (archiving/send-to-archive (set attachmentIds) (set documentIds)))]
    (fail error)
    (ok)))

(defquery document-states
  {:parameters       [:id]
   :input-validators [(partial non-blank-parameters [:id])]
   :user-roles       #{:authority}
   :states           states/all-application-or-archiving-project-states}
  [{:keys [application]}]
  (let [app-doc-id (str (:id application) "-application")
        case-file-doc-id (str (:id application) "-case-file")
        attachment-map (->> (:attachments application)
                            (map (fn [{:keys [id metadata]}] [id (:tila metadata)]))
                            (into {}))
        state-map (assoc attachment-map
                         app-doc-id (get-in application [:metadata :tila])
                         case-file-doc-id (get-in application [:processMetadata :tila]))]
    (ok :state state-map)))

(defcommand mark-pre-verdict-phase-archived
  {:parameters       [:id]
   :input-validators [(partial non-blank-parameters [:id])]
   :user-roles       #{:authority}
   :org-authz-roles  #{:archivist}
   :states           states/post-verdict-states
   :pre-checks       [permit/is-not-archiving-project]}
  [{:keys [application created]}]
  (archiving/mark-application-archived application created :application)
  (ok))

(defquery archiving-operations-enabled
  {:user-roles      #{:authority}
   :org-authz-roles (with-meta #{:archivist} {:skip-validation true})
   :states          (conj states/post-verdict-states :underReview)
   :pre-checks      [(fn [{:keys [user application]}]
                       (when-not (or application (usr/user-is-archivist? user nil)) ; If application, :org-authz-roles works as validator
                         unauthorized))
                     validate-permanent-archive-enabled]}
  (ok))

(defquery permanent-archive-enabled
  {:user-roles #{:applicant :authority :authorityAdmin}
   :categories #{:attachments}
   :pre-checks [validate-permanent-archive-enabled]}
  [_])

(defquery application-in-final-archiving-state
  {:user-roles #{:authority}
   :states     states/archival-final-states}
  (ok))

(defquery application-date-within-time-limit
  {:user-roles #{:authority}
   :pre-checks [application-within-time-limit]}
  [_])
