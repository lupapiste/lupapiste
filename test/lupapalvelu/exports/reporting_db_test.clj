(ns lupapalvelu.exports.reporting-db-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.exports.reporting-db :refer :all]
            [lupapalvelu.organization :as org]
            [lupapalvelu.rakennuslupa-canonical-util :refer [application-rakennuslupa]]))

(facts "->reporting-result"
  (against-background
    (org/pate-scope? irrelevant) => false)
  (->reporting-result application-rakennuslupa "fi")
  => (contains {:id (:id application-rakennuslupa)
                :location-etrs-tm35fin (:location application-rakennuslupa)
                :location-wgs84 (:location-wgs84 application-rakennuslupa)
                :permitType "R"
                :state "submitted"
                :araFunding false})

  (->reporting-result (update application-rakennuslupa
                              :documents
                              (partial map #(if (= (-> % :schema-info :name)
                                                   "hankkeen-kuvaus")
                                              (assoc-in % [:data :rahoitus :value] true)
                                              %)))
                      "fi")
  => (contains {:araFunding true}))
