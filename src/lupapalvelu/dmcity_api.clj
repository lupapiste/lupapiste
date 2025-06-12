(ns lupapalvelu.dmcity-api
  "Configuration and UI batchrun support for dmcity integration."
  (:require [clojure.set :as set]
            [lupapalvelu.action :refer [defcommand defquery defraw] :as action]
            [lupapalvelu.backing-system.dmcity.client :as dm-cl]
            [lupapalvelu.backing-system.dmcity.config :as dm-cfg]
            [lupapalvelu.backing-system.dmcity.core :as dmcity]
            [lupapalvelu.backing-system.dmcity.schemas :as dm-sc]
            [lupapalvelu.foreman-application-util :refer [foreman-app?]]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.states :as states]
            [sade.core :refer [fail ok]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(defn- application-exists
  "Application exists AND belongs to the organization"
  [{data :data}]
  (let [{:keys [organizationId applicationId]} data]
    (when (every? ss/not-blank? [organizationId applicationId])
      (when-not (mongo/any? :applications {:_id          applicationId
                                           :organization organizationId})
        (fail :error.application-not-found)))))

(defn- wrap [cfg-fn & args]
  (try
    (apply cfg-fn args)
    (ok)
    (catch Exception e
      (fail :error.dmcity.configuration
            :error (ex-message e)))))

(def check-simple-config (sc/checker dm-sc/SimpleConfig))
(def check-client-ops    (sc/checker [dm-sc/ClientOp]))

(defquery dmcity-enabled
  {:description "Pseudoquery that checks if the app's org has dmCity integration enabled for frontend"
   :permissions [{:required [:application/read]}]
   :pre-checks  [dmcity/check-dmcity-enabled]}
  [_]
  (ok))

(defquery dmcity-configuration
  {:description      "Current dmcity configuration, if defined. Returns two keys:
         `:configuration` The format is `SimpleConfig` (or nil)
         `:permit-types` Organization permit types that are supported by dmcity."
   :user-roles       #{:admin}
   :parameters       [organizationId]
   :input-validators [(partial action/non-blank-parameters [:organizationId])]
   :pre-checks       [org/organization-exists]}
  [_]
  (let [{:keys [scope]
         :as   organization} (mongo/by-id :organizations organizationId
                                          [:scope :dmcity])]
    (ok :configuration (some-> organization dm-cfg/config dm-cfg/config->simple)
        :permit-types (dm-cfg/supported-permit-types organization))))

(defcommand upsert-dmcity-configuration
  {:description      "Upserts organization's dmcity configuration."
   :user-roles       #{:admin}
   :parameters       [organizationId configuration]
   :input-validators [(partial action/non-blank-parameters [:organizationId])
                      (fn [{data :data}]
                        (when (some->> data :configuration ss/trimwalk
                                       check-simple-config)
                          (fail :error.dmcity.configuration)))]
   :pre-checks       [org/organization-exists]}
  [_]
  (wrap dm-cfg/upsert organizationId dm-cfg/update-config
        (ss/trimwalk configuration)))

(defcommand delete-dmcity-configuration
  {:description      "Deletes dmcity configuration, if defined."
   :user-roles       #{:admin}
   :parameters       [organizationId]
   :input-validators [(partial action/non-blank-parameters [:organizationId])]
   :pre-checks       [dmcity/check-dmcity-configured]}
  [_]
  (dm-cfg/delete organizationId)
  (ok))

(defcommand toggle-dmcity-configuration-target
  {:description      "Enable/disable dmcity configuration target."
   :user-roles       #{:admin}
   :parameters       [organizationId target enabled]
   :input-validators [(partial action/non-blank-parameters [:organizationId :target])
                      (partial action/boolean-parameters [:enabled])]
   :pre-checks       [dmcity/check-dmcity-configured]}
  [_]
  (wrap dm-cfg/toggle-target organizationId target enabled))

(defcommand set-dmcity-url
  {:description      "Convenience command for setting the dmcity url without full upsert. This
  is mainly used in testing."
   :user-roles       #{:admin}
   :parameters       [organizationId url]
   :input-validators [(partial action/non-blank-parameters [:organizationId :url])]
   :pre-checks       [dmcity/check-dmcity-configured]}
  [_]
  (wrap dm-cfg/upsert organizationId assoc :url (dm-cfg/trim-base-url url)))

(defn- resolve-client-ops
  "Valid operations or nil."
  [ops]
  (when-let [ops (some->> ops seq (map keyword) distinct)]
    (when-not (check-client-ops ops)
      ops)))

(defn- valid-client-ops
  "`ops` parameter is either a list of proper client operations or 'default'."
  [{{ops :ops} :data}]
  (cond
    (nil? (seq ops))
    (fail :error.missing-parameters :parameters [:ops])

    (nil? (resolve-client-ops ops))
    (fail :error.dmcity.bad-operations)))

(defn- channel->force-path! [channel]
  (some->> (case channel
             nil          nil
             "conversion" :conversion
             "production" :application)
           (hash-map :force-path)))

(defn- valid-channel [{{:keys [channel]} :data}]
  (try
    (channel->force-path! channel)
    nil
    (catch Exception _
      (fail :error.dmcity.bad-channel))))

(defcommand send-messages-to-dmcity
  {:description         "Send messages to dmcity. Returns client results. `ops` is either a list
  of client operations or just 'default'. When latter, the default messages are sent
  according to the application status. Optional `channel` parameter must be either
  `conversion` or `production`."
   :user-roles          #{:admin}
   :parameters          [organizationId applicationId ops]
   :optional-parameters [channel]
   :input-validators    [(partial action/non-blank-parameters [:organizationId :applicationId])
                         valid-client-ops
                         valid-channel]
   :pre-checks          [dmcity/check-dmcity-configured
                         application-exists]}
  [{:keys [user system]}]
  (ok :results (dm-cl/send-messages (dm-cl/client-config system organizationId user
                                                         (channel->force-path! channel))
                                    (mongo/by-id :applications applicationId)
                                    (resolve-client-ops ops))))

;; ---------------------
;; Authority admin
;; ---------------------

(defn- organization-flags [organization-id]
  (let [{:keys [targets]} (dm-cfg/config organization-id)
        pts               (some->> organization-id
                                   dm-cfg/supported-permit-types
                                   (map keyword))]
    (->> (select-keys targets (conj pts :state :building :address :link))
         (map (fn [[k v]]
                [k (boolean (:enabled v))]))
         (into (zipmap (conj pts :state :link) (repeat false))))))

(defquery dmcity-configuration-flags
  {:description      "Flags are configuration targets that an authority admin can
  toggle. Returns a flag map."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId]
   :input-validators [(partial action/non-blank-parameters [:organizationId])]
   :pre-checks       [dmcity/check-dmcity-configured]}
  [_]
  (ok :flags (organization-flags organizationId)))

(defn- supported-flags
  [{{:keys [organizationId flags]} :data}]
  (when flags
    (when-not (some-> (organization-flags organizationId)
                      keys set
                      (set/superset? (set (keys flags))))
      (fail :error.dmcity.unsupported-flag))))

(defcommand set-dmcity-configuration-flags
  {:description      "Set dmcity configuration flags. flags is a flag map. Only the flags in
  the map are updated."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId flags]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :flags          {sc/Keyword sc/Bool}}]
   :pre-checks       [dmcity/check-dmcity-configured
                      supported-flags]}
  [_]
  (wrap dm-cfg/upsert organizationId update :targets util/deep-merge
        (->> flags
             (map (fn [[k v]]
                    [k {:enabled v}]))
             (into {}))))

;; ---------------------
;; Authority
;; ---------------------

(defcommand send-verdict-messages
  {:description      "Sends verdict messages to dmcity.
                      If the sending fails, returns a list of errors."
   :permissions      [{:required [:application/approve]
                       :context  {:application {:state states/post-verdict-states}}}]
   :parameters       [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [dmcity/check-dmcity-enabled
                      (fn [{:keys [application] :as command}]
                        (when-not (and (not (:facta-imported application))
                                       (not (foreman-app? application))
                                       (vif/latest-published-verdict command))
                          (fail :error.not-found)))]}
  [{:keys [user application organization system]}]
  (if-let [errors (some->> (dm-cl/send-messages {:organization (force organization)
                                                 :user         user
                                                 :options      {:report? true}
                                                 :system       system}
                                                application
                                                [:verdicts])
                           (keep :error)
                           seq
                           (into []))]
    (fail :error.dmcity.kuntagml :errors errors)
    (ok)))

(defraw redirect-to-dmcity
  {:description "Redirects user to the corresponding application in dmcity."
   :parameters  [id]
   :permissions [{:required [:application/access-backend]}]
   :states      states/post-sent-states
   :pre-checks  [dmcity/check-dmcity-redirect]}
  [command]
  (let [url (dm-cfg/dmcity-application-url command)]
    (log/with-logging-context
      {:applicationId id}
      (log/info "Redirecting to dmcity" url))
    {:status 303 :headers {"Location" url}}))
