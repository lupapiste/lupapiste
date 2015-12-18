(ns lupapalvelu.control-api
  (:require [taoensso.timbre :as timbre :refer [info]]
            [noir.core :refer [defpage]]
            [noir.server :as server]
            [noir.request]
            [noir.response :as response]
            [sade.env :as env]
            [lupapalvelu.i18n :as i18n]))

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
