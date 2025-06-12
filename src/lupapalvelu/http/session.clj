(ns lupapalvelu.http.session
  (:require [integrant.core :as ig]
            [lupapalvelu.logging :as log]
            [sade.session :as session]))

(defmethod ig/init-key ::store [k _]
  (log/infof "Starting %s..." k)
  (session/session-store))
