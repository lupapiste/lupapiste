(ns lupapalvelu.fixture.ya-extension
  "Select `:ya-jatkoaika` operation for Sipoo YA organization."
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.system.util-components :as components]))

(defmethod fixture/fixture :ya-extension
  [_ system]
  (mongo/clear!)
  (mongo/insert-batch :users minimal/users)
  (mongo/insert-batch :companies minimal/companies)
  (mongo/insert-batch :organizations (map (fn [{org-id :id :as org}]
                                            (cond-> org
                                              (= org-id "753-YA") (update :selected-operations
                                                                          conj :ya-jatkoaika)))
                                          (minimal/organizations {:http-port (components/system->http-port system)}))))
