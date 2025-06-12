(ns lupapalvelu.itest-system
  (:require [clj-http.conn-mgr :as conn]
            [clojure.set :as set]
            [integrant.core :as ig]
            [lupapalvelu.elasticsearch.client :as elastic]
            [lupapalvelu.http.jetty :as jetty]
            [lupapalvelu.integrations.pubsub :as pubsub]
            [lupapalvelu.laundry.client :as laundry]
            [lupapalvelu.laundry.proto :as proto]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.system.build :as build]
            [lupapalvelu.system.config :as config]
            [lupapalvelu.system.system :as system]
            [lupapalvelu.system.util-components :as components]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]])
  (:import [java.io ByteArrayInputStream]))

(set! *warn-on-reflection* true)

(defonce ^{:private true} *itest-system (atom nil))

(defonce default-test-db (str "test_" (now)))

(def http-server-port (inc env/port))

(def test-config
  (let [default-config (config/config)]
    (-> {:bim           {:bim-viewer-max-filesize (env/value :bim :viewer-max-filesize)}
         :dev-mode?     true
         :elastic       (merge (-> default-config :elastic)
                               {:enabled?     false
                                :testing?     true
                                :index-prefix default-test-db})
         :host          (str "http://localhost:" http-server-port)
         :http          {:max-threads          50
                         :send-server-version? false
                         :join?                false
                         :port                 http-server-port}
         :laundry       {:url                 (env/value :laundry :url)
                         :iap-oauth?          (env/feature? :iap-oauth-client)
                         :iap-oauth-client-id (env/value :laundry :iap-oauth-client-id)}
         :migration     {:skip? true} ; saves 0.5-1.0 second per test
         :mml           {:yhteystiedot {:uri-template (format "http://localhost:%s/dev/mml/yhteystiedot-LU.xml"
                                                              http-server-port)}}
         :mongo         {:dbname      "lupapiste"
                         :current-db  default-test-db
                         :servers     (env/value :mongodb :servers)
                         :credentials {:username (env/value :mongodb :credentials :username)
                                       :password (env/value :mongodb :credentials :password)}}
         :mongo-watcher {:enabled? false}
         :noir          {:noir-opts   {:mode                 :prod
                                       :ns                   'lupapalvelu.web
                                       :resource-options     {:mime-types {"js" "application/javascript; charset=utf-8"}}
                                       :session-cookie-attrs (env/value :cookie)}
                         :cookie-name (env/value :cookie-name)}
         :pubsub        {:enabled?     false
                         ;; Alter the prefix so we can separate the integration test topics/queues from the actual topics/queues
                         ;; used for real Lupapiste use (in local development or QA or prod or ..).
                         ;; After the test is done, we can safely clear the queue as it's only used in tests.
                         :queue-prefix (str (env/value :integration-message-queue-user-prefix)
                                            "-test")
                         :client       {:ack-deadline-seconds          300
                                        :enable-exactly-once-delivery? false
                                        :endpoint                      (env/value :gcp :pubsub :endpoint)
                                        :project-id                    (env/value :gcs :project)
                                        :publisher-delay-threshold-ms  100
                                        :service-account-file          (env/value :gcs :service-account-file)
                                        :thread-count                  1}
                         ;; Same topics as in the default config, but disabled by default
                         :topics       (->> default-config
                                            :pubsub
                                            :topics
                                            (map (fn [[topic-id conf]]
                                                   [topic-id (-> conf
                                                                 ;; NOTE: We could set `:remove-on-shutdown?`
                                                                 ;; here for all topics. However, some future test might
                                                                 ;; use a "real" topic so maybe let the individual
                                                                 ;; tests decide themselves.
                                                                 (dissoc :subscribe? :publish?))]))
                                            (into {}))}
         :reitit        {:cookie-name (env/value :cookie-name)
                         :refresh?    false}
         :target-env    env/target-env
         :visma-sign    {:base-url          (format "http://localhost:%s/dev/dummy-visma-sign"
                                                    http-server-port)
                         :client-identifier "abc123"
                         :secret            "abc123"
                         :success           {:email "foobar@example.com"}}}
        util/strip-blanks
        config/valid-config!)))

;; Test components

(defmethod ig/init-key ::connection-manager [k _]
  (log/infof "Starting %s..." k)
  (conn/make-reusable-conn-manager {:timeout 60, :threads 4, :default-per-route 4}))

(defmethod ig/halt-key! ::connection-manager [k itest-conn-mgr]
  (log/infof "Stopping %s..." k)
  (conn/shutdown-manager itest-conn-mgr))

(defrecord MockLaundryClient []
  proto/LaundryClient
  (get-pdf-package [_ _content]
    (log/info "Mocking `get-pdf-package`..")
    {:ok              true
     :pdf-file-stream (ByteArrayInputStream. (util/file->byte-array "dev-resources/test-pdf.pdf"))}))

(derive ::mock-laundry :laundry/client)

(defmethod ig/init-key ::mock-laundry [k {:keys []}]
  (log/infof "Starting %s..." k)
  (->MockLaundryClient))

(defn with-test-components
  [system-map]
  (-> system-map
      (assoc ::connection-manager {})
      (assoc ::mock-laundry (:laundry test-config))))

(def default-system-keys #{::mongo/database
                           ::components/common
                           ::connection-manager})

