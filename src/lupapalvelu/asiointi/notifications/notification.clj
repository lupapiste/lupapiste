(ns lupapalvelu.asiointi.notifications.notification
  (:require [lupapalvelu.asiointi.notifications.common :as common]
            [lupapalvelu.asiointi.shared-schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.ryhti.api.mappings :as ryhti-mappings]
            [malli.core :as m]
            [malli.experimental :as mx]
            [malli.transform :as malli-transform]
            [malli.util :as mu]
            [monger.operators :refer [$exists $set $unset]]
            [sade.util :as util])
  (:import [java.util Date]))

(set! *warn-on-reflection* true)

;; Note that the `:description`s are mostly for the API consumers

(def BaseNotification
  [:map {:closed true}
   [:id schemas/ObjectIdField]
   [:registration-id
    {:description "The ID for the source registration"}
    schemas/ObjectIdField]
   [:created-at
    {:description "Timestamp when the notification was originally created"}
    schemas/DateField]
   [:pending {:optional true} :boolean]
   [:attempting-at {:optional true} schemas/DateField]
   [:last-attempt-at {:optional true} schemas/DateField]
   [:delivered-at {:optional true} schemas/DateField]
   [:organization-id
    {:description         "The parent organization of the source application"
     :json-schema/example "123-R"}
    schemas/NonBlankStr]
   [:permanent-permit-id
    {:description "The permanent permit ID for the source application"}
    schemas/NonBlankStr]
   [:control-case (into [:enum] (keys ryhti-mappings/building-control-case))]
   [:application-id
    {:description "The Lupapiste application ID for the source application"}
    schemas/ApplicationId]
   [:notification-type (into [:enum] common/notification-types)]])

(def NotificationStateChange
  [:map {:closed true}
   [:old-state
    {:description         "The application state before the state change"
     :json-schema/example "verdictGiven"}
    (into [:enum] common/all-application-states)]
   [:new-state
    {:description         "The application state after the state change"
     :json-schema/example "constructionStarted"}
    (into [:enum] common/all-application-states)]])

(defn- with-notification-type
  [base-schema notification-type description extra-data]
  {:pre [(contains? common/notification-types notification-type)]}
  [notification-type
   {:description description}
   (-> base-schema
       (mu/assoc :notification-type [:enum notification-type])
       (mu/assoc :extra-data extra-data))])

(defn make-notification-schema
  [base-schema]
  (m/schema
    [:multi {:dispatch    :notification-type
             :decode/json (fn [v]
                            (update v :notification-type keyword))}
     (with-notification-type
       base-schema
       :application-state-change
       "Application state change"
       NotificationStateChange)]))

(def Notification (make-notification-schema BaseNotification))

(def coerce-notification! (m/coercer Notification malli-transform/json-transformer))

(mx/defn ->notification :- Notification
  {:malli/always true}
  [{:keys [application-id ^long created control-case notification-type
           organization-id permanent-permit-id registration-id extra-data]}]
  (util/assoc-when {:application-id      application-id
                    :control-case        control-case
                    :created-at          (Date. created)
                    :id                  (mongo/create-object-id)
                    :notification-type   notification-type
                    :organization-id     organization-id
                    :pending             true
                    :permanent-permit-id permanent-permit-id
                    :registration-id     registration-id}
                   ;; Some notifications may contain extra data
                   :extra-data extra-data))

(mx/defn store-notifications!
  "Store multiple notifications"
  {:malli/always true}
  [notifications :- [:sequential Notification]]
  (when (seq notifications)
    (mongo/insert-batch :asiointiapi-notifications notifications)))

(defn registration->pending-notification-id
  "Find the next pending notification (id) for the given registration, or `nil`."
  ;; See the "pending_asiointiapi_notifications" index.
  [registration-id]
  (some->> (mongo/select-options :asiointiapi-notifications
                                 {:registration-id registration-id
                                  :pending         true}
                                 {:projection [:_id]
                                  :sort       (array-map :_id 1)
                                  :limit      1})
           first
           :id))

(defn get-notification
  "Return notification or throw."
  [notification-id]
  (-> (or (mongo/select-one :asiointiapi-notifications
                            {:_id notification-id})
          (throw (ex-info "No notification found" {:id notification-id})))
      ;; Poor mans migration for existing notifications, can be removed later
      (update :control-case (fnil identity "building"))
      coerce-notification!))

(defn reserve!
  "Try to reserve a pending notification for us."
  [notification-id]
  (let [now (Date.)]
    (= 1 (mongo/update-by-query :asiointiapi-notifications
                                {:_id           notification-id
                                 :pending       true
                                 :attempting-at {$exists false}}
                                {$set {:attempting-at   now
                                       :last-attempt-at now}}))))

(defn release!
  [notification-id]
  (mongo/update-by-id :asiointiapi-notifications
                      notification-id
                      {$unset {:attempting-at 1}}))

(defn mark-delivered-and-release!
  [notification-id]
  (mongo/update-by-id :asiointiapi-notifications
                      notification-id
                      {$set   {:delivered-at (Date.)}
                       $unset {:pending       1
                               :attempting-at 1}}))

(defn delete-by-registration!
  [registration-id]
  (mongo/remove-many :asiointiapi-notifications
                     {:registration-id registration-id}))
