(ns lupapalvelu.xml.asianhallinta.response
  "Handles AsianTunnusVastaus messages"
  (:require [taoensso.timbre :refer [warn]]
            [sade.common-reader :as cr]
            [sade.core :refer :all]
            [sade.xml :as xml]
            [lupapalvelu.integrations.messages :as msgs]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.xml.asianhallinta.reader :as ah-reader]))

(defmethod ah-reader/handle-asianhallinta-message :AsianTunnusVastaus
  [parsed-xml _ ftp-user system-user]
  (let [xml-edn   (xml/xml->edn parsed-xml)
        message-id (get-in parsed-xml [:attrs :messageId])
        application-id (get-in xml-edn [:AsianTunnusVastaus :HakemusTunnus])
        partners-id (get-in xml-edn [:AsianTunnusVastaus :AsianTunnus])
        received-date (get-in xml-edn [:AsianTunnusVastaus :VastaanotettuPvm])
        received-ts   (cr/to-timestamp received-date)]
    (logging/with-logging-context
      {:applicationId application-id :userId ftp-user}
      (when-not message-id
        (ah-reader/error-and-fail!
          (str "ah-response - AsianTunnusVastaus needs 'messageId' attribute" ftp-user)
          :error.integration.asianhallinta.no-message-id))
      (if-let [message (msgs/mark-acknowledged-and-return message-id received-ts)]
        nil
        (warnf "Integration message acknowledged to db failed, message-id: %s" message-id)))))
