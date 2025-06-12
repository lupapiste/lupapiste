(ns lupapalvelu.export-itest-util
  "Utilities for calling the reporting export API from itests"
  (:require [lupapalvelu.itest-util :refer :all]))

(defn export-applications [start-ts end-ts]
  (:body (decoded-get (str (server-address) "/data-api/json/export-applications-for-reporting-db")
                      {:basic-auth ["solita-etl" "solita-etl"]
                       :redirect-strategy :none
                       :throw-exceptions false
                       :query-params {:modifiedAfterTimestampMillis start-ts
                                      :modifiedBeforeTimestampMillis end-ts}})))

(defn export-application-ids [start-ts end-ts]
  (:body (decoded-get (str (server-address) "/data-api/json/export-application-ids-for-reporting-db")
                      {:basic-auth ["solita-etl" "solita-etl"]
                       :redirect-strategy :none
                       :throw-exceptions false
                       :query-params {:modifiedAfterTimestampMillis start-ts
                                      :modifiedBeforeTimestampMillis end-ts}})))

(defn export-application [id]
  (:body (decoded-get (str (server-address) "/data-api/json/export-application-for-reporting-db")
                      {:basic-auth ["solita-etl" "solita-etl"]
                       :redirect-strategy :none
                       :throw-exceptions false
                       :query-params {:applicationId id}})))
