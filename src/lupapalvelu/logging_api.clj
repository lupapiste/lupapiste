(ns lupapalvelu.logging-api
  (:require [taoensso.timbre :as timbre :refer [errorf]]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [defcommand]]
            [lupapalvelu.logging :as logging]))

(defcommand "frontend-error"
  {:roles [:anonymous]}
  [{{:keys [page message]} :data {:keys [email]} :user {:keys [user-agent]} :web}]
  (let [limit          1000
        sanitize       (partial logging/sanitize limit)
        sanitized-page (sanitize (or page "(unknown)"))
        user           (or (ss/lower-case email) "(anonymous)")
        sanitized-ua   (sanitize user-agent)
        sanitized-msg  (sanitize (str message))]
    (errorf "FRONTEND: %s [%s] got an error on page %s: %s"
            user sanitized-ua sanitized-page sanitized-msg)))
