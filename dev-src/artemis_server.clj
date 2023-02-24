(ns artemis-server
  "Embedded ActiveMQ Artemis server"
  (:require [mount.core :refer [defstate]]
            [taoensso.timbre :refer [debug info infof]])
  (:import (org.apache.activemq.artemis.core.config.impl ConfigurationImpl)
           (org.apache.activemq.artemis.core.server ActiveMQServers ActiveMQServer)
           (org.apache.activemq.artemis.core.settings.impl AddressSettings)
           (org.apache.activemq.artemis.api.core SimpleString)))

(debug "Requiring ActiveMQ Artemis server...")
(def delivery-attempts 5)
(def conf (doto (ConfigurationImpl.)
            (.setPersistenceEnabled false)
            (.setJournalDirectory "target/artemis_journal")
            (.setPagingDirectory "target/artemis_paging")
            (.setBindingsDirectory "target/artemis_bindings")
            (.setLargeMessagesDirectory "target/artemis_largemessages")
            (.setSecurityEnabled false)
            (.addAcceptorConfiguration "invm" "vm://0")
            (.addAddressesSetting "#"
                                 (doto (AddressSettings.)
                                   (.setDeadLetterAddress (SimpleString. "DLQ"))
                                   (.setExpiryAddress (SimpleString. "Expired"))
                                   (.setMaxDeliveryAttempts delivery-attempts)))))

(defstate embedded-broker
  :start (let [ts (double (System/currentTimeMillis))
               an-artemis ^ActiveMQServer (ActiveMQServers/newActiveMQServer conf)]
           (info "Starting Artemis...")
           (.start an-artemis)
           (infof "Started embedded ActiveMQ Artemis message broker, took %.3f s"
                  (/ (- (System/currentTimeMillis) ts) 1000))
           an-artemis)

  :stop (do (info "Stopping Artemis")
            (.stop ^ActiveMQServer embedded-broker)))
