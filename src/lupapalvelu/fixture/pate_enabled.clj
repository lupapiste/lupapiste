(ns lupapalvelu.fixture.pate-enabled
  "Enable Pate in every minimal organization."
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture.core :refer [deffixture]]
            [lupapalvelu.fixture.minimal :as minimal]
            [sade.core :refer :all]
            [schema.core :as sc]
            [lupapalvelu.pate.schemas :as ps]))

(deffixture "pate-enabled" {}
  (mongo/clear!)
  (mongo/insert-batch :users minimal/users)
  (mongo/insert-batch :companies minimal/companies)
  (mongo/insert-batch :organizations (map #(assoc % :pate-enabled true)
                                          minimal/organizations)))
