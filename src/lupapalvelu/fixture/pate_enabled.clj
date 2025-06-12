(ns lupapalvelu.fixture.pate-enabled
  "Enable Pate in every minimal organization."
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.system.util-components :as components]))

(defmethod fixture/fixture :pate-enabled
  [_ system]
  (mongo/clear!)
  (mongo/insert-batch :users minimal/users)
  (mongo/insert-batch :companies minimal/companies)
  (mongo/insert-batch :organizations (mapv #(assoc % :scope (mapv (fn [sco]
                                                                    (assoc-in sco [:pate :enabled] true))
                                                                  (:scope %)))
                                           (minimal/organizations {:http-port (components/system->http-port system)}))))
