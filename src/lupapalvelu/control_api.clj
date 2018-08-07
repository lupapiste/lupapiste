(ns lupapalvelu.control-api
  (:require [taoensso.timbre :refer [info]]
            [noir.core :refer [defpage]]
            [noir.request]
            [noir.response :as response]
            [sade.env :as env]
            [sade.http :as http]
            [lupapalvelu.i18n :as i18n]
            [sade.status :refer [defstatus]]))

(defmacro defcontrol [path params & body]
  `(defpage ~path ~params
     (if (#{"127.0.0.1" "0:0:0:0:0:0:0:1"} (:remote-addr (noir.request/ring-request)))
       (do ~@body)
       (response/status 401 "Unauthorized"))))

(defcontrol "/internal/reload" []
  (env/reload!)
  (i18n/reload!)
  (info "Reloaded env and i18n.")
  (response/status 200 "OK"))

(defonce lockdown (atom false))

(defcontrol "/internal/lock" []
  (reset! lockdown true)
  (info "Locked application to read only mode")
  (response/status 200 "OK"))

(defcontrol "/internal/unlock" []
  (reset! lockdown false)
  (info "Unlocked application")
  (response/status 200 "OK"))

(def allowed-methods-in-lockdown #{:get :head})
(def allowed-paths-in-lockdown [#"^/api/login$", #"^/api/datatables/[a-z\-]+$"])

(defn- allowed-in-lockdown [request]
  (or (allowed-methods-in-lockdown (:request-method request))
      (some #(re-matches % (:uri request)) allowed-paths-in-lockdown)))

(defn lockdown? [] @lockdown)

(defstatus :not-in-lockdown (not (lockdown?)))

(defn lockdown-middleware
  "Ring middleware. Allow only GET and HEAD methods and POST requests to
   whitelisted paths if the app is in lockdown."
  [handler]
  (fn [request]
    (if (and @lockdown (not (allowed-in-lockdown request)))
      (response/set-headers
        (assoc http/no-cache-headers "Allow" "GET, HEAD")
        (response/status 405 "Service is currently in read only mode due to maintenance"))
      (handler request))))
