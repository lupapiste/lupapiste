(ns lupapalvelu.logging-api
  (:require [taoensso.timbre :as timbre :refer [error errorf logf]]
            [noir.core :refer [defpage]]
            [sade.env :as env]
            [sade.core :refer [ok fail]]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.user :as user]
            [lupapalvelu.logging :as logging]))

(defonce frontend-log (atom {}))

(def levels #{:debug :info :warn :error :fatal})

(defcommand frontend-log
  {:user-roles #{:anonymous}}
  [{{:keys [level page message build]} :data {:keys [email]} :user {:keys [user-agent]} :web ts :created}]
  (let [limit           1000
        level           (get levels (-> level ss/lower-case keyword) :error)
        sanitize        (partial logging/sanitize limit)
        sanitized-page  (sanitize (or page "(unknown)"))
        user            (or (user/canonize-email email) "(anonymous)")
        sanitized-ua    (sanitize user-agent)
        sanitized-build (sanitize build)
        build-check     (if (not= sanitized-build (:build-number env/buildinfo))
                         " - CLIENT HAS EXPIRED VERSION"
                         "")
        sanitized-msg   (sanitize (str message))]
    (when (env/dev-mode?)
      (swap! frontend-log update level conj {:ts ts :msg sanitized-msg}))
    (logf level "FRONTEND: %s [%s] on page %s (build=%s%s): %s"
          user sanitized-ua sanitized-page sanitized-build build-check sanitized-msg)))

(defquery frontend-log-entries
  {:user-roles #{:admin}}
  [_]
  (ok :log @frontend-log))

(defquery newest-version
  {:user-roles #{:anonymous}
   :parameters [frontendBuild]
   :input-validators [(partial action/non-blank-parameters [:frontendBuild])]}
  [_]
  (let [currentBuild (:build-number env/buildinfo)]
    (if (= frontendBuild currentBuild)
      (ok)
      (fail :frontend-too-old))))

(def csp-report-keys [:blocked-uri :document-uri :line-number :referrer :script-sample :source-file :violated-directive])

(defn- log-csp-report [{csp-report :csp-report}]
  (if (map? csp-report)
    (let [report (select-keys csp-report csp-report-keys)
          sanitized-report (util/convert-values report #(when (string? %) (logging/sanitize 100 %)))]
      (error "FRONTEND: CSP-report" sanitized-report))
    (errorf "FRONTEND: CSP-report got posted without valid payload")))

(defpage [:post "/api/csp-report"] request
  (log-csp-report request)
  "OK")
