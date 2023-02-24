(ns lupapalvelu.reports.onkalo
  "Report of all of a given organization's applications inside Onkalo,
  regardless of how they got there.
  For the Lupapiste archival and digitizing report, see `lupapalvelu.reports.archival`"
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.json :as json]
            [lupapalvelu.reports.excel :as excel]
            [sade.env :as env]
            [sade.http :as http]
            [taoensso.timbre :as timbre]
            [sade.date :as date])
  (:import (java.io OutputStream)))

(def arkisto-host (env/value :arkisto :host))
(def app-id (env/value :arkisto :app-id))
(def app-key (env/value :arkisto :app-key))

(def columns
  [{:key    :application-id
    :title  :application.id}
   {:key    :backend-id
    :title  :verdict.id}
   {:key    :property-id
    :title  :pate.property-id}
   {:key    :archiver
    :title  :archival-report.archiver}])

(defn- get-onkalo-data [organization-id start-date end-date]
  (try
    (-> (http/get (str arkisto-host "/reporting/applications")
                  {:basic-auth   [app-id app-key]
                   :query-params {:organization organization-id
                                  :start-date   (date/iso-datetime start-date)
                                  :end-date     (date/iso-datetime end-date)}})
        :body
        (json/decode true))
    (catch Throwable t
      (timbre/error t "Could not get" organization-id "applications from onkalo."))))

(defn ^OutputStream onkalo-applications-excel [organization-id start-date end-date lang]
  (-> [{:sheet-name (i18n/localize lang :permitApplications)
        :header     (map #(i18n/localize lang (:title %))
                         columns)
        :row-fn     #(map (comp (partial get %) :key)
                          columns)
        :data       (get-onkalo-data organization-id start-date end-date)}]
      excel/create-workbook
      excel/xlsx-stream))
