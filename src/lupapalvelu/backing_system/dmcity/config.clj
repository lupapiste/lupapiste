(ns lupapalvelu.backing-system.dmcity.config
  "Organization dmcity configuration and related."
  (:require [lupapalvelu.backing-system.dmcity.schemas :as dm-sc]
            [lupapalvelu.backing-system.security :as bs-sec]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [selmer.parser :as selmer]
            [swiss.arrows :refer [-<>>]])
  (:import [java.net URL]))

(set! *warn-on-reflection* true)

(sc/defn ^:always-validate delete
  "Deletes dmcity configuration for `organization-id`. Safe to call for an organization
  without dmcity configuration."
  [organization-id :- ssc/NonBlankStr]
  (mongo/update-by-id :organizations organization-id {$unset {:dmcity true}}))

(defn- get-organization [organization-or-id projection]
  (if (string? organization-or-id)
    (mongo/by-id :organizations organization-or-id projection)
    (force organization-or-id)))

(defn config
  "Returns the current dmcity configuration. Read from mongo only if `organization-or-id` is
  id."
  [organization-or-id]
  (:dmcity (get-organization organization-or-id [:dmcity])))

(sc/defn ^:private ^:always-validate validate :- dm-sc/DmcityIntegration
  "Config is valid if it matches the schema and credentials are encoded."
  [cfg :- dm-sc/DmcityIntegration]
  (when (bs-sec/get-credentials cfg)
    cfg))

(sc/defn ^:always-validate upsert
  "Passes the old config to `update-fn` and stores the result. Throws if the new config is
  not valid. If `args` are present, they are passed after config."
  [organization-id :- ssc/NonBlankStr update-fn :- (sc/pred fn? "Function") & args]
  (try
    (-<>> (config organization-id)
          (apply update-fn <> args)
          ss/trimwalk
          validate
          (assoc-in {} [$set :dmcity])
          (mongo/update-by-id :organizations organization-id))
    (catch Exception e
      (throw (ex-info (str organization-id ": Bad dmcity configuration upsert.")
                      {:error (ex-message e)}
                      e)))))

(sc/defn ^:always-validate toggle-target
  "Convenience `upsert` shortcut that sets the given target's `enabled` field to `enabled?`."
  [organization-id :- ssc/NonBlankStr target-key :- dm-sc/TargetKey enabled?]
  (upsert organization-id
          assoc-in [:targets (keyword target-key) :enabled] (boolean enabled?)))

(def ^:private check-dmcity-integration (sc/checker dm-sc/DmcityIntegration))

(defn get-target
  [organization-or-id-or-cfg target-key]
  (if (dm-sc/target-key? target-key)
    (some-> (cond-> organization-or-id-or-cfg
              (check-dmcity-integration organization-or-id-or-cfg)
              config)
            (get-in [:targets (keyword target-key)]))
    (log/trace "Unsupported dmcity target:" target-key)))

(defn target-enabled?
  [organization-or-id-or-cfg target-key]
  (-> (get-target organization-or-id-or-cfg target-key)
      :enabled boolean))

(defn enabled-for-app?
  [organization-or-id-or-cfg application]
  (and (not (:infoRequest application))
       (target-enabled? (force organization-or-id-or-cfg) (:permitType application))))

(defn get-path
  "Returns a path from configuration or fallbacks to default path."
  [organization-or-id-or-cfg target-key path-key]
  (try
    (-> (get (get-target organization-or-id-or-cfg target-key)
             (keyword path-key))
        ss/blank-as-nil
        (or (dm-sc/default-path target-key path-key)))
    (catch Exception _
      (log/errorf "No path %s for dmcity config target %s."
                     path-key target-key))))

