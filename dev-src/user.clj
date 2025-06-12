(ns user
  (:require [clj-reload.core :as reload]
            [integrant.repl :as integrant-repl]
            [integrant.repl.state :as integrant-repl-state]
            [malli.dev :as malli-dev]))

(set! *warn-on-reflection* true)

(defn- load-server []
  (println "Loading system namespaces...")
  (let [now (System/currentTimeMillis)]
    (require 'lupapalvelu.server)
    (println (format "Namespaces loaded in %d ms" (- (System/currentTimeMillis) now)))))

(defn- make-prep
  []
  (load-server)
  ;; code has been loaded -> enable malli function instrumentation
  (malli-dev/start!)
  (let [build-lupapiste (deref (requiring-resolve 'lupapalvelu.server/build-lupapiste))]
    (build-lupapiste)))

(integrant-repl/set-prep! make-prep)

(def go
  "Load & start the system"
  integrant-repl/go)

;; Workaround for https://github.com/weavejester/integrant-repl/pull/28
(defn- fixed-reload []
  (let [result (reload/reload)]
    (prn :reloaded (apply list (:loaded result)))))

(defn reset
  "Suspend, reload sources, and resume the system"
  []
  (integrant-repl/suspend)
  (fixed-reload)
  (integrant-repl/resume))

(def halt
  "Stop the system"
  integrant-repl/halt)

(def reload
  "Reload modified namespaces, but do not alter the system.
   NOTE: Also reloads (and runs!) any previously loaded midje tests."
  fixed-reload)

(defn system
  "Grab the current system components"
  []
  (get integrant-repl-state/system :lupapalvelu.system.util-components/common))

(defn mongo-watcher-go
  "This command can be used to start a local REPL in mongo-watcher mode.
   Note that when mongo-watcher runs in a dedicated REPL, the lupapiste.server
   REPL should have the configuration
   - mongo-watcher.start-in-server-instance false
  to prevent multiple simultaneous watchers."
  []
  (println "Loading lupapalvelu.mongo-watcher.core and launching mongo-watcher...")
  ((requiring-resolve 'lupapalvelu.mongo-watcher.core/-main)))

(defn mongo-watcher-halt
  "Stop mongo-watcher if started with `mongo-watcher-go`"
  []
  (println "Stopping mongo-watcher..")
  ((requiring-resolve 'lupapalvelu.mongo-watcher.core/halt!)))

(defmacro s
  "Helper for running debug code using the MongoDB connection from the system

  ```clojure
  ;; In some random namespace:
  (comment
    ;; do stuff with the implicit mongo connection
    (user/s
      (println (mongo/count :users))
      (println (mongo/count :applications))))
  ```"
  [& body]
  (assert (find-ns 'lupapalvelu.system.system-util)
          "Util namespace not found, did you forget to run `(go)`?")
  (assert (map? integrant-repl-state/system)
          "Running system not found, did you forget to run `(go)`?")
  `(lupapalvelu.system.system-util/with-existing-system integrant-repl-state/system ~@body))

(defn connect-mongo!
  "Helper for establishing `*connection*`.

  NOTE: Only enable temporarily if you need to access MongoDB directly from the REPL.
  The bound `*connection*` is visible everywhere so you might e.g. accidentally
  write tests which work accidentally because the connection is available and
  the test will read/write to your local development database.

  ```clojure
  > (user/connect-mongo!) ;; => :connected
  > (mongo/ ...)
  > (mongo/ ...)
  > (user/disconnect-mongo!) ;; => :disconnected
  ```"
  []
  (assert (map? integrant-repl-state/system)
          "Running system not found, did you forget to run `(go)`?")
  (let [v (requiring-resolve 'lupapalvelu.mongo/*connection*)]
    (alter-var-root v (constantly (:mongo (system))))
    :connected))

(defn disconnect-mongo!
  "Helper for disconnecting `*connection*`"
  []
  (assert (map? integrant-repl-state/system)
          "Running system not found, did you forget to run `(go)`?")
  (let [v (requiring-resolve 'lupapalvelu.mongo/*connection*)]
    (alter-var-root v (constantly nil))
    :disconnected))
