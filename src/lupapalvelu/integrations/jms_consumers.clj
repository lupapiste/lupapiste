(ns lupapalvelu.integrations.jms-consumers
  (:require [mount.core :refer [defstate]]
            [lupapalvelu.archive.api-usage]
            [lupapalvelu.batchrun.fetch-verdict-consumer :as fetch-verdict-consumer]
            [lupapalvelu.organization :refer [get-organizations]]
            [sade.env :as env]))

(defstate fetch-verdict-consumers
  :start (when (env/value :integration-message-queue)
           (let [organization-ids (->> (get-organizations {} [:_id]) (mapv :id))]
             (fetch-verdict-consumer/create-fetch-verdict-consumers! organization-ids))))
