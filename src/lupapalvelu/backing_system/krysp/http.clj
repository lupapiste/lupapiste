(ns lupapalvelu.backing-system.krysp.http
  (:require [lupapalvelu.cookie :as lupa-cookie]
            [lupapalvelu.integrations.messages :as imessages]
            [lupapalvelu.integrations.pubsub :as lip]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.system.util-components :as components]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.http :as http]
            [sade.util :as util])
  (:import [clojure.lang ExceptionInfo]
           (com.mongodb WriteConcern)
           [java.net UnknownHostException]))

(set! *warn-on-reflection* true)

(defn- with-krysp-defaults [options]
  (merge
    {:content-type "text/xml;charset=UTF-8"}
    (lupa-cookie/add-mongo-cookie options)))

(defn- post-message-xml [{:keys [id url data] :as message}]
  (http/post url (imessages/wrap-message-options
                   message (with-krysp-defaults {:body data})))
  (imessages/update-message id
                            {$set {:acknowledged (now)
                                   :status       :done}}
                            WriteConcern/UNACKNOWLEDGED))

(def kuntagml-queue "lupapiste.kuntagml.http")

(defn- handle-pubsub-message
  [_system {:keys [message-id]}]
  ;; Possible test database must be bound for the Mongo query to work
  (if-let [{:keys [initiator application url]
            :as   message} (imessages/fetch message-id)]
    (log/with-logging-context {:userId        (:id initiator)
                               :applicationId (:ida application)}
      (try
        (post-message-xml message)

        (log/infof "KuntaGML (id: %s) consumed and acknowledged from queue successfully" message-id)
        true
        (catch UnknownHostException _
          (log/error "Unknown host:" url)
          true)
        (catch ExceptionInfo ex
          (log/errorf ex "Error when sending consumed KuntaGML message (%s) to %s." message-id url)
          (let [ed (ex-data ex)]
            (if (= (:status ed) 404)
              ;; ACK
              true
              false)))
        (catch Exception e
          (log/errorf e "Error when sending consumed KuntaGML message (%s) to %s. NACKing." message-id url)
          false)))
    (do
      (log/errorf "Pubsub %s: No message %s found from mongo." kuntagml-queue message-id)
      true)))

(defmethod lip/get-pubsub-subscription-handler :backing-system/http
  [_id]
  handle-pubsub-message)

(defn send-kuntagml-message [system message]
  {:pre [(map? system)]}
  (let [publisher (components/system->publisher system :backing-system/http)
        message   (->  message
                       (merge {:id           (mongo/create-id)
                               :direction    "out"
                               :transferType "http"
                               :format       "xml"
                               :created      (now)
                               :status       (if publisher "queued" "processing")})
                       util/strip-nils)
        msg-id    (:id message)
        user-id   (-> message :initiator :id)
        app-id    (-> message :application :id)]
    (imessages/save message)
    (log/with-logging-context {:userId        user-id
                               :applicationId app-id}
      (if publisher
        (publisher {:message-id msg-id})
        (post-message-xml message))
      (log/infof "KuntaGML (id: %s, type: %s) for partner %s sent via %s successfully"
                 msg-id
                 (:messageType message)
                 (:partner message)
                 (if publisher "MQ" "HTTP")))))
