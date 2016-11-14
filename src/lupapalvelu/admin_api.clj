(ns lupapalvelu.admin-api
  (:require [taoensso.timbre :as timbre :refer [trace tracef debug info infof warn warnf error errorf]]
            [sade.core :refer [now ok]]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.action :refer [defraw defquery] :as action]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.xml.krysp.building-reader :as building-reader]))

(defraw admin-download-application-xml
  {:parameters [applicationId]
   :input-validators [(partial action/non-blank-parameters [:applicationId])]
   :user-roles #{:admin}}
  [_]
  (if-let [application (domain/get-application-no-access-checking applicationId)]
    {:status 200
     :body (krysp-fetch/get-application-xml-by-application-id application true)
     :headers {"Content-Type" "application/xml;charset=UTF-8"
               "Content-Disposition" (format "attachment;filename=\"%s-%s.xml\"" applicationId (now))
               "Cache-Control" "no-cache"}}
    {:status 404
     :headers {"Content-Type"  "text/plain" "Cache-Control" "no-cache"}
     :body "Application not found"}))

(defraw admin-download-application-xml-with-kuntalupatunnus
  {:parameters [kuntalupatunnus municipality permitType]
   :input-validators [(partial action/non-blank-parameters [:kuntalupatunnus :municipality :permitType])]
   :user-roles #{:admin}}
  [_]
  (if-let [organization (organization/resolve-organization municipality permitType)]  ;; this also validates the permit-type
    (let [dummy-application {:id "" :permitType permitType :organization (:id organization)}]
      {:status 200
       :body (krysp-fetch/get-application-xml-by-backend-id dummy-application kuntalupatunnus true)
       :headers {"Content-Type" "application/xml;charset=UTF-8"
                 "Content-Disposition" (format "attachment;filename=\"%s-%s-%s.xml\"" municipality permitType (now))
                 "Cache-Control" "no-cache"}})
    {:status 404
     :headers {"Content-Type"  "text/plain" "Cache-Control" "no-cache"}
     :body "Organization not found"}))

(defraw admin-download-authority-usernames
  {:user-roles #{:admin}}
  [_]
  {:status 200
   :body (ss/join "\n" (mongo/distinct :users :username {:role "authority"}))
   :headers {"Content-Type" "text/plain"
             "Cache-Control" "no-cache"}})

(defraw admin-download-building-info
  {:parameters [applicationId]
   :input-validators [(partial action/non-blank-parameters [:applicationId])]
   :user-roles #{:admin}}
  [_]
    (if-let [application (mongo/by-id :applications applicationId ["organization" "permitType" "propertyId"])]
      (let [{url :url credentials :credentials} (organization/get-krysp-wfs application)]
        {:status  200
         :body    (building-reader/building-xml url credentials (:propertyId application) true)
         :headers {"Content-Type"        "application/xml;charset=UTF-8"
                   "Content-Disposition" (format "attachment;filename=\"%s-%s-%s.xml\"" applicationId (:propertyId application) (now))
                   "Cache-Control"       "no-cache"}})
      {:status 404
       :headers {"Content-Type"  "text/plain" "Cache-Control" "no-cache"}
       :body "Application not found"}))

(defn- attachment-report []
  (let [mapper-js "var size=0, count=0;
                   this.attachments.forEach(function(a) {
                      a.versions.forEach(
                        function(v){
                          size+=v.size;
                          count++;
                          if (v.originalFileId !== v.fileId) {count++;}
                        });
                   });
                   emit(this.organization, {size:size, count:count, applications: 1})"
        reducer-js "var size=0,count=0,applications=0;
                    values.forEach(function(v){
                      size+=v.size;
                      count+=v.count;
                      applications+=v.applications;
                    });
                    return {size:size, count:count, applications:applications}"
        result-fmt (fn [acc {id :id {:keys [size count] :as value} :value}]
                     (let [size-mb (/ size (* 1024 1024))
                           avg (if (pos? (:count value)) (/ (:size value) (:count value)) 0)
                           avg-kb (/ avg 1024)]
                       (->>
                         (assoc  value :avg avg :avg-kb avg-kb :size-mb size-mb)
                         (util/map-values long)
                         (assoc acc id))))]
    (reduce result-fmt (sorted-map) (mongo/map-reduce :applications {} mapper-js reducer-js))))

(defquery admin-attachment-report
  {:user-roles #{:admin}}
  [_]
  (ok :organizations (attachment-report)))

(defraw admin-attachment-report-csv
  {:user-roles #{:admin}}
  [_]
  (let [report-rows (cons "id;size-mb;size-b;files;avg-kb;applications"
                          (for [[k {:keys [avg-kb size-mb size count applications]}] (attachment-report)]
                            (str k \; size-mb \; size \; count \; avg-kb \; applications)))]
    {:status  200
     :headers {"Content-Type"        "text/csv;charset=UTF-8"
               "Content-Disposition" "attachment;filename=\"attachment-report.csv\""
               "Cache-Control"       "no-cache"}
     :body    (ss/join \newline report-rows)}))
