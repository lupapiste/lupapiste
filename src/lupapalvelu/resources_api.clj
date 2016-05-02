(ns lupapalvelu.resources-api
  (:require [sade.core :refer :all]
            [taoensso.timbre :as timbre :refer [info error]]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [schema.core :as sc :refer [defschema]]
            [sade.http :as http]
            [sade.env :as env]
            [lupapalvelu.user :as user]
            [clojure.string :as string])
  (:import (org.joda.time DateTime)))

(defschema FrontendCalendar
  {:id            sc/Int
   :name          sc/Str
   :organization  sc/Str})

(defschema FrontendCalendarSlot
  {:id            sc/Int
   :startTime     sc/Int
   :endTime       sc/Int
   :status        (sc/enum :available :reserved)})

(def LocalDateTimeRange
  {:start DateTime
   :end   DateTime})

(defschema BackendReservationSlot
  {:time      LocalDateTimeRange
   :services [sc/Str]
   :capacity  sc/Int})

(defn- ->FrontendReservationSlots [backend-slots]
  (map (fn [s] {:id        (:id s)
                :status    :available
                :startTime (sade.util/to-millis-from-local-datetime-string (-> s :time :start))
                :endTime   (sade.util/to-millis-from-local-datetime-string (-> s :time :end))}) backend-slots))

(defn- build-url [& path-parts]
  (apply str (env/value :ajanvaraus :host) path-parts))

(defn- api-call [f action opts]
  (let [url (build-url action)]
    (info "Calling:" url " with " opts)
    (f url (merge {:as               :json
                   :throw-exceptions false
                   :basic-auth       [(env/value :ajanvaraus :username)
                                      (env/value :ajanvaraus :password)]} opts))))

(defn- api-query
  ([action]
    (api-call http/get action {}))
  ([action params]
    (api-call http/get action {:query-params params})))

(defn- api-post-command [action request-body]
  (api-call http/post action {:body (clj-http.client/json-encode request-body)
                              :content-type :json}))

(defn- api-put-command [action request-body]
  (api-call http/put action {:body (clj-http.client/json-encode request-body)
                             :content-type :json}))

(defn- ->BackendReservationSlots [slots]
  (map (fn [s]
         (let [{start :start end :end} s]
           {:time     {:start (sade.util/to-xml-local-datetime start)
                       :end (sade.util/to-xml-local-datetime end)}
            :services ["SCC123"]
            :capacity 1})) slots))

(defn- find-calendars-for-organizations
  [orgIds]
  (let [response (api-query "/api/resources/by-organization" {:organizationCodes orgIds})]
    (if (= 200 (:status response))
      (group-by :externalRef (:body response))
      nil)))

(defquery calendar
  {:parameters [calendarId]
   :input-validators [(partial action/non-blank-parameters [:calendarId])]
   :user-roles #{:authorityAdmin}}
  [_]
  (let [response (api-query (str "/api/resources/" calendarId))]
    (if (= 200 (:status response))
      (ok :calendar {})
      (do (error response)
          (fail :resources.backend-error)))))

(defquery calendar-admin-list-users
  {:user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [orgIds    (map name (-> user :orgAuthz keys))
        users     (user/authority-users-in-organizations orgIds)
        calendars (find-calendars-for-organizations orgIds)]
    (if calendars
      (let [users (map #(assoc % :calendar (get calendars (str "user-" (:id %)))) users)]
        (ok :users users))
      (fail :resources.backend-error))))

(defcommand set-user-calendar-enabled
  {:user-roles #{:authorityAdmin}}
  [{{:keys [userId]} :data user :user}]
  (let [admin-in-organization-id (user/authority-admins-organization-id user)
        target-user              (user/get-user-by-id userId)
        #_(validate user is in organisation)
        url      "/api/resources/"
        response (api-post-command url {:name             (str (:firstName target-user) " " (:lastName target-user))
                                        :organizationCode admin-in-organization-id
                                        :externalRef      (str "user-" userId)})]
    (if (= 200 (:status response))
      (ok)
      (do (error response)
          (fail :resources.backend-error)))))

(defcommand update-calendar
  {:user-roles #{:authorityAdmin}}
  [{{:keys [calendarId name organization]} :data}]
  (let [url (str "/api/resources/" calendarId)
        response (api-put-command url {:name             name
                                       :organizationCode organization})]
    (ok :result (:body response))))

(defquery calendar-slots
  {:user-roles #{:authorityAdmin}}
  [{{:keys [calendarId year week]} :data}]
  (let [url      (str "/api/reservationslots/calendar/" calendarId)
        response (api-query url {:year year
                                 :week week})]
    (if (= 200 (:status response))
      (let [be-slots (:body response)
            slots    (->FrontendReservationSlots be-slots)]
        (ok :slots slots))
      (do (error response)
          (fail :resources.backend-error)))))

(defcommand create-calendar-slots
  {:user-roles #{:authorityAdmin}}
  [{{:keys [calendarId slots]} :data}]
  (let [url      (str "/api/reservationslots/calendar/" calendarId)
        be-slots (->BackendReservationSlots slots)
        response (api-post-command url be-slots)]
    (ok :result (:body response))))
