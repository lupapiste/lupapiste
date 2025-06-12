(ns lupapalvelu.asiointi.notifications.notify
  "Receive events from Lupapiste applications and send them (through pubsub) to AsiointiAPI consumers"
  (:require [lupapalvelu.asiointi.control-case :as control-case]
            [lupapalvelu.asiointi.notifications.common :as common]
            [lupapalvelu.asiointi.notifications.notification :as notification]
            [lupapalvelu.asiointi.notifications.outgoing :as outgoing]
            [lupapalvelu.asiointi.notifications.registration :as registration]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.integrations.pubsub :as lip]
            [lupapalvelu.logging :as log]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]
            [lupapalvelu.system.util-components :as components]
            [ring.util.http-predicates :as http-pred]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]])
  (:import [org.bson.types ObjectId]))

(set! *warn-on-reflection* true)

(def default-notifier (fn [registration contents]
                        (let [url (-> registration :target :url)]
                          (log/infof "Sending notification to %s" url)
                          (let [options                       {:as               :json
                                                               :accept           :json
                                                               :content-type     :json
                                                               :throw-exceptions false
                                                               :quiet            true
                                                               :form-params      contents}
                                {:keys [status] :as response} (http/post url options)]
                            (log/infof "Received %s from %s" status url)
                            (http-pred/success? response)))))

(defn- try-to-release!
  [notification-id]
  (try
    (log/infof "Releasing notification %s" notification-id)
    (notification/release! notification-id)
    (catch Exception ex
      (log/error ex "Failed to release notification!"))))

(defn- message-for
  "Oldest pending message for a registration"
  [registration-id]
  (when-let [notification-id (notification/registration->pending-notification-id registration-id)]
    {:registration-id registration-id
     :notification-id notification-id}))

(defn- publish-notification!
  [publisher {:keys [notification-id registration-id]}]
  (let [message {:message-id      (str notification-id)
                 :registration-id (str registration-id)}]
    (publisher message)))

;;; Pubsub reception

(defn- send-next-pending!
  [publisher registration-id]
  (try
    (or (some->> (message-for registration-id)
                 (publish-notification! publisher))
        (log/infof "No pending notifications for %s" registration-id))
    (catch Exception ex
      ;; NOTE: if the above publishing throws and there are no more incoming messages
      ;; for this registration, the process stops. However, the next new notification
      ;; to the same registration will restart the process.
      (log/warn ex "Error when publishing next notification after delivery"))))

(defn handle-pubsub-message
  [{:keys [publisher notifier]}
   message]
    (log/with-logging-context
      {:notificationId (:message-id message)
       :registrationId (:registration-id message)}
      (try
        (let [notification-id (ObjectId. ^String (:message-id message))
              registration-id (ObjectId. ^String (:registration-id message))]
          (if (registration/exists? registration-id)
            (if (notification/reserve! notification-id)
              ;; The notification is now reserved for us, must make sure to release
              ;; it if something goes wrong
              (try
                (let [registration (registration/get-registration registration-id)
                      notification (notification/get-notification notification-id)]
                  ;; Send the notification to the registered consumer
                  (if (notifier registration (outgoing/notification-body registration notification))
                    (do
                      ;; If nothing went wrong, mark as delivered and released
                      (notification/mark-delivered-and-release! notification-id)

                      ;; Check if there is pending messages after this one
                      (send-next-pending! publisher registration-id)
                      true)
                    false))
                (catch Exception ex
                  (log/warn ex "Error when delivering notification to consumer")
                  false)
                (finally
                  ;; Make sure notification is released
                  (try-to-release! notification-id)))
              (do ;; Failed to reserve notification
                (log/warnf "Discarding message %s, could not reserve it."
                           (str notification-id))
                ;; We could check `message-for` and publish the next message,
                ;; but let's assume whoever reserved the notification already handled it.
                true))
            (do ;; Could not find registration
              (log/warnf "Discarding message %s, target registration %s no longer exists!"
                         (str notification-id) (str registration-id))
              true)))
        (catch Exception ex
          (log/error ex "Exception when processing message!")
        false))))

