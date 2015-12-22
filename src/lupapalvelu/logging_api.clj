(ns lupapalvelu.logging-api
  (:require [taoensso.timbre :as timbre :refer [errorf]]
            [noir.request :as request]
            [noir.core :refer [defpage]]
            [sade.env :as env]
            [sade.core :refer [ok fail]]
            [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.user :as user]
            [lupapalvelu.logging :as logging]))

(defcommand "frontend-error"
  {:user-roles #{:anonymous}}
  [{{:keys [page message build]} :data {:keys [email]} :user {:keys [user-agent]} :web}]
  (let [limit           1000
        sanitize        (partial logging/sanitize limit)
        sanitized-page  (sanitize (or page "(unknown)"))
        user            (or (user/canonize-email email) "(anonymous)")
        sanitized-ua    (sanitize user-agent)
        sanitized-build (sanitize build)
        build-check     (if (not= sanitized-build (:build-number env/buildinfo))
                         " - CLIENT HAS EXPIRED VERSION"
                         "")
        sanitized-msg   (sanitize (str message))]
    (errorf "FRONTEND: %s [%s] got an error on page %s (build=%s%s): %s"
            user sanitized-ua sanitized-page sanitized-build build-check sanitized-msg)))

(defquery "newest-version"
  {:user-roles #{:anonymous}
   :parameters [frontendBuild]
   :input-validators [(partial action/non-blank-parameters [:frontendBuild])]}
  [_]
  (let [currentBuild (:build-number env/buildinfo)]
    (if (= frontendBuild currentBuild)
      (ok)
      (fail :frontend-too-old))))

(defn- log-csp-report [request]
  (errorf "FRONTEND: CSP-report %s" request)
  )

(defpage [:post "/api/csp-report"] request
  (log-csp-report request))
