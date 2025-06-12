(ns lupapalvelu.archive.archiving-api
  (:require [lupapalvelu.action :refer [defcommand defquery non-blank-parameters]]
            [lupapalvelu.application :as app]
            [lupapalvelu.archive.archiving :as archiving]
            [lupapalvelu.archive.archiving-util :as archiving-util]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.core :refer [fail ok unauthorized]]
            [sade.util :as util]))

(defn validate-permanent-archive-enabled [{user-orgs :user-organizations app-org :organization app :application}]
  (when-not (if (:organization app)
              (:permanent-archive-enabled @app-org)
              (some :permanent-archive-enabled user-orgs))
    unauthorized))

(defn- application-ts
  "Timestamp for checking against the archiving period. Converted and digitized applications
  always use `created` timestamp, since (especially for converted) the `submitted`
  timestamp can be almost anything."
  [{:keys [created submitted facta-imported permitType]}]
  (if (or facta-imported (util/=as-kw permitType :ARK))
    created
    (or submitted created)))

(defn application-within-time-limit
  [{app-org :organization app :application}]
  (when (or (nil? app)
            (not (:permanent-archive-enabled @app-org))
            (some->> (:permanent-archive-in-use-since @app-org)
                     (< (application-ts app))))
    (fail :error.application-too-old-for-archival)))

(defcommand archive-documents
  {:parameters       [:id attachmentIds documentIds linkedFileIds]
   :input-validators [(partial non-blank-parameters [:id])]
   :permissions      [{:required [:application/archive]}]
   :states           (conj states/post-verdict-states :underReview)
   :pre-checks       [application-within-time-limit]}
  [{:keys [organization] :as command}]
  (let [result (-> command
                   (update :application app/enrich-application-handlers @organization)
                   (update :application app/enrich-buildings)
                   (archiving/send-to-archive (set attachmentIds) (set documentIds) (set linkedFileIds)))]
    (if (map? result)
      (fail (:error result))
      (ok))))

(defquery document-states
  {:parameters       [:id]
   :input-validators [(partial non-blank-parameters [:id])]
   :permissions      [{:required [:application/read]}]
   :states           states/all-application-or-archiving-project-states}
  [{:keys [application]}]
  (let [app-doc-id       (str (:id application) "-application")
        foreman-list-id  (str (:id application) "-foreman-list")
        case-file-doc-id (str (:id application) "-case-file")
        attachment-map   (->> (:attachments application)
                              (map (fn [{:keys [id metadata]}] [id (:tila metadata)]))
                              (into {}))
        linked-files     (linked-files/get-linked-application-files-for {:application-id (:id application)})
        linked-file-map  (->> linked-files
                              (map (fn [{:keys [target-entity metadata]}] [(:file-id target-entity) (:tila metadata)]))
                              (into {}))
        state-map        (assoc attachment-map
                                app-doc-id (get-in application [:metadata :tila])
                                foreman-list-id (get-in application [:foreman-list-metadata :tila])
                                case-file-doc-id (get-in application [:processMetadata :tila]))]
    (ok :state state-map :linked-file-state linked-file-map)))

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
  (archiving-util/mark-application-archived application created :application)
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
  (archiving-util/mark-application-archived application created :completed)
  (ok))

(defquery archiving-operations-enabled
  {:contexts        [usr/without-application-context]
   :permissions     [{:required [:organization/get-archiving-status]}]
   :states          (conj states/post-verdict-states :underReview)}
  (ok))

(defquery permanent-archive-enabled
  {:optional-parameters [:organizationId]
   :permissions [{:required [:organization/check-permanent-archive-enabled]}]
   :categories #{:attachments}
   :pre-checks [validate-permanent-archive-enabled]}
  [_]
  (ok))

(defquery application-in-final-archiving-state
  {:permissions [{:required [:application/check-final-archiving-state]}]
   :states     states/archival-final-states}
  (ok))

(defquery application-date-within-time-limit
  {:permissions [{:required [:application/check-within-archiving-time-limit]}]
   :pre-checks [application-within-time-limit]}
  [_]
  (ok))

(defn- archival-access-check [permission]
  (fn [command]
    (when-not (archiving-util/archival-access? command permission)
      unauthorized)))

(defquery document-search-access-allowed
  {:description "Pseudo-query that is authed if the user has `:organization/document-search-access`
  permission AND the corresponding organization has the permanent archive enabled."
   :user-roles  #{:authority}
   :pre-checks  [(archival-access-check :organization/document-search-access)]}
  [_]
  (ok))

(defquery toj-access-allowed
  {:description "Pseudo-query that is authed if the user has `:organization/toj-access`
  permission AND the corresponding organization has the permanent archive enabled."
   :user-roles  #{:authority}
   :pre-checks  [(archival-access-check :organization/toj-access)]}
  [_]
  (ok))
