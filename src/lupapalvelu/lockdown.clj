(ns lupapalvelu.lockdown
  (:require [clojure.core.memoize :as memo]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$set]]
            [sade.env :as env])
  (:import [java.time Duration]))

(set! *warn-on-reflection* true)

;; Avoid hammering the db if `lockdown?` is called repeatedly
(def ^:private lockdown-ttl-ms (.toMillis (Duration/ofSeconds 5)))

(def ^:private lockdown-status-id "lockdown-status")

(defn lockdown-from-db?
  []
  (mongo/any? :admin-config {:_id         lockdown-status-id
                             :in-lockdown true}))

(def lockdown?
  "Check if system is in lockdown."
  (memo/ttl lockdown-from-db? :ttl/threshold lockdown-ttl-ms))

(defn- sleep-after
  []
  (Thread/sleep ^long lockdown-ttl-ms))

(defn- set-lockdown!
  "Set lockdown status"
  [enabled?]
  (log/warn "Setting lockdown:" (if enabled? "enabled" "disabled"))
  (mongo/update-by-id :admin-config
                      lockdown-status-id
                      {$set {:in-lockdown (boolean enabled?)}}
                      :upsert true)
  ;; Give other participants time to realize the lock is enabled
  ;; before the caller can proceed
  (when enabled?
    ;; No need to wait when developing locally etc.
    (when-not (env/dev-mode?)
      (sleep-after))))

(defmacro in-lockdown
  "Makes sure lockdown is enabled while executing `body`. Does not
  alter lockdown state if lockdown was already enabled. If lockdown
  was enabled for this call, attempts to clear it afterwards.

  Naturally, the caller should evaluate any lazy results within `body`
  as the lockdown will not be in effect afterwards."
  [& body]
  `(let [old-val# (lockdown-from-db?)]
     (try
       (when-not old-val# (#'set-lockdown! true))
       (do ~@body)
       (finally
         (when-not old-val#
           (#'set-lockdown! false))))))

(defn lock!
  "Manual forced lockdown"
  []
  (set-lockdown! true))

(defn unlock!
  "Fail-safe for unlocking, in case `in-lockdown` failed to unlock (JVM crash, OOM, connection error..)"
  []
  (set-lockdown! false))
