(ns lupapalvelu.batchrun
  (:require [cheshire.core :as json]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clojure.edn :as edn]
            [clojure.set :refer [difference]]
            [clojure.string :as str]
            [lupapalvelu.action :refer :all]
            [lupapalvelu.allu :as allu-semiconstants]
            [lupapalvelu.api-common :refer [execute-command]]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.archive.api-usage]
            [lupapalvelu.archive.archiving :as archiving]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.automatic-assignment.factory :as factory]
            [lupapalvelu.backing-system.allu.contract :as allu-contract]
            [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as as-krysp]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.backing-system.krysp.http]
            [lupapalvelu.batchrun.fetch-verdict :as fetch-verdict]
            [lupapalvelu.batchrun.reminders :as reminders]
            [lupapalvelu.batchrun.repair :as repair]
            [lupapalvelu.batchrun.vantaa-statements :as vantaa]
            [lupapalvelu.child-to-attachment :as child-to-attachment]
            [lupapalvelu.conversion.core :as conv]
            [lupapalvelu.conversion.link :as conv-link]
            [lupapalvelu.document.allu-schemas :as da]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.exports.attachments-export :as attachments-export]
            [lupapalvelu.integrations.jms-consumers]
            [lupapalvelu.integrations.state-change :as state-change]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.verdict :as pv]
            [lupapalvelu.review :as review]
            [lupapalvelu.sftp.core :as sftp]
            [lupapalvelu.statement :as statement]
            [lupapalvelu.states :as states]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.storage.move-util :as storage-move-util]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.tiedonohjaus :as tos]
            [lupapalvelu.user :as user]
            [lupapalvelu.verdict :as verdict]
            [lupapiste-commons.threads :as threads]
            [monger.operators :refer :all]
            [monger.query :as monger-query]
            [mount.core :as mount]
            [ring.util.codec :as codec]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.dummy-email-server]
            [sade.env :as env]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :refer [fn-> pcond->] :as util]
            [sade.validators :as v]
            [slingshot.slingshot :refer [get-throw-context try+]]
            [taoensso.timbre :refer [debug error errorf info infof warn]])
  (:import [org.xml.sax SAXParseException]))


