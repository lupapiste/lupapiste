(ns lupapalvelu.application-search-api
  (:require [monger.operators :refer :all]
            [monger.query :as query]
            [sade.core :refer :all]
            [sade.util :as util]
            [lupapalvelu.action :refer [defquery] :as action]
            [lupapalvelu.application-search :as search]
            [lupapalvelu.application-utils :refer [location->object]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]))

(defquery applications-search
  {:description "Service point for application search component"
   :parameters []
   :user-roles #{:applicant :authority}}
  [{user :user data :data}]
  (ok :data (search/applications-for-user
              user
              (select-keys
                data
                [:tags :organizations :applicationType :handlers
                 :limit :searchText :skip :sort :operations :areas :areas-wgs84]))))

(defquery applications-search-default
  {:description "The initial applications search. Returns data and used search."
   :parameters  []
   :user-roles  #{:applicant :authority}}
  [{user :user}]
  (let [{:keys [defaultFilter applicationFilters]} (usr/get-user-by-id (:id user))
        {:keys [sort filter]} (or (some-> defaultFilter
                                          :id
                                          (util/find-by-id applicationFilters))
                                  {:sort   {:field "modified"
                                            :asc   false}
                                   :filter {}})
        search (assoc filter
                      :sort sort
                      :applicationType (if (usr/authority? user)
                                         "application"
                                         "all")
                      :limit 100
                      :skip 0)]
    (ok :data (search/applications-for-user user search)
        :search search)))

(defquery applications-for-new-appointment-page
  {:description "Service point for application list in new-appointment page"
   :parameters []
   :user-roles #{:applicant}}
  [{user :user data :data}]
  (let [fields [:id :kind :organization :municipality :organizationName
                :permitType :address :primaryOperation]
        query  (search/make-query
                   (domain/applications-with-writer-authz-query-for user)
                   {:organizations (org/organizations-with-calendars-enabled)} user)
        skip   (or (util/->long (:skip data)) 0)
        limit  (or (util/->long (:limit data)) Integer/MAX_VALUE)
        apps   (search/search query fields (search/make-sort data) skip limit)
        apps   (app-utils/enrich-applications-with-organization-name apps)] ; Mapping of org ids to names
    (ok :data (map #(-> (mongo/with-id %)
                        (domain/filter-application-content-for user)
                        app-utils/with-application-kind
                        (select-keys fields))
                   apps))))

(defn- selected-ops-by-permit-type [selected-ops]
  (let [selected-ops-set (set (map keyword selected-ops))]
    (-> operations/operation-names-by-permit-type
        (util/convert-values (partial filter selected-ops-set)))))

(defquery get-application-operations
  {:user-roles #{:authority}}
  [{organizations :user-organizations}]
  (let [is-R?  (some #(= (:permitType %) "R") (mapcat :scope organizations))]
    (->> (if is-R? ["tyonjohtajan-nimeaminen-v2"] [])
         (concat (mapcat :selected-operations organizations))
         selected-ops-by-permit-type
         (ok :operationsByPermitType))))

(defn- localize-operation [op]
  (let [keys [:id :created :name :description]
        loc-path (if (:name op) ["operations" (:name op)] ["not-known"])]
    (merge
      (zipmap keys (repeat nil))
      (select-keys op keys)
      {:displayNameFi (apply i18n/localize "fi" loc-path)
       :displayNameSv (apply i18n/localize "sv" loc-path)})))

(defn- localize-application [application]
  (-> application
    (update :secondaryOperations (util/fn->> (remove (comp nil? :name))))
    (update :secondaryOperations #(map localize-operation %))
    (update :primaryOperation localize-operation)
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
        fields [:id :address :applicant :authority
                :drawings :infoRequest :location
                :modified :municipality
                :primaryOperation :secondaryOperations
                :permitType
                :state
                :submitted]

        skip        (or (util/->long (:skip data)) 0)
        limit       (or (util/->long (:limit data)) Integer/MAX_VALUE)

        apps        (search/search query fields (search/make-sort data) skip limit)

        rows (map #(-> (mongo/with-id %)
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
        fields [:municipality :submitted :primaryOperation]
        sort {:submitted -1}
        skip 0
        limit 5
        apps (search/search query fields sort skip limit)]
    (ok :applications (->> (filter :primaryOperation apps)
                           (map search/public-fields)))))
