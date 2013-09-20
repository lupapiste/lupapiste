(ns lupapalvelu.perf-mon
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [noir.core :refer [defpage]]
            [monger.operators :refer :all]
            [clojure.string :refer [join]]
            [clojure.java.io :as io]
            [monger.collection :as mc]
            [noir.response :as resp]
            [noir.request :as request]
            [noir.server :as server]
            [cheshire.core :as json])
  (:import [com.mongodb WriteConcern]))

;;
;; Instrumenting:
;;

(defn instrument [f v]
  (alter-var-root v f (str (. v ns) \/ (. v sym))))

(defn instrument-ns [f & namespaces]
  (doseq [n namespaces
          v (filter (comp fn? deref) (vals (ns-publics n)))]
    (instrument f v)))

;;
;; State:
;;

(defonce db-throttle (atom 0))
(defonce web-throttle (atom 0))
(def ^:dynamic *perf-context* nil)

;;
;; Utils:
;;

(defn- bypass? [request]
  (or (get-in request [:query-params "npm"]) (get-in request [:headers "npm"])))

;;
;; Performance minitoring:
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

(defn perf-mon-middleware [handler]
  (fn [request]
    (if (bypass? request)
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

;;
;; Throttling:
;;

(defn wrap-db-throttle [f f-name]
  (fn [& args]
    (Thread/sleep @db-throttle)
    (apply f args)))

(defn throttle-middleware [handler]
  (fn [request]
    (if-not (bypass? request) (Thread/sleep @web-throttle))
    (handler request)))

;;
;; REST API for performance data and throtting control:
;;

(defn get-data [start end]
  (map (fn [row] (dissoc row :_id))
       (mc/find-maps "perf-mon" {$and [{:ts {$gte start}}
                                       {:ts {$lte end}}]})))

(defn- to-long [v]
  (when v
    (if (string? v) (Long/parseLong v) (long v))))

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

(defpage [:post "/perfmon/throttle/:id"] {id :id value :value}
  (let [throttle ({"db" db-throttle "web" web-throttle} id)]
    (when (and throttle value)
      (reset! throttle (to-long value))
      (->> {id value} (resp/json) (resp/status 200)))))

(defpage [:post "/perfmon/browser-timing"] {timing :timing}
  (let [user-agent (-> (request/ring-request) :headers (get "user-agent"))]
    (mc/insert "perf-mon-timing" 
      {:ts (System/currentTimeMillis)
       :ua user-agent
       :timing timing}
      WriteConcern/NONE))
  (->> {:ok true} (resp/json) (resp/status 200)))

;;
;; Initialize: register middlewares and wrap vars:
;;

(defn init []
  (server/add-middleware perf-mon-middleware)
  (server/add-middleware throttle-middleware)
  (instrument-ns wrap-db-throttle 'lupapalvelu.mongo)
  (instrument-ns wrap-perf-mon 'lupapalvelu.mongo))
