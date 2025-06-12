(ns lupapalvelu.batchrun
  (:require [clojure.edn :as edn]
            [clojure.set :refer [difference]]
            [lupapalvelu.action :refer [application->command]]
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
            [lupapalvelu.backing-system.dmcity.config :as dm-cfg]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as as-krysp]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.backing-system.krysp.http]
            [lupapalvelu.batchrun.fetch-verdict :as fetch-verdict]
            [lupapalvelu.batchrun.merge-org :as merge-org]
            [lupapalvelu.batchrun.pate :as pate]
            [lupapalvelu.batchrun.rami :as rami]
            [lupapalvelu.batchrun.reminders :as reminders]
            [lupapalvelu.batchrun.repair :as repair]
            [lupapalvelu.batchrun.vantaa-statements :as vantaa]
            [lupapalvelu.bim.core]
            [lupapalvelu.building-attributes :as ba]
            [lupapalvelu.child-to-attachment :as child-to-attachment]
            [lupapalvelu.conversion.cli :as conv-cli]
            [lupapalvelu.conversion.core :as conv]
            [lupapalvelu.conversion.other :as other]
            [lupapalvelu.document.allu-schemas :as da]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.exports.attachments-export :as attachments-export]
            [lupapalvelu.ident.ad-login :as ad-login]
            [lupapalvelu.integrations.mq-consumers]
            [lupapalvelu.integrations.pubsub :as pubsub]
            [lupapalvelu.integrations.state-change :as state-change]
            [lupapalvelu.lockdown :as lockdown]
            [lupapalvelu.logging :as log :refer [debug error errorf info infof warn]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notices.batchrun :as notices-batchrun]
            [lupapalvelu.notices.pdf]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.verdict :as pv]
            [lupapalvelu.review :as review]
            [lupapalvelu.sftp.core :as sftp]
            [lupapalvelu.statement :as statement]
            [lupapalvelu.states :as states]
            [lupapalvelu.statistics.db :as stats-db]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.storage.move-util :as storage-move-util]
            [lupapalvelu.system.system-util :as system-util]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.tiedonohjaus :as tos]
            [lupapalvelu.user :as user]
            [lupapalvelu.verdict :as verdict]
            [lupapiste-commons.threads :as threads]
            [monger.operators :refer :all]
            [monger.query :as monger-query]
            [ring.util.codec :as codec]
            [sade.core :refer [fail! fail? now ok?]]
            [sade.date :as date]
            [sade.dummy-email-server]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :refer [fn-> pcond->] :as util]
            [sade.validators :as v]
            [schema.core :as sc]
            [slingshot.slingshot :refer [get-throw-context try+]]))

(set! *warn-on-reflection* true)

(def system-not-in-lockdown? (complement lockdown/lockdown-from-db?))

(def default-batchrun-system-keys #{::mongo/database
                                    ::components/common
                                    ;; For generating PDFs
                                    :laundry/client
                                    ;; For generating preview images for attachments
                                    [::pubsub/publisher :attachment/preview]
                                    ;; For sending verdict fetch requests over pubsub
                                    [::pubsub/publisher :batchrun/fetch-verdicts]})

(defn get-system-components
  []
  (system-util/get-system-components))

(defn send-reminder-emails [& _]
  (when (env/feature? :reminders)
    (system-util/with-system default-batchrun-system-keys
      (let [system (get-system-components)]
        (reminders/statement-request-reminder system)
        (reminders/statement-reminder-due-date system)
        (reminders/open-inforequest-reminder system)
        (reminders/neighbor-reminder system)
        (reminders/application-state-reminder system)
        (reminders/ya-work-time-is-expiring-reminder system)
        (reminders/no-final-review-reminder system)
        (reminders/construction-not-started-reminder system)
        (reminders/supplement-request-reminder-due-date system)))))

(defn fetch-verdict
  [system batchrun-name batchrun-user {:keys [id organization] :as app} & [{:keys [use-queue? foreman-doc-id]}]]
  {:pre [(map? system)]}
  (if use-queue?
    (fetch-verdict/publish-to-queue system organization id foreman-doc-id)
    (fetch-verdict/fetch-verdict system batchrun-name batchrun-user app foreman-doc-id)))

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
        (log/log-event :error {:event                 (str "Failed to fetch " (name type) " application kinds")
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
        (log/log-event :error {:event                    (str "Failed to fetch " (name kind) " fixed locations")
                               :run-by                   "Fetch ALLU fixed locations"
                               :organization-id          "091-YA"
                               :allu-fixed-location-kind kind
                               :exception                (.getName (class exn))
                               :message                  (.getMessage exn)}))))
  (info "All ALLU fixed locations fetched"))

(defn fetch-allu-semiconstants
  "Fetch all semi-constant data that we need from ALLU, e.g. application kinds and fixed locations."
  [& _]
  (system-util/with-system default-batchrun-system-keys
    (allu/login!)

    (fetch-all-application-kinds)
    (fetch-all-fixed-locations)))

(defn fetch-allu-contracts
  "Fetch verdicts (contracts/decisions) from ALLU."
  []
  (infof "Starting fetch-allu-contracts for 091-YA")
  (system-util/with-system default-batchrun-system-keys
    (allu/login!)

    (let [batchrun-user        (user/batchrun-user ["091-YA"])
          apps                 (domain/get-multiple-applications-no-access-checking
                                 {:state                   {$in ["sent" "agreementPrepared"]}
                                  :organization            "091-YA" ; HACK
                                  :integrationKeys.ALLU.id {$exists true}})

          min-datetime         (date/zoned-date-time (transduce (map :created) min (now) apps))
          histories-by-allu-id (->> (allu/application-histories {:user    batchrun-user
                                                                 :created (now)
                                                                 :action  "fetch-allu-contracts"}
                                                                apps min-datetime)
                                    (group-by :applicationId)
                                    (util/map-values first))
          fetchable?           (fn [{{{allu-id :id} :ALLU} :integrationKeys}]
                                 (allu/application-history-fetchable? (get histories-by-allu-id (Long/parseLong allu-id))))]
      (info "Checking verdicts for " (count apps) " application(s).")
      (doseq [app   apps
              :when (fetchable? app)
              :let  [command (assoc (application->command app) :user batchrun-user
                                    :system (get-system-components)
                                    :created (now)
                                    :action "fetch-allu-contract")]]
        (log/with-logging-context {:applicationId (:id app) :userId (:id batchrun-user)}
          (try
            (allu-contract/fetch-allu-contract command)
            (catch Exception e
              (error e))))))))

