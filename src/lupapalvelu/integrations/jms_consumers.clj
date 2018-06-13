(ns lupapalvelu.integrations.jms-consumers
  (:require [lupapalvelu.archive.api-usage]
            [lupapalvelu.batchrun.fetch-verdict-consumer :as fetch-verdict-consumer]
            [sade.env :as env]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :refer [get-organizations]]))

(defn init! []
  (when (env/feature? :jms)
    (let [organization-ids (->> (get-organizations {} [:_id]) (mapv :id))]
      (fetch-verdict-consumer/create-fetch-verdict-consumers! organization-ids))))
