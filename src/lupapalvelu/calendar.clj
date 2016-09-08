(ns lupapalvelu.calendar
  (:require [taoensso.timbre :as timbre :refer [info error]]
            [cheshire.core :as json]
            [monger.operators :refer :all]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [lupapalvelu.action :refer [application->command update-application]]
            [lupapalvelu.application :as app]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.application :as application]
            [sade.core :refer [fail! unauthorized]]
            [sade.env :as env]
            [sade.http :as http]
            [sade.strings :as str]
            [sade.util :as util]
            [lupapalvelu.application-search :as search]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.application-utils :as app-utils]))

; -- API Call helpers

(defn- build-url [& path-parts]
  (apply str (env/value :ajanvaraus :host) path-parts))

(defn- api-call [f action opts]
  (let [url (build-url action)]
    (info "Calling:" url " with " opts)
    (f url (merge {:as               :json
                   :coerce           :always
                   :throw-exceptions false
                   :basic-auth       [(env/value :ajanvaraus :username)
                                      (env/value :ajanvaraus :password)]}
                  opts))))

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

(defn- handle-error-response [{:keys [status body]}]
  (let [{:keys [code message]} (if (map? body) body {:message body})]
    (error "calendar api returned" status code message)
    (if code
      (fail! (str "calendar.error." code))
      (fail! :error.unknown))))

(defn api-query
  ([action]
   (api-query action {}))
  ([action params]
   (let [response (api-call http/get (str "/api/" (name action)) {:query-params params})]
     (when-not (= 200 (:status response))
       (handle-error-response response))
     (:body response))))

(defn post-command
  ([command]
   (post-command command []))
  ([command request-body]
   (let [response (api-post-command (str "/api/" (name command)) request-body)]
     (when-not (= 200 (:status response))
       (handle-error-response response))
     (:body response))))

(defn put-command
  ([command]
   (put-command command []))
  ([command request-body]
   (let [response (api-put-command (str "/api/" (name command)) request-body)]
     (when-not (= 200 (:status response))
       (handle-error-response response))
     (:body response))))

(defn delete-command
  ([command]
   (delete-command command []))
  ([command request-body]
   (let [response (api-delete-command (str "/api/" (name command)) request-body)]
     (when-not (= 200 (:status response))
       (handle-error-response response))
     (:body response))))

; -- Second level helpers

(defn get-calendar-for-resource
  ([resourceId]
   (api-query (str "resources/" resourceId))))

(defn get-calendar-user-id
  [calendarId]
  (:externalRef (get-calendar-for-resource calendarId)))

(defn calendar-belongs-to-user?
  [calendar userId]
  (.endsWith (:externalRef calendar) userId))

; -- Service functions

(defn calendars-enabled-api-pre-check
  [rolez {user :user {:keys [organization]} :application}]
  (let [org-set (if organization
                  #{organization}
                  (usr/organization-ids-by-roles user rolez))]
    (when (or (empty? org-set) (not (org/some-organization-has-calendars-enabled? org-set)))
      unauthorized)))

(defn find-calendars-for-organizations
  [& orgIds]
  (group-by :externalRef (api-query "resources/by-organization" {:organizationCodes orgIds})))

(defn find-application-authz-with-calendar
  [{:keys [organization] :as application}]
  (let [calendars     (find-calendars-for-organizations organization)
        authority-ids (keys calendars)]
    (filter #(util/contains-value? authority-ids (:id %))
             (app/application-org-authz-users application "authority"))))

(defn find-calendars-for-user
  [userId]
  (api-query (str "resources/by-external-ref/"  userId)))

