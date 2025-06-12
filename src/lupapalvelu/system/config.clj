(ns lupapalvelu.system.config
  (:require [lupapalvelu.elasticsearch.client-schema :as elastic-schema]
            [lupapalvelu.http.jetty-schema :as jetty-schema]
            [lupapalvelu.http.noir.schema :as noir-schema]
            [lupapalvelu.integrations.pubsub-schema :as pubsub-schema]
            [lupapalvelu.laundry.client-schema :as laundry-schema]
            [lupapalvelu.mongo-schema :as mongo-schema]
            [lupapalvelu.system.util :as system-util]
            [lupapalvelu.visma-sign-schema :as visma-sign-schema]
            [sade.env :as env]
            [sade.shared-schemas :as ssc]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]]))

(defschema Config
  "Shape of the configuration, regardless if we are running Lupapiste proper or e.g. batchruns."
  {(sc/optional-key :bim)           {:bim-viewer-max-filesize sc/Int}
   :dev-mode?                       sc/Bool
   (sc/optional-key :elastic)       elastic-schema/ElasticConfig
   (sc/optional-key :host)          ssc/NonBlankStr
   (sc/optional-key :http)          jetty-schema/JettyOptions
   (sc/optional-key :laundry)       laundry-schema/LaundryOpts
   :migration                       {(sc/optional-key :skip?) sc/Bool}
   (sc/optional-key :mml)           {(sc/optional-key :yhteystiedot) {:uri-template ssc/NonBlankStr}}
   :mongo                           mongo-schema/ConnectionArgs
   (sc/optional-key :mongo-watcher) {:enabled? sc/Bool}
   (sc/optional-key :noir)          {:noir-opts   noir-schema/NoirOptions
                                     :cookie-name ssc/NonBlankStr}
   (sc/optional-key :pubsub)        pubsub-schema/Config
   (sc/optional-key :reitit)        {:cookie-name                ssc/NonBlankStr
                                     (sc/optional-key :refresh?) sc/Bool}
   ;; Note about `deve`: The lupamonster CI job (unintentionally) uses `deve` as the target env.
   :target-env                      (sc/enum "local" "dev" "deve" "test" "qa" "prod")
   (sc/optional-key :visma-sign)    visma-sign-schema/Config})

(def valid-config! (system-util/config-validator! Config))

(def filters-and-analyzers {:filter   {:prefix_edge          {:type              "edge_ngram"
                                                              :min_gram          3
                                                              :max_gram          20
                                                              :preserve_original true}
                                       :remove_leading_zeros {:type        "pattern_replace"
                                                              :pattern     "^0+"
                                                              :replacement ""}}
                            :analyzer {:prefix               {:tokenizer "standard"
                                                              :filter    ["lowercase" "prefix_edge"]}
                                       :prefix_search        {:tokenizer "standard"
                                                              :filter    ["lowercase"]}
                                       :remove_leading_zeros {:tokenizer "standard"
                                                              :filter    ["lowercase" "remove_leading_zeros"]}}})

