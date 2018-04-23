(ns lupapalvelu.reports.reports-api
  (:require [taoensso.timbre :refer [error]]
            [sade.core :refer :all]
            [sade.util :as util]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clojure.set :as set]
            [lupapalvelu.action :as action :refer [defraw]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.user :as usr]
            [lupapalvelu.reports.applications :as app-reports]
            [lupapalvelu.reports.excel :as excel]
            [lupapalvelu.company :as com]))

(defn excel-response [filename body]
  (let [error-message "Exception while compiling open applications excel:"]
    (excel/excel-response filename body error-message)))

(defraw open-applications-xlsx
  {:user-roles #{:authorityAdmin}}
  [{user :user {lang :lang} :data}]
  (let [orgId               (usr/authority-admins-organization-id user)
        excluded-operations [:tyonjohtajan-nimeaminen :tyonjohtajan-nimeaminen-v2]
        resulting-file-name (str (i18n/localize lang "applications.report.open-applications.file-name")
                                 "_"
                                 (util/to-xml-date (now))
                                 ".xlsx")]
    (excel-response resulting-file-name
                    (app-reports/open-applications-for-organization-in-excel!
                      orgId
                      lang
                      excluded-operations))))


(defn max-month-window [{{:keys [startTs endTs]} :data}]
  (let [start-time (.withTimeAtStartOfDay (util/to-datetime-with-timezone (util/to-long startTs)))
        end-time   (.withTimeAtStartOfDay (util/to-datetime-with-timezone (util/to-long endTs)))]
    (when (t/before? start-time (t/minus end-time (t/years 1)))
      (fail :error.max-12-months-window))))

(defn start-gt-end [{{:keys [startTs endTs]} :data}]
  (when-not (< (util/to-long startTs) (util/to-long endTs))
    (fail :error.start-greater-than-end)))

(defraw applications-between-xlsx
  {:description      "Excel with applications that have been submitted between given timeperiod.
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
    (excel-response resulting-file-name
                    (app-reports/applications-between-excel
                      orgId
                      (util/to-long startTs)
                      (util/to-long endTs)
                      lang
                      excluded-operations))))

(defraw parties-between-xlsx                                ; LPK-3053
  {:description      "Excel to list parties for applications. Period can be max one year window."
   :parameters       [startTs endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-gt-end
                      max-month-window]
   :user-roles       #{:authorityAdmin}}
  [{user :user {lang :lang} :data ts :created}]
  (let [orgId               (usr/authority-admins-organization-id user)
        resulting-file-name (str (i18n/localize lang "applications.report.parties-between.file-name")
                                 "_"
                                 (util/to-xml-date ts)
                                 ".xlsx")]
    (excel-response resulting-file-name
                    (app-reports/parties-between-excel
                      orgId
                      (util/to-long startTs)
                      (util/to-long endTs)
                      lang))))

(defraw post-verdict-xlsx                                   ; LPK-3517
  {:description      "Excel to list applications in post verdict state"
   :parameters       [startTs endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-gt-end
                      max-month-window]
   :user-roles       #{:authorityAdmin}}
  [{user :user {lang :lang} :data ts :created}]
  (let [orgId               (usr/authority-admins-organization-id user)
        resulting-file-name (str (i18n/localize lang "applications.report.post-verdict.file-name")
                                 "_"
                                 (util/to-xml-date ts)
                                 ".xlsx")]
    (excel-response resulting-file-name
                    (app-reports/post-verdict-excel
                      orgId
                      (util/to-long startTs)
                      (util/to-long endTs)
                      lang))))

(defraw company-report
  {:description      "Excel report for company authority"
   :parameters       [startTs endTs]
   :input-validators [(partial action/string-parameters [:startTs :endTs])
                      max-month-window]
   :user-roles       #{:applicant :authority}
   :pre-checks       [(com/validate-has-company-role :admin)]}
  [{user :user {lang :lang} :data}]
  (let [company             (get-in user [:company :id])
        resulting-file-name (str (i18n/localize lang "company.reports.excel.filename")
                                 "_"
                                 (util/to-xml-date (now))
                                 ".xlsx")]
    (excel-response resulting-file-name
                    (app-reports/company-applications company startTs endTs lang user))))

(defraw digitizer-report
  {:description      "Excel report of digitized attachments"
   :parameters       [startTs endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])]
   :user-roles       #{:authority}}
  [{user :user {lang :lang} :data}]
  (let [resulting-file-name (str (i18n/localize lang "digitizer.reports.excel.filename")
                                 "_"
                                 (util/to-xml-date (now))
                                 ".xlsx")]
    (excel-response resulting-file-name
                    (app-reports/digitized-attachments user startTs endTs lang))))
