(ns artemis-server
  "Embedded ActiveMQ Artemis server"
  (:require [taoensso.timbre :refer [info infof]])
  (:import (org.apache.activemq.artemis.core.config.impl ConfigurationImpl)
           (org.apache.activemq.artemis.core.server ActiveMQServers ActiveMQServer)
           (org.apache.activemq.artemis.core.settings.impl AddressSettings)
           (org.apache.activemq.artemis.api.core SimpleString)))

(info "Requiring ActiveMQ Artemis...")
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

(defonce embedded-broker ^ActiveMQServer
  (ActiveMQServers/newActiveMQServer conf))

(defn start []
  (when-not (.isStarted embedded-broker)
    (let [ts (double (System/currentTimeMillis))]
      (info "Starting Artemis...")
      (.start embedded-broker)
      (infof "Started embedded ActiveMQ Artemis message broker, took %.3f s" (/ (- (System/currentTimeMillis) ts) 1000)))))