(defn mount-for-batchrun [action]
  (case action
    :start (mount/start-without #'allu/allu-pubsub-consumer
                                #'allu/allu-jms-consumer
                                #'allu/allu-jms-session
                                #'lupapalvelu.backing-system.krysp.http/kuntagml-jms-session
                                #'lupapalvelu.backing-system.krysp.http/kuntagml-consumer
                                #'lupapalvelu.backing-system.krysp.http/kuntagml-pubsub-consumer
                                #'state-change/state-change-pubsub-consumer
                                #'state-change/state-change-jms-session
                                #'state-change/state-change-consumer
                                #'lupapalvelu.archive.api-usage/api-usage-pubsub-subscriber
                                #'lupapalvelu.integrations.jms-consumers/fetch-verdict-consumers)
    :stop (mount/stop)))


(defn system-not-in-lockdown? []
  (-> (http/get "http://127.0.0.1:8000/system/status")
      :body (json/decode true)
      :data :not-in-lockdown :data))


(defn send-reminder-emails [& _]
  (when (env/feature? :reminders)
    (mount-for-batchrun :start)
    (reminders/statement-request-reminder)
    (reminders/statement-reminder-due-date)
    (reminders/open-inforequest-reminder)
    (reminders/neighbor-reminder)
    (reminders/application-state-reminder)
    (reminders/ya-work-time-is-expiring-reminder)
    (reminders/no-final-review-reminder)
    (reminders/construction-not-started-reminder)

    (mount-for-batchrun :stop)))

(defn fetch-verdict
  [batchrun-name batchrun-user {:keys [id organization] :as app} & [{:keys [use-queue? db-name]}]]
  (if use-queue?
    (fetch-verdict/publish-to-queue organization id db-name)
    (fetch-verdict/fetch-verdict batchrun-name batchrun-user app)))

(defn- fetch-all-application-kinds
  "Fetch all application kinds that we need from ALLU."
  []
  {:pre [(mongo/connected?) (allu/logged-in?)]}
  (info "Starting ALLU application kind fetch for 091-YA")
  (doseq [type (keys da/application-types)]
    (try
      (allu-semiconstants/fetch-application-kinds type)
      (info "Fetched" (name type) "application kinds")
      (catch Throwable exn
        (logging/log-event :error {:event                 (str "Failed to fetch " (name type) " application kinds")
                                   :run-by                "Fetch ALLU application kinds"
                                   :organization-id       "091-YA"
                                   :allu-application-type type
                                   :exception             (.getName (class exn))
                                   :message               (.getMessage exn)}))))
  (info "All ALLU application kinds fetched"))

(defn- fetch-all-fixed-locations
  "Fetch all fixed locations that we need from ALLU."
  []
  {:pre [(mongo/connected?) (allu/logged-in?)]}
  (info "Starting ALLU fixed location fetch for 091-YA")
  (doseq [kind (keys da/application-kinds)]
    (try
      (allu-semiconstants/fetch-fixed-locations kind)
      (info "Fetched" (name kind) "fixed locations")
      (catch Throwable exn
        (logging/log-event :error {:event                    (str "Failed to fetch " (name kind) " fixed locations")
                                   :run-by                   "Fetch ALLU fixed locations"
                                   :organization-id          "091-YA"
                                   :allu-fixed-location-kind kind
                                   :exception                (.getName (class exn))
                                   :message                  (.getMessage exn)}))))
  (info "All ALLU fixed locations fetched"))

(defn fetch-allu-semiconstants
  "Fetch all semi-constant data that we need from ALLU, e.g. application kinds and fixed locations."
  [& _]
  (mount-for-batchrun :start)
  (allu/login!)

  (fetch-all-application-kinds)
  (fetch-all-fixed-locations)
  (mount-for-batchrun :stop))

(defn fetch-allu-contracts
  "Fetch verdicts (contracts/decisions) from ALLU."
  []
  (infof "Starting fetch-allu-contracts for 091-YA")
  (mount-for-batchrun :start)
  (allu/login!)

  (let [batchrun-user (user/batchrun-user ["091-YA"])
        apps (domain/get-multiple-applications-no-access-checking
               {:state                   {$in ["sent" "agreementPrepared"]}
                :organization            "091-YA" ; HACK
                :integrationKeys.ALLU.id {$exists true}})

        min-datetime (c/from-long (transduce (map :created) min (now) apps))
        histories-by-allu-id (->> (allu/application-histories {:user batchrun-user
                                                               :created (now)
                                                               :action "fetch-allu-contracts"}
                                                              apps min-datetime)
                                  (group-by :applicationId)
                                  (util/map-values first))
        fetchable? (fn [{{{allu-id :id} :ALLU} :integrationKeys}]
                     (allu/application-history-fetchable? (get histories-by-allu-id (Long/parseLong allu-id))))]
    (info "Checking verdicts for " (count apps) " application(s).")
    (doseq [app apps
            :when (fetchable? app)
            :let [command (assoc (application->command app) :user batchrun-user
                                                            :created (now)
                                                            :action "fetch-allu-contract")]]
      (logging/with-logging-context {:applicationId (:id app) :userId (:id batchrun-user)}
        (try
          (allu-contract/fetch-allu-contract command)
          (catch Exception e
            (error e))))))
  (mount-for-batchrun :stop))

(defn fetch-missing-allu-decisions
  "Fetch allu-verdict:s (AKA /decision:s) from ALLU for placement contracts that should have them in :pate-verdicts
  but don't."
  []
  (infof "Starting fetch-missing-allu-decisions for 091-YA")
  (mount-for-batchrun :start)
  (allu/login!)

  (let [org-id "091-YA" ; Helsinki YA
        batchrun-user (user/batchrun-user [org-id])
        apps (domain/get-multiple-applications-no-access-checking
               {:organization  org-id
                :permitSubtype "sijoitussopimus"
                :state         "agreementSigned" ; terminal state for sijoitussopimus
                ;; Has no allu-verdict:s (which originate from ALLU /decision API):
                :pate-verdicts {$not {$elemMatch {:category "allu-verdict"}}}})]
    (info "Fetching decisions for " (count apps) " application(s).")
    (doseq [app apps
            :let [command (assoc (application->command app) :user batchrun-user
                                                            :created (now)
                                                            :action "fetch-allu-contract")]]
      (logging/with-logging-context {:applicationId (:id app) :userId (:id batchrun-user)}
        (try
          ;; `fetch-allu-contract` does not produce any duplicate verdicts or weird state changes so we can just:
          (allu-contract/fetch-allu-contract command)
          (catch Exception exn
            (error exn))))))
  (mount-for-batchrun :stop))

(defn- organization-has-krysp-url-function
  "Takes map of organization id as key and organization data as values.
  Returns function, that takes application and returns true if application's organization has krysp url set."
  [organizations-by-id]
  (fn [{org :organization permitType :permitType}]
    (ss/not-blank? (get-in organizations-by-id [org :krysp (keyword permitType) :url]))))

(defn- get-valid-applications
  "Returns applications, that have krysp.url set in their organizations permitType.
  Applications need to be filtered, as there might be several permitTypes per organization, but not all have krysp.url set."
  [organizations applications]
  {:pre [(sequential? organizations) (sequential? applications)]}
  (let [orgs-by-id (util/key-by :id organizations)
        org-has-url? (organization-has-krysp-url-function orgs-by-id)]
    (filter org-has-url? applications)))

(defn fetch-verdicts [batchrun-name batchrun-user applications & [options]]
  (let [start-ts (double (now))]
    (logging/log-event :info {:run-by batchrun-name
                              :event  (format "Starting verdict fetching with %s applications" (count applications))})
    (doseq [application applications]
      (fetch-verdict batchrun-name batchrun-user application options))
    (logging/log-event :info {:run-by batchrun-name
                              :event  "Finished verdict checking"
                              :took   (format "%.2f minutes" (/ (- (now) start-ts) 1000 60))})))

(defn application-id-args? [args]
  (every? v/application-id? args))

(defn fetch-verdicts-by-application-ids [ids]
  (infof "Starting fetch-verdicts-by-application-ids with %s ids" (count ids))
  (let [apps (domain/get-multiple-applications-no-access-checking {:_id {$in ids} :state "sent"})
        distinct-org-ids (into #{} (map :organization) apps)
        orgs (mongo/select :organizations {:_id {$in distinct-org-ids}} [:krysp])
        apps-with-urls (get-valid-applications orgs apps)
        eraajo-user (user/batchrun-user distinct-org-ids)
        batchrun-name "Verdicts checking by application ids"]
    (fetch-verdicts batchrun-name eraajo-user apps-with-urls)))

(defn fetch-verdicts-by-org-ids [ids]
  (infof "Starting fetch-verdicts-by-org-ids with %s ids" (count ids))
  (if-let [orgs (seq (org/get-organizations {:_id {$in ids}} [:krysp]))]
    (let [applications (mongo/select :applications {:state {$in ["sent"]}
                                                    :permitType {$nin ["ARK"]}
                                                    :organization {$in ids}})
          apps-with-urls (get-valid-applications orgs applications)
          batchrun-name "Verdicts checking by organizations"
          eraajo-user (user/batchrun-user (map :id orgs))]
      (fetch-verdicts batchrun-name eraajo-user apps-with-urls))
    (warn "No organizations found, exiting.")))

(defn fetch-verdicts-with-args [args]
  (debug "check-for-verdicts started with args" args)
  (if (application-id-args? args)
    (fetch-verdicts-by-application-ids args)
    (fetch-verdicts-by-org-ids args)))

(defn fetch-verdicts-default [& [additional-opts]]
  (let [organizations-with-krysp-url (org/get-organizations
                                       {$or [{:krysp.R.url {$exists true}}
                                             {:krysp.YA.url {$exists true}}
                                             {:krysp.P.url {$exists true}}
                                             {:krysp.MAL.url {$exists true}}
                                             {:krysp.VVVL.url {$exists true}}
                                             {:krysp.YI.url {$exists true}}
                                             {:krysp.YL.url {$exists true}}
                                             {:krysp.KT.url {$exists true}}]}
                                       {:krysp 1})
        org-ids (map :id organizations-with-krysp-url)
        apps (mongo/select-ordered :applications
                                   {:state {$in ["sent"]}
                                    :permitType {$nin ["ARK"]}
                                    :organization {$in org-ids}}
                                   {:modified -1})
        apps-with-urls (get-valid-applications organizations-with-krysp-url apps)
        eraajo-user (user/batchrun-user org-ids)
        batchrun-name "Automatic verdicts checking"]
    (fetch-verdicts batchrun-name eraajo-user apps-with-urls additional-opts)))

(defn check-for-verdicts [& args]
  (when-not (system-not-in-lockdown?)
    (logging/log-event :info {:run-by "Automatic verdict checking" :event "Not run - system in lockdown"})
    (fail! :system-in-lockdown))
  (mount-for-batchrun :start)
  (if (empty? args)
    (fetch-verdicts-default {:use-queue? (env/feature? :integration-message-queue)})
    (fetch-verdicts-with-args args))
  (mount-for-batchrun :stop))

(defn check-for-asianhallinta-messages [& _]
  (when-not (system-not-in-lockdown?)
    (logging/log-event :info {:run-by "Asianhallinta reader" :event "Not run - system in lockdown"})
    (fail! :system-in-lockdown))
  (mount-for-batchrun :start)
  (sftp/process-case-management-responses)
  (mount-for-batchrun :stop))

(defn orgs-for-review-fetch [& organization-ids]
  (mongo/select :organizations
                (merge {:krysp.R.url {$exists true},
                        :krysp.R.version {$gte "2.1.5"}}
                       (when-not (seq organization-ids) {:automatic-review-fetch-enabled true})
                       (when (seq organization-ids) {:_id {$in organization-ids}}))
                {:krysp true}))

(defn- save-reviews-for-application [user created application {:keys [updates added-tasks-with-updated-buildings attachments-by-ids] :as result}]
  (let [{:keys [only-use-inspection-from-backend
                assignments-enabled]} (org/get-organization (:organization application)
                                                            [:only-use-inspection-from-backend
                                                             :assignments-enabled])]
    (logging/with-logging-context {:applicationId (:id application) :userId (:id user)}
      (when (ok? result)
        (try
          (let [result (review/save-review-updates (assoc (application->command application)
                                                          :user user :created created)
                                                   updates
                                                   added-tasks-with-updated-buildings
                                                   attachments-by-ids
                                                   only-use-inspection-from-backend)]
            (when (and (ok? result) assignments-enabled)
              (factory/prune-application-review-assignments (:id application)))
            result)

          (catch Throwable t
            {:ok false :desc (.getMessage t)}))))))

(defn- read-reviews-for-application
  [user created application asia-xml opts]
  (try
    (when (and application asia-xml)
      (let [{:keys [review-count
                    all-tasks
                    validation-errors
                    attachments-by-ids] :as result} (review/read-reviews-from-xml user created application asia-xml opts)]
        (cond
          (and (ok? result) (pos? review-count)) (logging/log-event :info {:run-by    "Automatic review checking"
                                                                           :event     "Reviews found"
                                                                           :all-tasks (->> all-tasks
                                                                                           (mapv (juxt identity #(count (get attachments-by-ids %)))))})
          (ok? result) (logging/log-event :info {:run-by "Automatic review checking"
                                                 :event  "No reviews"})
          (fail? result) (logging/log-event :error {:run-by            "Automatic review checking"
                                                    :event             "Failed to read reviews"
                                                    :text              (:text result)
                                                    :validation-errors validation-errors}))
        result))
    (catch Throwable e
      (logging/log-event :error {:run-by         "Automatic review checking"
                                 :event          "Error while reading reviews"
                                 :application-id (:id application)
                                 :exception-msg  (str (get-throw-context e))})
      (errorf e "error.integration - Could not read reviews for %s" (:id application)))))

(defn fetch-reviews-for-organization-permit-type-consecutively [organization permit-type applications]
  (logging/log-event :info {:run-by "Automatic review checking"
                            :event "Fetch consecutively"
                            :organization-id (:id organization)
                            :application-count (count applications)
                            :applications (map :id applications)})
  (->> (map (fn [app]
              (try
                (krysp-fetch/fetch-xmls-for-applications organization permit-type [app])
                (catch Throwable t
                  (logging/log-event :error {:run-by "Automatic review checking"
                                             :application-id (:id app)
                                             :organization-id (:id organization)
                                             :exception (.getName (class t))
                                             :message (.getMessage t)
                                             :event (format "Unable to get reviews for %s from %s backend"  permit-type (:id organization))})
                  nil)))
            applications)
       (apply concat)
       (remove nil?)))

(defn- fetch-reviews-for-organization-permit-type [organization permit-type applications]
  ; NOTE! applications have only :id, :permitType and :verdicts keys
  (try+

   (logging/log-event :info {:run-by "Automatic review checking"
                             :event "Start fetching xmls"
                             :organization-id (:id organization)
                             :application-count (count applications)
                             :applications (map :id applications)})

   (krysp-fetch/fetch-xmls-for-applications organization permit-type applications)

   (catch SAXParseException _
     (logging/log-event :error {:run-by "Automatic review checking"
                                :organization-id (:id organization)
                                :event (format "Could not understand response when getting reviews in chunks from %s backend" (:id organization))})
     ;; Fallback into fetching xmls consecutively
     (fetch-reviews-for-organization-permit-type-consecutively organization permit-type applications))

   (catch [:sade.core/type :sade.core/fail
           :status         404] _
     (logging/log-event :error {:run-by "Automatic review checking"
                                :organization-id (:id organization)
                                :event (format "Unable to get reviews in chunks from %s backend: Got HTTP status 404" (:id organization))})
     ;; Fallback into fetching xmls consecutively
     (fetch-reviews-for-organization-permit-type-consecutively organization permit-type applications))


   (catch [:sade.core/type :sade.core/fail] t
     (logging/log-event :error {:run-by "Automatic review checking"
                                :organization-id (:id organization)
                                :event (format "Unable to get reviews from %s backend: %s" (:id organization) (select-keys t [:status :text]))}))

   (catch Throwable o
     (logging/log-event :error {:run-by "Automatic review checking"
                                :organization-id (:id organization)
                                :exception (.getName (class o))
                                :message (get &throw-context :message "")
                                :event (format "Unable to get reviews in chunks from %s backend: %s - %s"
                                               (:id organization) (.getName (class o)) (get &throw-context :message ""))})
     (warn "Error in automatic review checking:" (get &throw-context :message "") "stack trace:" (ss/join " | " (.getStackTrace o))))))

(defn- organization-applications-for-review-fetching
  ([organization-id permit-type projection]
   (organization-applications-for-review-fetching organization-id permit-type projection {}))
  ([organization-id permit-type projection {:keys [application-ids include-closed?]}]
   (let [eligible-application-states (cond-> (difference states/post-verdict-but-terminal #{:foremanVerdictGiven})
                                       include-closed? (conj :closed))]
     (domain/get-multiple-applications-no-access-checking
       (merge {:state {$in eligible-application-states}
               :permitType permit-type
               :organization organization-id
               :primaryOperation.name {$nin ["tyonjohtajan-nimeaminen-v2" "suunnittelijan-nimeaminen"]}}
              (when (not-empty application-ids)
                (if (= (count application-ids) 1)
                  {:_id (first application-ids)}
                  {:_id {$in application-ids}})))
       (merge app-state/timestamp-key
              (pcond-> projection
                       sequential? (zipmap (repeat true))))))))

(defn mark-reviews-faulty-for-application [application {:keys [new-faulty-tasks]}]
  (when-not (empty? new-faulty-tasks)
    (let [timestamp (now)]
      (doseq [task-id new-faulty-tasks]
        (tasks/task->faulty (assoc (application->command application) :created timestamp) task-id)))))

(defn- log-review-results-for-organization [organization-id save-results]
  (logging/log-event :info {:run-by "Automatic review checking"
                            :event "Review checking finished for organization"
                            :organization-id organization-id
                            :application-count (count save-results)
                            :save-results (into {} save-results)})
  save-results)

(defn read-and-save-reviews-from-xml [eraajo-user created app overwrite-background-reviews? asia-xml]
  (logging/with-logging-context {:applicationId (:id app) :userId (:id eraajo-user)}
    (let [result      (read-reviews-for-application eraajo-user created app asia-xml {:overwrite-background-reviews?
                                                                                      overwrite-background-reviews?
                                                                                      :skip-task-validation?
                                                                                      (or (app/previous-permit? app)
                                                                                          (:_skip-task-validation app))})
          save-result (save-reviews-for-application eraajo-user created app result)]
      ;; save-result is nil when application query fails (application became irrelevant for review fetching) -> no actions taken
      (when (fail? save-result)
        (logging/log-event :info {:run-by         "Automatic review checking"
                                  :event          "Failed to save review updates for application"
                                  :reason         (:desc save-result)
                                  :application-id (:id app)
                                  :result         result}))
      (when (ok? save-result)
        (mark-reviews-faulty-for-application app result))
      save-result)))

(defn- fetch-reviews-for-organization
  [eraajo-user created {org-krysp :krysp :as organization} permit-types applications {:keys [overwrite-background-reviews?]}]
  (let [projection   [:permitType :verdicts]
        permit-types (remove (fn-> keyword org-krysp :url ss/blank?) permit-types)
        grouped-apps (if (seq applications)
                       (group-by :permitType applications)
                       (->> (map #(organization-applications-for-review-fetching (:id organization) % projection) permit-types)
                            (zipmap permit-types)))]
    (->> (mapcat (partial apply fetch-reviews-for-organization-permit-type organization) grouped-apps)
         ; fetch-reviews-for-organization-permit-type returns lazy sequence of maps, where map's key is application
         ; and collection of 'asiat' is value.
         (map (fn [[{app-id :id permit-type :permitType} asia-xmls]]
                (let [wider-projection (distinct
                                         (concat
                                           [:address :attachments.target :attachments.type
                                            :attachments.id :attachments.latestVersion :_skip-task-validation
                                            :created :documents :history :modified :municipality
                                            :primaryOperation :permitType :permitSubtype
                                            :state :organization :tasks :verdicts]
                                           state-change/base-keys))
                      app              (first
                                         (organization-applications-for-review-fetching
                                           (:id organization)
                                           permit-type
                                           wider-projection
                                           {:application-ids [app-id]
                                            :include-closed? (not (empty? applications))}))]
                  (when app
                    (->> asia-xmls
                         (keep #(read-and-save-reviews-from-xml eraajo-user created app overwrite-background-reviews? %))
                         (vector app-id))))))
         (remove nil?)
         (log-review-results-for-organization (:id organization)))))

(defn poll-verdicts-for-reviews
  [& {:keys [application-ids organization-ids] :as options}]
  (let [applications  (when (seq application-ids)
                        (domain/get-multiple-applications-no-access-checking {:_id {$in application-ids}}))
        permit-types  (or (->> applications
                               (map (comp keyword :permitType))
                               distinct
                               (remove #{:ARK})
                               not-empty)
                          [:R])
        organizations (->> (map :organization applications) distinct (concat organization-ids) (apply orgs-for-review-fetch))
        eraajo-user   (user/batchrun-user (map :id organizations))
        threadpool    (threads/threadpool (util/->int (env/value :batchrun :review-check-threadpool-size) 8) "review checking worker")
        threads       (mapv (fn [org]
                              (threads/submit
                               threadpool
                               (try
                                 (fetch-reviews-for-organization eraajo-user (now) org permit-types (filter (comp #{(:id org)} :organization) applications) options)
                                 (catch Throwable t
                                   (logging/log-event :info {:run-by "Automatic review checking"
                                                             :event  "Failed to check reviews for organization"
                                                             :organization-id (:id org)
                                                             :reason (.getMessage t)})))))
                            organizations)]
    (threads/wait-for-threads threads)))

(defn check-for-reviews [& _]
  (when-not (system-not-in-lockdown?)
    (logging/log-event :info {:run-by "Automatic review checking" :event "Not run - system in lockdown"})
    (fail! :system-in-lockdown))
  (mount-for-batchrun :start)
  (logging/log-event :info {:run-by "Automatic review checking" :event "Started"})
  (poll-verdicts-for-reviews)
  (logging/log-event :info {:run-by "Automatic review checking" :event "Finished"})
  (mount-for-batchrun :stop))

(defn check-reviews-for-orgs [& args]
  (when-not (system-not-in-lockdown?)
    (logging/log-event :info {:run-by "Automatic review checking" :event "Not run - system in lockdown"})
    (fail! :system-in-lockdown))
  (mount-for-batchrun :start)
  (logging/log-event :info {:run-by "Automatic review checking" :event "Started" :organizations args})
  (poll-verdicts-for-reviews :organization-ids args)
  (logging/log-event :info {:run-by "Automatic review checking" :event "Finished" :organizations args})
  (mount-for-batchrun :stop))

(defn overwrite-reviews-for-orgs [& args]
  (when-not (system-not-in-lockdown?)
    (logging/log-event :info {:run-by "Review checking with overwrite" :event "Not run - system in lockdown"})
    (fail! :system-in-lockdown))
  (when (empty? args)
    (logging/log-event :info {:run-by "Review checking with overwrite" :event "Not run - no organizations specified"})
    (fail! :no-organizations))
  (mount-for-batchrun :start)
  (logging/log-event :info {:run-by "Review checking with overwrite" :event "Started" :organizations args})
  (poll-verdicts-for-reviews :organization-ids args
                             :overwrite-background-reviews? true)
  (logging/log-event :info {:run-by "Review checking with overwrite" :event "Finished" :organizations args})
  (mount-for-batchrun :stop))

(defn check-reviews-for-ids
  "Note: updates also closed applications."
  [& args]
  (when-not (system-not-in-lockdown?)
    (logging/log-event :info {:run-by "Automatic review checking" :event "Not run - system in lockdown"})
    (fail! :system-in-lockdown))
  (mount-for-batchrun :start)
  (logging/log-event :info {:run-by "Automatic review checking" :event "Started" :applications args})
  (poll-verdicts-for-reviews :application-ids args)
  (logging/log-event :info {:run-by "Automatic review checking" :event "Finished" :applications args})
  (mount-for-batchrun :stop))

(defn pdfa-convert-review-pdfs [& _]
  (mount-for-batchrun :start)
  (debug "# of applications with background generated tasks:"
         (mongo/count :applications {:tasks.source.type "background"}))
  (let [eraajo-user (user/batchrun-user (map :id (orgs-for-review-fetch)))]
    (doseq [application (mongo/select :applications {:tasks.source.type "background"})]
      (let [command (assoc (application->command application) :user eraajo-user :created (now))]
        (doseq [task (:tasks application)]
          (if (= "background" (:type (:source task)))
            (doseq [att (:attachments application)]
              (if (= (:id task) (:id (:source att)))
                (do
                  (debug "application" (:id (:application command)) "- converting task" (:id task) "-> attachment" (:id att))
                  (attachment/convert-existing-to-pdfa! (:application command) att)))))))))
  (mount-for-batchrun :stop))

(defn pdf-to-pdfa-conversion
  "Organization is the organization id string (e.g., 980-YA),
  dates are given in ISO format (e.g., 2019-06-20)."
  [& [organization start-date end-date]]
  (if (and organization start-date end-date)
    (let [start-ts (c/to-long (c/from-string start-date))
          end-ts (c/to-long (c/from-string end-date))]
      (mount-for-batchrun :start)
      ; Conversion with LibreOffice is error prone, disable it for this run
      (env/disable-feature! :convert-pdfs-with-libre)
      (info "Starting pdf to pdf/a conversion for" organization "from" start-date "to" end-date)
      (doseq [application (->> (mongo/with-collection "applications"
                                 (monger-query/find {:organization organization
                                                     :permitType {$nin ["ARK"]}
                                                     :archived.completed nil
                                                     $or [{:submitted {$gte start-ts $lte end-ts}}
                                                          {:submitted nil
                                                           :created   {$gte start-ts $lte end-ts}}]})
                                 (monger-query/sort {:submitted 1}))
                               (map mongo/with-id))]
        (logging/with-logging-context {:applicationId (:id application)}
          (info "Checking attachments of application" (:id application))
          (doseq [{:keys [latestVersion] :as attachment} (:attachments application)]
            (when (and latestVersion (not (:archivable latestVersion)))
              (info "Trying to convert attachment" (:filename latestVersion))
              ; Re-fetch application to minimize errors because of it changing during processing
              (let [{:keys [attachments] :as application} (mongo/by-id :applications (:id application))
                    attachment (first (filter #(= (:id attachment) (:id %)) attachments))
                    result (attachment/convert-existing-to-pdfa! application attachment)
                    log-message {:application-id (:id application)
                                 :attachment-id (:id attachment)
                                 :type (:type attachment)
                                 :filename (:filename latestVersion)}]
                (if (:archivable result)
                  (logging/log-event :info (merge {:run-by "Batch PDF/A conversion run"
                                                   :event "Successfully converted attachment to PDF/A"}
                                                  log-message))
                  (logging/log-event :error (merge {:run-by "Batch PDF/A conversion run"
                                                    :event "Attachment conversion to PDF/A failed"
                                                    :archivability-error (:archivabilityError result)}
                                                   log-message))))))))

      (mount-for-batchrun :stop))
    (println "Organization id, start date and end date arguments must be provided.")))

(defn generate-missing-neighbor-attachments
  "Generates the missing neighbor hearing attachments
  Organization is the organization id string (e.g., 980-YA)"
  [& args]
  (mount-for-batchrun :start)
  (let [{dry-run?      true
         organizations false} (group-by #(= % "-dry-run") args)
        enriched-neighbors (group-by :app-id (repair/neighbors-with-missing-attachments organizations))
        orgs               (->> enriched-neighbors
                                vals
                                (apply concat)
                                (keep :org)
                                distinct)
        eraajo-user        (user/batchrun-user orgs)
        note-ts            (now)]
    (when-not dry-run?
      (doseq [application (keys enriched-neighbors)]
        (mongo/update-by-id :applications
                            application
                            {$push {:_sheriff-notes {:note    "Batchrun: generate-missing-neighbor-attachments"
                                                     :created note-ts}}})
        (doseq [{:keys [app-id neighbor-id]} (get enriched-neighbors application)
                :when (and (seq enriched-neighbors) (not dry-run?))]
          (child-to-attachment/create-attachment-from-children
            eraajo-user
            (domain/get-application-no-access-checking app-id)
            :neighbors
            neighbor-id
            "fi"))))
    (if (seq enriched-neighbors)
      (if dry-run?
        (info "Neighbor hearing attachments missing from applications: " (->> enriched-neighbors keys (str/join ",")))
        (info "Neighbor hearing attachments successfully generated"))
      (info "No missing neighbor hearing attachments found")))
  (mount-for-batchrun :stop))

(defn fetch-verdict-attachments
  [start-timestamp end-timestamp organizations]
  {:pre [(number? start-timestamp)
         (number? end-timestamp)
         (< start-timestamp end-timestamp)
         (vector? organizations)]}
  (let [app-infos (verdict/applications-with-missing-verdict-attachments
                   {:start         start-timestamp
                    :end           end-timestamp
                    :organizations organizations})
        eraajo-user (user/batchrun-user (map :organization app-infos))]
    (->> (doall
          (map
           (fn [{:keys [id organization permitType]}]
             (logging/with-logging-context {:applicationId id, :userId (:id eraajo-user)}
               (try+
                 (logging/log-event :info {:run-by "Automatic verdict attachments checking"
                                           :event "Fetching verdict attachments"
                                           :application-id id})
                 (let [app (domain/get-application-no-access-checking id [:state :municipality :documents
                                                                          :address :permitType
                                                                          :permitSubtype :organization
                                                                          :primaryOperation :verdicts
                                                                          :attachments])
                       command (assoc (application->command app) :user eraajo-user :created (now) :action :fetch-verdict-attachments)
                       app-xml (krysp-fetch/get-valid-xml-by-application-id app)
                       result  (verdict/update-verdict-attachments-from-xml! command app-xml)]
                   (when (-> result :updated-verdicts count pos?)
                     ;; Print manually to events.log, because "normal" prints would be sent as emails to us.
                     (logging/log-event :info {:run-by "Automatic verdict attachments checking"
                                               :event "Found new verdict attachments"
                                               :application-id id
                                               :updated-verdicts (-> result :updated-verdicts)}))
                   (when (or (nil? result) (fail? result))
                     (logging/log-event :warning {:run-by "Automatic verdict attachment checking"
                                                  :event "Failed to check verdict attachments"
                                                  :application-id id
                                                  :failure (if (nil? result) :error.no-app-xml result)
                                                  :organization {:id organization :permit-type permitType}}))

                   ;; Return result for testing purposes
                   result)
                 (catch [:sade.core/type :sade.core/fail :text "error.no-legacy-available"] _
                   (logging/log-event :warning {:run-by "Automatic verdict attachments checking"
                                                :application-id id
                                                :event "No legacy available"}))
                 (catch [:sade.core/type :sade.core/fail] {:keys [text]}
                   (logging/log-event :warning {:run-by "Automatic verdict attachments checking"
                                                :event "fail!"
                                                :application-id id
                                                :text text}))
                 (catch Throwable t
                   (logging/log-event :error {:run-by "Automatic verdict attachments checking"
                                              :event "Unable to get verdict from backend"
                                              :exception-message (.getMessage t)
                                              :application-id id})))))
           app-infos))
         (remove #(empty? (:updated-verdicts %)))
         (hash-map :updated-applications)
         (merge {:start start-timestamp
                 :end   end-timestamp
                 :organizations organizations
                 :applications (map :id app-infos)}))))

(defn check-for-verdict-attachments
  "Fetch missing verdict attachments for verdicts given in the time
  interval between start-timestamp and end-timestamp (last 3 months by
  default), for the organizations whose id's are provided as
  arguments (all organizations by default)."
  [& [start-timestamp end-timestamp & organizations]]
  (when-not (system-not-in-lockdown?)
    (logging/log-event :info {:run-by "Automatic verdict attachment checking" :event "Not run - system in lockdown"})
    (fail! :system-in-lockdown))
  (mount-for-batchrun :start)
  (fetch-verdict-attachments (or (date/timestamp start-timestamp)
                                 (-> 3 t/months t/ago c/to-long))
                             (or (date/timestamp end-timestamp)
                                 (now))
                             (or (vec organizations) [])))

(defn unarchive [& [organization application-id]]
  (if organization
    (do
      (mount-for-batchrun :start)
      (info "Starting unarchiving operation for" organization "applications:" (or application-id "all"))
      (doseq [application (->> (mongo/with-collection "applications"
                                                      (monger-query/find (util/assoc-when
                                                                           {:organization organization
                                                                            :state        {$nin states/terminal-states}
                                                                            :permitType   {$ne "ARK"}
                                                                            :attachments  {$elemMatch {:metadata.tila   :arkistoitu
                                                                                                       :type.type-group :erityissuunnitelmat}}}
                                                                           :_id application-id)))
                               (map mongo/with-id))]
        (logging/with-logging-context {:applicationId (:id application)}
          (info "Checking attachments of application" (:id application))
          (let [results (pmap
                          (fn [{:keys [latestVersion metadata] :as attachment}]
                            (if (and latestVersion
                                     (= (keyword (get-in attachment [:type :type-group])) :erityissuunnitelmat)
                                     (= (keyword (:tila metadata)) :arkistoitu))
                              (let [_ (info "Trying to unarchive attachment" (:id attachment) "/" (:filename latestVersion))
                                    host (env/value :arkisto :host)
                                    app-id (env/value :arkisto :app-id)
                                    app-key (env/value :arkisto :app-key)
                                    encoded-id (codec/url-encode (:id attachment))
                                    url (str host "/documents/" encoded-id)
                                    {:keys [status body]} (http/delete url {:basic-auth       [app-id app-key]
                                                                            :throw-exceptions false
                                                                            :as               :json
                                                                            :query-params     {"organization" organization}})
                                    log-message {:application-id (:id application)
                                                 :attachment-id  (:id attachment)
                                                 :type           (:type attachment)
                                                 :filename       (:filename latestVersion)}]
                                (if (= 200 status)
                                  (logging/log-event :info (merge {:run-by "Batch unarchiving run"
                                                                   :event  "Successfully unarchived attachment"}
                                                                  log-message))
                                  (logging/log-event :error (merge {:run-by       "Batch unarchiving run"
                                                                    :event        "Attachment unarchiving failed"
                                                                    :onkalo-error body}
                                                                   log-message)))
                                (= 200 status))
                              true))
                          (:attachments application))]
            (if (every? true? results)
              (info "Attachments successfully unarchived for application" (:id application))
              (error "Some attachments were not successfully unarchived for application" (:id application))))))
      (mount-for-batchrun :stop))
    (println "Organization must be provided.")))

(defn fix-bad-archival-conversions-in-091-R []
  (mount-for-batchrun :start)
  (info "Reconverting attachments to PDF/A in 091-R archiving projects")
  (doseq [{:keys [id]} (mongo/select :applications
                                     {:organization "091-R"
                                      :permitType "ARK"
                                      ; 2018-07-12 09:00 Z
                                      :created {$gt 1531386000000}
                                      :state {$in ["open" "underReview"]}}
                                     [:_id]
                                     {:_id 1})]
    (logging/with-logging-context {:applicationId id}
      (doseq [{:keys [latestVersion versions metadata] :as att} (:attachments (mongo/by-id :applications id [:attachments]))
              :when (and (not= :arkistoitu (keyword (:tila metadata)))
                         (or (and (:archivable latestVersion)
                                  (not= (:fileId latestVersion) (:originalFileId latestVersion)))
                             (and (not (:archivable latestVersion))
                                  (= (:archivabilityError latestVersion) "invalid-pdfa"))))]
        (info "Reconverting attachment" (:id att))
        (let [last-idx (dec (count versions))]
          (mongo/update :applications
                        {:_id id
                         :attachments {$elemMatch {:id (:id att)}}}
                        {$set {:attachments.$.latestVersion.archivable false
                               :attachments.$.latestVersion.fileId (:originalFileId latestVersion)
                               (str "attachments.$.versions." last-idx ".archivable") false
                               (str "attachments.$.versions." last-idx ".fileId") (:originalFileId latestVersion)}})
          (let [updated-app (mongo/by-id :applications id)
                attachment (attachment/get-attachment-info updated-app (:id att))]
            (attachment/convert-existing-to-pdfa! updated-app attachment)))
        (info "Attachment" (:id att) "processed"))))
  (info "Done.")
  (mount-for-batchrun :stop))

(defn fix-file-links
  "For each given application, checks every attachment version for unlinked file-ids and
  links them to the application."
  [& application-ids]
  (mount-for-batchrun :start)
  (doseq [application (mongo/select :applications
                                    {:_id {$in application-ids}}
                                    [:attachments.versions])]
    (storage/fix-file-links application))
  (mount-for-batchrun :stop))

(defn archive-digitized-projects-in-org [& [start-timestamp end-timestamp organization digitizer]]
  (if (and start-timestamp end-timestamp organization)
    (do (mount-for-batchrun :start)
        (info "Archiving digitized projects (ARK/LX) for organizations:" organization "from" start-timestamp "until" end-timestamp)
        (let [from  (date/timestamp start-timestamp)
              until (date/timestamp end-timestamp)
              query (merge
                      {:organization organization
                       :permitType   "ARK"
                       :state        "underReview"
                       :created      {$gte from
                                      $lt  until}}
                      (when digitizer
                        {:creator.username digitizer}))
              app-count (mongo/count :applications
                                     query)]
          (info "Total project count:" app-count)
          (->> (mongo/select :applications
                             query)
               (mapcat (fn [app]
                         (when-not (:tosFunction app)
                           (info "Setting TOS function for" (:id app))
                           (tos/update-tos-metadata "10 03 00 01"
                                                    (assoc
                                                      (application->command app (user/batchrun-user [organization]))
                                                      :created (now))
                                                    "Menettely asetettu arkistointia varten."))
                         (info "Archiving" (:id app))
                         (let [app (mongo/by-id :applications (:id app))]
                           (archiving/send-to-archive
                             (assoc
                               (application->command app (user/batchrun-user [organization]))
                               :created (now))
                             (->> (:attachments app)
                                  (filter (fn [att]
                                            (not= "arkistoitu" (get-in att [:metadata :tila]))))
                                  (map :id)
                                  set)
                             #{}))))
               (threads/wait-for-threads))
          ; Post-archiving threads may be running long after and we don't have a handle to them
          (info "Waiting" app-count "seconds for post-archiving jobs.")
          (Thread/sleep (* app-count 2 1000))))
    (println "Need to provide start date, end date and organization. (And optionally digitizer's email.)"))
  (info "Done.")
  (mount-for-batchrun :stop))

(defn link-converted-apps
  "This should be run after the conversion for an organization is otherwise done. We use here
  the :conversion collection in Mongo, which contains a record for each
  converted application. These have a structure like {:backend-id (kuntalupatunnus),
  :converted (boolean), :linked (boolean), :LP-id (corresponding Lupapiste id),
  :app-links (array with kuntalupatunnus ids that should be linked to the current
  application)."
  [& [organization-id re-link?]]
  (if (nil? organization-id)
    (error "No organization given")
    (do
      (infof "Adding app-links to applications imported from backend system for %s..." organization-id)
      (mount-for-batchrun :start)
      (conv-link/link-converted-files organization-id (= re-link? "-r"))
      (mount-for-batchrun :stop))))

(defn move-files-to-gcs
  "Move attachment files etc. from Ceph (or backup GCS) to operative GCS"
  []
  (mount-for-batchrun :start)
  (storage-move-util/move-all-files)
  (mount-for-batchrun :stop))

(defn rescue-orphaned-files
  "Check if a file is missing from the correct storage and copy from any other storage, if found there"
  [& [application-modified-since]]
  (if application-modified-since
    (let [modified-since (Long/parseLong application-modified-since)]
      (mount-for-batchrun :start)
      (storage-move-util/rescue-files-from-wrong-storage modified-since)
      (mount-for-batchrun :stop))
    (println "Application modified-since must be provided as milliseconds")))

(defn vantaa-statements-from-excel
  "Update (previously converted) Vantaa application statements with information from the
  provided Excel file."
  [filename]
  (mount-for-batchrun :start)
  (vantaa/statements-from-excel filename)
  (mount-for-batchrun :stop))

(defn- julkipano-ts-in-past? [now-ts pate-verdict]
  (if-let [ts (metadata/unwrap (get-in pate-verdict [:data :julkipano]))]
    (< ts now-ts)
    (do
      (logging/log-event
        :warn
        {:event  "No julkipano timestamp available"
         :run-by "Scheduled verdict batchrun"})
      (warn "No julkipano date set for scheduled verdict"))))

(defn- verdicts-scheduled-for-today [now-ts {:keys [pate-verdicts]}]
  (->> pate-verdicts
       (filter pv/scheduled?)
       (filter (partial julkipano-ts-in-past? now-ts))))

(defn scheduled-verdict-publishing []
  (mount-for-batchrun :start)
  ;; We use the actual publish command, so we need to register
  ;; that command in order to invoke it or else we get ':error.invalid-command'.
  (require 'lupapalvelu.pate.verdict-api)
  (let [now-ts        (now)
        verdict-query {:state                      {$in states/all-application-states-but-draft-or-terminal}
                       :pate-verdicts.state._value "scheduled"}
        check-user    (fn [user]
                        (if user
                          true
                          (do
                            (logging/log-event
                              :error
                              {:event  "No user found from verdict state metadata"
                               :run-by "Scheduled verdict batchrun"})
                            (error "No user found from verdict state metadata"))))]
    (doseq [application (mongo/select :applications verdict-query)
            verdict     (verdicts-scheduled-for-today now-ts application)
            :let [user (some-> {:username (metadata/get-username (:state verdict))}
                               (user/get-user)
                               (user/session-summary)
                               (assoc :id (:id user/batchrun-user-data)))]
            :when (check-user user)]
      (logging/with-logging-context
        {:applicationId (:id application)
         :userId        (:id user)}
        (try
          (info "Publishing scheduled pate verdict" (:id verdict))
          (let [resp (execute-command
                       "publish-pate-verdict"
                       {:id         (:id application)
                        :verdict-id (:id verdict)}
                       {:user        user
                        :scheme      "cronjob"
                        :remote-addr "localhost"
                        :headers     {"user-agent" "Lupapiste Eräajo"
                                      "host"       "localhost"}})]
            (when-not (ok? resp)
              (logging/log-event
                :error
                {:event  "Scheduled publishing was not :ok"
                 :run-by "Scheduled verdict batchrun"
                 :resp   resp})
              (error "Scheduled publishing failed:" resp)))
          (catch Exception e
            (logging/log-event
              :error
              {:event  "Unknown exception when publishing verdict"
               :run-by "Scheduled verdict batchrun"
               :error  (str e)})
            (error e "Failure when publishing scheduled verdict, moving to next one (if any)..."))))))
  (mount-for-batchrun :stop))

(defn kuntagml-export-run
  "Export the KuntaGML XML and attachment files for the whole organization.
  Useful when a municipality leaves Lupapiste"
  [organization-id query & [limit]]
  (mount-for-batchrun :start)
  (let [parsed        (if (string? query)
                        (edn/read-string query)
                        query)
        mongo-query   (merge parsed {:organization organization-id})
        org           (delay (org/get-organization organization-id))
        batchrun-user (user/batchrun-user [organization-id])
        app-count     (mongo/count :applications mongo-query)
        success       (atom [])]
    (debug "Parsed query" mongo-query "limit:" limit ", query count:" app-count)
    (doseq [[i app] (map-indexed vector (mongo/select-ordered :applications
                                                              mongo-query
                                                              {:modified 1}
                                                              (or (util/->int limit) 0)))
            :let [post-processed (app/post-process-app-for-krysp app @org)]]
      (logging/with-logging-context
        {:applicationId (:id app)
         :userId        (:id batchrun-user)}
        (try
          (logging/log-event :debug {:event "Start export" :run-by "KuntaGML export batchrun"})
          (as-krysp/full-application-export-as-kuntagml {:application  post-processed
                                                         :organization org
                                                         :user         batchrun-user})
          (logging/log-event :debug {:event "Finished export" :run-by "KuntaGML export batchrun"})
          (swap! success conj (:id app))
          (when (zero? (rem i 10))
            (printf "%,d / %,d applications processed\r" (inc i) app-count)
            (flush))
          (catch Exception e
            (error "Exception when exporting KuntaGML" (.getMessage e))
            (logging/log-event
              :error
              {:event  (str "Exception when writing KuntaGML for org " organization-id)
               :run-by "KuntaGML export batchrun"
               :error  (str e)})))))
    (let [ids (count @success)]
      (info (format "Successfully exported %d applications" ids))
      (logging/log-event
        :info
        {:event  (format "Successfully exported %d applications" ids)
         :run-by "KuntaGML export batchrun"})))
  (mount-for-batchrun :stop))

(defn kuntagml-import-and-convert
  "Imports and converts KuntaGML messages into applications. See
  `conv/convert!` for parameters."
  [& args]
  (mount-for-batchrun :start)
  (apply conv/convert! args)
  (mount-for-batchrun :stop))

(defn send-conversion-messages
  "Sends KuntaGML import and convert results do dmCity. Parameters (in any order) are EDN
  filename and two optional options. The batchrun operates only on the applications
  corresponding to the `:files` of the EDN. The options are:

  `-state-changes`: Send the state change messages for the converted applications to dmCity.

  `-kuntagml`: Add the (conversion result) application ids to the orginal (`:files` in the
  EDN) KuntaGML messages and send them to dmCity"
  [& args]
  (mount-for-batchrun :start)
  (try
    (apply conv/send-conversion-messages-wrapper args)
    (catch Exception e
      (error e)))
  (mount-for-batchrun :stop))

(defn remove-bad-conversions
  "Remove erroneous conversions. Removes conversion document, converted application, its
  attachments and links. A conversion is deemed erroneous in two occasions:

  1. Conversion source KuntaGML refers already Lupapiste application id.

  2. The municipality backend id (kuntalupatunnus) for the conversion is not unique within
     the organization.

  Parameters (in any order) are EDN filename and two optional options:

  `-force`: The deletion is very conservative and does not remove applications that have
  been meaningfully edited after import. Some of these checks can be by-passed by this option.

  `-dry-run`: Only logs and does not actually remove anything."
  [& args]
  (mount-for-batchrun :start)
  (try
    (apply conv/remove-bad-conversions-wrapper args)
    (catch Exception e
      (error e)))
  (mount-for-batchrun :stop))

(defn add-missing-backend-ids
  "A converted application without verdict lacks the backend-id as well, since ids are
  stored into verdicts. However, since the backend ids are stored in to conversion
  collection, the situation can be remedied. The batchrun adds draft backing system
  verdict for any converted application (within the organization) that misses backend-id."
  [organization-id]
  (mount-for-batchrun :start)
  (conv/add-missing-backend-ids organization-id)
  (mount-for-batchrun :stop))

(defn- conversion-help
  ([bad-cmd]
   (when bad-cmd
     (println "Unknown conversion command:" bad-cmd "\n"))
   (println "Batchrun calling convention:

command param1 param2 ...

where the supported commands and their parameters are ([optional parameter]):

import configuration.edn

  Imports KuntaGML messages and converts them to Lupapiste applications.

link organization-id [-r]

  Updates the link permits of the converted applications. If `-r` option is given, the
  previously linked applications are re-linked, too.

send configuration.edn [-state-changes] [-kuntagml]

  Sends conversion results to dmCity. With `-state-changes` option, the state change
  messages are sent. If `-kuntagml` option is given the _original_ KuntaGML files are
  augmented with the converted application ids and sent to dmCity.

clean configuration.ed [-dry-run] [-force]

  Removes erroneous conversions. Should not be needed currently. With `-dry-run` option
  only logs, but does not actually remove anything. The`-force` option loosens some
  deletion checks and thus can clean conversions that would be left untouched otherwise.

backend-ids organization-id

  Adds missing backend-ids to the converted applications. Should not be needed anymore.

help

  Shows this help."))
  ([] (conversion-help nil)))

(defn conversion-router
  "Passes arguments the correct conversion command or shows help text."
  [& [cmd & args]]
  (when-let [fun (case cmd
                   "import"      kuntagml-import-and-convert
                   "link"        link-converted-apps
                   "send"        send-conversion-messages
                   "clean"       remove-bad-conversions
                   "backend-ids" add-missing-backend-ids
                   "help"        (conversion-help)
                   (conversion-help cmd))]
    (apply fun args)))

(defn attachments-export-batchrun
  "Collects the attachments of a given organization into a zip file along with
  an Excel spreadsheet with the metadata for all the attachments."
  [lang organization-id]
  (mount-for-batchrun :start)
  (attachments-export/export-attachments lang organization-id)
  (mount-for-batchrun :stop))

(defn review-cleanup
  "Marks faulty those reviews that do not belong to the application. Typical scenario is
  that a backing system has mixed up different applications. A review is marked faulty if
  it has been received from the backing system earlier, but is no longer in the
  application KuntaGML message AND is modified within the given time period.

  The application criteria is read from an EDN file (see `repair/RepairConfiguration`). If
  a parameter `-dry-run` is given, the applications are analyzed, the bad reviews logged,
  but no actual changes to Mongo is done."
  [& args]
  (mount-for-batchrun :start)
  (let [{:keys [targets dry-run?]
         :as   cfg} (apply repair/initialize-cleanup args)]
    (doseq [[org-id apps] targets
            :let          [organization (org/get-organization org-id)]]
      (doseq [[application xmls] (fetch-reviews-for-organization-permit-type organization
                                                                             "R"
                                                                             apps)
              :let               [bad-task-ids (repair/find-bad-reviews cfg application xmls)]
              :when              (and bad-task-ids (not dry-run?))]
        (mark-reviews-faulty-for-application application {:new-faulty-tasks bad-task-ids}))))
  (mount-for-batchrun :stop))

(defn fix-verdict-dates
  "Updates backing system verdict dates if those have changed since the original fetch. This
  can happen if the backing system has published bad data. Only the verdict date
  properties, application verdict-date and deadlines are updated (+ sheriff note) Notably,
  attachments and bulletins ARE NOT updated (the former does not matter that much and it
  is probably too late for the latter).

  The application criteria is read from an EDN file (see `repair/RepairConfiguration`). If
  a parameter `-dry-run` is given, the applications with changed verdict data are logged,
  but Mongo is not updated."
  [& args]
  (mount-for-batchrun :start)
  (repair/fix-verdict-dates args)
  (mount-for-batchrun :stop))

(defn fix-missing-statements
  "Earlier, the statement was marked given even if the statement.pdf generation/upload
  failed. This batchrun checks the statements for the application `application-id` and
  generates attachments if needed (as a batchrun user)."
  [application-id]
  (mount-for-batchrun :start)
  (statement/fix-missing-statement-attachments application-id)
  (mount-for-batchrun :stop))