(defn fetch-missing-allu-decisions
  "Fetch allu-verdict:s (AKA /decision:s) from ALLU for placement contracts that should have them in :pate-verdicts
  but don't."
  []
  (infof "Starting fetch-missing-allu-decisions for 091-YA")
  (system-util/with-system default-batchrun-system-keys
    (allu/login!)

    (let [org-id        "091-YA" ; Helsinki YA
          batchrun-user (user/batchrun-user [org-id])
          apps          (domain/get-multiple-applications-no-access-checking
                          {:organization  org-id
                           :permitSubtype "sijoitussopimus"
                           :state         "agreementSigned" ; terminal state for sijoitussopimus
                           ;; Has no allu-verdict:s (which originate from ALLU /decision API):
                           :pate-verdicts {$not {$elemMatch {:category "allu-verdict"}}}})]
      (info "Fetching decisions for " (count apps) " application(s).")
      (doseq [app  apps
              :let [command (assoc (application->command app)
                                   :system (get-system-components)
                                   :user batchrun-user
                                   :created (now)
                                   :action "fetch-allu-contract")]]
        (log/with-logging-context {:applicationId (:id app) :userId (:id batchrun-user)}
          (try
            ;; `fetch-allu-contract` does not produce any duplicate verdicts or weird state changes so we can just:
            (allu-contract/fetch-allu-contract command)
            (catch Exception exn
              (error exn))))))))

(defn do-resubmit-allu-applications
  "Finds every 091-YA application that is in `submitted` state but has not received an ALLU
  id. Each application is resubmitted (ALLU submit request is sent)."
  [system]
  {:pre [(map? system)]}
  (allu/login!)

  (let [org-id        "091-YA" ;; Helsinki YA
        batchrun-user (user/batchrun-user [org-id])
        apps          (->> {:organization            org-id
                            :state                   :submitted
                            :integrationKeys.ALLU.id nil}
                           domain/get-multiple-applications-no-access-checking
                           ;; Just in case
                           (filter allu/allu-application?))]
    (doseq [{app-id :id
             :as    app} apps
            :let         [command (assoc (application->command app)
                                         :system system
                                         :user batchrun-user
                                         :created (now)
                                         :action "resubmit-application")]]
      (log/with-logging-context {:applicationId app-id
                                 :userId        (:id batchrun-user)}
        (try
          (allu/submit-application! command)
          (info "Resubmitted ALLU application.")
          (catch Exception e
            (error e "ALLU resubmit failed.")))))))

(defn resubmit-allu-applications
  "Finds every 091-YA application that is in `submitted` state but has not received an ALLU
  id. Each application is resubmitted (ALLU submit request is sent)."
  []
  (system-util/with-system default-batchrun-system-keys
    (do-resubmit-allu-applications (get-system-components))))

(defn- organization-has-krysp-url-function
  "Takes map of organization id as key and organization data as values.
  Returns function, that takes application and returns true if application's organization
  has krysp url set AND dmcity is NOT enabled for the permit-type."
  [organizations-by-id]
  (fn [{org :organization permit-type :permitType}]
    (let [organization (get organizations-by-id org)
          url          (get-in organization [:krysp (keyword permit-type) :url])]
      (not (or (ss/blank? url)
               (dm-cfg/target-enabled? organization permit-type))))))

(defn- get-valid-applications
  "Returns applications, that have krysp.url set in their organizations permitType.
  Applications need to be filtered, as there might be several permitTypes per organization, but not all have krysp.url set."
  [organizations applications]
  {:pre [(sequential? organizations) (sequential? applications)]}
  (let [orgs-by-id   (util/key-by :id organizations)
        org-has-url? (organization-has-krysp-url-function orgs-by-id)]
    (filter org-has-url? applications)))

(defn fetch-verdicts [system batchrun-name batchrun-user applications & [options]]
  {:pre [(map? system)]}
  (let [start-ts (double (now))]
    (log/log-event :info {:run-by batchrun-name
                          :event  (format "Starting verdict fetching with %s applications" (count applications))})
    (doseq [application applications]
      (fetch-verdict system batchrun-name batchrun-user application options))
    (log/log-event :info {:run-by batchrun-name
                          :event  "Finished verdict checking"
                          :took   (format "%.2f minutes" (/ (- (now) start-ts) 1000 60))})))

(defn fetch-foreman-verdicts [system batchrun-name batchrun-user applications & [options]]
  {:pre [(map? system)]}
  (let [start-ts (double (now))]
    (log/log-event :info {:run-by batchrun-name
                          :event  (format "Starting foreman verdict fetching with %s applications containing foreman documents" (count applications))})
    (doseq [application applications
            document    (:documents application)]
      (fetch-verdict system batchrun-name batchrun-user application (assoc options :foreman-doc-id (:id document))))
    (log/log-event :info {:run-by batchrun-name
                          :event  "Finished foreman verdict checking"
                          :took   (format "%.2f minutes" (/ (- (now) start-ts) 1000 60))})))

(defn application-id-args? [args]
  (every? v/application-id? args))

(defn applications-for-verdicts-fetching [query foreman-verdicts?]
  (mongo/select :applications
                (merge query
                       {:facta-imported {$ne true}
                        :permitType     {$nin ["ARK"]}}
                       (if foreman-verdicts?
                         {:state     {$in states/post-submitted-states}
                          :documents {$elemMatch {:tj-document       true
                                                  "schema-info.name" "tyonjohtaja-v3"
                                                  :state             "declared"}}}
                         {:state :sent}))
                [:state :organization :permitType :documents :pate-verdicts :verdicts :auth]))

