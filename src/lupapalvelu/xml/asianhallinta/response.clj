(ns lupapalvelu.xml.asianhallinta.response
  "Handles AsianTunnusVastaus messages"
  (:require [taoensso.timbre :refer [infof warnf]]
            [sade.common-reader :as cr]
            [sade.core :refer :all]
            [sade.xml :as xml]
            [lupapalvelu.integrations.messages :as msgs]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.xml.asianhallinta.reader :as ah-reader]))

(defmulti handle-response-message (fn [responded-message _ _ _] (keyword (get-in responded-message [:target :type]))))
(defmethod handle-response-message :default [msg _ _ _]
  (warnf "No handle-response-message method for message-id %s" (:id msg)))

(defmethod ah-reader/handle-asianhallinta-message :AsianTunnusVastaus
  [parsed-xml _ ftp-user system-user]
  (let [xml-edn   (xml/xml->edn parsed-xml)
        message-id (get-in parsed-xml [:attrs :messageId])
        application-id (get-in xml-edn [:AsianTunnusVastaus :HakemusTunnus])
        received-date (get-in xml-edn [:AsianTunnusVastaus :VastaanotettuPvm])
        received-ts   (cr/to-timestamp received-date)]
    (logging/with-logging-context
      {:applicationId application-id :userId ftp-user}
      (when-not message-id
        (ah-reader/error-and-fail!
          (str "ah-response - AsianTunnusVastaus needs 'messageId' attribute" ftp-user)
          :error.integration.asianhallinta.no-message-id))
      (if-let [responded-message (msgs/mark-acknowledged-and-return message-id received-ts)]
        (handle-response-message responded-message xml-edn ftp-user system-user)
        (warnf "Integration message acknowledged to db failed, message-id: %s" message-id))
      (ok))))
