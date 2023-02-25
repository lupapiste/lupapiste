(ns lupapalvelu.calendars-api
  (:require [clj-time.coerce :as tc]
            [clj-time.format :as tf]
            [lupapalvelu.action :refer [defquery defcommand notify] :as action]
            [lupapalvelu.calendar :as cal :refer [api-query post-command put-command delete-command]]
            [lupapalvelu.calendar-emails]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.date :as date]
            [taoensso.timbre :refer [info error]]))

(def ^:private LOCAL-DATETIME (date/pattern-formatter "YYYY-MM-dd'T'HH:mm:ss"))

(defn to-xml-local-datetime [^Long timestamp]
  (date/zoned-format timestamp LOCAL-DATETIME))

(defn to-millis-from-local-datetime-string [^String datetime-as-string]
  (when-not (ss/blank? datetime-as-string)
    (let [d (tf/parse (tf/formatter-local "YYYY-MM-dd'T'HH:mm" ) datetime-as-string)]
      (tc/to-long d))))

; -- coercions between LP Frontend <-> Calendars API <-> Ajanvaraus Backend

(defn- reservation-participants [res] (flatten (vals (select-keys res [:from :to :externalRef :clientId]))))

(defn- ->FrontendCalendar [calendar user]
  {:id               (:id calendar)
   :name             (format "%s %s" (:firstName user) (:lastName user))
   :email            (:email user)
   :organization     (:organizationCode calendar)
   :organizationName (get-in (org/get-organization (:organizationCode calendar)) [:name])
   :active           (:active calendar)})

(defn- ->FrontendReservation [r]
  {:id                (:id r)
   :status            :booked
   :reservationStatus (:status r)
   :reservationType   (:reservationType r)
   :startTime         (to-millis-from-local-datetime-string (-> r :time :start))
   :endTime           (to-millis-from-local-datetime-string (-> r :time :end))
   :comment           (:comment r)
   :location          (:location r)
   :applicationId     (:contextId r)
   :reservedBy        (:reservedBy r)
   :participants      (map usr/get-user-by-id (reservation-participants r))})

(defn Reservations->FrontendSlots
  [status reservations]
  (map (fn [r] {:id               (:slotId r)
                :status           status
                :reservationTypes [(:reservationType r)]
                :startTime        (to-millis-from-local-datetime-string (-> r :time :start))
                :endTime          (to-millis-from-local-datetime-string (-> r :time :end))
                :reservation      (assoc (->FrontendReservation r) :status status)}) reservations))

