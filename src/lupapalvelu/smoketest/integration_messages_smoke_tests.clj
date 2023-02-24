(ns lupapalvelu.smoketest.integration-messages-smoke-tests
  (:require [lupapiste.mongocheck.core :refer [mongocheck]]
            [lupapalvelu.integrations.messages :as messages]
            [schema.core :as sc]
            [lupapalvelu.mongo :as mongo]))


(mongocheck
  :integration-messages
  #(when-let [res (sc/check messages/IntegrationMessage (mongo/with-id %))]
     {:id (:_id %) :errors res}))
