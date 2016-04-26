(ns lupapalvelu.resources-api
  (:require [sade.core :refer :all]
            [taoensso.timbre :as timbre :refer [info error]]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [schema.core :as sc :refer [defschema]]
            [sade.http :as http]
            [sade.env :as env])
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

(defn- ->FrontendCalendar [cal]
  (-> cal
      (select-keys [:id :name])
      (merge {:organization (:organizationCode cal)})))

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

(defquery calendar
  {:parameters [calendarId]
   :input-validators [(partial action/non-blank-parameters [:calendarId])]
   :user-roles #{:authorityAdmin}}
  [_]
  (let [response (api-query (str "/api/resources/" calendarId))]
    (if (= 200 (:status response))
      (ok :calendar (->FrontendCalendar (:body response)))
      (do (error response)
          (fail :resources.backend-error)))))

(defquery list-calendars
  {:user-roles #{:authorityAdmin}}
  [_]
  (let [response (api-query "/api/resources")]
    (if (= 200 (:status response))
      (ok :calendars (map ->FrontendCalendar (:body response)))
      (do (error response)
          (fail :resources.backend-error)))))

(defcommand create-calendar
  {:user-roles #{:authorityAdmin}}
  [{{:keys [name organization]} :data}]
  (let [url      "/api/resources/"
        response (api-post-command url {:name             name
                                        :organizationCode organization})]
    (ok :result (:body response))))

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
