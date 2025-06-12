(ns lupapalvelu.system.util-components
  "Some helper integrant components

  NOTE: Some components (HTTP handlers, pubsub listeners) require access
  to other common components (mongo, elastic, .., See the `CommonComponents` schema).
  Instead of passing the whole Integrant system, we pass a subset of common components
  (see `::common`). Therefore, in the application code (actions etc.), the term `system`
  refers to these common components rather than the whole Integrant system."
  (:require [integrant.core :as ig]
            [lupapalvelu.integrations.pubsub :as lip]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.system.util-components-schema :as component-schema]
            [sade.util :as util]))

;; Provide the config as a system component
(defmethod ig/init-key ::config [k {:keys [config]}]
  (log/infof "Starting %s..." k)
  ;; Clear some credentials so we don't accidentally log them etc.
  (-> config
      (update :mongo dissoc :credentials)
      (update :http dissoc :key-password)))

(defmethod ig/init-key ::common [k components]
  (log/infof "Starting %s..." k)
  (reduce (fn [acc [k refs]]
            (cond
              ;; Special handling for publishers & config
              (= :publishers k)  (assoc acc k (->> refs
                                                   (map (fn [{:keys [topic-id] :as p}]
                                                          [topic-id p]))
                                                   (into {})))
              (= :config k)      (assoc acc k refs)
              ;; No implementations?
              (empty? refs)      acc
              ;; Typical case, e.g. we have a single mongo connection
              (= 1 (count refs)) (assoc acc k (first refs))
              ;; This shouldn't happen, multiple concrete components found!
              :else              (throw (ex-info "Found multiple components, expected only one!"
                                                 {:k    k
                                                  :refs refs}))))
          {}
          components))

(defn with-system-components-mw
  [components]
  (component-schema/valid-components! components)
  (fn [handler]
    (fn with-system-mw-handler
      [request]
      (let [request (assoc request :system components)]
        ;; Ideally we'd like to access mongo too through `:system`,
        ;; but that would require quite a bit of refactoring
        (binding [mongo/*connection* (:mongo components)]
          (handler request))))))

(defn system->config
  [system]
  {:pre  [(map? system)]
   :post [(map? %)]}
  (-> system :config))

(defn system->host
  "Replaces `(sade.env/value :host)`"
  [system]
  {:pre  [(map? system)]
   :post [(string? %)]}
  (-> system system->config :host))

(defn system->http-port
  [system]
  {:pre  [(map? system)]
   :post [(int? %)]}
  (-> system system->config :http :port))

(defn system->laundry
  [system]
  {:pre  [(map? system)]
   :post [(some? %)]}
  (-> system :laundry))

(defn system->mongo-watcher
  [system]
  {:pre [(map? system)]}
  (:mongo-watcher system))

(defn system->elastic
  [system]
  {:pre [(map? system)]}
  (:elastic system))

(defn system->elastic-config
  [system]
  {:pre [(map? system)]}
  (-> system system->config :elastic))

(defn system->publisher-component
  [system topic-id]
  {:pre [(map? system)]}
  (-> system
      :publishers
      (get topic-id)))

(defn system->publisher
  [system topic-id]
  {:pre [(map? system)]}
  (:publisher-fn (system->publisher-component system topic-id)))

(defn config->actual-topic-name
  [config topic-id]
  {:pre  [(map? config)]
   :post [(string? %)]}
  (let [{:keys [pubsub target-env]}   config
        {:keys [queue-prefix topics]} pubsub
        {:keys [actual-topic topic]}  (get topics topic-id)]
    (or actual-topic
        (when topic
          (lip/actual-topic-name (util/mmap queue-prefix target-env topic))))))

(defn system->actual-topic-name
  [system topic-id]
  {:pre  [(map? system)]
   :post [(string? %)]}
  (config->actual-topic-name (system->config system) topic-id))

(defn command->publisher
  [command topic-id]
  (some-> command
          :system
          (system->publisher topic-id)))

(defn command->elastic
  [{:keys [system]}]
  (system->elastic system))

(defn command->elastic-config
  [{:keys [system]}]
  (system->elastic-config system))
