(ns lupapalvelu.calendars-api
  (:require [sade.core :refer :all]
            [taoensso.timbre :as timbre :refer [info error]]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [schema.core :as sc :refer [defschema]]
            [sade.http :as http]
            [sade.env :as env]
            [lupapalvelu.user :as user]))

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
  ([url]
   (post-command url []))
  ([url request-body]
   (let [response (api-post-command url request-body)]
     (when-not (= 200 (:status response))
       (error response)
       (fail! :resources.backend-error))
     (:body response))))

(defn- put-command
  ([url]
   (put-command url []))
  ([url request-body]
   (let [response (api-put-command url request-body)]
     (when-not (= 200 (:status response))
       (error response)
       (fail! :resources.backend-error))
     (:body response))))

(defn- delete-command
  ([url]
   (delete-command url []))
  ([url request-body]
   (let [response (api-delete-command url request-body)]
     (when-not (= 200 (:status response))
       (error response)
       (fail! :resources.backend-error))
     (:body response))))

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
  (info "Creating a new calendar" userId organization)
  (post-command "/api/resources" {:name             (str (:firstName target-user) " " (:lastName target-user))
                                  :organizationCode organization
                                  :externalRef      (calendar-ref-for-user userId)}))

(defn- activate-resource
  [{:keys [id]}]
  (info "Activating calendar" id)
  (post-command (str "/api/resources/" id "/activate"))
  id)

(defn- deactivate-resource
  [{:keys [id]}]
  (info "Deactivating calendar" id)
  (post-command (str "/api/resources/" id "/deactivate"))
  id)

(defn- enable-calendar
  [userId organization]
  (let [target-user       (user/get-user-by-id userId)
        existing-calendar (find-calendar-for-user-and-organization userId organization)]
    (when-not (user/user-is-authority-in-organization? (user/with-org-auth target-user) organization)
      (error "Tried to enable a calendar with invalid user-organization pairing: " userId organization)
      (unauthorized!))
    (if existing-calendar
      (activate-resource existing-calendar)
      (create-calendar userId target-user organization))))

(defn- disable-calendar
  [userId organization]
  (let [target-user       (user/get-user-by-id userId)
        existing-calendar (find-calendar-for-user-and-organization userId organization)]
    (when-not (user/user-is-authority-in-organization? (user/with-org-auth target-user) organization)
      (error "Tried to disable a calendar with invalid user-organization pairing: " userId organization)
      (unauthorized!))
    (when-not existing-calendar
      (info "Tried to disable a calendar but no such calendar could not be found:" userId organization)
      (fail! :error.unknown))
    (deactivate-resource existing-calendar)))

(env/in-dev
  (defn delete-calendar
    [userId]
    (let [calendars (find-calendars-for-user userId)]
      (doseq [id (map :id calendars)]
        (delete-command (str "/api/resources/" id))))))

(defcommand set-calendar-enabled-for-authority
  {:user-roles #{:authorityAdmin}
   :feature :ajanvaraus}
  [{{:keys [userId enabled]} :data user :user}]
  (if enabled
    (ok :calendarId (enable-calendar userId (user/authority-admins-organization-id user)) :enabled true)
    (ok :calendarId (disable-calendar userId (user/authority-admins-organization-id user)) :disabled true)))

(defquery calendar-slots
  {:user-roles #{:authorityAdmin}
   :feature    :ajanvaraus}
  [{{:keys [calendarId year week]} :data}]
  (let [response (api-query (str "/api/reservationslots/calendar/" calendarId) {:year year :week week})]
    (if (= 200 (:status response))
      (ok :slots (->FrontendReservationSlots (:body response)))
      (do (error response)
          (fail :resources.backend-error)))))

(defcommand create-calendar-slots
  {:user-roles #{:authorityAdmin}
   :feature    :ajanvaraus}
  [{{:keys [calendarId slots]} :data}]
  (->> (->BackendReservationSlots slots)
       (post-command (str "/api/reservationslots/calendar/" calendarId))
       (ok :result)))

(defcommand add-reservation-type-for-organization
  {:user-roles #{:authorityAdmin}
   :feature    :ajanvaraus}
  [{{:keys [reservationType]} :data user :user}]
  (let [admin-in-organization-id (user/authority-admins-organization-id user)]
    (info "Adding reservation type" reservationType "for organization" admin-in-organization-id)
    (ok :reservationTypes (post-command "/api/reservation-types/" {:reservationType   reservationType
                                                                    :organization      admin-in-organization-id}))))

(defquery reservation-types-for-organization
  {:user-roles #{:authorityAdmin}
   :feature    :ajanvaraus}
  [{user :user}]
  (let [admin-in-organization-id (user/authority-admins-organization-id user)]
    (info "Get reservation types for organization" admin-in-organization-id)
    ;; FIXME: Unwrap body from api-query response
    (ok :reservationTypes (:body (api-query "/api/reservation-types/by-organization" {:organization admin-in-organization-id})))))

(defcommand update-reservation-type
  {:user-roles #{:authorityAdmin}
   :feature    :ajanvaraus}
  [{{:keys [reservationTypeId name]} :data user :user}]
  (ok :reservationTypes (put-command (str "/api/reservation-types/" reservationTypeId) {:name name})))

(defcommand delete-reservation-type
  {:user-roles #{:authorityAdmin}
   :feature    :ajanvaraus}
  [{{:keys [reservationTypeId]} :data user :user}]
  (ok :reservationTypes (delete-command (str "/api/reservation-types/" reservationTypeId))))
