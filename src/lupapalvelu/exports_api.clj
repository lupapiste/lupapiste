(ns lupapalvelu.exports-api
  (:require [lupapalvelu.action :refer [defexport]]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.exports :as exports :refer [application-to-salesforce exported-application validate-application-export-data]]
            [lupapalvelu.exports.reporting-db :as reporting-db]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [noir.core :refer [defpage]]
            [sade.core :refer [ok fail now]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [taoensso.timbre :as timbre :refer [debug]]))


(defn- export [collection query fields]
  (ok collection (mongo/snapshot collection query fields)))

(defexport export-applications
  {:user-roles #{:trusted-etl}}
  [{{ts :modifiedAfterTimestampMillis} :data user :user}]
  (let [query (merge
                (domain/application-query-for user)
                {:primaryOperation.id {$exists true}}
                (when (ss/numeric? ts)
                  {:modified {$gte (Long/parseLong ts 10)}}))
        fields {:address 1 :applicant 1 :authority 1 :closed 1 :created 1 :convertedToApplication 1
                :infoRequest 1 :modified 1 :municipality 1 :opened 1 :openInfoRequest 1
                :primaryOperation 1 :secondaryOperations 1 :organization 1 :propertyId 1
                :permitSubtype 1 :permitType 1 :sent 1 :started 1 :state 1 :submitted 1
                :verdicts 1
                :documents.data.kaytto.kayttotarkoitus.value 1
                :documents.data.kaytto.rakennusluokka.value 1
                :documents.schema-info.op.id 1}
        raw-applications (mongo/snapshot :applications query fields)
        applications-with-operations (map
                                       (fn [a] (assoc a :operations (app-utils/get-operations a)))
                                       raw-applications)]
    (ok :applications (map exported-application applications-with-operations))))

(defexport salesforce-export
  {:user-roles #{:trusted-salesforce}
   :on-success validate-application-export-data}
  [{{after  :modifiedAfterTimestampMillis
     before :modifiedBeforeTimestampMillis} :data user :user}]
  (let [query (merge
                (domain/application-query-for user)
                {:primaryOperation.id {$exists true}
                 :facta-imported {$ne true} ;; Factasta konvertoituja hankkeita ei laskuteta
                 :non-billable-application {$ne true}} ;;Projektikäyttäjän (digitization-project-user) tekemiä hankkeita ei laskuteta
                (when (or (ss/numeric? after) (ss/numeric? before))
                  {:modified (util/assoc-when {}
                                              $gte (when after (Long/parseLong after 10))
                                              $lt  (when before (Long/parseLong before 10)))}))
        fields [:address :archived :closed :created
                :infoRequest :modified :municipality :opened :openInfoRequest :organization
                :primaryOperation :propertyId :permitSubtype :permitType
                :secondaryOperations :sent :started :state :submitted
                :documents.data.kaytto.kayttotarkoitus.value
                :documents.data.kaytto.rakennusluokka.value
                :documents.schema-info.op.id]
        raw-applications (mongo/snapshot :applications query fields)]
    (ok :applications (map application-to-salesforce raw-applications))))

(when (env/feature? :api-usage-export)
  (defexport export-archive-api-usage
   {:user-roles #{:trusted-salesforce}}
   [{{start-ts :startTimestampMillis
      end-ts   :endTimestampMillis} :data user :user}]
    (let [now-ts (now)
          start-ts (or (when start-ts (Long/parseLong start-ts 10)) now-ts)
          end-ts (or (when end-ts (Long/parseLong end-ts 10)) now-ts)]
      (if (> start-ts end-ts)
        (do (timbre/errorf "startTimestampMillis > endTimestampMillis: %d > %d" start-ts end-ts)
            (fail :error.invalid-timestamps))
        (ok (exports/archive-api-usage-to-salesforce start-ts end-ts))))))


(defexport export-applications-for-reporting-db
  {:user-roles #{:trusted-etl}}
  [{{start-ts :modifiedAfterTimestampMillis
     end-ts :modifiedBeforeTimestampMillis} :data}]
  (let [now-ts (now)
        start-ts (or (when start-ts (Long/parseLong start-ts 10)) 0)
        end-ts (or (when end-ts (Long/parseLong end-ts 10)) now-ts)]
    (if (> start-ts end-ts)
      (do (timbre/errorf "startTimestampMillis > endTimestampMillis: %d > %d" start-ts end-ts)
          (fail :error.invalid-timestamps))
      (ok {:applications (reporting-db/applications start-ts end-ts)}))))

;; Returns only the application ids for the time interval
;; [`:modifiedAfterTimestampMillis` .. `:modifiedBeforeTimestampMillis`]
(defexport export-application-ids-for-reporting-db
  {:user-roles #{:trusted-etl}}
  [{{start-ts :modifiedAfterTimestampMillis
     end-ts :modifiedBeforeTimestampMillis} :data}]
  (let [now-ts (now)
        start-ts (or (when start-ts (Long/parseLong start-ts 10)) 0)
        end-ts (or (when end-ts (Long/parseLong end-ts 10)) now-ts)]
    (if (> start-ts end-ts)
      (do (timbre/errorf "startTimestampMillis > endTimestampMillis: %d > %d" start-ts end-ts)
          (fail :error.invalid-timestamps))
      (ok {:applicationIds (reporting-db/applications-to-report start-ts end-ts)}))))

;; Returns a single application
(defexport export-application-for-reporting-db
  {:user-roles #{:trusted-etl}}
  [{{:keys [applicationId fields]} :data}]
  (if-let [application (reporting-db/report-application! applicationId fields)]
    (ok {:application application})
    (fail :not-found-or-validation-error)))

;; Returns multiple applications
(defexport export-reporting-applications
  {:user-roles #{:trusted-etl}}
  [{{:keys [applicationIds fields]} :data}]
  (if-let [apps (keep #(reporting-db/report-application! % fields) applicationIds)]
    (ok :applications apps)
    (fail :no-valid-applications)))


(defexport export-organizations
  {:user-roles #{:trusted-etl}}
  [_]
  (export :organizations {:scope.0 {$exists true}} [:name :scope]))

(env/in-dev
 ;; Updates a special timestamp in the application, forcing the next reporting database batchrun to fetch the app
 (defpage [:post "/dev/force-application-reporting-database-export"] {id :id}
   (logging/with-logging-context {:applicationId id}
     (try
       (let [now (now)]
         (debug "forcing reporting db update with ts: " now)
         (str (reporting-db/force-reporting-db-update-by-query! {:_id id} now)))
       (catch Throwable t
         (str "caught exception: " (.getMessage t)))))))