(defn config
  [& {:keys [mongo-watcher-enabled?
             override-http-port
             disable-ssl?]}]
  (let [host      (env/value :host)
        http-port (or override-http-port env/port)
        use-http? (or (and host http-port)
                      override-http-port)]
    ;; TODO: Ideally this ought to be `.edn` + e.g. `aero`
    (-> {:bim           (when (env/value :bim)
                          {:bim-viewer-max-filesize (or (env/value :bim :viewer-max-filesize) 524288000)})
         :dev-mode?     (env/dev-mode?)
         :target-env    env/target-env
         :elastic       (when (env/value :elasticsearch)
                          {:enabled? (or (env/value :elasticsearch :enabled) false)
                           :settings {:shards   (or (env/value :elasticsearch :settings :shards) 0)
                                      :replicas (or (env/value :elasticsearch :settings :replicas) 0)}
                           :hosts    (or (env/value :elasticsearch :hosts) {})
                           :indices  [;; Increasing major version creates a new index (equivalent to
                                      ;; collection in mongo) in elasticsearch.
                                      ;; New index is needed when fields are removed or field types
                                      ;; change. New fields can be added without increasing major
                                      ;; version.

                                      ;; Increasing the minor version triggers an index update when the
                                      ;; application starts. The minor version should be increased when
                                      ;; new fields are added or the contents of existing fields change,
                                      ;; but the field types stay the same.
                                      {:name     :elastic.index/applications
                                       :version  {:major 7 :minor 1}
                                       :settings {:analysis filters-and-analyzers}}
                                      {:name     :elastic.index/foremen
                                       :version  {:major 3 :minor 1}
                                       :settings {:analysis filters-and-analyzers}}]})
         :host          host
         :http          (when use-http?
                          (merge {:send-server-version? false
                                  :join?                false
                                  :port                 http-port}
                                 (when-let [max-threads (env/value :jetty :max-threads)]
                                   {:max-threads max-threads})
                                 (when (and (env/value :ssl :enabled)
                                            (not disable-ssl?))
                                   (-> (env/value :ssl)
                                       (assoc :ssl? true)
                                       (dissoc :enabled)))))
         :laundry       (when (env/value :laundry)
                          {:url                 (env/value :laundry :url)
                           :iap-oauth?          (env/feature? :iap-oauth-client)
                           :iap-oauth-client-id (env/value :laundry :iap-oauth-client-id)})
         :migration     {}
         :mml           (when (env/value :mml)
                          (cond-> {}
                            (env/value :mml :yhteystiedot)
                            (assoc :yhteystiedot {:uri-template (env/value :mml :yhteystiedot :uri-template)})))
         :mongo         {:dbname                        (env/value :mongodb :dbname)
                         :servers                       (env/value :mongodb :servers)
                         :credentials                   {:username (env/value :mongodb :credentials :username)
                                                         :password (env/value :mongodb :credentials :password)}
                         :ssl-enabled                   (env/value :mongodb :ssl-enabled)
                         :ssl-invalid-host-name-allowed (env/value :mongodb :ssl-invalid-host-name-allowed)}
         :mongo-watcher (when (env/value :mongo-watcher)
                          {:enabled? (or (env/value :mongo-watcher :start-in-server-instance)
                                         mongo-watcher-enabled?
                                         false)})
         :noir          (when use-http?
                          {:noir-opts   (-> {:mode             (keyword (or (env/value :noir :mode) env/mode))
                                             :ns               'lupapalvelu.web
                                             ;; Options for noir default resource handler, /* checks classpath under public/
                                             ;; These options are forwarded to compojure.route/resources
                                             ;; Mime-types option goes to ring.util.mime-type/ext-mime-type
                                             :resource-options {:mime-types {"js" "application/javascript; charset=utf-8"}}}
                                            (util/assoc-when :session-cookie-attrs (env/value :cookie)))
                           :cookie-name (env/value :cookie-name)})
         :reitit        (when use-http?
                          {:cookie-name (env/value :cookie-name)
                           :refresh?    (env/dev-mode?)})
         :pubsub        (when (env/feature? :pubsub)
                          {:enabled?     (= (env/value :integration-message-queue) "pubsub")
                           :queue-prefix (env/value :integration-message-queue-user-prefix)
                           :client       {:ack-deadline-seconds          (-> (env/value :gcp :pubsub :ack-deadline-seconds)
                                                                             (util/->int 300))
                                          ;; Seems this is still a bit buggy and causes slowness and weird gcp ack errors
                                          :enable-exactly-once-delivery? false
                                          :endpoint                      (env/value :gcp :pubsub :endpoint)
                                          :project-id                    (env/value :gcs :project)
                                          :publisher-delay-threshold-ms  100
                                          :service-account-file          (env/value :gcs :service-account-file)
                                          :thread-count                  (-> (env/value :gcp :pubsub :executor-thread-count)
                                                                             (util/->int 1))}
                           :topics       {:allu/message-queue            {:topic      "lupapalvelu.backing-system.allu"
                                                                          :subscribe? (env/feature? :integration-message-queue)
                                                                          :publish?   (env/feature? :integration-message-queue)}
                                          :archive/api-usage             {:topic      "onkalo.api-usage"
                                                                          :subscribe? true}
                                          :asiointi-api/notifications    {:topic      "lupapalvelu.asiointiapi.notifications"
                                                                          :publish?   (env/feature? :asiointi-api)
                                                                          :subscribe? (env/feature? :asiointi-api)}
                                          :attachment/preview            {:topic    "to-conversion-service"
                                                                          :publish? true}
                                          :backing-system/http           {:topic      "lupapiste.kuntagml.http"
                                                                          :publish?   (env/feature? :integration-message-queue)
                                                                          :subscribe? (env/feature? :integration-message-queue)}
                                          :batchrun/fetch-verdicts       {:topic      "lupapiste.fetch-verdicts"
                                                                          :publish?   true
                                                                          :subscribe? true
                                                                          :options    {:thread-count            15
                                                                                       :max-elements-per-thread 3}}
                                          :bim/to-bim-service            {:actual-topic (or (env/value :bim :bimmer-pubsub-topic)
                                                                                            "to-bim-service")
                                                                          :publish?     true}
                                          :bim/checking-response         {:topic      "from-bim-check-service"
                                                                          :publish?   true
                                                                          :subscribe? true}
                                          :bim/extraction-response       {:topic      "from-bim-service"
                                                                          :subscribe? true}
                                          :bim/ifc-to-xkt-response       {:topic      "from-bim-xkt-service"
                                                                          :subscribe? true}
                                          :bim/internal-checking-request {:topic      "internal-bim-checking-request"
                                                                          :subscribe? true
                                                                          :publish?   true}
                                          :dmcity/state-change-queue     {:topic      "lupapiste.application.state-change"
                                                                          :subscribe? (env/feature? :integration-message-queue)
                                                                          :publish?   (env/feature? :integration-message-queue)}
                                          :notices/resolution-queue      {:topic      "lupapiste.notice.resolution"
                                                                          :subscribe? true
                                                                          :publish?   true}
                                          :ryhti/permanent-id            {:topic      "lupapalvelu.ryhti.permanent-id-fetcher"
                                                                          :publish?   (env/feature? :fetch-ryhti-plt)
                                                                          :subscribe? (env/feature? :fetch-ryhti-plt)}}})
         :visma-sign    (env/value :visma-sign)}
        util/strip-blanks
        valid-config!)))
