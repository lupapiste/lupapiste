(ns lupapalvelu.admin-api
  (:require [lupapalvelu.action :refer [defraw defquery defcommand] :as action]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.backing-system.core :as bs]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.matti :as matti]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.storage.file-storage :as storage]
            [monger.operators :refer :all]
            [sade.core :refer [now ok fail!]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(defraw admin-download-application-data
  {:parameters [applicationId]
   :input-validators [(partial action/non-blank-parameters [:applicationId])]
   :user-roles #{:admin}}
  [command]
  (let [app-id (ss/trim applicationId)]
    (if-some [{:keys [permitType]
               org-id :organization} (domain/get-application-no-access-checking app-id [:permitType :organization])]
      (let [organization (org/get-organization org-id)
            command (assoc command :organization organization)]
        (if-some [fmt (bs/application-format organization permitType)]
          {:status  200
           :body    (bs/get-application command permitType [:application-id app-id] true)
           :headers {"Content-Type"        (bs/application-format->content-type fmt)
                     "Content-Disposition" (bs/application-format-content-disposition fmt app-id (now))
                     "Cache-Control"       "no-cache"}}
          (fail! :error.no-legacy-available)))
      {:status  404
       :headers {"Content-Type"  "text/plain" "Cache-Control" "no-cache"}
       :body    (format "Application '%s' not found" app-id)})))

(defraw admin-download-application-data-with-kuntalupatunnus
  {:parameters [kuntalupatunnus municipality permitType]
   :input-validators [(partial action/non-blank-parameters [:kuntalupatunnus :municipality :permitType])]
   :user-roles #{:admin}}
  [command]
  (if-some [organization (org/resolve-organization municipality permitType)]  ;; this also validates the permit-type
    (let [command (assoc command :organization organization)]
      (if-some [fmt (bs/application-format organization permitType)]
        {:status  200
         :body    (bs/get-application command permitType [:kuntalupatunnus kuntalupatunnus] true)
         :headers {"Content-Type"        (bs/application-format->content-type fmt)
                   "Content-Disposition" (bs/application-format-content-disposition fmt municipality permitType (now))
                   "Cache-Control"       "no-cache"}}
        (fail! :error.no-legacy-available)))
    {:status  404
     :headers {"Content-Type"  "text/plain" "Cache-Control" "no-cache"}
     :body    "Organization not found"}))

(defraw admin-download-authority-usernames
  {:user-roles #{:admin}}
  [_]
  {:status 200
   :body (ss/join "\n" (mongo/distinct :users :username {:role "authority"}))
   :headers {"Content-Type" "text/plain"
             "Cache-Control" "no-cache"}})

(defraw admin-download-building-info
  {:parameters       [applicationId]
   :input-validators [(partial action/non-blank-parameters [:applicationId])]
   :user-roles       #{:admin}}
  [_]
  (if-let [application (mongo/by-id :applications applicationId ["organization" "permitType" "propertyId"])]
    (let [{url         :url
           credentials :credentials} (org/get-building-wfs (update application :permitType
                                                                   #(if (= % "ARK") "R" %)))]
      {:status  200
       :body    (building-reader/building-xml url credentials (:propertyId application) true)
       :headers {"Content-Type"        "application/xml;charset=UTF-8"
                 "Content-Disposition" (format "attachment;filename=\"%s-%s-%s.xml\"" applicationId (:propertyId application) (now))
                 "Cache-Control"       "no-cache"}})
    {:status  404
     :headers {"Content-Type" "text/plain" "Cache-Control" "no-cache"}
     :body    "Application not found"}))

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
        result-fmt (fn [acc {id :id {:keys [size] :as value} :value}]
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

(defcommand matti-batchrun
  {:description      "Send state change and HTTP integration messages for
  given applications. The applications are specified aiether as a list
  or a datestring range (matching either sent or published Pate
  verdict timestamp) Return a report as a list."
   :user-roles       #{:admin}
   :input-validators [(sc/conditional
                        :ids {:organizationId   org/OrgId
                              :validateXml      sc/Bool
                              :ids              ssc/NonBlankStr
                              :sendVerdicts     sc/Bool
                              :sendStateChanges sc/Bool}
                        :else {:organizationId   org/OrgId
                               :validateXml      sc/Bool
                               :startDate        ssc/NonBlankStr
                               :endDate          ssc/NonBlankStr
                               :sendVerdicts     sc/Bool
                               :sendStateChanges sc/Bool})]
   :pre-checks       [org/dmcity-backend]}
  [{data :data}]
  (->> (matti/parse-params data)
       matti/batchrun
       flatten
       (ok :results)))

(defquery matti-review-batchrun-targets
  {:description      "Returns a list of application ids that have tasks to
  be sent (the review tasks match the batchrun criteria)."
   :user-roles       #{:admin}
   :input-validators [{:organizationId      org/OrgId
                       :startDate           ssc/NonBlankStr
                       :endDate             ssc/NonBlankStr
                       (sc/optional-key :_) sc/Any}]
   :pre-checks       [org/dmcity-backend]}
  [{data :data}]

  (->> (matti/parse-params data)
       matti/review-batchrun-targets
       (ok :applicationIds)))

(defcommand matti-review-batchrun
  {:description      "Resends every review task of the given
  applications."
   :user-roles       #{:admin}
   :parameters       [organizationId ids]
   :input-validators [{:organizationId org/OrgId
                       :ids            [ssc/NonBlankStr]}]
   :pre-checks       [org/dmcity-backend]}
  [_]
  (ok :results (matti/review-batchrun organizationId ids)))

(defraw download-attachments-export-file
  {:description      "Lets the admin download an exported attachments zip without accessing the storage directly"
   :parameters       [organization-id file-id]
   :input-validators [(partial action/non-blank-parameters [:organization-id :file-id])]
   :user-roles       #{:admin}}
  [_]
  (if-let [organization (org/get-organization organization-id [:export-files])]
    (if-let [export-file (->> (:export-files organization)
                              (util/find-by-key :fileId (ss/trim file-id)))]
      (-> (storage/download-with-user-id "batchrun-user" (:fileId export-file))
           (att/output-attachment true))
      (fail! :error.file-not-found))
    (fail! :error.organization-not-found)))

(defcommand delete-attachments-export-file
  {:description      "Lets the admin remove an exported attachments zip without accessing the storage directly"
   :parameters       [organization-id file-id]
   :input-validators [(partial action/non-blank-parameters [:organization-id :file-id])]
   :user-roles       #{:admin}}
  [_]
  (if-let [organization (org/get-organization organization-id [:export-files])]
    (if-let [export-file (->> (:export-files organization)
                              (util/find-by-key :fileId (ss/trim file-id)))]
      (do (storage/delete-with-user-id "batchrun-user" (:fileId export-file))
          (org/update-organization organization-id {$pull {:export-files {:fileId (:fileId export-file)}}})
          (ok))
      (fail! :error.file-not-found))
    (fail! :error.organization-not-found)))
