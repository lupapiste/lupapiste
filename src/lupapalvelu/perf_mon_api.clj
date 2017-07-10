(ns lupapalvelu.perf-mon-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [noir.core :refer [defpage]]
            [noir.response :as resp]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.action :refer [defcommand] :as action]
            [lupapalvelu.perf-mon :as perf])
  (:import (com.mongodb WriteConcern)
           (java.util Date)))

(defpage "/perfmon/data" {:keys [start end]}
  (let [now-ts (now)]
    (->> (perf/get-data (Date. (or (perf/to-long start) (- now-ts (* 60 60 1000))))
                        (Date. (or (perf/to-long end) now-ts)))
         (resp/json)
         (resp/status 200))))

(defpage [:get "/perfmon/throttle"] _
  (->> {:db @perf/db-throttle :web @perf/web-throttle} (resp/json) (resp/status 200)))

(defpage [:post "/perfmon/throttle/:id"] {id :id value :value}
  (let [throttle ({"db" perf/db-throttle "web" perf/web-throttle} id)]
    (when (and throttle value)
      (reset! throttle (perf/to-long value))
      (->> {id value} (resp/json) (resp/status 200)))))

(defcommand browser-timing
  {:parameters [timing pathname]
   :input-validators [(partial action/non-blank-parameters [:pathname])
                      (partial action/map-parameters [:timing])]
   :user-roles #{:anonymous}}
  [command]
  (info "browser-timing called from" pathname)
  (let [ua (ss/limit (get-in command [:web :user-agent]) 256)
        ts (java.util.Date.)
        timing-events (->
                        (into {} (filter (fn [[k v]] (number? v)) timing))
                        (select-keys [:navigationStart
                                      :unloadEventStart
                                      :unloadEventEnd
                                      :redirectStart
                                      :redirectEnd
                                      :fetchStart
                                      :domainLookupStart
                                      :domainLookupEnd
                                      :connectStart
                                      :connectEnd
                                      :requestStart
                                      :responseStart
                                      :responseEnd
                                      :domLoading
                                      :domInteractive
                                      :domContentLoadedEventStart
                                      :domContentLoadedEventEnd
                                      :domComplete
                                      :loadEventStart
                                      :loadEventEnd]))]
    (mongo/insert :perf-mon-timing {:ts ts, :ua ua, :timing timing-events} WriteConcern/UNACKNOWLEDGED))
  (ok))
