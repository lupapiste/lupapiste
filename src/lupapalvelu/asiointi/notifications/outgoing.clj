(ns lupapalvelu.asiointi.notifications.outgoing
  "Convert notifications from the database format into HTTP payloads"
  (:require [lupapalvelu.asiointi.notifications.notification :as notification]
            [malli.util :as mu]
            [sade.date :as date]))

(def ^:private Common
  (-> notification/BaseNotification
      (mu/select-keys #{:application-id
                        :control-case
                        :created-at
                        :id
                        :organization-id
                        :permanent-permit-id
                        :registration-id})
      (mu/rename-keys {:id :notification-id})))

(def Notification
  "Outgoing notification payloads"
  (notification/make-notification-schema Common))

(defn- default-notification-body
  [registration notification]
  {:application-id      (-> notification :application-id)
   :control-case        (-> notification :control-case)
   :created-at          (-> notification :created-at (date/zoned-format :iso-datetime))
   :notification-id     (-> notification :id str)
   :notification-type   (-> notification :notification-type)
   :organization-id     (-> notification :organization-id)
   :permanent-permit-id (-> notification :permanent-permit-id)
   :registration-id     (-> registration :id str)})

(defn notification-body
  "Prepare the JSON payload when notifying an AsiointiAPI consumer"
  [registration {:keys [extra-data] :as notification}]
  ;; NOTE: the output does not match `Notification` as-is but must be coerced,
  ;; as some of the fields are converted to strings.
  (cond-> (default-notification-body registration notification)
    extra-data (assoc :extra-data extra-data)))
