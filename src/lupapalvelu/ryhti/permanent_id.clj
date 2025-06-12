(ns lupapalvelu.ryhti.permanent-id
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.asiointi.control-case :as control-case]
            [lupapalvelu.integrations.pubsub :as lip]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.ryhti.api.ryhti-api :as ryhti-api]
            [lupapalvelu.system.util-components :as components]
            [sade.core :refer [fail]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]))

(def context {::dev-mode?            (env/dev-mode?)
              ::fetch-ryhti-plt?     (env/feature? :fetch-ryhti-plt)
              ::generate-random-plt? (env/feature? :generate-random-plt)})

(defn- get-app
  [application-id]
  (mongo/by-id :applications
               application-id
               [:municipality :organization :permitType
                :primaryOperation :ryhti :ryhti-data-2026]))

(defn- get-organization
  [organization-id]
  (mongo/select-one :organizations
                    {:_id organization-id}
                    [:ryhti-api-configuration]))

(defn- application-applicable?
  [{{op-name :name} :primaryOperation
    :keys           [permitType
                     ryhti-data-2026]
    :as             app}]
  (boolean
    (and
      ;; Suitable permit type
      (util/includes-as-kw? permit/asiointi-api-permit-types permitType)
      ;; Application belongs to Ryhti
      ryhti-data-2026
      ;; No permanent identifier yet?
      (nil? (ss/blank-as-nil (get-in app [:ryhti :permanentPermitIdentifier])))
      ;; We have a control case supported by Ryhti
      (-> (operations/operation-name->control-case op-name)
          control-case/supports-permanent-permit-id?))))

(defn- can-fetch-id?
  [{::keys [fetch-ryhti-plt?]} organization municipality]
  (and fetch-ryhti-plt?
       (ryhti-api/organization-has-config? organization municipality)))

(defn- generate-random-id?
  [{::keys [dev-mode? generate-random-plt?] :as ctx} organization municipality]
  (or
    ;; Always generate when running locally, never try to fetch ID
    dev-mode?
    ;; Only generate as a fallback, but prefer using real
    ;; credentials if possible.
    (and generate-random-plt?
         (not (can-fetch-id? ctx organization municipality)))))

(defn id-strategy
  [ctx organization municipality]
  (cond
    (generate-random-id? ctx organization municipality) ::random
    (can-fetch-id? ctx organization municipality)       ::fetch))

(defn- organization-applicable?
  "Check if this organization can somehow establish a permanent id for an application"
  [ctx organization municipality]
  (some? (id-strategy ctx organization municipality)))

(defn permanent-id-required?
  [{::keys [fetch-ryhti-plt?] :as ctx} organization {:keys [municipality] :as application}]
  (and fetch-ryhti-plt?
       application
       organization
       (organization-applicable? ctx organization municipality)
       (application-applicable? application)))

(defn permanent-id-prechecker
  "Pre-check that fails if the applicable application has no permanent id (yet)."
  [{:keys [application organization]}]
  (when (permanent-id-required? context (force organization) application)
    (fail :error.permanent-id-required)))

(defn- get-and-save-permanent-permit-id!
  "Get a permanent permit id from Ryhti if the application doesn't have one yet."
  [ctx application-id]
  (if-let [{:keys           [municipality]
            {op-name :name} :primaryOperation
            organization-id :organization
            :as             app} (get-app application-id)]
    ;; NOTE: API call already returns the existing ID if requested previously with this app-id
    ;; NOTE: We use the current Ryhti control case for this application, even though
    ;; the applicant might alter the operations later. However, Ryhti has said that
    ;; it does not matter, the same PLT can be used for the application even
    ;; if the operations change.
    (let [organization (get-organization organization-id)]
      (if (permanent-id-required? ctx organization app)
        (if-let [new-id (case (id-strategy ctx organization municipality)
                          ::random (do
                                     (log/info "Generating random permanent permit identifier..")
                                     (str (random-uuid)))
                          ::fetch  (some-> (ryhti-api/get-permanent-permit-id! organization-id
                                                                               municipality
                                                                               application-id
                                                                               (-> (operations/operation-name->control-case op-name)
                                                                                   control-case/control-case->ryhti-control-case))
                                           :permanentPermitIdentifier))]
          (do (log/infof "Received Ryhti Permanent Permit Identifier, %s" new-id)
              (action/update-application (action/application->command app)
                                         {:_id application-id}
                                         {:$set {:ryhti.permanentPermitIdentifier new-id}})
              true)
          (do
            (log/warnf "Could not fetch permanent identifier, retrying later.")
            false))
        (do
          (log/infof "Application not suitable, ignoring.")
          true)))
    (do
      (log/warnf "Could not find application, ignoring.")
      true)))

(defn- handle-pubsub-message
  [_system {:keys [application-id]}]
  ;; Possible test database must be bound for the Mongo query to work
  (log/with-logging-context
    {:applicationId application-id}
    (try
      (get-and-save-permanent-permit-id! context application-id)
      (catch Exception ex
        (log/error ex "Exception when processing message!")
        false))))

(defmethod lip/get-pubsub-subscription-handler :ryhti/permanent-id
  [_id]
  handle-pubsub-message)

(defn trigger-ryhti-id-fetching
  "When a new application is created, we must acquire a permanent permit identifier from Ryhti."
  [{:keys [system] :as command} {:keys [id]}]
  (let [{::keys [fetch-ryhti-plt? dev-mode?] :as ctx} context]
    (when fetch-ryhti-plt?
      (log/with-logging-context
        {:applicationId id}
        (when-let [application (get-app id)]
          (let [organization (get-organization (:organization application))]
            (when (permanent-id-required? ctx organization application)
              (let [message   {:message-id     (str (random-uuid))
                               :application-id (:id application)}
                    publisher (components/command->publisher command :ryhti/permanent-id)]
                (cond
                  ;; Process message synchronously so that tests don't have to
                  ;; wait between create and submit.
                  dev-mode?
                  (handle-pubsub-message system message)

                  ;; Use a pubsub queue to fetch the id, and retry if necessary
                  publisher
                  (do
                    (log/info "Queuing fetch for Ryhti permanent permit identifier")
                    (publisher message))

                  :else
                  (log/warn "Cannot fetch Ryhti permanent id, no pubsub publisher found!"))))))))))
