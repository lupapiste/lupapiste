(ns lupapalvelu.smoketest.integration-messages-smoke-tests
  (:require [lupapalvelu.integrations.messages :as messages]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mongocheck.core :refer [mongocheck]]
            [schema.core :as sc]))

(def integration-message-checker (sc/checker messages/IntegrationMessage))

(mongocheck
  :integration-messages
  #(when-let [res (integration-message-checker (mongo/with-id %))]
     {:id (:_id %) :errors res}))
