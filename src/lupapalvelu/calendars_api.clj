(ns lupapalvelu.calendars-api
  (:require [sade.core :refer :all]
            [taoensso.timbre :as timbre :refer [info error]]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [sade.env :as env]
            [sade.util :as util]
            [lupapalvelu.calendar :as cal :refer [api-query post-command put-command delete-command]]
            [lupapalvelu.user :as usr]
            [lupapalvelu.organization :as o]))

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

(defn- ->FrontendReservations [backend-reservations]
  (map (fn [r] {:id        (:id r)
                :status    :booked
                :reservationStatus (:status r)
                :reservationType (:reservationType r)
                :startTime (util/to-millis-from-local-datetime-string (-> r :time :start))
                :endTime   (util/to-millis-from-local-datetime-string (-> r :time :end))
                :comment   (:comment r)
                :location  (:location r)
                :applicationId (:contextId r)}) backend-reservations))

(defn- ->BackendReservationSlots [slots]
  (map (fn [s]
         (let [{start :start end :end reservationTypeIds :reservationTypes} s]
           {:time             {:start (util/to-xml-local-datetime start)
                               :end   (util/to-xml-local-datetime end)}
            :reservationTypeIds reservationTypeIds
            :capacity 1}))
       slots))


; -- calendar API functions

(defn- get-calendar
  [calendarId userId]
  (let [calendar (api-query (str "resources/" calendarId))
        user     (usr/get-user-by-id userId)]
    (when (cal/calendar-belongs-to-user? calendar userId)
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
        (and (usr/authority? user) (cal/calendar-belongs-to-user? calendar (:id user))))))
    (catch Exception _
      false)))

(defn- authority-admin-assoc-calendar-to-user [cals user]
  (let [reference-code-for-user (:id user)
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
                                  :externalRef      userId}))

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
        existing-calendar (cal/find-calendar-for-user-and-organization userId organization)]
    (when-not (usr/user-is-authority-in-organization? (usr/with-org-auth target-user) organization)
      (error "Tried to enable a calendar with invalid user-organization pairing: " userId organization)
      (unauthorized!))
    (if existing-calendar
      (activate-resource existing-calendar)
      (create-calendar userId target-user organization))))

(defn- disable-calendar
  [userId organization]
  (let [target-user       (usr/get-user-by-id userId)
        existing-calendar (cal/find-calendar-for-user-and-organization userId organization)]
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
   :feature :ajanvaraus
   :pre-checks [(partial cal/calendars-enabled-api-pre-check #{:authority})]}
  [{user :user}]
  (let [calendars     (map #(->FrontendCalendar % user) (cal/find-calendars-for-user (:id user)))
        calendars     (filter :active calendars)
        organizations (keys (group-by :organization calendars))]
    (ok :calendars        calendars
        :reservationTypes (zipmap organizations (map reservation-types organizations)))))

(defquery calendar
  {:parameters [calendarId userId]
   :feature :ajanvaraus
   :input-validators [(partial action/non-blank-parameters [:calendarId :userId])]
   :user-roles #{:authorityAdmin :authority}
   :pre-checks [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin :authority})]}
  [{user :user}]
  (let [calendar (get-calendar calendarId userId)]
    (when-not (authorized-to-edit-calendar? user calendarId)
      (unauthorized!))
    (when-not (:active calendar)
      (unauthorized!))
    (ok :calendar calendar)))

(defquery calendars-for-authority-admin
  {:user-roles #{:authorityAdmin}
   :feature :ajanvaraus
  :pre-checks [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin})]}
  [{user :user}]
  (let [admin-in-organization-id (usr/authority-admins-organization-id user)
        users                    (usr/authority-users-in-organizations [admin-in-organization-id])
        calendars                (cal/find-calendars-for-organizations admin-in-organization-id)]
    (if calendars
      (let [users (map #(authority-admin-assoc-calendar-to-user calendars %) users)]
        (ok :users users))
      (fail :resources.backend-error))))

(defcommand set-calendar-enabled-for-authority
  {:user-roles #{:authorityAdmin}
   :parameters [userId enabled]
   :input-validators [(partial action/non-blank-parameters [:userId])
                      (partial action/boolean-parameters [:enabled])]
   :feature    :ajanvaraus
   :pre-checks [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin})]}
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
   :feature    :ajanvaraus
   :pre-checks [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin :authority})]}
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
   :feature    :ajanvaraus
   :pre-checks [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin :authority})]}
  [{user :user}]
  (when-not (authorized-to-edit-calendar? user calendarId)
    (unauthorized!))
  (info "Creating new calendar slots in calendar #" calendarId)
  (->> slots
       ->BackendReservationSlots
       (post-command (str "reservationslots/calendar/" calendarId))
       (ok :result)))

