(ns lupapalvelu.throttler
  (:require [lupapalvelu.logging :as log])
  (:import [java.util.concurrent Executors ScheduledExecutorService TimeUnit]))

(set! *warn-on-reflection* true)

(defonce ^:private scheduler (Executors/newScheduledThreadPool 1))

(defn ^:dynamic schedule [delay-ms f]
  ;; `f` may rely on dynamic bindings; make sure they are installed
  ;; also on the scheduled thread
  (.schedule ^ScheduledExecutorService scheduler
             ^Runnable (bound-fn* f)
             ^long delay-ms
             TimeUnit/MILLISECONDS))

(defn make-throttler
  "Creates a throttled function executor.

  The returned function evaluates the first function call immediately, then
  blocks subsequent evaluations for the given delay (in milliseconds). If
  additional calls are made during the throttle period, only the latest one is
  retained and executed after the delay. Execution of the latest call after the
  throttle period start a new throttle period.

  Best suited for scenarios involving numerous intensive operations within a
  short timeframe, where only the most recent operation holds significance. Note
  that if the system happens to crash during the throttle period, the most
  recent operation will not be executed.

  The returned function (throttler) always returns nil.

  Example usage:
  ```clojure
  (def throttled-evaluation (make-throttler 1000))

  (throttled-evaluation #(println \"Saving value... 1\"))
  (throttled-evaluation #(println \"Saving value... 2\"))
  (throttled-evaluation #(println \"Saving value... 3\"))
  (throttled-evaluation #(println \"Saving value... 4\"))

  => Output:
  Saving value... 1
    ... A few moments later ...
  Saving value... 4
  ```
  "
  [^long delay-ms]
  (let [*state (atom {:throttling? false
                      :pending-fn  nil})]
    (letfn [(run-pending-fn-after-throttling []
              (schedule delay-ms #(let [[old _new] (swap-vals! *state
                                                               (fn [state]
                                                                 (-> state
                                                                     (dissoc :pending-fn)
                                                                     (assoc :throttling? (-> state :pending-fn boolean)))))]
                                    (when-let [[pending-fn args] (:pending-fn old)]
                                      (try
                                        (apply pending-fn args)
                                        (catch Throwable t
                                          (log/error t "Failed to run throttled fn")
                                          (throw t))
                                        (finally
                                          (run-pending-fn-after-throttling))))))
              nil)]
      (fn [f & args]
        (let [[old _new] (swap-vals! *state (fn [state]
                                              (if (:throttling? state)
                                                (assoc state :pending-fn [f args])
                                                (assoc state :throttling? true))))]
          (when-not (:throttling? old)
            (try
              (apply f args)
              (catch Throwable t
                (log/error t "Failed to run throttled fn")
                (throw t))
              (finally
                (run-pending-fn-after-throttling)))))))))
