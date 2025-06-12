(ns lupapalvelu.system.system-util
  "Utilities for running a system.

  Sometimes we need a system for a moment while running some other task
  (migrations, batchruns, smoke tests..). This namespace provides utilities
  to run code within a running system."
  (:require [integrant.core :as ig]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.system.build :as build]
            [lupapalvelu.system.config :as config]
            [lupapalvelu.system.system :as system]
            [lupapalvelu.system.util-components :as components]))

(defonce ^{:private true :dynamic true} *system* nil)

(defn get-raw-system
  "Return the running system."
  []
  {:pre [(map? *system*)]}
  *system*)

(defn get-system-components
  "Return the common system components.
  Similar to what you'll find under the `:system` key in commands etc."
  []
  {:post [(map? %)]}
  (-> (get-raw-system)
      ::components/common))

(defmacro with-existing-system
  "Run `body` while providing an existing `system`.

  Useful if you already have a running system somewhere (e.g. `integrant.repl.state/system`)
  and you want to run a piece of code within it."
  [system & body]
  `(let [system# ~system]
     (when-not system#
       (throw (ex-info "No system provided!" {})))
     (binding [*system*           system#
               mongo/*connection* (::mongo/connection system#)]
       (do ~@body))))

(defmacro with-system
  "Start system with given components while running `body`"
  [system-keys & body]
  `(do
     (log/info "Building and starting system..")
     (let [system# (-> (config/config)
                       build/build-system-lupapiste
                       (system/init! ~system-keys))]
       (try
         (with-existing-system system#
           ~@body)
         (catch Exception e#
           (log/error e# "Exception while running body")
           (throw e#))
         (finally
           (log/info "Stopping system..")
           (ig/halt! system#)
           (log/info "System stopped."))))))

(defmacro with-mongo
  "Start a minimal system with just a plain MongoDB connection while running `body`"
  [& body]
  ;; NOTE: Because we use `::mongo/connection` rather than `::mongo/database`,
  ;; we won't run migrations or create indexes etc. We just start the MongoDB connection.
  `(with-system #{::mongo/connection}
     ~@body))
