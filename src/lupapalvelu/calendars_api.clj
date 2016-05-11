(ns lupapalvelu.calendars-api
  (:require [sade.core :refer :all]
            [taoensso.timbre :as timbre :refer [info error]]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [schema.core :as sc :refer [defschema]]
            [sade.http :as http]
            [sade.env :as env]
            [lupapalvelu.user :as user]
            [clojure.string :as string])
  (:import (org.joda.time DateTime)))

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

(defn- api-post-command
  ([action]
   (api-post-command action {}))
  ([action request-body]
    (api-call http/post action {:body (clj-http.client/json-encode request-body)
                                :content-type :json})))

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

(defn- calendar-ref-for-user [userId]
  (str "user-" userId))

(defn- get-calendar
  [calendarId userId]
  (let [response (api-query (str "/api/resources/" calendarId))]
    (if (= 200 (:status response))
      (let [calendar     (:body response)
            external-ref (:externalRef calendar)]
        (if (.endsWith external-ref userId)
          {:id           (:id calendar)
           :organization (:organizationCode calendar)}))
      (do (error response)
          (fail! :resources.backend-error)))))

(defn- find-calendars-for-organizations
  [& orgIds]
  (let [response (api-query "/api/resources/by-organization" {:organizationCodes orgIds})]
    (if (= 200 (:status response))
      (let [calendars (:body response)]
        (group-by :externalRef calendars))
      nil)))

(defn- find-calendars-for-user
  [userId]
  (let [response (api-query (str "/api/resources/by-external-ref/" (calendar-ref-for-user userId)))]
    (if (= 200 (:status response))
      (:body response)
      nil)))

(defn- find-calendar-for-user-and-organization
  [userId orgId]
  (->> (find-calendars-for-user userId)
       (filter #(= (:organizationCode %) orgId))
       first))

(defn- authority-admin-assoc-calendar-to-user [cals user]
  (let [reference-code-for-user (calendar-ref-for-user (:id user))
        calendars-for-user      (get cals reference-code-for-user [])
        calendar                (first calendars-for-user)]
    (if calendar
      (cond
        (:active calendar) (assoc user :calendarId (:id calendar))
        :else              (assoc user :calendarPassive true))
      user)))

(defquery calendar
  {:parameters [calendarId userId]
   :feature :ajanvaraus
   :input-validators [(partial action/non-blank-parameters [:calendarId :userId])]
   :user-roles #{:authorityAdmin}}
  [_]
  (let [calendar (get-calendar calendarId userId)
        user     (user/get-user-by-id userId)]
    (ok :calendar (assoc calendar :name (format "%s %s" (:firstName user) (:lastName user))
                                  :email (:email user)))))

(defquery calendars-for-authority-admin
  {:user-roles #{:authorityAdmin}
   :feature :ajanvaraus}
  [{user :user}]
  (let [admin-in-organization-id (user/authority-admins-organization-id user)
        users                    (user/authority-users-in-organizations [admin-in-organization-id])
        calendars                (find-calendars-for-organizations admin-in-organization-id)]
    (if calendars
      (let [users (map #(authority-admin-assoc-calendar-to-user calendars %) users)]
        (ok :users users))
      (fail :resources.backend-error))))

(defn- create-calendar
  [userId target-user organization]
  (let [url          "/api/resources/"
        response    (api-post-command url {:name             (str (:firstName target-user) " " (:lastName target-user))
                                           :organizationCode organization
                                           :externalRef      (calendar-ref-for-user userId)})]
    (info "Creating a new calendar" userId organization)
    (if (= 200 (:status response))
      (:body response)
      (do (error response)
          (fail! :resources.backend-error)))))

(defn- activate-calendar
  [{:keys [id]}]
  (let [url      (str "/api/resources/" id "/activate")
        response (api-post-command url)]
    (info "Activating calendar" id)
    (if (= 200 (:status response))
      id
      (do (error response)
          (fail! :resources.backend-error)))))

(defn- enable-calendar
  [userId organization]
  (let [target-user       (user/get-user-by-id userId)
        existing-calendar (find-calendar-for-user-and-organization userId organization)]
    (when-not (user/user-is-authority-in-organization? (user/with-org-auth target-user) organization)
      (error "Tried to enable a calendar with invalid user-organization pairing: " userId organization)
      (fail! :error.unknown-operation))
    (if existing-calendar
      (activate-calendar existing-calendar)
      (create-calendar userId target-user organization))))

(defcommand set-calendar-enabled-for-authority
  {:user-roles #{:authorityAdmin}
   :feature :ajanvaraus}
  [{{:keys [userId enabled]} :data user :user}]
  (if enabled
    (ok :calendarId (enable-calendar userId (user/authority-admins-organization-id user)))
    (fail! :not-implemented)))

(defquery calendar-slots
  {:user-roles #{:authorityAdmin}
   :feature :ajanvaraus}
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
  {:user-roles #{:authorityAdmin}
   :feature :ajanvaraus}
  [{{:keys [calendarId slots]} :data}]
  (let [url      (str "/api/reservationslots/calendar/" calendarId)
        be-slots (->BackendReservationSlots slots)
        response (api-post-command url be-slots)]
    (ok :result (:body response))))
