(ns lupapalvelu.fixture.submit-restriction
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture.core :refer [deffixture]]
            [lupapalvelu.fixture.company-application :as company-application-fixture]))

(def users company-application-fixture/users)

(def organizations company-application-fixture/organizations)

(def companies (map #(cond-> % (= "esimerkki" (:id %)) (assoc :submitRestrictor true)) company-appliation-fixture/companies))

(def created 1514877090000)

(def default-app-id company-application-fixture/default-app-id)

(def applications (map #(assoc % :state "open") company-application-fixture/applications))

(deffixture "submit-restriction" {}
  (mongo/clear!)
  (mongo/insert-batch :users users)
  (mongo/insert-batch :companies companies)
  (mongo/insert-batch :organizations organizations)
  (mongo/insert-batch :applications applications)
  (mongo/insert :sequences {:_id (str "applications-753-" minimal/now-year) :count 1}))
