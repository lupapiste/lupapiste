(ns lupapalvelu.mongo-watcher.core
  (:require [lupapalvelu.http.jetty :as jetty]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo-watcher.client :as mongo-watcher]
            [lupapalvelu.system.build :as build]
            [lupapalvelu.system.config :as config]
            [lupapalvelu.system.system :as system]
            [sade.env :as env]))

(set! *warn-on-reflection* true)

(def watcher-keys #{::mongo-watcher/watcher
                    ::mongo-watcher/event-handler
                    ::jetty/server})

(defonce ^{:private true} *watcher-system (atom nil))

(def ^:private on-shutdown-hook (Thread. ^Runnable (fn []
                                                     (log/info "Shutting down mongo watcher system..")
                                                     (system/halt! *watcher-system))))

(defn -main []
  (log/info "Starting mongo watcher system..")
  ;; Cleanup system when shutting down
  (try
    (-> (Runtime/getRuntime)
        (.addShutdownHook on-shutdown-hook))
    (catch IllegalArgumentException _
      (log/info "Shutdown hook already installed, ignoring..")))
  ;; Start system
  (reset! *watcher-system (-> ;; Override default HTTP config when running mongo-watcher separately
                            (config/config {:mongo-watcher-enabled? true
                                            ;; Use separate port & disable SSL to avoid port collision
                                            :override-http-port     (env/value :mongo-watcher :healthcheck :port)
                                            :disable-ssl?           true})
                            build/build-system-mongo-watcher
                            (system/init! watcher-keys)))
  (log/info "Started mongo watcher system."))

(defn halt!
  []
  (system/halt! *watcher-system))