(defn fetch-verdicts-by-application-ids [system ids & [foreman-verdicts?]]
  {:pre [(map? system)]}
  (infof "Starting fetch-verdicts-by-application-ids with %s ids" (count ids))
  (let [apps             (applications-for-verdicts-fetching {:_id {$in ids}} foreman-verdicts?)
        distinct-org-ids (into #{} (map :organization) apps)
        orgs             (mongo/select :organizations {:_id {$in distinct-org-ids}} [:krysp :dmcity])
        apps-with-urls   (get-valid-applications orgs apps)
        eraajo-user      (user/batchrun-user distinct-org-ids)
        batchrun-name    "Verdicts checking by application ids"]
    (if fetch-foreman-verdicts
      (fetch-foreman-verdicts system batchrun-name eraajo-user apps-with-urls)
      (fetch-verdicts system batchrun-name eraajo-user apps-with-urls))))

(defn fetch-verdicts-by-org-ids [system ids & [foreman-verdicts?]]
  {:pre [(map? system)]}
  (infof "Starting fetch-verdicts-by-org-ids with %s ids" (count ids))
  (if-let [orgs (seq (org/get-organizations {:_id {$in ids}} [:krysp :dmcity]))]
    (let [applications   (applications-for-verdicts-fetching {:organization {$in ids}} foreman-verdicts?)
          apps-with-urls (get-valid-applications orgs applications)
          batchrun-name  "Verdicts checking by organizations"
          eraajo-user    (user/batchrun-user (map :id orgs))]
      (if foreman-verdicts?
        (fetch-foreman-verdicts system batchrun-name eraajo-user apps-with-urls)
        (fetch-verdicts system batchrun-name eraajo-user apps-with-urls)))
    (warn "No organizations found, exiting.")))

(defn fetch-verdicts-with-args [system args]
  {:pre [(map? system)]}
  (debug "check-for-verdicts started with args" args)
  (if (application-id-args? args)
    (fetch-verdicts-by-application-ids system args)
    (fetch-verdicts-by-org-ids system args)))

(defn fetch-foreman-verdicts-with-args [system args]
  {:pre [(map? system)]}
  (debug "check-for-verdicts started with args" args)
  (if (application-id-args? args)
    (fetch-verdicts-by-application-ids system args true)
    (fetch-verdicts-by-org-ids system args true)))

(defn fetch-verdicts-default [system & [additional-opts]]
  {:pre [(map? system)]}
  (let [permit-types                 [:R :YA :P :MAL :VVVL :YI :YL :KT]
        organizations-with-krysp-url (org/get-organizations
                                       {$or          (for [pt permit-types]
                                                       {(util/kw-path :krysp pt :url)              {$exists true}
                                                        (util/kw-path :dmcity.targets pt :enabled) {$ne true}})
                                        :deactivated {$ne true}}
                                       [:krysp :dmcity])
        org-ids                      (map :id organizations-with-krysp-url)
        apps                         (mongo/select-ordered :applications
                                                           {:state          :sent
                                                            :facta-imported {$ne true}
                                                            :permitType     {$in permit-types}
                                                            :organization   {$in org-ids}}
                                                           {:modified -1})
        apps-with-urls               (get-valid-applications organizations-with-krysp-url apps)
        eraajo-user                  (user/batchrun-user org-ids)
        batchrun-name                "Automatic verdicts checking"]
    (fetch-verdicts system batchrun-name eraajo-user apps-with-urls additional-opts)))

(defn fetch-foreman-verdicts-default [system & [additional-opts]]
  {:pre [(map? system)]}
  (let [permit-types                 [:R :P]
        organizations-with-krysp-url (org/get-organizations
                                       {$or          (for [pt permit-types]
                                                       {(util/kw-path :krysp pt :url)              {$exists true}
                                                        (util/kw-path :dmcity.targets pt :enabled) {$ne true}})
                                        :deactivated {$ne true}}
                                       [:krysp :dmcity])
        org-ids                      (map :id organizations-with-krysp-url)
        apps                         (->> (mongo/select :applications
                                                        {:state          {$in states/post-verdict-states}
                                                         :facta-imported {$ne true}
                                                         :permitType     {$in permit-types}
                                                         :organization   {$in org-ids}
                                                         :documents      {$elemMatch {:tj-document       true
                                                                                      "schema-info.name" "tyonjohtaja-v3"
                                                                                      :state             "approved"}}})
                                          (mapv (fn [item] (update item :documents #(filterv :tj-document %)))))
        apps-with-urls               (get-valid-applications organizations-with-krysp-url apps)
        eraajo-user                  (user/batchrun-user org-ids)
        batchrun-name                "Automatic foreman verdicts checking"]
    (fetch-foreman-verdicts system batchrun-name eraajo-user apps-with-urls additional-opts)))

(defn check-for-verdicts [& args]
  (system-util/with-system default-batchrun-system-keys
    (when-not (system-not-in-lockdown?)
      (log/log-event :info {:run-by "Automatic verdict checking" :event "Not run - system in lockdown"})
      (fail! :system-in-lockdown))
    (if (empty? args)
      (fetch-verdicts-default (get-system-components) {:use-queue? (env/feature? :integration-message-queue)})
      (fetch-verdicts-with-args (get-system-components) args))))

(defn check-for-foreman-verdicts [& args]
  (system-util/with-system default-batchrun-system-keys
    (when-not (system-not-in-lockdown?)
      (log/log-event :info {:run-by "Automatic foreman verdict checking" :event "Not run - system in lockdown"})
      (fail! :system-in-lockdown))
    (if (empty? args)
      (fetch-foreman-verdicts-default (get-system-components) {:use-queue? (env/feature? :integration-message-queue)})
      (fetch-foreman-verdicts-with-args (get-system-components) args))))

(defn check-for-asianhallinta-messages [& _]
  (system-util/with-system default-batchrun-system-keys
    (when-not (system-not-in-lockdown?)
      (log/log-event :info {:run-by "Asianhallinta reader" :event "Not run - system in lockdown"})
      (fail! :system-in-lockdown))
    (sftp/process-case-management-responses (get-system-components))))

(defn orgs-for-review-fetch
  ([{:keys [organization-ids dmcity-allowed?]}]
   (mongo/select :organizations
                 (merge {:deactivated     {$ne true}
                         :krysp.R.url     {$exists true}
                         :krysp.R.version {$gte "2.1.5"}}
                        (when-not dmcity-allowed?
                          {:dmcity.targets.R.enabled {$ne true}})
                        (if (seq organization-ids)
                          {:_id {$in organization-ids}}
                          {:automatic-review-fetch-enabled true}))
                 [:krysp]))
  ([] (orgs-for-review-fetch nil)))

(defn- save-reviews-for-application [system user created application {:keys [updates added-tasks-with-updated-buildings attachments-by-ids] :as result}]
  (let [{:keys [only-use-inspection-from-backend
                assignments-enabled]} (org/get-organization (:organization application)
                                                            [:only-use-inspection-from-backend
                                                             :assignments-enabled])]
    (log/with-logging-context {:applicationId (:id application) :userId (:id user)}
      (when (ok? result)
        (try
          (let [result (review/save-review-updates (assoc (application->command application)
                                                          :user user
                                                          :created created
                                                          :system system)
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
      (let [{:keys                      [review-count
                    all-tasks
                    validation-errors
                    attachments-by-ids] :as result} (review/read-reviews-from-xml user created application asia-xml opts)]
        (cond
          (and (ok? result) (pos? review-count)) (log/log-event :info {:run-by    "Automatic review checking"
                                                                       :event     "Reviews found"
                                                                       :all-tasks (->> all-tasks
                                                                                       (mapv (juxt identity #(count (get attachments-by-ids %)))))})
          (ok? result)                           (log/log-event :info {:run-by "Automatic review checking"
                                                                       :event  "No reviews"})
          (fail? result)                         (log/log-event :error {:run-by            "Automatic review checking"
                                                                        :event             "Failed to read reviews"
                                                                        :text              (:text result)
                                                                        :validation-errors validation-errors}))
        result))
    (catch Throwable e
      (log/log-event :error {:run-by         "Automatic review checking"
                             :event          "Error while reading reviews"
                             :application-id (:id application)
                             :exception-msg  (str (get-throw-context e))})
      (errorf e "error.integration - Could not read reviews for %s" (:id application)))))

(defn fetch-reviews-for-organization-permit-type [organization permit-type applications]
                                        ; NOTE! applications have only :id, :permitType and :verdicts keys
  (try+
    (log/log-event :info {:run-by            "Automatic review checking"
                          :event             "Start fetching xmls"
                          :organization-id   (:id organization)
                          :permit-type       permit-type
                          :application-count (count applications)
                          :applications      (map :id applications)})

    (krysp-fetch/fetch-xmls-for-applications organization permit-type applications)
    ;; The usual exceptions are handled in the chunk by trying to fetch the applications individually
    ;; so this should only catch significant errors that are unlikely to be limited to a single chunk
    ;; e.g. a coding error
    (catch Throwable o
      (log/log-event :error {:run-by          "Automatic review checking"
                             :organization-id (:id organization)
                             :permit-type     permit-type
                             :exception       (.getName (class o))
                             :message         (get &throw-context :message "")
                             :event           (format "Unable to get reviews in chunks from %s backend: %s - %s"
                                            (:id organization) (.getName (class o)) (get &throw-context :message ""))})
      (warn "Error in automatic review checking:" (get &throw-context :message "") "stack trace:" (ss/join " | " (.getStackTrace o))))))

(defn- organization-applications-for-review-fetching
  ([organization-id permit-type projection]
   (organization-applications-for-review-fetching organization-id permit-type projection {}))
  ([organization-id permit-type projection {:keys [application-ids include-closed?]}]
   (let [eligible-application-states (cond-> (difference states/post-verdict-but-terminal #{:foremanVerdictGiven})
                                       include-closed? (conj :closed))]
     (domain/get-multiple-applications-no-access-checking
       (merge {:state                 {$in eligible-application-states}
               :permitType            permit-type
               :organization          organization-id
               :facta-imported        {$ne true}
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
  (log/log-event :info {:run-by            "Automatic review checking"
                        :event             "Review checking finished for organization"
                        :organization-id   organization-id
                        :application-count (count save-results)
                        :save-results      (into {} save-results)})
  save-results)

(defn read-and-save-reviews-from-xml [system eraajo-user created app overwrite-background-reviews? asia-xml]
  (log/with-logging-context {:applicationId (:id app) :userId (:id eraajo-user)}
    (let [result      (read-reviews-for-application eraajo-user created app asia-xml {:overwrite-background-reviews?
                                                                                      overwrite-background-reviews?
                                                                                      :skip-task-validation?
                                                                                      (or (app/previous-permit? app)
                                                                                          (:_skip-task-validation app))})
          save-result (save-reviews-for-application system eraajo-user created app result)]
      ;; save-result is nil when application query fails (application became irrelevant for review fetching) -> no actions taken
      (when (fail? save-result)
        (log/log-event :info {:run-by         "Automatic review checking"
                              :event          "Failed to save review updates for application"
                              :reason         (:desc save-result)
                              :application-id (:id app)
                              :result         result}))
      (when (ok? save-result)
        (mark-reviews-faulty-for-application app result))
      save-result)))

(defn- fetch-reviews-for-organization
  [system eraajo-user created {org-krysp :krysp :as organization} permit-types applications {:keys [overwrite-background-reviews?]}]
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
                         (keep #(read-and-save-reviews-from-xml system eraajo-user created app overwrite-background-reviews? %))
                         (vector app-id))))))
         (remove nil?)
         (log-review-results-for-organization (:id organization)))))

(defn poll-verdicts-for-reviews
  "Fetches (verdict) KuntaGML messages from the backing systems and updates applications'
  reviews, if needed. Only R applications are supported."
  [system & {:keys [application-ids organization-ids] :as options}]
  {:pre [(map? system)]}
  (let [applications  (when (seq application-ids)
                        (or (seq (domain/get-multiple-applications-no-access-checking
                                   {:_id        {$in application-ids}
                                    :permitType :R}))
                            (throw (ex-info "No suitable applications found." {:ids application-ids}))))
        permit-types  [:R]
        organizations (orgs-for-review-fetch
                        (util/assoc-when {}
                                         :organization-ids (some->> (map :organization applications)
                                                                    (concat organization-ids)
                                                                    distinct)
                                         :dmcity-allowed? (boolean applications)))
        eraajo-user   (user/batchrun-user (map :id organizations))
        threadpool    (threads/threadpool (util/->int (env/value :batchrun :review-check-threadpool-size) 8) "review checking worker")]
    (try
      (let [threads (mapv (fn [org]
                            (threads/submit
                              threadpool
                              (try
                                (fetch-reviews-for-organization system eraajo-user (now) org permit-types
                                                                (filter (comp #{(:id org)} :organization)
                                                                        applications) options)
                                (catch Throwable t
                                  (log/log-event :info {:run-by          "Automatic review checking"
                                                        :event           "Failed to check reviews for organization"
                                                        :organization-id (:id org)
                                                        :reason          (.getMessage t)})))))
                          organizations)]
        (threads/wait-for-threads threads))
      (finally
        (.shutdown threadpool)))))

(defn check-for-reviews [& _]
  (system-util/with-system default-batchrun-system-keys
    (when-not (system-not-in-lockdown?)
      (log/log-event :info {:run-by "Automatic review checking" :event "Not run - system in lockdown"})
      (fail! :system-in-lockdown))
    (log/log-event :info {:run-by "Automatic review checking" :event "Started"})
    (poll-verdicts-for-reviews (get-system-components))
    (log/log-event :info {:run-by "Automatic review checking" :event "Finished"})))

(defn check-reviews-for-orgs [& args]
  (system-util/with-system default-batchrun-system-keys
    (when-not (system-not-in-lockdown?)
      (log/log-event :info {:run-by "Automatic review checking" :event "Not run - system in lockdown"})
      (fail! :system-in-lockdown))
    (log/log-event :info {:run-by "Automatic review checking" :event "Started" :organizations args})
    (poll-verdicts-for-reviews (get-system-components) :organization-ids args)
    (log/log-event :info {:run-by "Automatic review checking" :event "Finished" :organizations args})))

(defn overwrite-reviews-for-orgs [& args]
  (when (empty? args)
    (log/log-event :info {:run-by "Review checking with overwrite" :event "Not run - no organizations specified"})
    (fail! :no-organizations))
  (system-util/with-system default-batchrun-system-keys
    (when-not (system-not-in-lockdown?)
      (log/log-event :info {:run-by "Review checking with overwrite" :event "Not run - system in lockdown"})
      (fail! :system-in-lockdown))
    (log/log-event :info {:run-by "Review checking with overwrite" :event "Started" :organizations args})
    (poll-verdicts-for-reviews (get-system-components)
                               :organization-ids args
                               :overwrite-background-reviews? true)
    (log/log-event :info {:run-by "Review checking with overwrite" :event "Finished" :organizations args})))

(defn check-reviews-for-ids
  "Note: updates also closed applications."
  [& args]
  (system-util/with-system default-batchrun-system-keys
    (when-not (system-not-in-lockdown?)
      (log/log-event :info {:run-by "Automatic review checking" :event "Not run - system in lockdown"})
      (fail! :system-in-lockdown))
    (log/log-event :info {:run-by "Automatic review checking" :event "Started" :applications args})
    (poll-verdicts-for-reviews (get-system-components) :application-ids args)
    (log/log-event :info {:run-by "Automatic review checking" :event "Finished" :applications args})))

(defn pdfa-convert-review-pdfs [& _]
  (system-util/with-system default-batchrun-system-keys
    (debug "# of applications with background generated tasks:"
           (mongo/count :applications {:tasks.source.type "background"}))
    (let [eraajo-user (user/batchrun-user (map :id (orgs-for-review-fetch)))]
      (doseq [application (mongo/select :applications {:tasks.source.type "background"})]
        (let [command (assoc (application->command application) :user eraajo-user :created (now))]
          (doseq [task (:tasks application)]
            (when (= "background" (:type (:source task)))
              (doseq [att (:attachments application)]
                (when (= (:id task) (:id (:source att)))
                  (debug "application" (:id (:application command)) "- converting task" (:id task) "-> attachment" (:id att))
                  (attachment/convert-existing-to-pdfa! (get-system-components) (:application command) att))))))))))

(defn pdf-to-pdfa-conversion
  "Organization is the organization id string (e.g., 980-YA),
  dates are can be given in any `date/zoned-date-time` supported format."
  [organization start-date end-date]
  (let [{:keys [start-ts
                end-ts]} (sc/validate (sc/constrained {:start-ts     ssc/Timestamp
                                                       :end-ts       ssc/Timestamp
                                                       :organization ssc/NonBlankStr}
                                                      #(< (:start-ts %) (:end-ts %))
                                                      "Start date must be before the end date.")
                                      {:start-ts     (date/timestamp start-date)
                                       :end-ts       (date/timestamp end-date)
                                       :organization organization})]
    (system-util/with-system default-batchrun-system-keys
      ;; Conversion with LibreOffice is error prone, disable it for this run
      (env/disable-feature! :convert-pdfs-with-libre)
      (info "Starting pdf to pdf/a conversion for" organization "from" start-date "to" end-date)
      (doseq [application (->> (mongo/with-collection "applications"
                                 (monger-query/find {:organization       organization
                                                     :permitType         {$nin ["ARK"]}
                                                     :archived.completed nil
                                                     $or                 [{:submitted {$gte start-ts $lte end-ts}}
                                                                          {:submitted nil
                                                                           :created   {$gte start-ts $lte end-ts}}]})
                                 (monger-query/sort {:submitted 1}))
                               (map mongo/with-id))]
        (log/with-logging-context {:applicationId (:id application)}
          (info "Checking attachments of application" (:id application))
          (doseq [{:keys [latestVersion] :as attachment} (:attachments application)]
            (when (and latestVersion (not (:archivable latestVersion)))
              (info "Trying to convert attachment" (:filename latestVersion))
              ;; Re-fetch application to minimize errors because of it changing during processing
              (let [{:keys [attachments] :as application} (mongo/by-id :applications (:id application))
                    attachment                            (first (filter #(= (:id attachment) (:id %)) attachments))
                    result                                (attachment/convert-existing-to-pdfa! (get-system-components) application attachment)
                    log-message                           {:application-id (:id application)
                                                           :attachment-id  (:id attachment)
                                                           :type           (:type attachment)
                                                           :filename       (:filename latestVersion)}]
                (if (:archivable result)
                  (log/log-event :info (merge {:run-by "Batch PDF/A conversion run"
                                               :event  "Successfully converted attachment to PDF/A"}
                                              log-message))
                  ;; Conversion failed or just skipped (e.g., org has allowed archiving TIFF/JPEG as-is).
                  (log/log-event :error (merge {:run-by              "Batch PDF/A conversion run"
                                                :event               "Attachment conversion to PDF/A failed"
                                                :archivability-error (:archivabilityError result)}
                                               log-message)))))))))))

(defn generate-missing-neighbor-attachments
  "Generates the missing neighbor hearing attachments
  Organization is the organization id string (e.g., 980-YA)"
  [& args]
  (system-util/with-system default-batchrun-system-keys
    (let [{dry-run?      true
           organizations false} (group-by #(= % "-dry-run") args)
          enriched-neighbors    (group-by :app-id (repair/neighbors-with-missing-attachments organizations))
          orgs                  (->> enriched-neighbors
                                     vals
                                     (apply concat)
                                     (keep :org)
                                     distinct)
          eraajo-user           (user/batchrun-user orgs)
          note-ts               (now)]
      (when-not dry-run?
        (doseq [application (keys enriched-neighbors)]
          (mongo/update-by-id :applications
                              application
                              {$push {:_sheriff-notes {:note    "Batchrun: generate-missing-neighbor-attachments"
                                                       :created note-ts}}})
          (doseq [{:keys [app-id neighbor-id]} (get enriched-neighbors application)
                  :when                        (and (seq enriched-neighbors) (not dry-run?))]
            (child-to-attachment/create-attachment-from-children
              (get-system-components)
              eraajo-user
              (domain/get-application-no-access-checking app-id)
              :neighbors
              neighbor-id
              "fi"))))
      (if (seq enriched-neighbors)
        (if dry-run?
          (info "Neighbor hearing attachments missing from applications: " (->> enriched-neighbors keys (ss/join ",")))
          (info "Neighbor hearing attachments successfully generated"))
        (info "No missing neighbor hearing attachments found")))))

(defn- do-check-for-verdict-attachments [options]
  (try
    (verdict/valid-missing-options! options)
    (system-util/with-system default-batchrun-system-keys
      (when-not (system-not-in-lockdown?)
        (log/log-event :info {:run-by "Automatic verdict attachment checking"
                              :event  "Not run - system in lockdown"})
        (fail! :system-in-lockdown))
      (let [result (fetch-verdict/fetch-verdict-attachments (get-system-components) options)]
        ;; Result returned for the testing purposes
        result))
    (catch Throwable t
      (error "Bad options for fetching missing verdict attachments:" (ex-message t)))))

(defn check-for-verdict-attachments
  "Fetch missing verdict attachments for verdicts given in the time
  interval between start-timestamp and end-timestamp (last 3 months by
  default), for the organizations whose id's are provided as
  arguments (all organizations by default)."
  [& [start-timestamp end-timestamp & organizations]]
  (-> {:start         (date/timestamp (or start-timestamp
                                          (date/minus (date/today) :months 3)))
       :end           (date/timestamp (or end-timestamp (date/now)))
       :organizations (seq organizations)}
      util/strip-nils
      do-check-for-verdict-attachments))

(defn check-for-verdict-attachments-for-ids
  "Fetch missing verdict attachments for the given applications."
  [& application-ids]
  (do-check-for-verdict-attachments {:application-ids (distinct application-ids)}))

(defn unarchive [& [organization application-id]]
  (if organization
    (system-util/with-system default-batchrun-system-keys
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
        (log/with-logging-context {:applicationId (:id application)}
          (info "Checking attachments of application" (:id application))
          (let [results (pmap
                          (fn [{:keys [latestVersion metadata] :as attachment}]
                            (if (and latestVersion
                                     (= (keyword (get-in attachment [:type :type-group])) :erityissuunnitelmat)
                                     (= (keyword (:tila metadata)) :arkistoitu))
                              (let [_                     (info "Trying to unarchive attachment" (:id attachment) "/" (:filename latestVersion))
                                    host                  (env/value :arkisto :host)
                                    app-id                (env/value :arkisto :app-id)
                                    app-key               (env/value :arkisto :app-key)
                                    encoded-id            (codec/url-encode (:id attachment))
                                    url                   (str host "/documents/" encoded-id)
                                    {:keys [status body]} (http/delete url {:basic-auth       [app-id app-key]
                                                                            :throw-exceptions false
                                                                            :as               :json
                                                                            :query-params     {"organization" organization}})
                                    log-message           {:application-id (:id application)
                                                           :attachment-id  (:id attachment)
                                                           :type           (:type attachment)
                                                           :filename       (:filename latestVersion)}]
                                (if (= 200 status)
                                  (log/log-event :info (merge {:run-by "Batch unarchiving run"
                                                               :event  "Successfully unarchived attachment"}
                                                              log-message))
                                  (log/log-event :error (merge {:run-by       "Batch unarchiving run"
                                                                :event        "Attachment unarchiving failed"
                                                                :onkalo-error body}
                                                               log-message)))
                                (= 200 status))
                              true))
                          (:attachments application))]
            (if (every? true? results)
              (info "Attachments successfully unarchived for application" (:id application))
              (error "Some attachments were not successfully unarchived for application" (:id application)))))))
    (error "Organization must be provided.")))

(defn fix-bad-archival-conversions-in-091-R []
  (system-util/with-system default-batchrun-system-keys
    (info "Reconverting attachments to PDF/A in 091-R archiving projects")
    (doseq [{:keys [id]} (mongo/select :applications
                                       {:organization "091-R"
                                        :permitType   "ARK"
                                        ; 2018-07-12 09:00 Z
                                        :created      {$gt 1531386000000}
                                        :state        {$in ["open" "underReview"]}}
                                       [:_id]
                                       {:_id 1})]
      (log/with-logging-context {:applicationId id}
        (doseq [{:keys [latestVersion versions metadata]
                 :as   att} (:attachments (mongo/by-id :applications id [:attachments]))
                :when       (and (not= :arkistoitu (keyword (:tila metadata)))
                                 (or (and (:archivable latestVersion)
                                          (not= (:fileId latestVersion) (:originalFileId latestVersion)))
                                     (and (not (:archivable latestVersion))
                                          (= (:archivabilityError latestVersion) "invalid-pdfa"))))]
          (info "Reconverting attachment" (:id att))
          (let [last-idx (dec (count versions))]
            (mongo/update :applications
                          {:_id         id
                           :attachments {$elemMatch {:id (:id att)}}}
                          {$set {:attachments.$.latestVersion.archivable                false
                                 :attachments.$.latestVersion.fileId                    (:originalFileId latestVersion)
                                 (str "attachments.$.versions." last-idx ".archivable") false
                                 (str "attachments.$.versions." last-idx ".fileId")     (:originalFileId latestVersion)}})
            (let [updated-app (mongo/by-id :applications id)
                  attachment  (attachment/get-attachment-info updated-app (:id att))]
              (attachment/convert-existing-to-pdfa! (get-system-components) updated-app attachment)))
          (info "Attachment" (:id att) "processed"))))
    (info "Done.")))

(defn fix-file-links
  "For each given application, checks every attachment version for unlinked file-ids and
  links them to the application."
  [& application-ids]
  (system-util/with-system default-batchrun-system-keys
    (doseq [application (mongo/select :applications
                                      {:_id {$in application-ids}}
                                      [:attachments.versions])]
      (storage/fix-file-links application))))

(defn archive-digitized-projects-in-org [& [start-timestamp end-timestamp organization digitizer]]
  (if (and start-timestamp end-timestamp organization)
    (system-util/with-system default-batchrun-system-keys
      (info "Archiving digitized projects (ARK/LX) for organizations:" organization "from" start-timestamp "until" end-timestamp)
      (let [from      (date/timestamp start-timestamp)
            until     (date/timestamp end-timestamp)
            query     (merge
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
                                                    :system (get-system-components)
                                                    :created (now))
                                                  "Menettely asetettu arkistointia varten."))
                       (info "Archiving" (:id app))
                       (let [app (mongo/by-id :applications (:id app))]
                         (archiving/send-to-archive (-> app
                                                        (application->command (user/batchrun-user [organization]))
                                                        (merge {:created      (now)
                                                                :system       (get-system-components)
                                                                :organization (delay (mongo/by-id :organizations organization))}))
                                                    (->> (:attachments app)
                                                         (filter #(-> % :metadata :tila (not= "arkistoitu")))
                                                         (map :id)
                                                         set)
                                                    #{}
                                                    #{}))))
             (threads/wait-for-threads))
                                        ; Post-archiving threads may be running long after and we don't have a handle to them
        (info "Waiting" app-count "seconds for post-archiving jobs.")
        (Thread/sleep ^long (* app-count 2 1000))))
    (error "Need to provide start date, end date and organization. (And optionally digitizer's email.)"))
  (info "Done."))

(defn rescue-orphaned-files
  "Check if a file is missing from the correct storage and copy from any other storage, if found there"
  [& [application-modified-since]]
  (if application-modified-since
    (let [modified-since (Long/parseLong application-modified-since)]
      (system-util/with-system default-batchrun-system-keys
        (storage-move-util/rescue-files-from-wrong-storage modified-since)))
    (error "Application modified-since must be provided as milliseconds")))

(defn vantaa-statements-from-excel
  "Update (previously converted) Vantaa application statements with information from the
  provided Excel file."
  [filename]
  (system-util/with-system default-batchrun-system-keys
    (vantaa/statements-from-excel filename)))

(defn- julkipano-ts-in-past? [now-ts pate-verdict]
  (if-let [ts (or (metadata/unwrap (get-in pate-verdict [:data :julkipano]))
                  (metadata/unwrap (get-in pate-verdict [:data :k-kuulutus])))]
    (< ts now-ts)
    (do
      (log/log-event
        :warn
        {:event  "No julkipano/kuulutus timestamp available"
         :run-by "Scheduled verdict batchrun"})
      (warn "No julkipano/kuulutus date set for scheduled verdict"))))

(defn- verdicts-scheduled-for-today [now-ts {:keys [pate-verdicts]}]
  (->> pate-verdicts
       (filter pv/scheduled?)
       (filter (partial julkipano-ts-in-past? now-ts))))

(defn scheduled-verdict-publishing []
  ;; We use the actual publish command, so we need to register
  ;; that command in order to invoke it or else we get ':error.invalid-command'.
  (require 'lupapalvelu.pate.verdict-api)
  (system-util/with-system default-batchrun-system-keys
    (let [now-ts        (now)
          verdict-query {:state                      {$in states/all-application-states-but-draft-or-terminal}
                         :pate-verdicts.state._value "scheduled"}
          check-user    (fn [user]
                          (if user
                            true
                            (do
                              (log/log-event
                                :error
                                {:event  "No user found from verdict state metadata"
                                 :run-by "Scheduled verdict batchrun"})
                              (error "No user found from verdict state metadata"))))]
      (doseq [application (mongo/select :applications verdict-query)
              verdict     (verdicts-scheduled-for-today now-ts application)
              :let        [user (some-> {:username (metadata/get-username (:state verdict))}
                                        (user/get-user)
                                        (user/session-summary)
                                        (assoc :id (:id user/batchrun-user-data)))]
              :when       (check-user user)]
        (log/with-logging-context
          {:applicationId (:id application)
           :userId        (:id user)}
          (try
            (info "Publishing scheduled pate verdict" (:id verdict))
            (let [resp (execute-command
                         "publish-pate-verdict"
                         {:id         (:id application)
                          :verdict-id (:id verdict)}
                         {:user        user
                          :system      (get-system-components)
                          :scheme      "cronjob"
                          :remote-addr "localhost"
                          :headers     {"user-agent" "Lupapiste Eräajo"
                                        "host"       "localhost"}})]
              (when-not (ok? resp)
                (log/log-event
                  :error
                  {:event  "Scheduled publishing was not :ok"
                   :run-by "Scheduled verdict batchrun"
                   :resp   resp})
                (error "Scheduled publishing failed:" resp)))
            (catch Exception e
              (log/log-event
                :error
                {:event  "Unknown exception when publishing verdict"
                 :run-by "Scheduled verdict batchrun"
                 :error  (str e)})
              (error e "Failure when publishing scheduled verdict, moving to next one (if any)..."))))))))

(defn kuntagml-export-run
  "Export the KuntaGML XML and attachment files for the whole organization.
  Useful when a municipality leaves Lupapiste"
  [organization-id query & [limit]]
  (system-util/with-system default-batchrun-system-keys
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
              :let    [post-processed (as-krysp/post-process-app-for-krysp app org)]]
        (log/with-logging-context
          {:applicationId (:id app)
           :userId        (:id batchrun-user)}
          (try
            (log/log-event :debug {:event "Start export" :run-by "KuntaGML export batchrun"})
            (as-krysp/full-application-export-as-kuntagml {:application  post-processed
                                                           :organization org
                                                           :user         batchrun-user
                                                           :system       (get-system-components)})
            (log/log-event :debug {:event "Finished export" :run-by "KuntaGML export batchrun"})
            (swap! success conj (:id app))
            (when (zero? (rem i 10))
              (printf "%,d / %,d applications processed\r" (inc i) app-count)
              (flush))
            (catch Exception e
              (error "Exception when exporting KuntaGML" (.getMessage e))
              (log/log-event
                :error
                {:event  (str "Exception when writing KuntaGML for org " organization-id)
                 :run-by "KuntaGML export batchrun"
                 :error  (str e)})))))
      (let [ids (count @success)]
        (info (format "Successfully exported %d applications" ids))
        (log/log-event
          :info
          {:event  (format "Successfully exported %d applications" ids)
           :run-by "KuntaGML export batchrun"})))))

(defn- generic-router
  "Passes arguments the correct conversion command or shows help text."
  [{:keys [parse-fn exec-fn]} & args]
  (let [{inf :info err :error
         :as command} (apply parse-fn args)]
    (cond
      inf   (info inf)
      err   (error err)
      :else (system-util/with-system default-batchrun-system-keys
              (exec-fn (assoc command :system (get-system-components)))))))

(def conversion-router (partial generic-router
                                {:parse-fn conv-cli/parse-conversion-command-line
                                 :exec-fn  conv/execute-conversion-command}))

(def other-router (partial generic-router
                           {:parse-fn conv-cli/parse-other-command-line
                            :exec-fn  other/execute-other-command}))

(defn attachments-export-batchrun
  "Collects the attachments of a given organization into a zip file along with
  an Excel spreadsheet with the metadata for all the attachments."
  [lang organization-id]
  (system-util/with-system default-batchrun-system-keys
    (attachments-export/export-attachments lang organization-id)))

(defn do-review-cleanup
  [& args]
  (let [{:keys [targets dry-run?]
         :as   cfg} (apply repair/initialize-cleanup args)]
    (doseq [[org-id apps] targets
            :let          [organization (org/get-organization org-id)]]
      (doseq [[application xmls] (fetch-reviews-for-organization-permit-type organization
                                                                             "R"
                                                                             apps)
              :let               [bad-task-ids (repair/find-bad-reviews cfg application xmls)]
              :when              (and bad-task-ids (not dry-run?))]
        (mark-reviews-faulty-for-application application {:new-faulty-tasks bad-task-ids})))))

(defn review-cleanup
  "Marks faulty those reviews that do not belong to the application. Typical scenario is
  that a backing system has mixed up different applications. A review is marked faulty if
  it has been received from the backing system earlier, but is no longer in the
  application KuntaGML message AND is modified within the given time period.

  The application criteria is read from an EDN file (see `repair/RepairConfiguration`). If
  a parameter `-dry-run` is given, the applications are analyzed, the bad reviews logged,
  but no actual changes to Mongo is done."
  [& args]
  (system-util/with-system default-batchrun-system-keys
    (apply do-review-cleanup args)))

(defn do-fix-verdict-dates
  [& args]
  (repair/fix-verdict-dates args))

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
  (system-util/with-system default-batchrun-system-keys
    (apply do-fix-verdict-dates args)))

(defn fix-missing-statements
  "Earlier, the statement was marked given even if the statement.pdf generation/upload
  failed. This batchrun checks the statements for the application `application-id` and
  generates attachments if needed (as a batchrun user)."
  [application-id]
  (system-util/with-system default-batchrun-system-keys
    (statement/fix-missing-statement-attachments (get-system-components) application-id)))

(defn update-ad-login-certs
  "Fetches the current certificates for those AD login configurations which have the Federation Metadata URL."
  [& _]
  (system-util/with-system default-batchrun-system-keys
    (doseq [ad-login-conf (mongo/select :ad-logins {:idp-cert-url {$ne nil}} [:id :idp-cert-url])]
      (ad-login/update-certificates-from-url! ad-login-conf))))

(defn pate-backend-ids
  "Fetches backend-ids (`kuntalupatunnus`, `vrk-lupatunnus`) for Pate
  applications. Supported (mutually exclusive) arguments:

  - Organization ids. Processing is limited to the Pate-enabled active organizations.
  - Application ids. Applications are updated, if needed and supported by their organizations.
  - No arguments. Every suitable organization is processed."
  [& args]
  (system-util/with-system default-batchrun-system-keys
    (apply pate/backend-ids-update args)))

(defn push-building-data-to-onkalo [& args]
  (system-util/with-system default-batchrun-system-keys
    (when-not (system-not-in-lockdown?)
      (log/log-event :info {:run-by "Push building data to Onkalo" :event "Not run - system in lockdown"})
      (fail! :system-in-lockdown))
    (log/with-logging-context {:userId "batchrun-user"}
      (let [organization     (first args)
            building-vtjprts (-> args rest seq)]
        (log/log-event :info {:run-by "Push building data to Onkalo" :event "Started" :organizations organization})

        (let [buildings      (ba/fetch-buildings organization {:vtjprts building-vtjprts})
              building-count (count buildings)]
          (infof "Push building data to Onkalo - Organization: %s, buildings: %d" organization building-count)
          (doseq [[i {:keys [id vtjprt]}] (map-indexed vector buildings)]
            (infof "Push building data to Onkalo - Organization: %s, Building: %d/%d, ID: %s VTJ-PRT: %s  " organization (inc i) building-count id vtjprt)
            (ba/update-in-archive! organization [id])))
        (log/log-event :info {:run-by "Push building data to Onkalo" :event "Finished" :organizations organization})))))

(defn rami-campaign
  "Starts/ends Rami campaign. When started, the target organizations and users are given
  Rami 2.0 access. When ended, the changes are rolled back."
  [& args]
  (system-util/with-system default-batchrun-system-keys
    (apply rami/campaign (get-system-components) args)))

(defn process-notices
  "Processes notices (e.g., purkamisilmoitus). Approves notices that have not been processed
  in thirty days."
  []
  (system-util/with-system default-batchrun-system-keys
    (log/with-logging-context
      {:userId   "batchrun-user"
       :batchrun "process-notices"}
      (infof "Approved %d notice resolutions."
             (notices-batchrun/process-due-demolition-notices (get-system-components))))))

(defn process-statistics
  "Process application statistics (e.g., processing duration)."
  []
  (system-util/with-system default-batchrun-system-keys
    (log/with-logging-context
      {:userId   "batchrun-user"
       :batchrun "process-statistics"}
      (infof "Updated %d processing statistics."
             (stats-db/processing-batchrun)))))

(defn merge-org
  [& args]
  (system-util/with-system default-batchrun-system-keys
    (log/with-logging-context {:userId   "batchrun-user"
                               :batchrun "merge-org"}
      (merge-org/batchrun args))))
