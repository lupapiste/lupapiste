(ns lupapalvelu.reports.reports-api
  (:require [taoensso.timbre :refer [error]]
            [sade.core :refer :all]
            [sade.util :as util]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [lupapalvelu.action :as action :refer [defraw]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.user :as usr]
            [lupapalvelu.reports.applications :as app-reports]))

(defraw open-applications-xlsx
  {:user-roles #{:authorityAdmin}}
  [{user :user {lang :lang} :data}]
  (let [orgId               (usr/authority-admins-organization-id user)
        excluded-operations [:tyonjohtajan-nimeaminen :tyonjohtajan-nimeaminen-v2]
        resulting-file-name (str (i18n/localize lang "applications.report.open-applications.file-name")
                                 "_"
                                 (util/to-xml-date (now))
                                 ".xlsx")]
    (try
      {:status  200
       :headers {"Content-Type"        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                 "Content-Disposition" (str "attachment;filename=\"" resulting-file-name "\"")}
       :body    (app-reports/open-applications-for-organization-in-excel! orgId lang excluded-operations)}
      (catch Exception e#
        (error "Exception while compiling open applications excel:" e#)
        {:status 500}))))


(defn max-month-window [{{:keys [startTs endTs]} :data}]
  (let [start-time (.withTimeAtStartOfDay (util/to-datetime-with-timezone (util/to-long startTs)))
        end-time   (.withTimeAtStartOfDay (util/to-datetime-with-timezone (util/to-long endTs)))]
    (when (t/before? start-time (t/minus end-time (t/years 1)))
      (fail :error.max-12-months-window))))

(defn start-gt-end [{{:keys [startTs endTs]} :data}]
  (when-not (< (util/to-long startTs) (util/to-long endTs))
    (fail :error.start-greater-than-end)))

(defraw applications-between-xlsx
  {:description "Excel with applications that have been submitted between given timeperiod.
                 Period can be max one year window."
   :parameters       [startTs endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-gt-end
                      max-month-window]
   :user-roles       #{:authorityAdmin}}
  [{user :user {lang :lang} :data}]
  (let [orgId               (usr/authority-admins-organization-id user)
        excluded-operations [:tyonjohtajan-nimeaminen :tyonjohtajan-nimeaminen-v2 :aiemmalla-luvalla-hakeminen]
        resulting-file-name (str (i18n/localize lang "applications.report.applications-between.file-name")
                                 "_"
                                 (util/to-xml-date (now))
                                 ".xlsx")]
    (try
      {:status  200
       :headers {"Content-Type"        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                 "Content-Disposition" (str "attachment;filename=\"" resulting-file-name "\"")}
       :body    (app-reports/applications-between-excel orgId
                                                        (util/to-long startTs)
                                                        (util/to-long endTs)
                                                        lang
                                                        excluded-operations)}
      (catch Exception e#
        (error "Exception while compiling open applications excel:" e#)
        {:status 500}))))