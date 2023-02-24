(ns lupapalvelu.fixture.pate-enabled
  "Enable Pate in every minimal organization."
  (:require [lupapalvelu.fixture.core :refer [deffixture]]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.mongo :as mongo]))

(deffixture "pate-enabled" {}
  (mongo/clear!)
  (mongo/insert-batch :users minimal/users)
  (mongo/insert-batch :companies minimal/companies)
  (mongo/insert-batch :organizations (map #(assoc % :scope (mapv (fn [sco]
                                                                   (assoc-in sco [:pate :enabled] true))
                                                                 (:scope %)))
                                          minimal/organizations)))
