(ns lupapalvelu.integrations.message-queue
  (:require [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.integrations.pubsub :as lip]
            [sade.env :as env]
            [taoensso.nippy :as nippy]
            [taoensso.timbre :as timbre]))


(defn publish [topic-name message]
  (case (env/value :integration-message-queue)
    "pubsub" (lip/publish topic-name message)
    "jms" (jms/produce-with-context topic-name
                                    (nippy/freeze message))
    (timbre/error "message queue publish called but no integration-message-queue defined"
                  topic-name
                  message)))
