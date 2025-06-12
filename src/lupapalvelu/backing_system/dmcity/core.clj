(ns lupapalvelu.backing-system.dmcity.core
  (:require [lupapalvelu.backing-system.dmcity.config :as dm-cfg]
            [lupapalvelu.backing-system.dmcity.schemas :as dm-sc]
            [lupapalvelu.backing-system.krysp.http :as krysp-http]
            [lupapalvelu.backing-system.security :as bs-sec]
            [lupapalvelu.integrations.messages :refer [wrap-message-options]]
            [lupapalvelu.integrations.state-change :as state-change]
            [lupapalvelu.logging :as log]
            [monger.operators :refer :all]
            [sade.core :refer [fail]]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(set! *warn-on-reflection* true)

;; ------------------------------
;; Pre-checkers
;; ------------------------------

(defn check-dmcity-configured
  "Pre-check that fails if dmcity integration is not configured. Note that a configuration
  does not mean that the integration is active."
  [{:keys [organization data]}]
  (when-let [o-or-id (or organization (:organizationId data))]
    (when-not (dm-cfg/config o-or-id)
      (fail :error.dmcity.not-configured))))

(defn check-dmcity-enabled
  "Pre-check that fails if dmcity integration is not active"
  [{:keys [organization application]}]
  (when (and application
             (not (dm-cfg/enabled-for-app? organization application)))
    (fail :error.dmcity.enabled)))

(defn check-dmcity-disabled
  "Pre-check that fails if the `application` HAS a corresponding active dmcity
  integration."
  [{:keys [organization application]}]
  (when (and application
             (dm-cfg/enabled-for-app? organization application))
    (fail :error.dmcity.enabled)))

(defn check-dmcity-targets-enabled
  "Returns pre-checker that fails if not the every given target is enabled"
  [& targets]
  (fn [{:keys [organization]}]
    (when-not (every? #(dm-cfg/target-enabled? organization %) targets)
      (fail :error.dmcity.enabled))))

(defn check-dmcity-redirect
  "Pre-checker that fails if the dmcity linking is not supported for the application.
  In practise, the checker simply tries to create the link."
  [command]
  (when-not (dm-cfg/dmcity-application-url command)
    (fail :error.dmcity.enabled)))

(defn- make-text [log-fn-key fmt & args]
  (let [text (apply format fmt args)]
    (case log-fn-key
      :warn (log/warn text)
      :info (log/info text))
    {:text text}))

(sc/defn ^:private resolve-url :- (sc/conditional
                                    :url {:url ssc/HttpUrl}
                                    :text {:text ssc/NonBlankStr})
  ([{org-id :id :as organization}
    {:keys [id permitType verdicts facta-imported]
     :as application}
    {user-id :id} ;; Only for logging
    ctx-key :- sc/Keyword
    {:keys [force-enabled? force-path]}]
   (let [{base-url :url
          :as      cfg}     (dm-cfg/config organization)
         permit-type?       (and (dm-sc/supported-permit-type? permitType)
                                 (or (dm-cfg/target-enabled? cfg permitType)
                                     force-enabled?))
         conversion-target? (= ctx-key :conversion)
         regular-target?    (and (dm-sc/target-key? ctx-key)
                                 (not conversion-target?))
         enabled?           (and regular-target?
                                 (or force-enabled?
                                     (dm-cfg/target-enabled? cfg ctx-key)))
         state-target?      (= ctx-key :state)
         ;; Conversion target cannot be 'force-enabled' since Matti does not support
         ;; conversion paths.
         conversion-path    (when (dm-cfg/target-enabled? cfg :conversion)
                              [:conversion (if state-target?
                                             :state
                                             permitType)])
         force-conversion?  (= force-path :conversion)
         force-application? (= force-path :application)
         backend-id?        (some (comp ss/not-blank? :kuntalupatunnus)
                                  verdicts)
         make-url           #(some->> % (str base-url "/") (hash-map :url))
         warn               (partial make-text :warn)
         info               (partial make-text :info)]
     (log/with-logging-context {:userId user-id :applicationId id}
       (cond
         (nil? cfg)
         (warn "No dmcity configuration for %s." org-id)

         (dm-cfg/foreman-app? application)
         (info "Foreman application messages are not sent to dmcity.")

         (and regular-target? (not enabled?))
         (warn "Target %s not supported or not enabled for %s."
               (name ctx-key) org-id)

         (not permit-type?)
         (warn "Permit type %s not supported or not enabled in %s."
               permitType org-id)

         ;; Review path is unaffected by conversion
         (= ctx-key :review)
         (or (make-url (dm-cfg/get-path cfg permitType :review))
             (info "No review support for %s in dmcity." permitType))

         ;; Conversion path resolution can be either implicit or explicit.
         (and (dm-sc/schema-path? :targets conversion-path)
              (not force-application?)
              (or force-conversion? backend-id? conversion-target?
                  facta-imported))
         (make-url (apply dm-cfg/get-path cfg conversion-path))

         ;; No conversion support, fallback to :application
         conversion-target?
         (make-url (dm-cfg/get-path cfg permitType :application))

         state-target?
         (make-url (dm-cfg/get-path cfg :state :state))

         :else
         (or (make-url (dm-cfg/get-path cfg permitType ctx-key))
             (warn "No such path for %s: %s" permitType (name ctx-key)))))))
  ([organization application user ctx-key]
   (resolve-url organization application user ctx-key nil)))

(defn- make-message [application user url & kvs]
  (-> (apply hash-map kvs)
      util/strip-nils
      (merge {:partner     "dmcity"
              :url         url
              :initiator   (select-keys user [:id :username])
              :application (select-keys application [:id :organization])})))

(defn send-xml
  "Resolves the dmcity `ctx-key` endpoint url, if one is available and initiates the sending
  of `xml`. The actual sending is done via `krysp-http/send-kuntagml-message`.

  Options:

  `:force-enabled?` If true, every target endpoint is always considered enabled.

  `:force-path` If given, the conversion paths for use cases are explicitly selected to by
  either `:application` or `:conversion`. By default, the conversion path usage is
  determined by the application properties. Also enables conversion targets.

  `type-suffix` By default, `messageType` for the integration message is of format
  'KuntaGML ctx-key'. If `type-suffix` is given, the format is 'KuntaGML
  type-suffix'. While the `messageType` is actually used, the intuitive value helps
  tracking. `type-suffix` can be string or keyword.

  `:report?` By default, function does not return anything. If `:report?` option is true,
  an error message is returned if the endpoint url cannot be resolved."
  ([system organization application user ctx-key xml {:keys [report? type-suffix] :as options}]
   {:pre [(map? system)]}
   (let [{:keys [url text]} (resolve-url organization application user
                                         ctx-key options)]
     (cond
       url     (krysp-http/send-kuntagml-message
                 system
                 (make-message application user url
                               :messageType (str "KuntaGML "
                                                 (name (or type-suffix ctx-key)))
                               :data xml))
       report? text)))
  ([system organization application user ctx-key xml]
   (send-xml system organization application user ctx-key xml nil))
  ([{:keys [system organization application user]} ctx-key xml options]
   (send-xml system (force organization) application user
             ctx-key xml options))
  ([command ctx-key xml]
   (send-xml command ctx-key xml nil)))

(defmethod wrap-message-options :dmcity
  ;; Adds organization's dmcity authentication headers to `options`.
  [{:keys [application]} options]
  (let [{:keys [username vault]
         :as   cfg} (some-> (:organization application) dm-cfg/config)]
    (update options :headers merge {"x-vault"    vault
                                    "x-username" username
                                    "x-password" (bs-sec/decode-password cfg)})))

(defn send-state-change
  "Resolves the dmcity state-change endpoint url, if one is available and initiates a
  state-change message. The actual sending is done via
  `state-change/send-state-change-message`.

  Options:

  `:force-enabled?` If true, state-change endpoint is always considered enabled.

  `:force-path` If given, the conversion paths for use cases are explicitly selected to by
  either `:application` or `:conversion`. By default, the conversion path usage is
  determined by the application properties. Also enables conversion targets.

  `:report?` By default, function does not return anything. If `:report?` option is true,
  an error message is returned if the endpoint url cannot be resolved."
  ([{:keys [organization application user action created system]}
    new-state {:keys [report?] :as options}]
   {:pre [(map? system)]}
   (cond

     (state-change/valid-states new-state (:state application))
     (let [{:keys [url text]} (resolve-url organization application user
                                           :state options)]
       (cond
         url     (state-change/send-state-change-message
                   application
                   (make-message application user url
                                 :created created
                                 :action action)
                   new-state
                   system)
         report? text))

     report?
     (:text (make-text :warn "Not a valid state change: %s -> %s."
                       (:state application) (name new-state)))))
  ([command new-state]
   (send-state-change command new-state nil))
  ([system organization application user new-state options]
   (send-state-change {:organization organization
                       :application  application
                       :user         user
                       :created      (date/timestamp (date/now))
                       :system       system}
                      new-state
                      options))
  ([system organization application user new-state]
   (send-state-change system organization application user new-state nil)))
