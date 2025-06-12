(ns lupapalvelu.statistics.statistics-api
  (:require [lupapalvelu.action :as action :refer [defcommand defquery defraw notify]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.states :as states]
            [lupapalvelu.statistics.db :as stats-db]
            [lupapalvelu.statistics.entry :as stats-e]
            [lupapalvelu.statistics.log :as stats-log]
            [lupapalvelu.statistics.schemas :as stats-sc]
            [sade.core :refer [fail ok]]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [schema.core :as sc]))

(defn- processing-application [{:keys [application]}]
  (when-not (stats-db/read-processing application)
    (fail :error.not-processing)))

(defn- processing-configured [{:keys [application]}]
  (when-not (some-> application stats-db/read-processing :complexity)
    (fail :error.not-processing)))

(defn- entries-application
  [{:keys [application]}]
  (when-not (stats-e/entries-application? application)
    (fail :error.unauthorized)))

;; -------------------------------------
;; Processing
;; -------------------------------------

(defquery processing-dashboard-tab
  {:description "Pseudo-query for checking whether a processing tab is visible on the
  dashboard. The tab is hidden for authorities that do not belong to any R
  organization. Query is authed if the tab should be shown."
   :user-roles  #{:applicant :authority}
   :feature     :statistics-processing
   :pre-checks  [stats-db/processing-user]}
  [_]
  (ok))

(defquery processing-days
  {:description      "Application processing information in days."
   :permissions      [{:required [:processing/read]}]
   :parameters       [:id]
   :input-validators [{:id ssc/ApplicationId}]
   :feature          :statistics-processing
   :states           states/post-submitted-states
   :pre-checks       [processing-application]}
  [command]
  (ok :processing-days (stats-db/processing+extras command)))

(defcommand set-processing-complexity
  {:description      "Updates the processing complexity and returns the updated processing
  days. The complexity determines the length of the allocated processing period."
   :permissions      [{:required [:processing/edit]}]
   :feature          :statistics-processing
   :parameters       [:id complexity]
   :input-validators [{:id         ssc/ApplicationId
                       :complexity stats-sc/StrComplexity}]
   :states           states/post-submitted-states
   :pre-checks       [processing-application]}
  [{:keys [application] :as command}]
  (->> (stats-db/update-processing! application (keyword complexity))
       (hash-map :processing)
       (stats-db/processing+extras command)
       (ok :processing-days)))

(defquery processing-logdata
  {:description      "Processing log and details."
   :permissions      [{:required [:processing/read]}]
   :feature          :statistics-processing
   :parameters       [:id]
   :input-validators [{:id ssc/ApplicationId}]
   :states           states/post-submitted-states
   :pre-checks       [processing-application]}
  [{:keys [application] :as command}]
  (ok :processing-logdata (stats-log/processing-logdata application)
      :unseen? (stats-e/unseen-entries? command)))

(defraw processing-log-pdf
  {:description      "PDF version of the processing log."
   :permissions      [{:required [:processing/read]}]
   :feature          :statistics-processing
   :parameters       [:id :lang]
   :input-validators [{:id   ssc/ApplicationId
                       :lang i18n/EnumSupportedLanguages}]
   :states           states/post-submitted-states
   :pre-checks       [processing-configured]}
  [command]
  (stats-log/processing-log-pdf command))

;; -------------------------------------
;; Entries
;; -------------------------------------

(defn- request-email-model [command _ recipient]
  (let [{:keys [id delay-start created-text]} (:data command)]
    (assoc (notifications/create-app-model command {:tab "entries"} recipient)
           :id id
           :deadline (date/finnish-date delay-start)
           :request (ss/trim created-text))))

(defn- delay-email-model [command _ recipient]
  (let [{:keys [id created-text]} (:data command)]
    (assoc (notifications/create-app-model command {:tab "entries"} recipient)
           :id id
           :delay (ss/trim created-text))))

(notifications/defemail :new-processing-request
  {:subject-key   "new-supplement-request"
   :recipients-fn notifications/application-state-reminder-recipients-fn
   :model-fn      request-email-model})

(notifications/defemail :new-processing-delay
  {:subject-key   "new-processing-delay"
   :recipients-fn notifications/application-state-reminder-recipients-fn
   :model-fn      delay-email-model})

(defn- update-processing+response [command entry-id]
  (let [response (ok (stats-e/frontend-entry+auth-model command
                                                        entry-id))]
    (stats-db/update-processing! (:application command))
    response))

(defcommand update-processing-entry
  {:description      "Updates a processing entry (request or delay)."
   :permissions      [{:required [:processing/edit]}]
   :feature          :statistics-processing
   :parameters       [:id :entry-id]
   :input-validators [{:id        ssc/ApplicationId
                       :entry-id  ssc/ObjectIdStr
                       sc/Keyword sc/Any}]
   :pre-checks       [(stats-e/check-entry)]}
  [command]
  (some->> (stats-e/update-entry command)
           (update-processing+response command)))

