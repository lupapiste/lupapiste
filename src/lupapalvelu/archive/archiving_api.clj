(ns lupapalvelu.archive.archiving-api
  (:require [sade.core :refer [ok unauthorized fail]]
            [lupapalvelu.action :refer [defquery defcommand non-blank-parameters vector-parameters]]
            [lupapalvelu.archive.archiving :as archiving]
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
  (when (or (nil? app)
            (not (:permanent-archive-enabled @app-org))
            (let [archive-in-use-since (:permanent-archive-in-use-since @app-org)]
              (if submitted
                (< submitted archive-in-use-since)
                (< created archive-in-use-since))))
    (fail :error.application-too-old-for-archival)))

(defcommand archive-documents
  {:parameters       [:id attachmentIds documentIds]
   :input-validators [(partial non-blank-parameters [:id])]
   :permissions      [{:required [:application/archive]}]
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
   :permissions      [{:required [:application/read]}]
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

(defn- not-archived-precheck [key]
  (fn [{:keys [application]}]
    (when (get-in application [:archived key])
      (fail :error.command-illegal-state))))

(defcommand mark-pre-verdict-phase-archived
  {:parameters       [:id]
   :input-validators [(partial non-blank-parameters [:id])]
   :permissions      [{:required [:application/archive]}]
   :states           states/post-verdict-states
   :pre-checks       [permit/is-not-archiving-project
                      (not-archived-precheck :application)]}
  [{:keys [application created]}]
  (archiving/mark-application-archived application created :application)
  (ok))

(defcommand mark-fully-archived
  {:parameters       [:id]
   :input-validators [(partial non-blank-parameters [:id])]
   :permissions      [{:required [:application/archive]}]
   :states           states/archival-final-states
   :pre-checks       [permit/is-not-archiving-project
                      (fn [{:keys [application]}]
                        (when-not (get-in application [:archived :application])
                          (fail :error.command-illegal-state)))
                      (not-archived-precheck :completed)]}
  [{:keys [application created]}]
  (archiving/mark-application-archived application created :completed)
  (ok))

(defquery archiving-operations-enabled
  {:contexts        [usr/without-application-context]
   :permissions     [{:required [:organization/get-archiving-status]}]
   :states          (conj states/post-verdict-states :underReview)}
  (ok))

(defquery permanent-archive-enabled
  {:permissions [{:required [:organization/check-permanent-archive-enabled]}]
   :categories #{:attachments}
   :pre-checks [validate-permanent-archive-enabled]}
  [_])

(defquery application-in-final-archiving-state
  {:permissions [{:required [:application/check-final-archiving-state]}]
   :states     states/archival-final-states}
  (ok))

(defquery application-date-within-time-limit
  {:permissions [{:required [:application/check-within-archiving-time-limit]}]
   :pre-checks [application-within-time-limit]}
  [_])