(defn ->FrontendReservationSlots
  ([backend-slots]
   (map (fn [s] {:id               (:id s)
                 :status           (if (:fullyBooked s) :booked :available)
                 :reservationTypes (map #(select-keys % [:id :name]) (:reservationTypes s))
                 :startTime        (to-millis-from-local-datetime-string (-> s :time :start))
                 :endTime          (to-millis-from-local-datetime-string (-> s :time :end))}) backend-slots))
  ([backend-slots reservations]
    ; associate reservation data to the slots when exists
   (reduce
     (fn [acc {slotId :id :as slot}]
       (let [res-for-slot (util/find-by-key :slotId slotId reservations)]
         (conj acc (if res-for-slot
                     (assoc slot :reservation (->FrontendReservation res-for-slot))
                     slot))))
     []
     (->FrontendReservationSlots backend-slots))))

(defn- ->BackendReservationSlots [slots]
  (map (fn [s]
         (let [{start :start end :end reservationTypeIds :reservationTypes} s]
           {:time               {:start (to-xml-local-datetime start)
                                 :end   (to-xml-local-datetime end)}
            :reservationTypeIds reservationTypeIds
            :capacity           1}))
       slots))


; -- calendar API functions

(defn get-calendar
  [calendarId userId]
  (let [calendar (cal/get-calendar-for-resource calendarId)
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
      (and (:active calendar)
           (or (usr/authority-admin-in? orgId user)
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
        :else (assoc user :calendarPassive true))
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

(defn- with-reservation-types
  [calendar]
  (assoc calendar :reservationTypes (reservation-types (:organization calendar))))

(defquery my-calendars
  {:user-roles #{:authority}
   :feature    :ajanvaraus
   :pre-checks [(partial cal/calendars-enabled-api-pre-check #{:authority})]}
  [{user :user}]
  (let [calendars (map #(->FrontendCalendar % user) (cal/find-calendars-for-user (:id user)))
        calendars (filter :active calendars)
        calendars (map with-reservation-types calendars)]
    (ok :calendars calendars)))

(defquery calendar
  {:parameters       [calendarId userId]
   :feature          :ajanvaraus
   :input-validators [(partial action/non-blank-parameters [:calendarId :userId])]
   :user-roles       #{:authority}
   :pre-checks       [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin :authority})]}
  [{user :user}]
  (let [calendar (get-calendar calendarId userId)]
    (when-not (authorized-to-edit-calendar? user calendarId)
      (unauthorized!))
    (when-not (:active calendar)
      (unauthorized!))
    (ok :calendar calendar)))

(defquery calendars-for-authority-admin
  {:parameters [organizationId]
   :permissions [{:required [:organization/admin]}]
   :feature     :ajanvaraus
   :pre-checks  [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin})]}
  [_]
  (let [users (usr/authority-users-in-organizations [organizationId])
        calendars (cal/find-calendars-for-organizations organizationId)]
    (if calendars
      (let [users (map #(authority-admin-assoc-calendar-to-user calendars %) users)]
        (ok :users users))
      (fail :resources.backend-error))))

(defcommand set-calendar-enabled-for-authority
  {:permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId userId enabled]
   :input-validators [(partial action/non-blank-parameters [:userId])
                      (partial action/boolean-parameters [:enabled])]
   :feature          :ajanvaraus
   :pre-checks       [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin})]}
  [_]
  (info "Set calendar enabled status" userId "in organization" organizationId "as" enabled)
  (if enabled
    (ok :calendarId (enable-calendar userId organizationId) :enabled true)
    (ok :calendarId (disable-calendar userId organizationId) :disabled true)))

(defquery calendar-slots
  {:user-roles       #{:authority}
   :parameters       [calendarId year week]
   :input-validators [(partial action/non-blank-parameters [:calendarId :year :week])]
   :feature          :ajanvaraus
   :pre-checks       [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin :authority})]}
  [_]
  (let [slots        (api-query (str "reservationslots/calendar/" calendarId) {:year year :week week})
        userId       (cal/get-calendar-user-id calendarId)
        reservations (cal/authority-reservations userId {:year year :week week})]
    (ok :slots (->FrontendReservationSlots slots reservations)))
  #_(->> slots
         ->FrontendReservationSlots
         (ok :slots)))

(defn- valid-frontend-slot? [m]
  (and (map? m) (number? (:start m)) (number? (:end m))
       (not (empty? (:reservationTypes m)))
       (every? number? (:reservationTypes m))))

(defcommand create-calendar-slots
  {:user-roles       #{:authority}
   :parameters       [calendarId slots]
   :input-validators [(partial action/number-parameters [:calendarId])
                      (partial action/vector-parameter-of :slots valid-frontend-slot?)]
   :feature          :ajanvaraus
   :pre-checks       [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin :authority})]}
  [{user :user}]
  (when-not (authorized-to-edit-calendar? user calendarId)
    (unauthorized!))
  (info "Creating new calendar slots in calendar #" calendarId)
  (->> slots
       ->BackendReservationSlots
       (post-command (str "reservationslots/calendar/" calendarId))
       (ok :result)))

(defcommand update-calendar-slot
  {:user-roles       #{:authority}
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
  {:user-roles       #{:authority}
   :parameters       [slotId]
   :input-validators [(partial action/number-parameters [:slotId])]
   :feature          :ajanvaraus
   :pre-checks       [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin :authority})]}
  [{user :user}]
  (let [slot       (get-calendar-slot slotId)
        calendarId (:resourceId slot)]
    (when-not (authorized-to-edit-calendar? user calendarId)
      (unauthorized!))
    (info "Deleting reservation slot #" slotId)
    (ok :result (delete-command (str "reservationslots/" slotId)))))

(defcommand add-reservation-type-for-organization
  {:permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId reservationType]
   :input-validators [(partial action/non-blank-parameters [:reservationType])]
   :feature          :ajanvaraus
   :pre-checks       [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin})]}
  [_]
  (info "Inserting a reservation type ")
  (ok :result (post-command "reservation-types/" {:reservationType reservationType
                                                  :organization    organizationId})))

(defquery reservation-types-for-organization
  {:user-roles       #{:authority}
   :parameters       [organizationId]
   :input-validators [(partial action/non-blank-parameters [:organizationId])]
   :feature          :ajanvaraus
   :pre-checks       [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin :authority})]}
  [_]
  (info "Get reservation types for organization" organizationId)
  (ok :organization organizationId
      :reservationTypes (reservation-types organizationId)))

(defcommand update-reservation-type
  {:permissions      [{:required [:organization/admin]}]
   :parameters       [reservationTypeId name]
   :input-validators [(partial action/number-parameters [:reservationTypeId])
                      (partial action/non-blank-parameters [:name])]
   :feature          :ajanvaraus
   :pre-checks       [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin})]}
  [_]
  (ok :reservationTypes (put-command (str "reservation-types/" reservationTypeId) {:name name})))

