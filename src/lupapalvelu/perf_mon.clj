(ns lupapalvelu.perf-mon
  (:use [clojure.tools.logging]
        [noir.core :only [defpage]]
        [monger.operators])
  (:require [clojure.string :refer [join]]
            [clojure.java.io :as io]
            [monger.collection :as mc]
            [noir.response :as resp]
            [noir.request :as request]
            [noir.server :as server]
            [cheshire.core :as json])
  (:import [com.mongodb WriteConcern]))

(defonce db-throttle (atom 0))
(defonce web-throttle (atom 0))

(def ^:dynamic *perf-context* nil)

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

(defn wrap-throttle [f f-name]
  (fn [& args]
    (Thread/sleep @db-throttle)
    (apply f args)))

(defn instrument [f v]
  (alter-var-root v f (str (. v ns) \/ (. v sym))))

(defn instrument-ns [f & namespaces]
  (doseq [n namespaces
          v (filter (comp fn? deref) (vals (ns-publics n)))]
    (instrument f v)))

(defn perf-mon-middleware [handler]
  (fn [request]
    (if (get-in request [:params :npm])
      (handler request)
      (binding [*perf-context* (atom [])]
        (let [start (System/nanoTime)] 
          (try
            (handler request)
            (finally
              (let [end (System/nanoTime)]
                (mc/insert "perf-mon" 
                           {:ts (System/currentTimeMillis)
                            :duration (- end start)
                            :uri (get request :uri)
                            :user (get-in request [:session :noir :user :username])
                            :perfmon @*perf-context*}
                           WriteConcern/NONE)))))))))

(defn throttle-middleware [handler]
  (fn [request]
    (if-not (or (get-in request [:query-params "npm"]) (get-in request [:headers "npm"]))
      (Thread/sleep @web-throttle))
    (handler request)))

(defn get-data [start end]
  (map (fn [row] (dissoc row :_id))
       (mc/find-maps "perf-mon" {$and [{:ts {$gte start}}
                                       {:ts {$lte end}}]})))

(defn- to-long [v]
  (when v
    (if (string? v) (Long/parseLong v) (long v))))

(defn- group-by-uri [data]
  (with-logs "lupapiste"
    (clojure.pprint/pprint data))
  data)

(defn- find-min-max-avg [{:keys [min-val max-val avg cnt] :as m} {duration :duration perfmon :perfmon}]
  (assoc m :min-val (min min-val duration)
           :max-val (max max-val duration)
           :avg (double (/ (+ (* avg cnt) duration) (inc cnt)))
           :cnt (inc cnt)
           :db-cnt (count perfmon)))

(defn- find-min-max-avg* [[k v]]
  [k (reduce find-min-max-avg {:min-val Double/MAX_VALUE :max-val Double/MIN_VALUE :avg 0.0 :cnt 0} v)])

(defpage "/perfmon/data" {:keys [start end]}
  (->> (get-data (or (to-long start) (- (System/currentTimeMillis) (* 5 60 1000)))
                 (or (to-long end) (System/currentTimeMillis)))
    (group-by :uri)
    (map find-min-max-avg*)
    (resp/json)
    (resp/status 200)))

(defpage [:get "/perfmon/throttle"] _
  (->> {:db @db-throttle :web @web-throttle} (resp/json) (resp/status 200)))

(defpage [:post "/perfmon/throttle/:id"] {id :id}
  (if-let [throttle ({"db" db-throttle "web" web-throttle} id)]
    (let [value (-> (request/ring-request) :body io/reader json/parse-stream (get "value") str Long/parseLong)]
      (reset! throttle value)
      (->> {id value} (resp/json) (resp/status 200)))
    (resp/status 404 (str "unknown throttle: '" id "'"))))

(defn init []
  (server/add-middleware perf-mon-middleware)
  (server/add-middleware throttle-middleware)
  (instrument-ns wrap-throttle 'lupapalvelu.mongo)
  (instrument-ns wrap-perf-mon 'lupapalvelu.mongo))

;;
;; Here be dragons...
;;

(comment
  (mc/remove "perf-mon")

  ; Prints call hierarchy:
  (defn perf-logger
    ([context]
      (println (with-out-str (perf-logger context ""))))
    ([[[f-name args duration sub-contexts] & r] indent]
      (println indent f-name (format "%.3f ms" (/ duration 1000000.0)))
      (doseq [sub-context sub-contexts]
        (perf-logger [sub-context] (str indent "   ")))
      (when r (perf-logger r indent))))
  
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
              (println (format "   %-60s %5d:  %9.3f ms (%.3f ms)" (str k \:) c (/ v 1000000.0) (/ (/ v 1000000.0) c))))))))))
