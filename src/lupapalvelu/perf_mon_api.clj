(ns lupapalvelu.perf-mon-api
  (:require [lupapalvelu.perf-mon :as perf]
            [noir.core :refer [defpage]]
            [noir.response :as resp]
            [sade.core :refer :all]))

(defpage [:get "/perfmon/throttle"] _
  (->> {:db @perf/db-throttle :web @perf/web-throttle} (resp/json) (resp/status 200)))

(defpage [:post "/perfmon/throttle/:id"] {id :id value :value}
  (let [throttle ({"db" perf/db-throttle "web" perf/web-throttle} id)]
    (when (and throttle value)
      (reset! throttle (perf/to-long value))
      (->> {id value} (resp/json) (resp/status 200)))))
