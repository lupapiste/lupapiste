(ns lupapalvelu.smoketest.verdict-template-smoke-tests
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mongocheck.core :refer [mongocheck]]
            [lupapalvelu.pate.schemas :as pate-schemas]
            [monger.operators :refer :all]))

(mongocheck :verdict-templates
            #(when-let [res (pate-schemas/check-pate-saved-template (mongo/with-id %))]
               (assoc (select-keys % [:id]) :errors res))
            [])
