(ns lupapalvelu.resources-api
  (:require [sade.core :refer :all]
            [taoensso.timbre :as timbre :refer [info error]]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [schema.core :as sc :refer [defschema]]
            [sade.http :as http]
            [sade.env :as env]))

(defschema FrontendCalendar
  {:id            sc/Int
   :name          sc/Str
   :organization  sc/Str
   :slots        [sc/Any]})

(defn ->FrontendCalendar [cal]
  (-> cal
      (select-keys [:id :name])
      (merge {:organization (:organizationCode cal)
              :slots []})))

(defn- build-url [& path-parts]
  (apply str (env/value :ajanvaraus :host) path-parts))

(defn- api-call [f action opts]
  (let [url (build-url action)]
    (info "Calling:" url " with " opts)
    (f url (merge {:as               :json
                   :throw-exceptions false
                   :basic-auth       [(env/value :ajanvaraus :username)
                                      (env/value :ajanvaraus :password)]} opts))))

(defn- api-query [action]
  (api-call http/get action {}))

(defn- api-put-command [action request-body]
  (api-call http/put action {:body (clj-http.client/json-encode request-body)
                             :content-type :json}))

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

(defcommand update-calendar
  {:user-roles #{:authorityAdmin}}
  [{{:keys [calendarId name organization]} :data}]
  (let [url (str "/api/resources/" calendarId)
        response (api-put-command url {:name             name
                                       :organizationCode organization})]
    (ok :result (:body response))))