(ns lupapalvelu.api-common
  (:require [clojure.walk :refer [keywordize-keys]]
            [sade.http :as http]
            [lupapalvelu.i18n :refer [*lang*] :as i18n]
            [lupapalvelu.action :as action]
            [lupapalvelu.user :as usr]
            [lupapalvelu.logging :refer [with-logging-context]]
            [noir.response :as resp]))

(defn from-query [request]
  (keywordize-keys (:query-params request)))

(defn host [request]
  (str (name (:scheme request)) "://" (get-in request [:headers "host"])))

(defn user-agent [request]
  (str (get-in request [:headers "user-agent"])))

(defn web-stuff [request]
  {:user-agent (user-agent request)
   :client-ip  (http/client-ip request)
   :host       (host request)})

(defn enriched [m request]
  (merge m {:user    (usr/current-user request)
            :lang    *lang*
            :session (:session request)
            :web     (web-stuff request)}))

(defn execute [action]
  (with-logging-context
    {:applicationId (get-in action [:data :id])
     :userId        (get-in action [:user :id])}
    (action/execute action)))

(defn execute-command [name params request]
  (execute (enriched (action/make-command name params) request)))

(defn execute-query [name params request]
  (execute (enriched (action/make-query name params) request)))

(defn execute-export [name params request]
  (execute (enriched (action/make-export name params) request)))

(def basic-401
  (assoc-in (resp/status 401 "Unauthorized") [:headers "WWW-Authenticate"] "Basic realm=\"Lupapiste\""))

(defn basic-authentication
  "Returns a user map or nil if authentication fails"
  [request]
  (let [[u p] (http/decode-basic-auth request)]
    (when (and u p)
      (:user (execute-command "login" {:username u :password p} request)))))
