(ns lupapalvelu.smoketest.statistics-entry-smoke-tests
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mongocheck.core :refer [mongocheck]]
            [lupapalvelu.statistics.entry :as stats-e]))

(defn check-entry [entry]
  (try
    (stats-e/->entry entry)
    nil
    (catch Exception e
      (ex-message e))))

(mongocheck :processing-entries
            (comp check-entry mongo/with-id))
