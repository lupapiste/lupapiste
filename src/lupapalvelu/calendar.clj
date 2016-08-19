(ns lupapalvelu.calendar
  (:require [sade.core :refer [fail! unauthorized]]
            [sade.env :as env]
            [taoensso.timbre :as timbre :refer [info error]]
            [sade.strings :as str]
            [cheshire.core :as json]
            [sade.http :as http]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [sade.util :as util]
            [lupapalvelu.action :refer [application->command update-application]]
            [lupapalvelu.application :as app]
            [lupapalvelu.comment :as comment]
            [monger.operators :refer :all]
            [lupapalvelu.application :as application]))

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

(defn- handle-error-response [response]
  (let [body (json/decode (:body response) keyword)]
    (error response)
    (case (:status response)
      401 (fail! "Unauthorized" :code "calendar.error.unauthorized-access" :message "Bad credentials")
      403 (fail! "Unauthorized" :code "calendar.error.unauthorized-access" :message "Forbidden")
      (fail! "Bad request" :code (str "calendar.error." (:code body)) :message (:message body)))))

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

; -- Configuration

(defn ui-params []
  (let [m (get (env/get-config) :calendar)]
    ; convert keys to camel-case-keywords
    (zipmap (map (comp keyword str/to-camel-case name) (keys m)) (vals m))))


