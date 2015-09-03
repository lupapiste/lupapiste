(ns lupapalvelu.perf-mon-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [noir.core :refer [defpage]]
            [noir.response :as resp]
            [monger.collection :as mc]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [defcommand]]
            [lupapalvelu.perf-mon :as perf])
  (:import (com.mongodb WriteConcern)))

(defpage "/perfmon/data" {:keys [start end]}
  (->> (perf/get-data (or (perf/to-long start) (- (System/currentTimeMillis) (* 5 60 1000)))
                      (or (perf/to-long end) (System/currentTimeMillis)))
       (group-by :uri)
       (map perf/find-min-max-avg*)
       (resp/json)
       (resp/status 200)))

(defpage [:get "/perfmon/throttle"] _
  (->> {:db @perf/db-throttle :web @perf/web-throttle} (resp/json) (resp/status 200)))

(defpage [:post "/perfmon/throttle/:id"] {id :id value :value}
  (let [throttle ({"db" perf/db-throttle "web" perf/web-throttle} id)]
    (when (and throttle value)
      (reset! throttle (perf/to-long value))
      (->> {id value} (resp/json) (resp/status 200)))))

(defcommand browser-timing
  {:parameters [timing pathname]
   :user-roles #{:anonymous}}
  [command]
  (info "browser-timing called from" pathname)
  (mc/insert "perf-mon-timing"
             {:ts     (java.util.Date.)
              :ua     (get-in command [:web :user-agent])
              :timing timing}
             WriteConcern/UNACKNOWLEDGED)
  (ok))