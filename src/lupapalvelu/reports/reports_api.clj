(ns lupapalvelu.reports.reports-api
  (:require [lupapalvelu.action :refer [defraw]]
            [lupapalvelu.user :as usr]
            [lupapalvelu.reports.applications :as app-reports]
            [sade.files :as files]))

(defraw open-applications-xlsx
  {:user-roles #{:authorityAdmin}}
  [{adminUser :user}]
  (let [orgId (usr/authority-admins-organization-id adminUser)]
    (try
      {:status  200
       :headers {"Content-Type"        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                 "Content-Disposition" "attachment;filename=\"avoimet-hakemukset.xlsx\""}
       :body    (files/temp-file-input-stream (app-reports/open-applications-for-organization-in-excel! orgId))})))