(defcommand delete-reservation-type
  {:permissions      [{:required [:organization/admin]}]
   :parameters       [reservationTypeId]
   :input-validators [(partial action/number-parameters [:reservationTypeId])]
   :feature          :ajanvaraus
   :pre-checks       [(partial cal/calendars-enabled-api-pre-check #{:authorityAdmin})]}
  [_]
  (ok :reservationTypes (delete-command (str "reservation-types/" reservationTypeId))))

(defquery available-calendar-slots
  {:user-roles       #{:authority :applicant}
   :feature          :ajanvaraus
   :parameters       [clientId year week]
   :input-validators [(partial action/non-blank-parameters [:clientId :year :week])]
   :pre-checks       [(partial cal/calendars-enabled-api-pre-check #{:authority :applicant})]}
  [{user :user {applicationId :id} :application {authorityId :authorityId reservationTypeId :reservationTypeId} :data}]
  (ok :availableSlots
      (if (not-any? ss/empty? [reservationTypeId authorityId])
        (->FrontendReservationSlots
          (cal/available-calendar-slots-for-appointment {:year              year :week week
                                                         :authority         authorityId
                                                         :clientId          clientId
                                                         :reservationTypeId reservationTypeId}))
        [])
      :readOnlySlots (Reservations->FrontendSlots :read-only
                                                  (filter (fn [r] (= applicationId (:contextId r)))
                                                          (cond
                                                            (usr/authority? user) (cal/applicant-reservations clientId {:year year :week week})
                                                            (usr/applicant? user) (cal/reservations-for-application applicationId {:year year :week week}))))))

(defquery application-calendar-config
  {:description "Returns application and organization specific configuration items for the calendar tab. If an exception occurs, \\
                 returning an empty response so that the calendar tab is hidden in the UI but still other operations are possible \\
                 in the application page."
   :user-roles  #{:applicant :authority}
   :parameters  [:id]
   :feature     :ajanvaraus
   :pre-checks  [(partial cal/calendars-enabled-api-pre-check #{:applicant :authority})]}
  [{{:keys [organization] :as appl} :application}]
  (try
    (ok :authorities (cal/find-application-authz-with-calendar appl)
        :reservationTypes (reservation-types organization)
        :defaultLocation (get-in (org/get-organization organization) [:reservations :default-location] ""))
    (catch Exception _ (ok :authorities []
                           :reservationTypes []
                           :defaultLocation ""))))

(defn- get-reservation [id]
  (->FrontendReservation (api-query (str "reservations/" id))))

(defcommand reserve-calendar-slot
  {:user-roles       #{:authority :applicant}
   :feature          :ajanvaraus
   :parameters       [clientId slotId reservationTypeId comment location :id]
   :input-validators [(partial action/number-parameters [:slotId :reservationTypeId])
                      (partial action/string-parameters [:clientId :comment :location])]
   :pre-checks       [(partial cal/calendars-enabled-api-pre-check #{:authority :applicant})]
   :on-success       [(notify :suggest-appointment-authority)
                      (notify :suggest-appointment-from-applicant)
                      (notify :suggest-appointment-to-applicant)]}
  [{{userId :id :as user} :user {:keys [id organization] :as application} :application timestamp :created}]
  ; Applicant: clientId must be the same as user id
  ; Authority: authorityId must be the same as user id
  ; Organization of application must be the same as the organization in reservation slot
  (let [slot        (get-calendar-slot slotId)
        calendar    (cal/get-calendar-for-resource (:resourceId slot))
        authorityId (:externalRef calendar)]
    ; validation
    (when (and (usr/applicant? user) (not= clientId userId))
      (error "applicant trying to impersonate as " clientId " , failing reservation")
      (fail! :error.unauthorized))
    (when (and (usr/authority? user) (not (domain/write-access? application clientId)))
      (error "authority trying to invite " clientId "  not satisfying the owner-or-write-access rule, failing reservation")
      (fail! :error.unauthorized))
    (when (not= (:organizationCode slot) organization)
      (fail! :error.illegal-organization))

    (let [reservationId (cal/new-reservation
                          {:clientId          clientId :reservationSlotId slotId
                           :reservationTypeId reservationTypeId :comment comment
                           :location          location :contextId id :reservedBy userId})
          ; Reservations made by applicants are auto-accepted
          _             (if (usr/applicant? user)
                          (post-command (str "reservations/" reservationId "/accept")))
          reservation   (get-reservation reservationId)
          to-user       (cond
                          (usr/applicant? user) (usr/get-user-by-id authorityId)
                          (usr/authority? user) (usr/get-user-by-id clientId))
          reservation   (assoc reservation :from (:id user)
                                           :to [(:id to-user)]
                                           :action-required-by [(:id to-user)])]
      (cal/update-mongo-for-new-reservation application reservation user to-user timestamp)
      (ok :reservationId reservationId))))

(defcommand accept-reservation
  {:user-roles       #{:applicant}
   :feature          :ajanvaraus
   :parameters       [reservationId :id]
   :input-validators [(partial action/number-parameters [:reservationId])]
   :pre-checks       [(partial cal/calendars-enabled-api-pre-check #{:applicant})]
   :on-success       (notify :accept-appointment)}
  [{:keys [user application] timestamp :created}]
  (let [reservation (util/find-by-id reservationId (:reservations application))]
    (info "Accepting reservation" reservationId)
    (cal/accept-reservation application reservation user timestamp)
    (ok :reservationId reservationId)))

(defcommand decline-reservation
  {:user-roles       #{:authority :applicant}
   :feature          :ajanvaraus
   :parameters       [reservationId :id]
   :input-validators [(partial action/number-parameters [:reservationId])]
   :pre-checks       [(partial cal/calendars-enabled-api-pre-check #{:authority :applicant})]
   :on-success       (notify :decline-appointment)}
  [{:keys [user application] timestamp :created}]
  (let [reservation (util/find-by-id reservationId (:reservations application))]
    (info "Declining reservation" reservationId)
    (cal/decline-reservation application reservation user timestamp)
    (ok :reservationId reservationId)))

(defn- process-application-with-reservation-updates
  [{:keys [reservations] :as appl}]
  (let [appl-without-reservations (dissoc appl :reservations)
        reservation-fields        [:id :applicationId :status :reservationStatus :reservationType :startTime :endTime
                                   :comment :location :reservedBy :from :to :participants]
        participant-fields        [:firstName :lastName :id]]
    (map #(merge (select-keys % reservation-fields)
                 {:application  appl-without-reservations
                  :participants (map (fn [u] (select-keys u participant-fields)) (:participants %))}) reservations)))

(defquery calendar-actions-required
  {:user-roles #{:authority :applicant}
   :feature    :ajanvaraus
   :pre-checks [(partial cal/calendars-enabled-api-pre-check #{:applicant :authority})]}
  [{user :user}]
  (ok :actionsRequired (mapcat process-application-with-reservation-updates
                               (cal/applications-with-calendar-actions-required user))))

(defquery applications-with-appointments
  {:user-roles #{:authority :applicant}
   :feature    :ajanvaraus
   :pre-checks [(partial cal/calendars-enabled-api-pre-check #{:applicant :authority})]}
  [{user :user}]
  (ok :appointments (mapcat process-application-with-reservation-updates
                            (cal/applications-with-appointments-for-user user))))

(defcommand cancel-reservation
  {:user-roles       #{:authority}
   :feature          :ajanvaraus
   :parameters       [reservationId :id]
   :input-validators [(partial action/number-parameters [:reservationId])]
   :pre-checks       [(partial cal/calendars-enabled-api-pre-check #{:authority})]
   :on-success       (notify :decline-appointment)}
  [{:keys [user application] timestamp :created}]
  (let [reservation (util/find-by-id reservationId (:reservations application))]
    (info "Canceling reservation" reservationId)
    (cal/cancel-reservation application reservation user timestamp)
    (ok :reservationId reservationId)))

(defcommand mark-reservation-update-seen
  {:user-roles       #{:authority :applicant}
   :feature          :ajanvaraus
   :parameters       [reservationId :id]
   :input-validators [(partial action/number-parameters [:reservationId])]
   :pre-checks       [(partial cal/calendars-enabled-api-pre-check #{:applicant})]}
  [{{userId :id} :user application :application}]
  (cal/mark-reservation-update-seen application reservationId userId)
  (ok))

(defquery my-reserved-slots
  {:user-roles       #{:authority :applicant}
   :feature          :ajanvaraus
   :parameters       [year week]
   :input-validators [(partial action/non-blank-parameters [:year :week])]}
  [{{:keys [id] :as user} :user}]
  (->>
    (cond
      (usr/authority? user) (cal/authority-reservations id {:year year :week week})
      (usr/applicant? user) (cal/applicant-reservations id {:year year :week week}))
    (Reservations->FrontendSlots :booked)
    (ok :reservations)))

; For integration tests in dev
(env/in-dev
  (defn clear-database
    []
    (ok :res (post-command "testdata/clear"))))
