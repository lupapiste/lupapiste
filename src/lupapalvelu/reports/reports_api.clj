(ns lupapalvelu.reports.reports-api
  (:require [lupapalvelu.action :as action :refer [defraw]]
            [lupapalvelu.company :as com]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.invoices :as invoices]
            [lupapalvelu.organization :as org]
            [lupapalvelu.reports.applications :as app-reports]
            [lupapalvelu.reports.archival :as archival]
            [lupapalvelu.reports.excel :as excel]
            [lupapalvelu.reports.invoices :as inv-rep]
            [lupapalvelu.reports.oauth :as oauth-reports]
            [lupapalvelu.reports.onkalo :as onkalo-reports]
            [lupapalvelu.reports.store-billing :as store-billing]
            [lupapalvelu.reports.users :as user-reports]
            [ring.util.io :refer [piped-input-stream]]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.http :refer [no-cache-headers]]
            [sade.util :as util]))

(defn excel-response [filename body]
  (let [error-message "Exception while compiling excel:"]
    (excel/excel-response filename body error-message)))

(defn- report-filename
  ([lang startTs endTs string]
   (format "%s_%s_-_%s_%s.xlsx"
           (i18n/localize lang string)
           (date/iso-date startTs :local)
           (date/iso-date endTs :local)
           (now)))
  ([lang ts string]
   (format "%s_%s.xlsx"
           (i18n/localize lang string)
           (date/iso-date ts :local))))

(defraw open-applications-xlsx
  {:parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [{{lang :lang} :data}]
  (excel-response (report-filename lang (now) "applications.report.open-applications.file-name")
                  (app-reports/open-applications-for-organization-in-excel!
                    organizationId
                    lang)))

(defn max-month-window [{{:keys [startTs endTs]} :data}]
  (let [start-time (date/start-of-day (util/to-long startTs))
        end-time   (date/end-of-day (util/to-long endTs))]
    (when (.isBefore start-time (.minusYears end-time 1))
      (fail :error.max-12-months-window))))

(defn start-gt-end [{{:keys [startTs endTs]} :data}]
  (when-not (< (util/to-long startTs) (util/to-long endTs))
    (fail :error.start-greater-than-end)))

(defraw applications-between-xlsx
  {:description      "Excel with applications that have been submitted between given timeperiod.
                      Period can be max one year window."
   :parameters       [organizationId startTs endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-gt-end
                      max-month-window]
   :permissions      [{:required [:organization/admin]}]}
  [{{lang :lang} :data}]
  (let [excluded-operations [:aiemmalla-luvalla-hakeminen]]
    (excel-response (report-filename lang (now) "applications.report.applications-between.file-name")
                    (app-reports/applications-between-excel
                      organizationId
                      (util/to-long startTs)
                      (util/to-long endTs)
                      lang
                      excluded-operations))))

(defraw parties-between-xlsx                                                    ; LPK-3053
  {:description      "Excel to list parties for applications. Period can be max one year window."
   :parameters       [organizationId startTs endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-gt-end
                      max-month-window]
   :permissions      [{:required [:organization/admin]}]}
  [{{lang :lang} :data ts :created}]
  (excel-response (report-filename lang ts "applications.report.parties-between.file-name")
                  (app-reports/parties-between-excel
                    organizationId
                    (util/to-long startTs)
                    (util/to-long endTs)
                    lang)))


(defraw invoices-between-xlsx ; LPK-5326
  {:description      "Excel to list invoices. Period can be max one year window."
   :parameters       [organizationId startTs endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-gt-end
                      max-month-window]
   :permissions      [{:required [:organization/admin]}]
   :pre-checks       [invoices/invoicing-enabled]}
  [{{lang :lang} :data ts :created}]
  (excel-response (report-filename lang ts "applications.report.invoices-between.file-name")
                  (inv-rep/invoices-between-excel
                    organizationId
                    (util/to-long startTs)
                    (util/to-long endTs)
                    lang)))

(defraw post-verdict-xlsx                                                       ; LPK-3517
  {:description      "Excel to list applications in post verdict state"
   :parameters       [organizationId startTs endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-gt-end
                      max-month-window]
   :permissions      [{:required [:organization/admin]}]}
  [{{lang :lang} :data ts :created}]
  (excel-response (report-filename lang ts "applications.report.post-verdict.file-name")
                  (app-reports/post-verdict-excel
                    organizationId
                    (util/to-long startTs)
                    (util/to-long endTs)
                    lang)))

(defraw onkalo-applications-xlsx
  {:description      "Excel to list all applications (LP and otherwise) in Onkalo by archival date"
   :parameters       [organizationId startTs endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-gt-end]
   :permissions      [{:required [:organization/admin]}]}
  [{{:keys [lang]} :data ts :created}]
  (excel-response (report-filename lang ts "applications.report.onkalo-applications.file-name")
                  (onkalo-reports/onkalo-applications-excel
                    organizationId
                    (util/to-long startTs)
                    (util/to-long endTs)
                    lang)))

(defraw company-report
  {:description      "Excel report for company authority"
   :parameters       [startTs endTs]
   :input-validators [(partial action/string-parameters [:startTs :endTs])
                      max-month-window]
   :user-roles       #{:applicant :authority}
   :pre-checks       [(com/validate-has-company-role :admin)]}
  [{user :user {lang :lang} :data}]
  (let [company (get-in user [:company :id])]
    (excel-response (report-filename lang (now) "company.reports.excel.filename")
                    (app-reports/company-applications company startTs endTs lang user))))

(defraw digitizer-report
  {:description      "Excel report of digitized attachments"
   :parameters       [startTs endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])]
   :user-roles       #{:authority}}
  [{user :user {lang :lang} :data}]
  (excel-response (report-filename lang (now) "digitizer.reports.excel.filename")
                  (app-reports/digitized-attachments user startTs endTs lang)))

