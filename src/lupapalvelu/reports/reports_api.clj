(ns lupapalvelu.reports.reports-api
  (:require [lupapalvelu.action :refer [defraw]]
            [lupapalvelu.user :as usr]
            [lupapalvelu.reports.applications :as app-reports]
            [taoensso.timbre :refer [error]]
            [lupapalvelu.i18n :as i18n]))

(defraw open-applications-xlsx
  {:user-roles #{:authorityAdmin}}
  [{user :user {lang :lang} :data}]
  (let [orgId               (usr/authority-admins-organization-id user)
        excluded-operations [:tyonjohtajan-nimeaminen :tyonjohtajan-nimeaminen-v2]
        resulting-file-name (i18n/localize lang "applications.report.file-name")]
    (try
      {:status  200
       :headers {"Content-Type"        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                 "Content-Disposition" (str "attachment;filename=\"" resulting-file-name "\"")}
       :body    (app-reports/open-applications-for-organization-in-excel! orgId lang excluded-operations)}
      (catch Exception e#
        (error "Exception while compiling open applications excel:" e#)
        {:status 500}))))