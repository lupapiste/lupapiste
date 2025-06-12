(ns lupapalvelu.control-api
  (:require [lupapalvelu.lockdown :as lockdown]
            [lupapalvelu.logging :refer [info]]
            [noir.core :refer [defpage]]
            [noir.request]
            [noir.response :as response]
            [sade.http :as http]))

(defmacro defcontrol [path params & body]
  `(defpage ~path ~params
     (if (#{"127.0.0.1" "0:0:0:0:0:0:0:1" "[0:0:0:0:0:0:0:1]"}
           (:remote-addr (noir.request/ring-request)))
       (do ~@body)
       (response/status 401 "Unauthorized"))))

;; TODO: Kept temporarily for backwards compatibility with existing scripts
(defcontrol "/internal/lock" []
  (info "Locking request ignored")
  (response/status 200 "OK"))

(defcontrol "/internal/unlock" []
  (info "Unlocking request ignored")
  (response/status 200 "OK"))

(defcontrol "/internal/really-lock" []
  (lockdown/lock!)
  (info "Locked application to read only mode")
  (response/status 200 "OK"))

(defcontrol "/internal/really-unlock" []
  (lockdown/unlock!)
  (info "Unlocked application")
  (response/status 200 "OK"))

(def allowed-methods-in-lockdown #{:get :head})
(def allowed-paths-in-lockdown [#"^/api/login$", #"^/api/datatables/[a-z\-]+$"])

(defn- allowed-in-lockdown [request]
  (or (allowed-methods-in-lockdown (:request-method request))
      (some #(re-matches % (:uri request)) allowed-paths-in-lockdown)))

(defn lockdown-middleware
  "Ring middleware. Allow only GET and HEAD methods and POST requests to
   whitelisted paths if the app is in lockdown."
  [handler]
  (fn [request]
    (if (and (lockdown/lockdown?) (not (allowed-in-lockdown request)))
      (response/set-headers
        (assoc http/no-cache-headers "Allow" "GET, HEAD")
        (response/status 405 "Service is currently in read only mode due to maintenance"))
      (handler request))))
