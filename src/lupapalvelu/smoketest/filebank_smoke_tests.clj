(ns lupapalvelu.smoketest.filebank-smoke-tests
  (:require [lupapalvelu.filebank :as filebank]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mongocheck.core :refer [mongocheck]]
            [schema.core :as sc]))

(def filebank-checker (sc/checker filebank/Filebank))

(defn validate-filebank
  [filebank]
  (when-let [res (filebank-checker (mongo/with-id filebank))]
    (assoc (select-keys filebank [:id]) :errors res)))

;; Validate filebank against schema
(mongocheck :filebank validate-filebank [])
