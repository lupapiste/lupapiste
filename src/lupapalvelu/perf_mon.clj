(ns lupapalvelu.perf-mon
  (:require [integrant.core :as ig]
            [lupapalvelu.logging :as log :refer [debug infof]]
            [monger.operators :refer :all])
  (:import [clojure.lang Var]))

(set! *warn-on-reflection* true)

;; TODO: Replace all of this with OpenTelemetry

;;
;; Instrumenting:
;;

(defn instrument! [f ^Var v]
  (let [old (deref v)]
    (alter-var-root v f (str (.ns v) \/ (.sym v)))
    [v old]))

(defn unstrument! [v val]
  (alter-var-root v (constantly val)))

(defn instrument-ns
  "Instrument public functions of namespace(s) with `f`.
  Returns a vector of old vars and values"
  [f & namespaces]
  (->> namespaces
       (mapcat (fn [n]
                 (->> (ns-publics n)
                      vals
                      (remove (comp :perfmon-exclude meta))
                      (filter (comp fn? deref)))))
       (map (fn [v] (instrument! f v)))
       (into {})))


;;
;; State:
;;

(defonce db-throttle (atom 0))
(defonce web-throttle (atom 0))
(def ^:dynamic *perf-context* nil)

;;
;; Utils:server
;;

(defn- bypass? [request]
  (let [uri (:uri request)]
    (or (get-in request [:query-params "npm"])
        (get-in request [:headers "npm"])
        (re-matches #"^\/api\/alive.*" uri)
        (not (re-matches #"^\/api\/.*" uri)))))

;;
;; Performance monitoring:
;;

(defn wrap-perf-mon [f f-name]
  (fn [& args]
    (if-let [context *perf-context*]
      (let [sub-context (atom [])]
        (binding [*perf-context* sub-context]
          (let [start (System/nanoTime)]
            (try
              (apply f args)
              (finally
                (let [end (System/nanoTime)]
                  (swap! context conj [f-name (- end start) @sub-context])))))))
      (apply f args))))

(defn- get-total-db-call-duration [perf-context]
  (->> perf-context (map second) (reduce +)))

(defn ns->ms [ns] (quot ns 1000000))

(defn log-perf-context [handler request]
  (if (bypass? request)
    (handler request)
    (binding [*perf-context* (atom [])]
      (let [start (System/nanoTime)]
        (try
          (handler request)
          (finally
            (let [end (System/nanoTime)]
              (debug (log/sanitize 200 (get request :uri)) ":"
                     (ns->ms (- end start)) "ms,"
                     (count @*perf-context*) "db calls took totally"
                     (ns->ms (get-total-db-call-duration @*perf-context*)) "ms"))))))))

(defn perf-mon-middleware [handler] (fn [request] (log-perf-context handler request)))

;;
;; Throttling:
;;

(defn wrap-db-throttle [f _]
  (fn [& args]
    (Thread/sleep ^long @db-throttle)
    (apply f args)))

(defn throttle-middleware [handler]
  (fn [request]
    (when-not (bypass? request) (Thread/sleep ^long @web-throttle))
    (handler request)))

(defn to-long ^Long [v]
  (when v
    (if (string? v) (Long/parseLong v) (long v))))

;; We alter state (the vars) outside the system. Similar to e.g. `with-redefs`,
;; this is not re-entrant! In other words, you cannot have multiple systems running
;; this concurrently, unlike e.g. HTTP servers in different ports. So, only use this
;; component when running Lupapiste proper, not in itests.
(defonce *install-counter (atom 0))

(defmethod ig/init-key ::instrumentation [k {:keys [dev-mode?]}]
  (infof "Starting %s..." k)
  (let [counter (deref *install-counter)]
    (when-not (zero? counter)
      (throw (ex-info "Instrumentation already installed!" {:counter counter}))))
  (swap! *install-counter inc) ;; 0 -> 1
  (let [originals (instrument-ns wrap-perf-mon 'lupapalvelu.mongo)]
    (when dev-mode?
      (instrument-ns wrap-db-throttle 'lupapalvelu.mongo))
    originals))

(defmethod ig/halt-key! ::instrumentation [k instrumented]
  (infof "Stopping %s..." k)
  (let [counter (deref *install-counter)]
    (when-not (= 1 counter)
      (throw (ex-info "Expected instrumentation to be installed once!" {:counter counter}))))
  (swap! *install-counter dec) ;; 1 -> 0
  (->> instrumented
       (run! (fn [[v val]]
               (unstrument! v val)))))