(defmethod lip/get-pubsub-subscription-handler :asiointi-api/notifications
  [_id]
  (fn [system message]
    (handle-pubsub-message {:notifier  default-notifier
                            :publisher (components/system->publisher system :asiointi-api/notifications)}
                           message)))

;;; Notification API

(defn- registrations-to-notify
  [organization-id notification-type]
  (->> (registration/org-id->registrations organization-id)
       (remove (fn [{:keys [ignored]}]
                 (contains? ignored notification-type)))
       (into [])))

(defn- make-notifications
  [registrations draft]
  (->> registrations
       (mapv (fn [{registration-id :id}]
               (-> draft
                   (assoc :registration-id registration-id)
                   notification/->notification)))))


(defn- publish-notifications!
  [publisher notifications]
  (run! (partial publish-notification! publisher) notifications))

(defn- oldest-pending
  "Find the oldest pending notifications for the given registrations"
  [registrations]
  (->> registrations
       (keep (fn [{registration-id :id}]
               (message-for registration-id)))
       (into [])))

(defschema Context
  {:notification-type common/NotificationType
   :organization-id   org/OrgId
   ;; Command that triggered the notification
   :command           {sc/Any sc/Any}
   ;; Application after the action
   :new-app           {sc/Any sc/Any}
   ;; Function for sending pubsub messages
   :publisher         ssc/IFn})

(defn- make-draft-application-state-change
  "Application state change notification"
  [{:keys [notification-type command new-app]}]
  (let [{:keys   [created]
         old-app :application}                                    command
        {:keys [state]}                                           old-app
        {application-id                                   :id
         organization-id                                  :organization
         new-state                                        :state
         {permanent-permit-id :permanentPermitIdentifier} :ryhti} new-app]
    (when permanent-permit-id
      (util/mmap {:control-case (-> (operations/application->control-case new-app)
                                    control-case/control-case->notification-control-case)
                  :extra-data   {:new-state (keyword new-state)
                                 :old-state (keyword state)}}
                 application-id permanent-permit-id created organization-id notification-type))))

(defn- make-draft
  "Try to construct a draft notification for the given notification type.
  Return `nil` if cannot construct a notification."
  [{:keys [notification-type] :as context}]
  (case notification-type
    :application-state-change (make-draft-application-state-change context)))

;; New notification types may require projecting additional fields
(def application-projection-keys #{:id :organization :state :ryhti :primaryOperation})

(sc/defn create-and-publish-notifications!
  "Send a notification to all registered consumers for this notification-type & organization

  NOTE: If there are no registered consumers => we do nothing, the notification is ignored"
  [{:keys [publisher organization-id notification-type] :as context} :- Context]
  (when-let [registrations (not-empty (registrations-to-notify organization-id notification-type))]
    (when-let [draft (make-draft context)]
      (let [notifications (make-notifications registrations draft)]
        (notification/store-notifications! notifications)
        ;; NOTE: Even though we created new pending notification(s), we will
        ;; publish the oldest pending ones. Most of the time these are the same,
        ;; but if some of the consumers are offline we might retry old messages.
        (let [messages (oldest-pending registrations)]
          (publish-notifications! publisher messages))))))

(defn- handle-notification
  [{:keys [command publisher notification-type]}]
  (let [{{application-id :id} :application} command]
    ;; Find a fresh copy of the target application with relevant fields
    (when-let [{organization-id :organization
                :as             new-app} (domain/get-application-no-access-checking
                                           application-id (vec application-projection-keys))]
      (-> (util/mmap new-app organization-id command notification-type publisher)
          create-and-publish-notifications!))))

(def ^:private ignore (constantly nil))

(sc/defn from-command
  "Like `lupapalvelu.action/notify`, but notifies AsiointiAPI listeners."
  {:always-validate true}
  [notification-type :- common/NotificationType]
  (if (env/feature? :asiointi-api)
      (fn [command status]
      (if-let [publisher (components/command->publisher command :asiointi-api/notifications)]
        (handle-notification (util/mmap command status publisher notification-type))
        (log/infof "Not notifying, no publisher.")))
    ignore))
