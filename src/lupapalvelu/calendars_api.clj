(ns lupapalvelu.calendars-api
  (:require [sade.core :refer :all]
            [taoensso.timbre :as timbre :refer [info error]]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [schema.core :as sc :refer [defschema]]
            [sade.http :as http]
            [sade.env :as env]
            [sade.util :as util]
            [lupapalvelu.user :as usr]))

; -- coercions between LP Frontend <-> Calendars API <-> Ajanvaraus Backend

(defn- ->FrontendCalendar [calendar user]
  {:id           (:id calendar)
   :name         (format "%s %s" (:firstName user) (:lastName user))
   :email        (:email user)
   :organization (:organizationCode calendar)
   :active       (:active calendar)})

(defn- ->FrontendReservationSlots [backend-slots]
  (map (fn [s] {:id        (:id s)
                :status    (if (:fullyBooked s) :booked :available)
                :reservationTypes (map #(select-keys % [:id :name]) (:reservationTypes s))
                :startTime (util/to-millis-from-local-datetime-string (-> s :time :start))
                :endTime   (util/to-millis-from-local-datetime-string (-> s :time :end))}) backend-slots))

(defn- ->BackendReservationSlots [slots]
  (map (fn [s]
         (let [{start :start end :end reservationTypeIds :reservationTypes} s]
           {:time             {:start (util/to-xml-local-datetime start)
                               :end   (util/to-xml-local-datetime end)}
            :reservationTypeIds reservationTypeIds
            :capacity 1}))
       slots))

; -- API Call helpers

(defn- build-url [& path-parts]
  (apply str (env/value :ajanvaraus :host) path-parts))

(defn- api-call [f action opts]
  (let [url (build-url action)]
    (info "Calling:" url " with " opts)
    (f url (merge {:as               :json
                   :throw-exceptions false
                   :basic-auth       [(env/value :ajanvaraus :username)
                                      (env/value :ajanvaraus :password)]}
                  opts))))

(defn- api-query
  ([action]
   (api-query action {}))
  ([action params]
   (let [response (api-call http/get (str "/api/" (name action)) {:query-params params})]
     (when-not (= 200 (:status response))
       (error response)
       (fail! :resources.backend-error))
     (:body response))))

(defn- api-post-command
  [action request-body]
  (api-call http/post action {:body (clj-http.client/json-encode request-body)
                                :content-type :json}))

(defn- api-put-command [action request-body]
  (api-call http/put action {:body (clj-http.client/json-encode request-body)
                             :content-type :json}))

(defn- api-delete-command [action request-body]
  (api-call http/delete action {:body (clj-http.client/json-encode request-body)
                                :content-type :json}))

(defn- post-command
  ([command]
   (post-command command []))
  ([command request-body]
   (let [response (api-post-command (str "/api/" (name command)) request-body)]
     (when-not (= 200 (:status response))
       (error response)
       (fail! :resources.backend-error))
     (:body response))))

(defn- put-command
  ([command]
   (put-command command []))
  ([command request-body]
   (let [response (api-put-command (str "/api/" (name command)) request-body)]
     (when-not (= 200 (:status response))
       (error response)
       (fail! :resources.backend-error))
     (:body response))))

(defn- delete-command
  ([command]
   (delete-command command []))
  ([command request-body]
   (let [response (api-delete-command (str "/api/" (name command)) request-body)]
     (when-not (= 200 (:status response))
       (error response)
       (fail! :resources.backend-error))
     (:body response))))

; -- calendar API functions

(defn- calendar-ref-for-user [userId]
  (str "user-" userId))

(defn- calendar-belongs-to-user?
  [calendar userId]
  (.endsWith (:externalRef calendar) userId))

(defn- get-calendar
  [calendarId userId]
  (let [calendar (api-query (str "resources/" calendarId))
        user     (usr/get-user-by-id userId)]
    (when (calendar-belongs-to-user? calendar userId)
      (->FrontendCalendar calendar user))))

(defn- get-calendar-slot
  [slotId]
  (api-query (str "reservationslots/" slotId)))

(defn- authorized-to-edit-calendar?
  [user calendarId]
  (try
    (let [calendar (api-query (str "resources/" calendarId))
          orgId    (:organizationCode calendar)]
      (and (:active calendar) (or
        (and (usr/authority-admin? user) (= (usr/authority-admins-organization-id user) orgId))
        (and (usr/authority? user) (calendar-belongs-to-user? calendar (:id user))))))
    (catch Exception _
      false)))

(defn- find-calendars-for-organizations
  [& orgIds]
  (group-by :externalRef (api-query "resources/by-organization" {:organizationCodes orgIds})))

(defn- find-calendars-for-user
  [userId]
  (api-query (str "resources/by-external-ref/" (calendar-ref-for-user userId))))

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

(defn- create-calendar
  [userId target-user organization]
  (info "Creating a new calendar" userId organization)
  (post-command "resources" {:name             (str (:firstName target-user) " " (:lastName target-user))
                                  :organizationCode organization
                                  :externalRef      (calendar-ref-for-user userId)}))

(defn- activate-resource
  [{:keys [id]}]
  (info "Activating calendar" id)
  (post-command (str "resources/" id "/activate"))
  id)

(defn- deactivate-resource
  [{:keys [id]}]
  (info "Deactivating calendar" id)
  (post-command (str "resources/" id "/deactivate"))
  id)

(defn- enable-calendar
  [userId organization]
  (let [target-user       (usr/get-user-by-id userId)
        existing-calendar (find-calendar-for-user-and-organization userId organization)]
    (when-not (usr/user-is-authority-in-organization? (usr/with-org-auth target-user) organization)
      (error "Tried to enable a calendar with invalid user-organization pairing: " userId organization)
      (unauthorized!))
    (if existing-calendar
      (activate-resource existing-calendar)
      (create-calendar userId target-user organization))))

(defn- disable-calendar
  [userId organization]
  (let [target-user       (usr/get-user-by-id userId)
        existing-calendar (find-calendar-for-user-and-organization userId organization)]
    (when-not (usr/user-is-authority-in-organization? (usr/with-org-auth target-user) organization)
      (error "Tried to disable a calendar with invalid user-organization pairing: " userId organization)
      (unauthorized!))
    (when-not existing-calendar
      (info "Tried to disable a calendar but no such calendar could not be found:" userId organization)
      (fail! :error.unknown))
    (deactivate-resource existing-calendar)))

(defn- reservation-types
  [organization]
  (api-query "reservation-types/by-organization" {:organization organization}))

(defquery my-calendars
  {:user-roles #{:authority}
   :feature :ajanvaraus}
  [{user :user}]
  (let [calendars     (map #(->FrontendCalendar % user) (find-calendars-for-user (:id user)))
        calendars     (filter :active calendars)
        organizations (keys (group-by :organization calendars))]
    (ok :calendars        calendars
        :reservationTypes (zipmap organizations (map reservation-types organizations)))))

(defquery calendar
  {:parameters [calendarId userId]
   :feature :ajanvaraus
   :input-validators [(partial action/non-blank-parameters [:calendarId :userId])]
   :user-roles #{:authorityAdmin :authority}}
  [{user :user}]
  (let [calendar (get-calendar calendarId userId)]
    (when-not (authorized-to-edit-calendar? user calendarId)
      (unauthorized!))
    (when-not (:active calendar)
      (unauthorized!))
    (ok :calendar calendar)))

(defquery calendars-for-authority-admin
  {:user-roles #{:authorityAdmin}
   :feature :ajanvaraus}
  [{user :user}]
  (let [admin-in-organization-id (usr/authority-admins-organization-id user)
        users                    (usr/authority-users-in-organizations [admin-in-organization-id])
        calendars                (find-calendars-for-organizations admin-in-organization-id)]
    (if calendars
      (let [users (map #(authority-admin-assoc-calendar-to-user calendars %) users)]
        (ok :users users))
      (fail :resources.backend-error))))

(defcommand set-calendar-enabled-for-authority
  {:user-roles #{:authorityAdmin}
   :parameters [userId enabled]
   :input-validators [(partial action/non-blank-parameters [:userId])
                      (partial action/boolean-parameters [:enabled])]
   :feature    :ajanvaraus}
  [{adminUser :user}]
  (let [orgId (usr/authority-admins-organization-id adminUser)]
    (info "Set calendar enabled status" userId "in organization" orgId "as" enabled)
    (if enabled
      (ok :calendarId (enable-calendar userId orgId) :enabled true)
      (ok :calendarId (disable-calendar userId orgId) :disabled true))))

(defquery calendar-slots
  {:user-roles #{:authorityAdmin :authority}
   :parameters [calendarId year week]
   :input-validators [(partial action/non-blank-parameters [:calendarId :year :week])]
   :feature    :ajanvaraus}
  [_]
  (->> (api-query (str "reservationslots/calendar/" calendarId) {:year year :week week})
       ->FrontendReservationSlots
       (ok :slots)))

(defn- valid-frontend-slot? [m]
  (and (map? m) (number? (:start m)) (number? (:end m))
       (not (empty? (:reservationTypes m)))
       (every? number? (:reservationTypes m))))

(defcommand create-calendar-slots
  {:user-roles #{:authorityAdmin :authority}
   :parameters [calendarId slots]
   :input-validators [(partial action/number-parameters [:calendarId])
                      (partial action/vector-parameter-of :slots valid-frontend-slot?)]
   :feature    :ajanvaraus}
  [{user :user}]
  (when-not (authorized-to-edit-calendar? user calendarId)
    (unauthorized!))
  (info "Creating new calendar slots in calendar #" calendarId)
  (->> slots
       ->BackendReservationSlots
       (post-command (str "reservationslots/calendar/" calendarId))
       (ok :result)))

(defcommand delete-calendar-slot
  {:user-roles       #{:authorityAdmin :authority}
   :parameters       [slotId]
   :input-validators [(partial action/number-parameters [:slotId])]
   :feature          :ajanvaraus}
  [{user :user}]
  (let [slot       (get-calendar-slot slotId)
        calendarId (:resourceId slot)]
    (println slot)
    (when-not (authorized-to-edit-calendar? user calendarId)
      (unauthorized!))
    (when (> (:amountOfReservations slot) 1)
      (fail! :error.calendar.cannot-delete-a-booked-slot))
    (info "Deleting reservation slot #" slotId)
    (ok :result (delete-command (str "reservationslots/" slotId)))))

(defcommand add-reservation-type-for-organization
  {:user-roles #{:authorityAdmin}
   :parameters [reservationType]
   :input-validators [(partial action/non-blank-parameters [:reservationType])]
   :feature    :ajanvaraus}
  [{user :user}]
  (info "Inserting a reservation type ")
  (ok :result (post-command "reservation-types/" {:reservationType   reservationType
                                                       :organization      (usr/authority-admins-organization-id user)})))

(defquery reservation-types-for-organization
  {:user-roles #{:authorityAdmin}
   :feature    :ajanvaraus}
  [{user :user}]
  (let [admin-in-organization-id (usr/authority-admins-organization-id user)]
    (info "Get reservation types for organization" admin-in-organization-id)
    (ok :organization admin-in-organization-id
        :reservationTypes (reservation-types admin-in-organization-id))))

(defcommand update-reservation-type
  {:user-roles #{:authorityAdmin}
   :parameters [reservationTypeId name]
   :input-validators [(partial action/number-parameters [:reservationTypeId])
                      (partial action/non-blank-parameters [:name])]
   :feature :ajanvaraus}
  [_]
  (ok :reservationTypes (put-command (str "reservation-types/" reservationTypeId) {:name name})))

(defcommand delete-reservation-type
  {:user-roles #{:authorityAdmin}
   :parameters [reservationTypeId]
   :input-validators [(partial action/number-parameters [:reservationTypeId])]
   :feature    :ajanvaraus}
  [_]
  (ok :reservationTypes (delete-command (str "reservation-types/" reservationTypeId))))

; For integration tests in dev
(env/in-dev
  (defn clear-database
    []
    (ok :res (post-command "testdata/clear"))))