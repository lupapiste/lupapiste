(ns lupapalvelu.reports.reports-api
  (:require [taoensso.timbre :refer [error]]
            [sade.core :refer :all]
            [sade.util :as util]
            [lupapalvelu.action :refer [defraw]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.user :as usr]
            [lupapalvelu.reports.applications :as app-reports]))

(defraw open-applications-xlsx
  {:user-roles #{:authorityAdmin}}
  [{user :user {lang :lang} :data}]
  (let [orgId               (usr/authority-admins-organization-id user)
        excluded-operations [:tyonjohtajan-nimeaminen :tyonjohtajan-nimeaminen-v2]
        resulting-file-name (str (i18n/localize lang "applications.report.file-name")
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