(ns lupapalvelu.system.build
  (:require [integrant.core :as ig]
            [lupapalvelu.elasticsearch.applications :as elastic-applications]
            [lupapalvelu.elasticsearch.client :as elastic]
            [lupapalvelu.elasticsearch.foremen :as elastic-foremen]
            [lupapalvelu.http.jetty :as jetty]
            [lupapalvelu.http.noir.core :as noir]
            [lupapalvelu.http.reitit.core :as reitit]
            [lupapalvelu.http.session :as session]
            [lupapalvelu.integrations.pubsub :as pubsub]
            [lupapalvelu.laundry.client :as laundry]
            [lupapalvelu.migration.component :as migration]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mongo-watcher.client :as mongo-watcher]
            [lupapalvelu.perf-mon :as perf-mon]
            [lupapalvelu.system.util-components :as components]))

(defn- sort-system
  [system-map]
  (->> system-map
       (into (sorted-map-by (fn [a b]
                              (compare (if (coll? a) a [a nil])
                                       (if (coll? b) b [b nil])))))))

(defn- build-pubsub
  [{:keys [pubsub dev-mode?] :as config}]
  (merge
    {::pubsub/client (-> (:client pubsub)
                         (assoc :deps [(ig/ref ::mongo/database)]))}
    ;; subscriptions
    (->> (:topics pubsub)
         (filter (fn [[_id conf]] (:subscribe? conf)))
         (map (fn [[id conf]]
                (when (and (:remove-on-shutdown? conf)
                           (not dev-mode?))
                  (throw (ex-info "Must not remove pubsub subscriptions if running outside dev!"
                                  {:dev-mode? dev-mode? :pubsub pubsub})))
                [[::pubsub/subscriber id]
                 (-> (select-keys conf #{:topic :actual-topic :options :remove-on-shutdown?})
                     (assoc :client (ig/ref ::pubsub/client)
                            :components (ig/ref ::components/common))
                     (update :actual-topic (fnil identity (components/config->actual-topic-name config id))))]))
         (into {}))
    ;; publishers
    (->> (:topics pubsub)
         (filter (fn [[_id conf]] (:publish? conf)))
         (map (fn [[id conf]]
                [[::pubsub/publisher id]
                 (-> (select-keys conf #{:topic :actual-topic})
                     (assoc :client (ig/ref ::pubsub/client))
                     (update :actual-topic (fnil identity (components/config->actual-topic-name config id))))]))
         (into {}))))

(defn build-system-common
  "Build a common `integrant` system blueprint from the config."
  [{:keys [dev-mode? elastic http laundry migration mongo mongo-watcher noir pubsub] :as config}]
  {:pre [(map? config)]}
  (cond-> {::components/config        {:config config}
           ::components/common        {;; Config should be always present
                                       :config        (ig/ref ::components/config)
                                       :elastic       (ig/refset ::elastic/client)
                                       ;; NOTE: we use the abstract `:laundry/client`
                                       :laundry       (ig/refset :laundry/client)
                                       :mongo         (ig/refset ::mongo/database)
                                       :mongo-watcher (ig/refset ::mongo-watcher/watcher)
                                       :publishers    (ig/refset ::pubsub/publisher)}
           ::migration/migrations     (merge migration
                                             ;; NOTE: indices are built before running migrations
                                             {:conn (ig/ref ::mongo/connection)
                                              :deps [(ig/ref ::mongo/indices)]})
           ;; `::mongo/connection` just starts the plain MongoDB client/connection
           ::mongo/connection         (assoc mongo :deps [(ig/refset ::perf-mon/instrumentation)])
           ;; `::mongo/database` also makes sure the indices and migrations have been executed
           ::mongo/database           {:conn (ig/ref ::mongo/connection)
                                       :deps [(ig/ref ::mongo/indices)
                                              (ig/ref ::migration/migrations)]}
           ::mongo/indices            {:conn (ig/ref ::mongo/connection)}

           ::perf-mon/instrumentation {:dev-mode? dev-mode?}

           ::session/store            {}}
    http                      (assoc ::jetty/server {:handler (ig/ref :reitit/handler)
                                                     :opts    http})

    noir                      (assoc ::noir/handler {:components  (ig/ref ::components/common)
                                                     :cookie-name (:cookie-name noir)
                                                     :dev-mode?   dev-mode?
                                                     :noir-opts   (merge (:noir-opts noir)
                                                                         {:session-store (ig/ref ::session/store)})})

    laundry                   (assoc ::laundry/client-impl laundry)

    (:enabled? elastic)       (assoc ::elastic/client (assoc elastic :mongo-watcher-enabled? (boolean (:enabled? mongo-watcher))
                                                             :conn (ig/ref ::mongo/database)))
    (:enabled? mongo-watcher) (merge
                                {::mongo-watcher/watcher                                        (assoc mongo
                                                                                                       :conn (ig/ref ::mongo/database)
                                                                                                       :handlers (ig/refset ::mongo-watcher/event-handler))
                                 [::mongo-watcher/event-handler ::elastic-applications/handler] {:collection-name :applications
                                                                                                 :elastic         (ig/ref ::elastic/client)}
                                 [::mongo-watcher/event-handler ::elastic-foremen/handler]      {:collection-name :applications
                                                                                                 :elastic         (ig/ref ::elastic/client)}})
    (:enabled? pubsub)        (merge (build-pubsub config))
    true                      sort-system))

(defn build-system-lupapiste
  "Build an `integrant` system blueprint from the config for Lupapiste."
  [{:keys [reitit] :as config}]
  (-> (build-system-common config)
      (merge {[:reitit/handler ::reitit/routes] (merge reitit
                                                       {:default-handler (ig/ref ::noir/handler)
                                                        :session-store   (ig/ref ::session/store)
                                                        :components      (ig/ref ::components/common)})})
      sort-system))

(defn build-system-mongo-watcher
  "Build an `integrant` system blueprint from the config for mongo watcher."
  [config]
  (-> (build-system-common config)
      (merge {[:reitit/handler ::mongo-watcher/routes] {:mongo-watcher (ig/ref ::mongo-watcher/watcher)}})
      sort-system))
