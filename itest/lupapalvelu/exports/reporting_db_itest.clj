(ns lupapalvelu.exports.reporting-db-itest
  (:require [lupapalvelu.exports.reporting-db :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [sade.env :as env]))

(apply-remote-minimal)

(defn export-applications [start-ts end-ts]
  (:body (decoded-get (str (server-address) "/data-api/json/export-applications-for-reporting-db")
                      {:basic-auth ["solita-etl" "solita-etl"]
                       :follow-redirects false
                       :throw-exceptions false
                       :query-params {:modifiedAfterTimestampMillis start-ts
                                      :modifiedBeforeTimestampMillis end-ts}})))

(defn export-application-ids [start-ts end-ts]
  (:body (decoded-get (str (server-address) "/data-api/json/export-application-ids-for-reporting-db")
                      {:basic-auth ["solita-etl" "solita-etl"]
                       :follow-redirects false
                       :throw-exceptions false
                       :query-params {:modifiedAfterTimestampMillis start-ts
                                      :modifiedBeforeTimestampMillis end-ts}})))

(defn export-application [id]
  (:body (decoded-get (str (server-address) "/data-api/json/export-application-for-reporting-db")
                      {:basic-auth ["solita-etl" "solita-etl"]
                       :follow-redirects false
                       :throw-exceptions false
                       :query-params {:applicationId id}})))

 (defn force-application-reporting-database-export! [id]
   (http-post (str (env/server-address) "/dev/force-application-reporting-database-export")
              {:form-params {:id id}}))

(facts "Reporting db export"
  (fact "No apps created"
    (:applications (export-applications 0 (now))) => empty?)
  (let [timestamp-before-application-is-created (- (now) 60000)
        application-id                          (create-app-id pena :address "Rapsakuja 1" :operation "markatilan-laajentaminen")
        application                             (query-application pena application-id)
        timestamp-after-application-is-created  (inc (:modified application))
        future-timestamp                        (+ timestamp-after-application-is-created 60000)]

    (facts "One app created, but is not exported because the modified timestamp is not within the given time interval"
      (fact {:midje/description (str "applications - time ending before: " timestamp-before-application-is-created)}
        (:applications (export-applications 0 timestamp-before-application-is-created))
        => empty?)
      (fact {:midje/description (str "applicationIds - time ending before: " timestamp-before-application-is-created)}
        (:applicationIds (export-application-ids 0 timestamp-before-application-is-created))
        => empty?)
      (fact {:midje/description (str "applications - time starting after: " timestamp-after-application-is-created)}
        (:applicationIds (export-application-ids timestamp-after-application-is-created future-timestamp))
        => empty?))

    (facts "Exporting can be forced without affecting the application timestamp"
      (let [app-in-exported-applications-before-forcing? (-> (export-application-ids timestamp-after-application-is-created
                                                                                     future-timestamp)
                                                             :applicationIds
                                                             seq some?)
            ;; Sleep for a bit to make sure time advances
            _ (Thread/sleep 100)
            _ (force-application-reporting-database-export! application-id)
            app-after-forcing (query-application pena application-id)
            app-in-exported-applications-after-forcing? (-> (export-application-ids timestamp-after-application-is-created
                                                                                    future-timestamp)
                                                            :applicationIds
                                                            seq some?)]
        (fact "modified timestamp is not affected"
          (:modified application) => (:modified app-after-forcing))
        (fact "application is not exported before forcing"
          app-in-exported-applications-before-forcing? => false)
        (fact "application is exported after forcing"
          app-in-exported-applications-after-forcing? => true)))

    (fact "The created app is exported if the modified timestamp is within the given time interval"
      (:applications (export-applications timestamp-before-application-is-created (now)))
      => (just [(contains {:id application-id})])
      (:applicationIds (export-application-ids timestamp-before-application-is-created (now)))
      => (just [application-id])
      (:application (export-application application-id))
      => (contains {:id application-id})))

  (let [{:keys [created] first-application-id :id} (create-application pena :operation "poikkeamis")
        ;; This is a call to a remote server, so the clocks might not be strictly in sync, take the time from the created app
        timestamp-before-applications-are-created (- created 1)
        timestamp-between-applications            (+ created 1)
        _                                         (Thread/sleep 100)
        second-application-id                     (create-app-id pena :operation "kerrostalo-rivitalo")]

    (fact "Two apps created, but only one is exported if the timestamp of the second one is not within the time interval"
      (:applications (export-applications timestamp-before-applications-are-created
                                          timestamp-between-applications))
      => (just [(contains {:id first-application-id})]))

    (fact "Both apps are exported if their timestamps are contained within the time interval"
      (:applications (export-applications timestamp-before-applications-are-created (now)))
      => (just [(contains {:id first-application-id})
                (contains {:id second-application-id})]
               :in-any-order)))
  (let [timestamp-before-applications-are-created (now)
        application-id (:id (create-and-submit-application pena :operation "kerrostalo-rivitalo"))
        _ (give-legacy-verdict sonja application-id)
        foreman-app-id (create-foreman-application application-id pena pena-id "KVV-ty\u00f6njohtaja" "B")]

    (fact "Application links are present in both linked applications"
          (:applications (export-applications timestamp-before-applications-are-created (now)))
          => (just [(contains {:id application-id
                               :links [{:id foreman-app-id :permitType "tyonjohtajan-nimeaminen-v2"}]})
                    (contains {:id foreman-app-id
                               :links [{:id application-id :permitType "kerrostalo-rivitalo"}]})]
                   :in-any-order))))
