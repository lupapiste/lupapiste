(ns lupapalvelu.system.system
  (:require [integrant.core :as ig]
            [lupapalvelu.logging :as log]
            [sade.core :as core]))

(set! *warn-on-reflection* true)

(defn init!
  "Build an `integrant` system using the given config.
   Returns the created system. "
  ([system-config]
   (init! system-config (keys system-config)))
  ([system-config system-keys]
   (try
     (let [start-ts (core/now)
           system   (ig/init system-config system-keys)
           end-ts   (core/now)]
       (log/infof "Started: %s in %d ms!" (keys system) (- end-ts start-ts))
       system)
     (catch Exception e
       (log/warn e "Exception when starting system!")
       (let [{:keys [system]} (ex-data e)]
         (when system
           (log/warn "Stopping partially started system..")
           (try
             (ig/halt! system)
             (catch Exception e2
               (log/warn e2 "Exception when stopping system!")))))
       (throw e)))))

(defn halt!
  [system-atom]
  (let [[old _new] (reset-vals! system-atom nil)]
    (some-> old ig/halt!)))
