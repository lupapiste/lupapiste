(ns lupapalvelu.admin-api
  (:require [lupapalvelu.action :refer [defcommand defquery defraw] :as action]
            [lupapalvelu.admin.elasticsearch-api]
            [lupapalvelu.admin.import-export.api]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.backing-system.core :as bs]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.ryhti.api.ryhti-api :as ryhti-api]
            [lupapalvelu.storage.file-storage :as storage]
            [monger.operators :refer [$pull]]
            [ring.util.http-status :as http-status]
            [sade.core :refer [fail! now ok]]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

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

(defraw admin-download-foreman-v3-data
  {:parameters       [documentId orgId]
   :input-validators [(partial action/non-blank-parameters [:documentId])]
   :user-roles       #{:admin}}
  [command]
  (if-some [{:keys  [permitType]
             org-id :organization
             app-id :id} (domain/get-foreman-doc-application-no-access-checking documentId orgId)]
    (let [organization (org/get-organization org-id)
          command      (assoc command :organization organization)]
      (if-some [fmt (bs/application-format organization permitType)]
        {:status  200
         :body    (bs/get-application command permitType [:application-id documentId] true)
         :headers {"Content-Type"        (bs/application-format->content-type fmt)
                   "Content-Disposition" (bs/application-format-content-disposition fmt app-id (now))
                   "Cache-Control"       "no-cache"}}
        (fail! :error.no-legacy-available)))
    {:status  404
     :headers {"Content-Type" "text/plain" "Cache-Control" "no-cache"}
     :body    (format "Application for document '%s' not found" documentId)}))

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

(defquery fetch-response-headers
  {:description      "Makes an arbitrary HTTP GET request and returns the response headers and
  friendly status string. We use GET since the target systems often do not support HEAD
  requests."
   :parameters       [url]
   :input-validators [(partial action/parameters-matching-schema [:url] ssc/HttpUrl)]
   :user-roles       #{:admin}}
  [_]
  (try
    (let [{:keys [status
                  headers]} (http/get url {;; 20 seconds is less than the frontend request
                                           ;; timeout.
                                           :socket-timeout     20000
                                           :connection-timeout 20000})]
      (ok :status (str status " " (:name (get http-status/status status)))
          :headers (sort headers)))
    (catch Exception e
      (ok :status (str e)
          :headers [["Stacktrace" (ss/join "\n" (.getStackTrace e))]]))))

(defquery admin-test-ryhti-api
  {:description "Tests the Ryhti API connection by connecting to the authentication endpoint to get a token."
   :parameters  [organizationId municipality]
   :user-roles  #{:admin}
   :input-validators [(partial action/non-blank-parameters [:organizationId :municipality])]
   :pre-checks  [ryhti-api/organization-has-ryhti-configuration]
   }
  [_]
  (if-let [authentication (ryhti-api/authenticate! organizationId municipality)]
    {:status (if authentication 200 500)
     :body   authentication}
    (fail! :error.ryhti-api-unavailable)))

(defquery get-permanent-building-permit-id
  {:description      "Tests the Ryhti API connection by retrieving a building permit id from Ryhti"
   :parameters       [organizationId municipality appId]
   :user-roles       #{:admin}
   :input-validators [(partial action/non-blank-parameters [:organizationId :municipality :appId])]
   :pre-checks       [ryhti-api/organization-has-ryhti-configuration]
   }
  [_]
  (if-let [resp (ryhti-api/get-permanent-permit-id! organizationId municipality appId :building)]
    {:status (if resp 200 500)
     :body   resp}
    (fail! :error.ryhti-api-unavailable)))