(defn find-calendar-for-user-and-organization
  [userId orgId]
  (->> (find-calendars-for-user userId)
       (filter #(= (:organizationCode %) orgId))
       first))

(defn new-reservation [args]
  (post-command "reservation/" args))

(defn authority-reservations
  [authorityId {:keys [year week]}]
  (api-query (str "reservations/by-external-ref/" authorityId) {:year year :week week}))

(defn applicant-reservations
  [applicantId {:keys [year week]}]
  (api-query (str "reservations/for-client/" applicantId) {:year year :week week}))

(defn reservations-for-application
  [applicationId {:keys [year week]}]
  (api-query (str "reservations/by-context/" applicationId) {:year year :week week}))

(defn available-calendar-slots-for-appointment
  [opts]
  (let [calendar-external-ref (:authority opts)
        query-params (select-keys opts [:year :week :clientId :reservationTypeId])
        query-params (merge query-params {:externalRef calendar-external-ref})]
    (api-query "reservationslots/available-slots" query-params)))

(defn update-mongo-for-new-reservation
  [application reservation user to-user timestamp]
  (let [comment-update (comment/comment-mongo-update (:state application)
                                                     (:comment reservation)
                                                     {:type "reservation-new"
                                                      :id (:id reservation)}
                                                     "system"
                                                     false ; mark-answered
                                                     user
                                                     to-user
                                                     timestamp)
        reservation-push {$push {:reservations reservation}}
        state-change (case (keyword (:state application))
                       :draft (application/state-transition-update :open timestamp user)
                       nil)]
    (update-application
      (application->command application)
      (util/deep-merge comment-update reservation-push state-change))))

(defn update-reservation
  [application reservation-id changes]
  (update-application
    (application->command application)
    {:reservations {$elemMatch {:id reservation-id}}}
    changes))

(defn update-mongo-for-reservation-state-change
  [application {reservation-id :id :as reservation} new-state {user-id :id :as user} to-user timestamp]
  {:pre [(or (= new-state :ACCEPTED) (= new-state :DECLINED))]}
  (let [type (cond
               :ACCEPTED "reservation-accepted"
               :DECLINED "reservation-declined")
        comment-update (comment/comment-mongo-update (:state application)
                                                     (:comment reservation)
                                                     {:type type
                                                      :id (:id reservation)}
                                                     "system"
                                                     false ; mark-answered
                                                     user
                                                     to-user
                                                     timestamp)]
    (update-application
      (application->command application)
      comment-update)
    (update-reservation application reservation-id {$set {:reservations.$.reservationStatus (name new-state)}})
    (update-reservation application reservation-id {$pull {:reservations.$.action-required-by user-id}})
    (update-reservation application reservation-id {$push {:reservations.$.action-required-by (:id to-user)}})))

(defn accept-reservation
  [application {reservation-id :id to-user-id :reservedBy :as reservation} user timestamp]
  (post-command (str "reservations/" reservation-id "/accept"))
  (update-mongo-for-reservation-state-change application reservation :ACCEPTED user (usr/get-user-by-id to-user-id) timestamp))

(defn decline-reservation
  [application {reservation-id :id to-user-id :reservedBy :as reservation} user timestamp]
  (post-command (str "reservations/" reservation-id "/decline"))
  (update-mongo-for-reservation-state-change application reservation :DECLINED user (usr/get-user-by-id to-user-id) timestamp))

(defn mark-reservation-update-seen
  [application reservation-id user-id]
  (update-reservation application reservation-id {$pull {:reservations.$.action-required-by user-id}}))

(defn- filter-reservations-for-user [user rs]
  (filter #(util/contains-value? (:action-required-by %) (:id user)) rs))

(defn applications-with-unseen-reservation-updates
  [user]
  (let [query      (search/make-query
                     (domain/applications-containing-reservations-requiring-action-query-for user)
                     {} user)
        enrich-app (comp app-utils/with-organization-name app-utils/with-application-kind)]
    (->> (mongo/select :applications query)
         (map (fn [app] (update app :reservations (partial filter-reservations-for-user user))))
         (map enrich-app)
         (map #(select-keys % [:id :kind :municipality :organizationName
                               :address :primaryOperation :reservations])))))

; -- Configuration

(defn ui-params []
  (let [m (get (env/get-config) :calendar)]
    ; convert keys to camel-case-keywords
    (zipmap (map (comp keyword str/to-camel-case name) (keys m)) (vals m))))


