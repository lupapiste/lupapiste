(ns lupapalvelu.asiointi.notifications.handler
  "Integration between the AsiointiAPI HTTP endpoints and the notification mechanism"
  (:require [lupapalvelu.asiointi.http-util :as http-util]
            [lupapalvelu.asiointi.notifications.notification :as notification]
            [lupapalvelu.asiointi.notifications.registration :as registration]
            [lupapalvelu.asiointi.shared-schemas :as schemas]
            [malli.util :as mu]
            [ring.util.response :as resp]
            [sade.date :as date])
  (:import [org.bson.types ObjectId]))

(set! *warn-on-reflection* true)

(def Registration
  (-> registration/Registration
      (mu/select-keys #{:target :ignored})
      (mu/assoc :id schemas/ObjectIdString)
      (mu/assoc :created-at schemas/DateString)))

(def RegistrationDraft (mu/select-keys Registration #{:target :ignored}))

(def Registrations [:map
                    [:registrations [:sequential Registration]]])

(defn- registration->api
  [registration]
  (-> registration
      (dissoc :created-by :organization-id)
      (update :id str)
      (update :created-at (fn [date] (date/zoned-format date :iso-datetime)))))

(defn find-registrations
  "Find all registrations for the organization.
  Returns a HTTP response."
  [organization-id]
  (-> {:registrations (->> (registration/org-id->registrations organization-id)
                           (mapv registration->api))}
      resp/response))

(defn find-registration
  "Find a registration for the organization.
  Returns a HTTP response."
  [{:keys [organization-id ^String registration-id]}]
  (or (some-> (registration/find-registration organization-id (ObjectId. registration-id))
              registration->api
              resp/response)
      http-util/not-found))

(defn- handle-error
  [text]
  (case (keyword text)
    :error.duplicate-registration (-> {:detail  "Duplicate registration on the given target URL."
                                       :pointer "/registration/target/url"}
                                      http-util/bad-request)
    :error.forbidden-host         (-> {:detail  "The host name within the target URL is not allowed."
                                       :pointer "/registration/target/url"}
                                      http-util/bad-request)
    :error.invalid-registration   (-> {:detail  "The given registration does not appear to be valid."
                                       :pointer "/registration/target/url"}
                                      http-util/bad-request)
    :error.invalid-uri            (-> {:detail  "The target URL is not a valid URI."
                                       :pointer "/registration/target/url"}
                                      http-util/bad-request)
    :error.invalid-url            (-> {:detail  "The target URL is not a valid URL."
                                       :pointer "/registration/target/url"}
                                      http-util/bad-request)
    :error.not-found              http-util/not-found
    (http-util/bad-request)))

(defn create-registration!
  "Creates a new registration.
  Returns a HTTP response."
  [{:keys [registration organization-id user created]}]
  (let [result (-> registration
                   (merge {:organization-id organization-id
                           :created         created
                           :user            user})
                   registration/create!)]
    (if (:ok result)
      (-> result :registration registration->api resp/response)
      (-> result :text handle-error))))

(defn update-registration!
  "Update an existing registration.
  Returns a HTTP response."
  [{:keys [^String registration-id registration organization-id]}]
  (let [{:keys [ok text registration]} (registration/update! (ObjectId. registration-id)
                                                             organization-id
                                                             registration)]
    (if ok
      (resp/response (registration->api registration))
      (handle-error text))))

(defn delete-registration!
  "Delete an existing registration (and any notifications).
  Returns a HTTP response."
  [{:keys [^String registration-id organization-id]}]
  (let [registration-id   (ObjectId. registration-id)
        {:keys [ok text]} (registration/delete! registration-id
                                                organization-id)]
    (if ok
      (do
        (notification/delete-by-registration! registration-id)
        (resp/status 204))
      (handle-error text))))
