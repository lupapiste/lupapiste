(ns lupapalvelu.application-search-api
  (:require [lupapalvelu.action :as action :refer [defquery]]
            [lupapalvelu.application-search :as search]
            [lupapalvelu.application-utils :as app-utils :refer [location->object]]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :refer [application-id?]]))

(defquery applications-search
  {:description "Service point for application search component"
   :parameters []
   :user-roles #{:applicant :authority :financialAuthority}}
  [{:keys [user data]}]
  (ok :data (search/applications-for-user user data)))

(defquery applications-search-total-count
  {:description "Separate query for total result count, as it may be slow"
   :parameters []
   :user-roles #{:applicant :authority :financialAuthority}}
  [{:keys [user data]}]
  (ok :data (search/query-total-count user data)))

(defquery applications-search-default
  {:description "The initial applications search. Returns data and used search."
   :parameters  []
   :user-roles  #{:applicant :authority :financialAuthority}}
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

(defquery application-map-markers-search
  {:description "Bare minimum info on applications for the map markers."
   :parameters  []
   :user-roles  #{:applicant :authority :financialAuthority}}
  [{:keys [user data]}]
  (->> data
       ss/trimwalk
       util/strip-blanks
       (search/map-markers-for-user user)
       (ok :markers)))

(defquery map-marker-infos
  {:description      "More marker information for given applications."
   :parameters       [ids]
   :input-validators [(partial action/vector-parameter-of :ids application-id?)]
   :user-roles       #{:applicant :authority :financialAuthority}}
  [{user :user}]
  (ok :infos (search/map-marker-infos user ids)))

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
  {:user-roles #{:authority :applicant}}
  [{organizations :user-organizations user :user}]
  (if (usr/authority? user)
    (let [is-R?  (some #(= (:permitType %) "R") (mapcat :scope organizations))]
      (->> (if is-R? ["tyonjohtajan-nimeaminen-v2" "aiemmalla-luvalla-hakeminen"] [])
           (concat (mapcat :selected-operations organizations))
           selected-ops-by-permit-type
           (ok :operationsByPermitType)))
    (ok :operationsByPermitType operations/operation-names-by-permit-type)))

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
    (update :secondaryOperations (util/fn->> (remove (comp nil? :name)) (map localize-operation)))
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

        rows (->> apps
                  (filter (partial auth/application-allowed-for-company-user? user))
                  (map #(-> (mongo/with-id %)
                            (domain/filter-application-content-for user)
                            (select-keys fields) ; filters empty lists from previous step
                            localize-application
                            location->object)))]
    (ok :applications rows)))


(defquery latest-applications
  {:description "Query for public website"
   :parameters []
   :user-roles #{:anonymous}}
  [_]
  (let [query {:submitted {$type "long"}
               :modified {$gt (-> (date/now) (date/minus :day) (date/timestamp))}}
        fields [:municipality :submitted :primaryOperation]
        sort {:submitted -1}
        skip 0
        limit 5
        apps (search/search query fields sort skip limit)]
    (ok :applications (->> (filter :primaryOperation apps)
                           (map search/public-fields)))))

(defquery event-search
  {:description "Query for event search availability"
   :parameters []
   :user-roles #{:authority}
   :pre-checks [(fn [command]
                  (when-not (some #(= (:permitType %) "YA") (mapcat :scope (:user-organizations command)))
                    unauthorized))]}
   [_])
