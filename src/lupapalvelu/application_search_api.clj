(ns lupapalvelu.application-search-api
  (:require [monger.operators :refer :all]
            [monger.query :as query]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery] :as action]
            [lupapalvelu.application-search :as search]
            [lupapalvelu.application-utils :refer [location->object]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]))

(defquery applications-search
  {:description "Service point for application search component"
   :parameters []
   :user-roles #{:applicant :authority}}
  [{user :user data :data}]
  (ok :data (search/applications-for-user
              user
              (select-keys
                data
                [:tags :organizations :applicationType :handler
                 :limit :searchText :skip :sort :operations :areas]))))

(defn- selected-ops-by-permit-type [selected-ops]
  (->> operations/operations
       (filter (fn [[opname _]]
                 (some #(= opname (keyword %)) selected-ops)))
       (map (fn [[op {permit-type :permit-type}]]
              {:op op :permit-type permit-type}))
       (group-by :permit-type)
       (map (fn [[permit-type ops]]
              {permit-type (map :op ops)}))
       (apply merge)))

(defquery get-application-operations
  {:user-roles #{:authority}}
  [{user :user}]

  (let [orgIds             (map name (-> user :orgAuthz keys))
        organizations      (map lupapalvelu.organization/get-organization orgIds)
        selected-ops       (mapcat :selected-operations organizations)
        ops-by-permit-type (selected-ops-by-permit-type selected-ops)]
    (ok :operationsByPermitType ops-by-permit-type)))

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
        fields [:id :location :infoRequest :address :municipality :primaryOperation :secondaryOperations :drawings :permitType :indicators
                :attachmentsRequiringAction :unseenComments :primaryOperation :applicant :submitted :modified :state :authority]
        apps (mongo/select :applications query (zipmap fields (repeat 1)))
        rows (map #(-> %
                     (domain/filter-application-content-for user)
                     (select-keys fields) ; filters empty lists from previous step
                     localize-application
                     location->object)
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
                           (filter :primaryOperation)
                           (map search/public-fields)))))
