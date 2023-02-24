(ns lupapalvelu.backing-system.asianhallinta.response
  "Handles AsianTunnusVastaus messages"
  (:require [lupapalvelu.integrations.messages :as msgs]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.statement :as statement]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.xml :as xml]
            [taoensso.timbre :refer [warnf]]))

(defmulti handle-response-message (fn [responded-message _ _ _ _] (keyword (get-in responded-message [:target :type]))))
(defmethod handle-response-message :default [msg _ _ _ _]
  (warnf "No handle-response-message method for message-id %s" (:id msg)))
(defmethod handle-response-message :statement   ; LPK-3126
  [responded-message xml-edn ftp-user _ created]
  (statement/handle-ah-response-message responded-message xml-edn ftp-user created))

(defn asian-tunnus-vastaus-handler [parsed-xml ftp-user system-user created]
  (let [xml-edn   (xml/xml->edn parsed-xml)
        message-id (get-in parsed-xml [:attrs :messageId])
        application-id (get-in xml-edn [:AsianTunnusVastaus :HakemusTunnus])
        received-date (get-in xml-edn [:AsianTunnusVastaus :VastaanotettuPvm])
        received-ts   (date/timestamp received-date)]
    (logging/with-logging-context
      {:applicationId application-id :userId ftp-user}
      (when-not message-id
        (error-and-fail!
          (str "ah-response - AsianTunnusVastaus needs 'messageId' attribute" ftp-user)
          :error.integration.asianhallinta.no-message-id))
      (if-let [responded-message (msgs/mark-acknowledged-and-return message-id received-ts)]
        (handle-response-message responded-message xml-edn ftp-user system-user created)
        (warnf "Integration message acknowledged to db failed, message-id: %s" message-id))
      (ok))))