(defraw digitizer-applications-report-all-municipalities
  {:description "Excel report of digitizing projects created during the given time range in all municipalities"
   :parameters [startTs endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-gt-end]
   :permissions [{:required [:global/get-billing-reports]}]}
  [{user :user {lang :lang} :data}]
  (excel-response (report-filename lang startTs endTs "digitizer.reports.applications.excel.filename")
                  (app-reports/new-digitized-applications nil startTs endTs lang)))

(defraw store-billing-report
  {:description      "Excel report of documents sold in Lupapiste Kauppa"
   :parameters       [organizationId startTs endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-gt-end]
   :permissions      [{:required [:organization/admin]}]
   :pre-checks       [org/check-docstore-enabled]}
  [{user :user {lang :lang} :data}]
  (excel-response (report-filename lang startTs endTs "billing.excel.filename")
                  (store-billing/billing-entries organizationId startTs endTs lang)))

(defraw store-billing-report-all-municipalities
  {:description      "Excel report of documents sold in Lupapiste kauppa for all municipalities"
   :parameters       [startTs endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-gt-end]
   :permissions      [{:required [:global/get-billing-reports]}]}
  [{user :user {lang :lang} :data}]
  (excel-response (report-filename lang startTs endTs "billing.excel.filename")
                  (store-billing/billing-entries nil startTs endTs lang)))

(defraw store-downloads-report
  {:description      "Excel report of documents downloaded from Lupapiste kauppa"
   :parameters       [organizationId startTs endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-gt-end
                      (partial action/non-blank-parameters [:organizationId])]
   :permissions      [{:required [:organization/admin]}]
   :pre-checks       [org/check-docstore-enabled]}
  [{user :user {lang :lang} :data}]
  (excel-response (report-filename lang startTs endTs "billing.excel.downloads.filename")
                  (store-billing/docstore-downloads-entries organizationId startTs endTs lang)))

(defraw store-downloads-report-all-municipalities
  {:description      "Excel report of documents downloaded from Lupapiste kauppa for all municipalities"
   :parameters       [startTs endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-gt-end]
   :permissions      [{:required [:global/get-billing-reports]}]}
  [{user :user {lang :lang} :data}]
  (excel-response (report-filename lang startTs endTs "billing.excel.downloads.filename")
                  (store-billing/docstore-downloads-entries nil startTs endTs lang)))

(defraw authorities-report
  {:description "Excel report of organizations auhorities"
   :parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [{{lang :lang} :data}]
  (excel-response (str (i18n/localize lang "authorities.report.filename") ".xlsx")
                  (user-reports/authorities organizationId lang)))

(defn archiving-enabled
  "Pre-check that succeeds only when permanent archive is enabled for the organization."
  [{:keys [user-organizations data]}]
  (when-not (:permanent-archive-enabled (util/find-by-id (:organizationId data)
                                                         user-organizations))
    (fail :error.archive-not-enabled)))

(defraw archival-report
  {:description      "Generates a massive XLSX file/stream where each row represents one attachment."
   :parameters       [organizationId]
   :input-validators [{:organizationId org/OrgId
                       :startTs        #"\d+"
                       :endTs          #"\d+"}
                      start-gt-end]
   :permissions      [{:required [:organization/admin]}]
   :pre-checks       [archiving-enabled]}
  [{:as command}]
  {:status  200
   :headers (assoc no-cache-headers
                   "Content-Type"        excel/xlsx-mime-type
                   "Content-Disposition" (format "attachment;filename=\"%s\""
                                                 (archival/report-filename command)))
   :body    (piped-input-stream (partial archival/write-report command))})

(defraw rami-logins-report
  {:description      "Rami (reporting system) logins as counted by `oauth-code` tokens. Each
  row has the login day and the user email."
   :parameters       [startTs endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-gt-end]
   :user-roles       #{:admin}}
  [_]
  (excel-response (i18n/localize :fi :oauth.report.rami-filename)
                  (oauth-reports/logins-report :bi-app
                                               (util/->long startTs)
                                               (util/->long endTs))))
