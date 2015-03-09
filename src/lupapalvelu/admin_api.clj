(ns lupapalvelu.admin-api
  (:require [taoensso.timbre :as timbre :refer [trace tracef debug info infof warn warnf error errorf]]
            [lupapalvelu.action :refer [defraw]]
            [sade.core :refer [now]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch-api]
            [lupapalvelu.xml.krysp.reader]))

(defraw admin-download-application-xml
  {:parameters [applicationId]
   :user-roles #{:admin}}
  [_]
  (if-let [application (domain/get-application-no-access-checking applicationId)]
    {:status 200
     :body (krysp-fetch-api/get-application-xml application true)
     :headers {"Content-Type" "application/xml;charset=UTF-8"
               "Content-Disposition" (format "attachment;filename=\"%s-%s.xml\"" applicationId (now))
               "Cache-Control" "no-cache"}}
    {:status 404
     :headers {"Content-Type"  "text/plain" "Cache-Control" "no-cache"}
     :body "Application not found"}))
