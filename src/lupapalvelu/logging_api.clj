(ns lupapalvelu.logging-api
  (:require [taoensso.timbre :as timbre :refer [error errorf]]
            [noir.core :refer [defpage]]
            [net.cgrand.enlive-html :as enlive]
            [sade.env :as env]
            [sade.core :refer [ok fail]]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]))

(defonce frontend-log (atom {}))

(def levels #{:debug :info :warn :error :fatal})

(defmacro with-test-context [& body]
  `(let [~'dbname (or mongo/*db-name* mongo/default-db-name)]
     ~@body))

(defcommand frontend-log
  {:user-roles #{:anonymous}}
  [{{:keys [level page message build]} :data {:keys [email]} :user {:keys [user-agent]} :web ts :created}]
  (let [limit           1000
        level           (get levels (-> level ss/lower-case keyword) :error)
        sanitize        (partial logging/sanitize limit)
        sanitized-page  (sanitize (or page "(unknown)"))
        user            (or (ss/canonize-email email) "(anonymous)")
        sanitized-ua    (sanitize user-agent)
        sanitized-build (sanitize build)
        expired?        (not= sanitized-build env/build-number)
        build-check     (if expired?
                         " - CLIENT HAS EXPIRED VERSION"
                         "")
        sanitized-msg   (sanitize (str message))
        formatted-msg   (format "FRONTEND: %s [%s] on page %s (build=%s%s): %s"
                          user sanitized-ua sanitized-page sanitized-build build-check sanitized-msg)]
    (when (env/dev-mode?)
      (with-test-context
        (swap! frontend-log update-in [(keyword dbname) level] conj {:ts ts :msg formatted-msg})))
    (timbre/log level formatted-msg)
    (when expired?
      (ok :expired true))))

(defquery frontend-log-entries
  {:user-roles #{:admin}
   :description "Returns frontend entries from all test runs."}
  [_]
  (ok :log (merge (zipmap levels (repeat [])) (apply merge-with concat (vals @frontend-log)))))

(defquery newest-version
  {:user-roles #{:anonymous}
   :parameters [frontendBuild]
   :input-validators [(partial action/non-blank-parameters [:frontendBuild])]}
  [_]
  (if (= frontendBuild env/build-number)
    (ok)
    (fail :frontend-too-old)))

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

(env/in-dev
  (defcommand reset-frontend-log
    {:user-roles #{:anonymous}}
    [_]
    (reset! frontend-log {})
    (ok))

  (defn log-entries [reset]
    (let [entries (->> (with-test-context (get @frontend-log (keyword dbname)))
                       (mapcat (fn [[level entries]] (map #(assoc % :level (name level)) entries)))
                       (sort-by :ts))]
      (when reset (with-test-context (swap! frontend-log dissoc (keyword dbname))))
      entries))

  (defpage "/api/frontend-log" {reset :reset}
    (enlive/emit*
     {:tag :html
      :content [{:tag :head
                 :content [{:tag :title, :content "Frontend log"}
                           {:tag :style, :content "* {font-family: sans-serif}\npre {font-family: courier; font-size: 10pt}\ndl {background-color: #eee}"}]}
                {:tag :body
                 :content (cons {:tag :h1 :content "Frontend log"}
                                (map (fn [msg] {:tag :div
                                                :attrs {:style "border-bottom: 4px dashed black;margin: 2em"
                                                        :data-test-id "log entry"
                                                        :data-test-level (:level msg)}
                                                :content (:msg msg)})
                                     (log-entries reset)))}]})))
