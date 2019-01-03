(ns lupapalvelu.exports.reporting-db-itest
  (:require [lupapalvelu.exports.reporting-db :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]))

(apply-remote-minimal)

(defn export-applications [start-ts end-ts]
  (:body (decoded-get (str (server-address) "/data-api/json/export-applications-for-reporting-db")
                      {:basic-auth ["solita-etl" "solita-etl"]
                       :follow-redirects false
                       :throw-exceptions false
                       :query-params {:modifiedAfterTimestampMillis start-ts
                                      :modifiedBeforeTimestampMillis end-ts}})))

(facts "Reporting db export"
  (fact "No apps created"
    (:applications (export-applications 0 (now))) => empty?)
  (let [timestamp-before-application-is-created (now)
        application-id (create-app-id pena :operation "markatilan-laajentaminen")]
    (fact "One app created, but is not exported because the modified timestamp is not within the given time interval"
      (:applications (export-applications 0 timestamp-before-application-is-created))
      => empty?)
    (fact "The created app is exported if the modified timestamp is within the given time interval"
      (:applications (export-applications timestamp-before-application-is-created (now)))
      => (just [(contains {:id application-id})])))
  (let [timestamp-before-applications-are-created (now)
        first-application-id (create-app-id pena :operation "aita")
        timestamp-between-applications (now)
        second-application-id (create-app-id pena :operation "kerrostalo-rivitalo")]
    (fact "Two apps created, but only one is exported if the timestamp of the second one is not within the time interval"
          (:applications (export-applications timestamp-before-applications-are-created
                                              timestamp-between-applications))
          => (just [(contains {:id first-application-id})]))
    (fact "Both apps are exported if their timestamps are contained within the time interval"
          (:applications (export-applications timestamp-before-applications-are-created (now)))
          => (just [(contains {:id first-application-id})
                    (contains {:id second-application-id})]
                   :in-any-order))))
