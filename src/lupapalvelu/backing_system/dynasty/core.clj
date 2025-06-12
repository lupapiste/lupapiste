(ns lupapalvelu.backing-system.dynasty.core
  (:require [clojure.set :as set]
            [lupapalvelu.backing-system.dynasty :as dynasty-schema]
            [lupapalvelu.backing-system.dynasty.message :as message]
            [lupapalvelu.backing-system.dynasty.municipal-context :as municipal-ctx]
            [lupapalvelu.backing-system.dynasty.writer :as writer]
            [lupapalvelu.backing-system.integration-config :as integration-config]
            [lupapalvelu.backing-system.security :as bs-sec]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization-integrations :as integrations]
            [lupapalvelu.permit :as permit]
            [sade.core :refer [fail!]]))

;; Compatibility with the generic `integration-config` API

(def ^:private password-token "---dynasty-secret---")

(defn- find-integrations
  "Find all Dynasty integrations for `organization`.
   Optionally replace all secrets with `password-token`."
  [organization hide-secrets?]
  (->> (integrations/find-by-type organization :dynasty)
       (map (fn [integration]
              (if hide-secrets?
                (-> integration
                    (update :options dissoc :crypto-iv)
                    (assoc-in [:options :client-secret] password-token))
                integration)))))

(defn- available-targets
  [organization]
  (let [current (integrations/current-targets-by-type organization :dynasty)]
    (-> (municipal-ctx/supported-permit-types organization)
        ;; Forbid adding multiple integrations with overlapping target(s).
        (set/difference current))))

(defmethod integration-config/available-targets :dynasty
  [_ organization]
  (available-targets organization))

(defmethod integration-config/current-configs :dynasty
  [_ organization]
  (find-integrations organization true))

(defn- insert-options
  [{:keys [client client-secret] :as options}]
  (let [{:keys [password crypto-iv]} (bs-sec/encode-credentials client client-secret)]
    (merge
      (select-keys options dynasty-schema/plain-options)
      {:client-secret password
       :crypto-iv     crypto-iv})))

(defn- update-options
  [{:keys [client client-secret] :as options}]
  (merge
    (select-keys options dynasty-schema/plain-options)
    (when (not= password-token client-secret)
      (let [{:keys [password crypto-iv]} (bs-sec/encode-credentials client client-secret)]
        {:client-secret password
         :crypto-iv     crypto-iv}))))

(defn- do-insert!
  [{:keys [config organization-id] :as args}]
  (let [org       (mongo/by-id :organizations organization-id)
        available (available-targets org)]
    (when-let [missing (seq (set/difference (:targets config) available))]
      (log/errorf "Cannot insert dynasty integration, targets unavailable: %s" missing)
      (fail! :error.dynasty-target-in-use))
    (integrations/insert! args)))

(defmethod integration-config/upsert-config! :dynasty
  [{{:keys [id]} :config :as args}]
  (if id
    (integrations/update! (update-in args [:config :options] update-options))
    (do-insert! (update-in args [:config :options] insert-options))))

;; Compatibility with the `BackingSystem` API

(defn- find-integration
  [organization application]
  (let [target? #{(permit/permit-type application)}]
    (->> (find-integrations organization false)
         (filter (fn [{:keys [targets]}]
                   (some target? targets)))
         first)))

(defn enabled-for-organization?
  [organization application]
  (when-let [{:keys [id enabled?]} (find-integration organization application)]
    (let [op-supported? (municipal-ctx/operation-supported? application)]
      (when (and enabled? (not op-supported?))
        (log/warnf "Dynasty integration %s is enabled but no support for %s"
                   id
                   (-> application :primaryOperation :name)))
      (and enabled?
           op-supported?))))

(defn- assert-enabled!
  [organization application]
  (when-not (enabled-for-organization? organization application)
    (fail! :error.integration.dynasty-not-enabled)))

(defn- integration->server-config
  [{:keys [options]}]
  {:host         (:host options)
   :organization (:organization options)
   :auth         {:host          (:auth-host options)
                  :client-id     (:client options)
                  :client-secret (bs-sec/decode-password
                                   (:client-secret options)
                                   (:crypto-iv options))}})

(defn approve-application!
  [{:keys [organization application user lang created]}]
  (log/with-logging-context
    {:applicationId (:id application)}
    (assert-enabled! organization application)
    (let [integration   (find-integration organization application)
          server-config (integration->server-config integration)
          submitted     (->> (municipal-ctx/build-municipal-context integration application lang created)
                             message/create-case-messages
                             (writer/submit-messages! server-config application user))]
      (log/infof "Submitted to dynasty: %s" submitted)
      (get submitted :file-ids #{}))))
