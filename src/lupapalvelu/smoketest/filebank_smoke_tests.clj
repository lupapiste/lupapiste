(ns lupapalvelu.smoketest.filebank-smoke-tests
  (:require [lupapiste.mongocheck.core :refer [mongocheck]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.filebank :as filebank]
            [schema.core :as sc]))

(defn validate-filebank
  [filebank]
  (when-let [res (sc/check filebank/Filebank (mongo/with-id filebank))]
    (assoc (select-keys filebank [:id]) :errors res)))

;; Validate filebank against schema
(mongocheck :filebank validate-filebank [])