(defn with-extra-system-keys
  [opts & ks]
  (update opts :extra-system-keys (fnil into #{}) ks))

(defn with-remote-components
  "Add component(s) for starting the test HTTP server for remote actions"
  [opts]
  (with-extra-system-keys opts ::jetty/server))

(defn with-mock-laundry
  "Add components for mocking PDF generation with laundry"
  [opts]
  (with-extra-system-keys opts ::mock-laundry))

(defn with-real-laundry
  "Add components for using real PDF generation with laundry"
  [opts]
  ;; Refer to the concrete implementation (`::laundry/client-impl`)
  ;; rather than the generic "any laundry" (`:laundry/client`)
  (with-extra-system-keys opts ::laundry/client-impl))

(defn with-elastic
  "Add components for using Elasticsearch client"
  [opts]
  (-> opts
      (with-extra-system-keys ::elastic/client)
      (update :update-config (fnil comp identity) (fn [config]
                                                    (-> config
                                                        (assoc-in [:elastic :enabled?] true))))))

(defn with-pubsub-publisher
  "Add components for using a pubsub publisher for a particular topic"
  [opts topic-id]
  (-> opts
      (with-extra-system-keys [::pubsub/publisher topic-id])
      (update :update-config (fnil comp identity) (fn [config]
                                                    (-> config
                                                        (assoc-in [:pubsub :enabled?] true)
                                                        (assoc-in [:pubsub :topics topic-id :publish?] true))))))

(defn with-pubsub-subscriber
  "Add components for using a pubsub subscriber for a particular topic

  If `remove-on-shutdown?` is set, we will remove the subscription and any messages
  after the test. Note that this also slows down the test; we must (re)create the subscription
  if the previous test run removed it."
  [opts topic-id & {:keys [remove-on-shutdown?]}]
  (-> opts
      (with-extra-system-keys [::pubsub/subscriber topic-id])
      (update :update-config (fnil comp identity) (fn [config]
                                                    (cond-> (-> config
                                                                (assoc-in [:pubsub :enabled?] true)
                                                                (assoc-in [:pubsub :topics topic-id :subscribe?] true))
                                                      remove-on-shutdown? (assoc-in [:pubsub :topics topic-id :remove-on-shutdown?] true))))))

(def Component (sc/cond-pre sc/Keyword [sc/Keyword]))

(defschema TestSystemOpts
  {(sc/optional-key :db-name)           ssc/NonBlankStr
   (sc/optional-key :extra-system-keys) #{Component}
   (sc/optional-key :update-config)     ssc/Fn})

(def valid-opts! (sc/validator TestSystemOpts))

(defn init!
  "Initialize the test system
  * `db-name` -- Name of the test database
  * `extra-system-keys` -- Which components to start, in addition to the defaults.
  * `update-config` -- Update the default test config with the given fn."
  [& {:keys [db-name extra-system-keys update-config] :as opts}]
  (valid-opts! opts)
  (when (nil? (deref *itest-system))
    (let [config               (cond-> test-config
                                 db-name       (assoc-in [:mongo :current-db] db-name)
                                 db-name       (assoc-in [:elastic :index-prefix] db-name)
                                 update-config update-config
                                 true          config/valid-config!)
          system-keys          (cond-> default-system-keys
                                 extra-system-keys (set/union extra-system-keys))
          blueprint            (-> config
                                   build/build-system-lupapiste
                                   with-test-components)
          available-components (set (keys blueprint))]
      (when (not (set/subset? system-keys available-components))
        (throw (ex-info "Attempting to start unknown components"
                        {:unknown   (set/difference system-keys available-components)
                         :available available-components})))
      (reset! *itest-system (system/init! blueprint system-keys)))))

(defn halt!
  []
  (when (deref *itest-system)
    (system/halt! *itest-system)))

;; NOTE: This hook is executed only when using a trampoline, e.g. running `lein trampoline itest`
;; Otherwise cancelling the tests with `ctrl+c` just kills the outer leiningen process, which then kills
;; the test process without running the shutdown hooks.
(def on-shutdown-hook (Thread. ^Runnable (fn [] (system/halt! *itest-system))))

(try
  (.addShutdownHook (Runtime/getRuntime) on-shutdown-hook)
  (catch IllegalArgumentException _
    (log/info "Shutdown hook already installed, ignoring..")))

(defn- test-system
  []
  (deref *itest-system))

(defn get-common-components
  []
  {:pre  [(map? (test-system))]
   :post [(map? %)]}
  (::components/common (test-system)))

(defn get-mongo
  []
  {:pre  [(map? (test-system))]
   :post [(some? %)]}
  (::mongo/database (test-system)))

(defn get-connection-manager
  []
  {:pre  [(some? (test-system))]
   :post [(some? %)]}
  (::connection-manager (test-system)))

(defn get-publisher
  [topic-id]
  {:pre  [(some? (test-system))]
   :post [(some? %)]}
  (-> (get-common-components)
      (components/system->publisher topic-id)))

(defn get-elastic
  []
  {:pre  [(map? (test-system))]
   :post [(some? %)]}
  (::elastic/client (test-system)))

(defn get-server-address
  "Return the address for the HTTP server (e.g. `http://localhost:8000`)"
  []
  {:pre  [(some? (test-system))]
   :post [(string? %)]}
  (-> (get-common-components)
      components/system->host))

(defn get-topic-name
  [topic-id]
  {:pre  [(some? (test-system))]
   :post [(string? %)]}
  (-> (get-common-components)
      (components/system->actual-topic-name topic-id)))

(defn fix-urls
  "Update hard-coded URLs (e.g. `http://localhost:8000`) to match the running system."
  [document]
  (let [port (-> (get-common-components)
                 components/system->http-port)]
    (assert (int? port))
    (cond
      (string? document)
      (ss/replace document #"localhost:8000" (str "localhost:" port))

      (map? document)
      (ss/stringwalk fix-urls document)

      :else
      (throw (ex-info "Unknown document" {:document document})))))
