(ns lupapalvelu.application-search-api
  (:require [monger.operators :refer :all]
            [monger.query :as query]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery] :as action]
            [lupapalvelu.application-search :as search]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]))


(defquery applications-for-datatables
  {:description "Service point for jQuery dataTables"
   :parameters [params]
   :user-roles #{:applicant :authority}}
  [{user :user}]
  (ok :data (search/applications-for-user user params)))

(defn- localize-operation [op]
  (assoc op
    :displayNameFi (i18n/localize "fi" "operations" (:name op))
    :displayNameSv (i18n/localize "sv" "operations" (:name op))))

(defn- localize-application [application]
  (-> application
    (update-in [:secondaryOperations] #(map localize-operation %))
    (update-in [:primaryOperation] localize-operation)
    (assoc
      :stateNameFi (i18n/localize "fi" (:state application))
      :stateNameSv (i18n/localize "sv" (:state application)))))

(defquery applications
  {:description "Query for integrations"
   :parameters []
   :user-roles #{:applicant :authority}}
  [{user :user data :data}]
  (let [user-query (domain/basic-application-query-for user)
        query (search/make-query user-query data user)
        fields (concat [:id :location :infoRequest :address :municipality :primaryOperation :secondaryOperations :drawings :permitType] (filter keyword? search/col-sources))
        apps (mongo/select :applications query (zipmap fields (repeat 1)))
        rows (map #(-> %
                     (domain/filter-application-content-for user)
                     (select-keys fields) ; filters empty lists from previous step
                     localize-application)
               apps)]
    (ok :applications rows)))


(defquery latest-applications
  {:description "Query for public website"
   :parameters []
   :user-roles #{:anonymous}}
  [_]
  (let [query {:submitted {$ne nil}}
        limit 5
        apps (query/with-collection "applications"
               (query/find query)
               (query/fields [:municipality :submitted :primaryOperation])
               (query/sort {:submitted -1})
               (query/limit limit))]
    (ok :applications (->> apps
                           (map search/public-fields)))))