(defcommand update-calendar-slot
  {:user-roles       #{:authorityAdmin :authority}
   :parameters       [slotId reservationTypeIds]
   :input-validators [(partial action/number-parameters [:slotId])
                      (partial action/vector-parameters [:reservationTypeIds])]
   :feature          :ajanvaraus}
  [{user :user}]
  (let [slot       (get-calendar-slot slotId)
        calendarId (:resourceId slot)]
    (when-not (authorized-to-edit-calendar? user calendarId)
      (unauthorized!))
    (info "Updating calendar slot #" slotId)
    (ok :result (put-command (str "reservationslots/" slotId) {:capacity 1 :reservationTypeIds reservationTypeIds}))))

(defcommand delete-calendar-slot
  {:user-roles       #{:authorityAdmin :authority}
   :parameters       [slotId]
   :input-validators [(partial action/number-parameters [:slotId])]
   :feature          :ajanvaraus
   :pre-checks [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin :authority})]}
  [{user :user}]
  (let [slot       (get-calendar-slot slotId)
        calendarId (:resourceId slot)]
    (when-not (authorized-to-edit-calendar? user calendarId)
      (unauthorized!))
    (info "Deleting reservation slot #" slotId)
    (ok :result (delete-command (str "reservationslots/" slotId)))))

(defcommand add-reservation-type-for-organization
  {:user-roles #{:authorityAdmin}
   :parameters [reservationType]
   :input-validators [(partial action/non-blank-parameters [:reservationType])]
   :feature    :ajanvaraus
   :pre-checks [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin})]}
  [{user :user}]
  (info "Inserting a reservation type ")
  (ok :result (post-command "reservation-types/" {:reservationType   reservationType
                                                  :organization      (usr/authority-admins-organization-id user)})))

(defquery reservation-types-for-organization
  {:user-roles #{:authorityAdmin :authority}
   :parameters [organizationId]
   :input-validators [(partial action/non-blank-parameters [:organizationId])]
   :feature    :ajanvaraus
   :pre-checks [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin :authority})]}
  [_]
  (info "Get reservation types for organization" organizationId)
  (ok :organization organizationId
      :reservationTypes (reservation-types organizationId)))

(defcommand update-reservation-type
  {:user-roles #{:authorityAdmin}
   :parameters [reservationTypeId name]
   :input-validators [(partial action/number-parameters [:reservationTypeId])
                      (partial action/non-blank-parameters [:name])]
   :feature :ajanvaraus
   :pre-checks [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin})]}
  [_]
  (ok :reservationTypes (put-command (str "reservation-types/" reservationTypeId) {:name name})))

(defcommand delete-reservation-type
  {:user-roles #{:authorityAdmin}
   :parameters [reservationTypeId]
   :input-validators [(partial action/number-parameters [:reservationTypeId])]
   :feature    :ajanvaraus
   :pre-checks [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin})]}
  [_]
  (ok :reservationTypes (delete-command (str "reservation-types/" reservationTypeId))))

(defquery available-calendar-slots
  {:user-roles #{:authority :applicant}
   :feature    :ajanvaraus
   :parameters       [authorityId clientId reservationTypeId year week]
   :input-validators [(partial action/non-blank-parameters [:authorityId :clientId :reservationTypeId :year :week])]
   :pre-checks [(partial cal/calendars-enabled-api-pre-check #{:authority :applicant})]}
  [_]
  (ok :slots (->FrontendReservationSlots
               (cal/available-calendar-slots-for-appointment {:year year :week week
                                                              :authority authorityId
                                                              :clientId clientId
                                                              :reservationTypeId reservationTypeId}))))

(defquery application-calendar-config
  {:user-roles #{:applicant :authority}
   :parameters [:id]
   :feature    :ajanvaraus
   :pre-checks [(partial cal/calendars-enabled-api-pre-check #{:applicant :authority})]}
  [{{:keys [organization] :as appl} :application}]
  (ok :authorities (cal/find-application-authz-with-calendar appl)
      :reservationTypes (reservation-types organization)
      :defaultLocation (get-in (o/get-organization organization) [:reservations :default-location] "")))

(defcommand reserve-calendar-slot
  {:user-roles       #{:authorityAdmin :authority}
   :feature          :ajanvaraus
   :parameters       [clientId slotId reservationTypeId comment location :id]
   :input-validators [(partial action/number-parameters [:slotId :reservationTypeId])
                      (partial action/string-parameters [:clientId :comment :location])]
   :pre-checks       [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin :authority})]}
  [{{:keys [id]} :application}]
  (ok :result (post-command "reservation/" {:clientId clientId :reservationSlotId slotId
                                            :reservationTypeId reservationTypeId :comment comment
                                            :location location :contextId id })))

(defquery my-reservations
  {:user-roles       #{:authority :applicant}
   :feature          :ajanvaraus
   :parameters       [year week]
   :input-validators [(partial action/non-blank-parameters [:year :week])]}
  [{{:keys [id] :as user} :user}]
  (->>
    (cond
      (usr/authority? user) (api-query (str "reservations/by-external-ref/" id) {:year year :week week})
      (usr/applicant? user) [])
    ->FrontendReservations
    (ok :reservations)))

; For integration tests in dev
(env/in-dev
  (defn clear-database
    []
    (ok :res (post-command "testdata/clear"))))