(defn- building-or-address? [k]
  (contains? #{:building :address} k))

(sc/defn ^:always-validate config->simple :- (sc/maybe dm-sc/SimpleConfig)
  "Simple view of the current config. The password is in plain text."
  [cfg :- (sc/maybe dm-sc/DmcityIntegration)]
  (some-> cfg
          (assoc :password (bs-sec/decode-password cfg))
          (dissoc :crypto-iv)
          (update :targets (fn [targets]
                             (some->> targets
                                      (map (fn [[k {:keys [enabled] :as m}]]
                                             [k (if (building-or-address? k)
                                                  m
                                                  enabled)]))
                                      (into {})
                                      not-empty)))
          util/strip-blanks))

(defn trim-base-url
  "Trims `url` and removes trailing slashes."
  [url]
  (some-> url ss/trim (ss/replace #"/+$" "")))

(sc/defn ^:always-validate simple->config :- (sc/maybe dm-sc/DmcityIntegration)
  "Transform the given simple config into a format that can be safely (deep) merged into a
  configuration."
  [cfg :- (sc/maybe dm-sc/SimpleConfig)]
  (when-let [cfg (ss/trimwalk cfg)]
    (some-> cfg
            (merge (bs-sec/encode-credentials cfg))
            (update :targets (fn [targets]
                               (some->> targets
                                        (map (fn [[k v]]
                                               (let [b-or-a? (building-or-address? k)]
                                                 [k (cond
                                                      ;; If url is not given the target is
                                                      ;; removed whether it is enabled or not
                                                      (and b-or-a? (ss/blank? (:url v)))
                                                      nil

                                                      b-or-a? v
                                                      :else   {:enabled v})])))
                                        (into {})
                                        util/strip-nils
                                        not-empty)))
            (update :url trim-base-url)
            util/strip-blanks)))

(defn- reset-targets
  "Disbles every target and remove those that are fully set by a simple config."
  [cfg]
  (-> cfg
      (update :targets
              (util/fn->> (keep (fn [[k v]]
                                  (when-not (building-or-address? k)
                                    [k (assoc v :enabled false)])))
                          (into {})
                          not-empty))
      util/strip-nils))

(sc/defn ^:always-validate update-config :- dm-sc/DmcityIntegration
  "Updates `cfg` with `simple`"
  [cfg    :- (sc/maybe dm-sc/DmcityIntegration)
   simple :- dm-sc/SimpleConfig]
  (util/deep-merge (reset-targets cfg) (simple->config simple)))

(sc/defn building-wfs :- (sc/maybe {:url         ssc/HttpUrl
                                    :credentials [ssc/NonBlankStr]})
  "Returns enabled building url and decoded credentials, if available. Otherwise nil."
  [organization-or-id]
  (when-let [cfg (config organization-or-id)]
    (let [{:keys [url enabled]} (get-target cfg :building)]
      (when enabled
        {:url         url
         :credentials (bs-sec/get-credentials cfg)}))))

(sc/defn address-endpoint :- (sc/maybe {:url         ssc/HttpUrl
                                        :no-bbox-srs sc/Bool})
  "Address endpoint is resolved by `municipality`. Note that currently NO credentials are
  needed for accessing dmcity address search. Nil if no address endpoint found."
  [municipality]
  (when-let [municipality (some-> municipality ss/trim ss/blank-as-nil)]
    (some->> (mongo/select-one :organizations
                               {:scope.municipality             municipality
                                ;; Schema-wise, the address cannot be enabled if the url is
                                ;; not given.
                                :dmcity.targets.address.enabled true}
                               [:dmcity.targets.address.url])
             :dmcity :targets :address
             ;; Currently always enabled since so far every dmcity address configuration
             ;; is the same.
             (merge {:no-bbox-srs true}))))

(defn supported-permit-types
  "Organization permit types that are also supported by dmcity. List of strings or nil."
  [organization-or-id]
  (some->> (get-organization organization-or-id [:scope])
           :scope
           (map :permitType)
           distinct
           (filter dm-sc/supported-permit-type?)
           seq))

(defn foreman-app?
  "Separate definition in order to avoid cyclical dependencies. See
  `lupapalvelu.foreman-application-util` for the more official version."
  [{primary-op :primaryOperation}]
  (boolean (some->> primary-op :name keyword
                    (contains? #{:tyonjohtajan-nimeaminen-v2
                                 :tyonjohtajan-nimeaminen-v3}))))

(sc/defn ^:always-validate dmcity-application-url :- (sc/maybe ssc/HttpUrl)
  "Returns an url to application in the target dmcity. Returns nil if the linking is not
  supported or configured."
  ([{:keys [url vault] :as cfg} :- dm-sc/DmcityIntegration
    {application-id :id
     :as            application} :- {:id        ssc/ApplicationId
                                     sc/Keyword sc/Any}]
   (when (and (enabled-for-app? cfg application)
              (target-enabled? cfg :link)
              (not (foreman-app? application)))
     (when-let [fmt (ss/blank-as-nil (get-path cfg :link :fmt))]
       (let [url    (URL. url)
             server (str (URL. (.getProtocol url) (.getHost url) (.getPort url) ""))]
         (selmer/render fmt (util/mmap server vault application-id))))))
  ([{:keys [organization application]}]
   (some-> (config organization)
           (dmcity-application-url application))))
