(ns lupapalvelu.perf-mon
  (:require [clojure.string :refer [join]]
            [lupapalvelu.env :refer [dev-mode?]]))

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
          (when context
            (swap! context conj [f-name args (- end start) @sub-context]))
          result)))))

(defn perf-mon-instrument-ns [& namespaces]
  (when (dev-mode?)
    (doseq [n namespaces
            [k v] (filter (comp fn? deref val) (ns-publics n))]
      (alter-var-root v wrap-perf (str n \/ k)))))

(defn perf-logger
  ([context]
    (println (with-out-str (perf-logger context ""))))
  ([[[f-name args duration sub-contexts] & r] indent]
    (println indent f-name (vec args) (format "%.3f ms" (/ duration 1000000.0)))
    (doseq [sub-context sub-contexts]
      (perf-logger [sub-context] (str indent "   ")))
    (when r (perf-logger r indent))))

(defmacro with-perf-mon [& statements]
  (if (dev-mode?)
    `(let [context# (atom [])]
       (binding [*perf-context* context#]
         (let [result# (do ~@statements)]
           (perf-logger (deref context#))
           result#)))
    `(do ~@statements)))