(defcommand new-processing-request
  {:description      "Creates new processing request entry. Notification email is sent. Note
  that the requests are available even for non-processing applications."
   :permissions      [{:required [:processing/edit]}]
   :feature          :statistics-processing
   :parameters       [:id :delay-start :created-text]
   :input-validators [stats-sc/NewRequestParams]
   :states           states/all-but-draft-or-terminal
   :pre-checks       [entries-application]
   :notified         true
   :on-success       [(notify :new-processing-request)]}
  [command]
  (some->> (stats-e/insert-new-entry command :request)
           (update-processing+response command)))

(defcommand new-processing-delay
  {:description      "Creates new processing delay
  entry. Notification email is sent. Delays are available only for processing
  applications."
   :permissions      [{:required [:processing/edit]}]
   :feature          :statistics-processing
   :parameters       [:id :delay-start :created-text]
   :input-validators [stats-sc/NewDelayParams]
   :states           states/all-but-draft-or-terminal
   :pre-checks       [processing-application]
   :notified         true
   :on-success       [(notify :new-processing-delay)]}
  [command]
  (some->> (stats-e/insert-new-entry command :delay)
           (update-processing+response command)))

(defcommand answer-processing-entry
  {:description      "Marks entry answered with message. The answer can be edited until the
  entry has been closed."
   :permissions      [{:required [:processing/answer]}]
   :feature          :statistics-processing
   :categories       #{:processing-entries}
   :parameters       [:id :entry-id :answered-text]
   :input-validators [{:id            ssc/ApplicationId
                       :entry-id      ssc/ObjectIdStr
                       :answered-text ssc/NonBlankStr}]
   :pre-checks       [(stats-e/check-entry :deleted? false
                                           :closed? false)]}
  [{:keys [created] :as command}]
  (some->> (stats-e/update-entry command
                                 {:answered?   true
                                  :answered-ts created})
           (update-processing+response command)))

(defcommand close-processing-entry
  {:description         "Closes processing entry."
   :permissions         [{:required [:processing/edit]}]
   :feature             :statistics-processing
   :categories          #{:processing-entries}
   :parameters          [:id :entry-id :closed-ts]
   :optional-parameters [:closed-text]
   :input-validators    [{:id                            ssc/ApplicationId
                          :entry-id                      ssc/ObjectIdStr
                          :closed-ts                     ssc/Timestamp
                          (sc/optional-key :closed-text) ssc/NonBlankStr}]
   :pre-checks          [(stats-e/check-entry :closed? false)]}
  [command]
  (some->> (stats-e/update-entry command)
           (update-processing+response command)))

(defcommand mark-entries-seen
  {:description      "Marks the given entries seen by the user. This is called automatically
  for non-authorities."
   :permissions      [{:required [:processing/mark-seen]}]
   :feature          :statistics-processing
   :parameters       [:id entry-ids]
   :input-validators [{:id        ssc/ApplicationId
                       :entry-ids [ssc/ObjectIdStr]}]
   :pre-checks       [entries-application]}
  [command]
  (stats-e/toggle-seen command true entry-ids)
  (ok))

(defcommand toggle-entry-seen
  {:description      "Marks the given entry seen/unseen by the user. An authority can/must mark
  the entries seen manually."
   :permissions      [{:required [:processing/toggle-seen]}]
   :feature          :statistics-processing
   :categories       #{:processing-entries}
   :parameters       [:id entry-id seen]
   :input-validators [{:id       ssc/ApplicationId
                       :entry-id ssc/ObjectIdStr
                       :seen     sc/Bool}]
   :pre-checks       [(stats-e/check-entry :deleted? false)]}
  [command]
  (stats-e/toggle-seen command seen entry-id)
  (ok))

(defquery processing-entries
  {:description      "Entries for the application. The actual set depends on the user."
   :permissions      [{:required [:processing/read]}]
   :feature          :statistics-processing
   :parameters       [:id]
   :input-validators [{:id ssc/ApplicationId}]
   :pre-checks       [entries-application]}
  [command]
  (ok (stats-e/frontend-entries+auth-models command)))

(defquery processing-entries-tab
  {:description      "Pseudo-query for checking whether the application entries section should
  be visible. For applicants, the section is visible only if there are non-deleted
  entries. For authorities the section is visible also, when it is possible to create
  requests or delays."
   :permissions      [{:required [:processing/read]}]
   :feature          :statistics-processing
   :parameters       [:id]
   :input-validators [{:id ssc/ApplicationId}]
   :pre-checks       [stats-e/check-entries-tab]}
  [_]
  (ok))

(defquery processing-requests-status
  {:description      "Open and unseen requests information."
   :permissions      [{:required [:processing/read]}]
   :parameters       [:id]
   :input-validators [{:id ssc/ApplicationId}]
   :feature          :statistics-processing
   :pre-checks       [entries-application]}
  [{:keys [application] :as command}]
  (ok :requests (stats-e/application-open-entry-count application)
      :unseen? (stats-e/unseen-entries? command)))
