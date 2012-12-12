(ns lupapalvelu.perf-mon
  (:require [clojure.string :refer [join]]))

(comment
  ; Prints call hierarchy:
  (defn perf-logger
    ([context]
      (println (with-out-str (perf-logger context ""))))
    ([[[f-name args duration sub-contexts] & r] indent]
      (println indent f-name (format "%.3f ms" (/ duration 1000000.0)))
      (doseq [sub-context sub-contexts]
        (perf-logger [sub-context] (str indent "   ")))
      (when r (perf-logger r indent)))))

(defn- append-summary [summary [f-name args duration sub-contexts]]
  (let [[c v] (get summary f-name [0 0.0])
        summary (assoc summary f-name [(inc c) (+ v duration)])
        summary (reduce append-summary summary sub-contexts)]
    summary))

(defn perf-logger [context]
  (let [summary (reduce append-summary {} context)]
    (println
      (with-out-str
        (let [k (ffirst context)
              [c v] (get summary k)]
          (println (format "%s: %5d:  %9.3f ms (%.3f ms)" k c (/ v 1000000.0) (/ (/ v 1000000.0) c)))
          (doseq [[k [c v]] (dissoc summary k)]
            (println (format "   %-60s: %5d:  %9.3f ms (%.3f ms)" k c (/ v 1000000.0) (/ (/ v 1000000.0) c)))))))))

(defn now [] (System/nanoTime))

(def ^:dynamic *perf-context* nil)

(defn- wrap-perf [f f-name]
  (fn [& args]
    (let [context *perf-context*
          sub-context (atom [])]
      (binding [*perf-context* sub-context]
        (let [start (now)
              result (apply f args)
              end (now)]
          (if context
            (swap! context conj [f-name args (- end start) @sub-context])
            (perf-logger [[f-name args (- end start) @sub-context]]))
          result)))))

(defn instrument-ns [& namespaces]
  (doseq [n namespaces
          [k v] (filter (comp fn? deref val) (ns-publics n))]
    (alter-var-root v wrap-perf (str n \/ k))))
