(ns lupapalvelu.application-search-api
  (:require [clojure.core.memoize :as memo]
            [lupapalvelu.action :as action :refer [defquery]]
            [lupapalvelu.application-search :as search]
            [lupapalvelu.application-search-elastic :as search-es]
            [lupapalvelu.application-utils :as app-utils :refer [location->object]]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer [fail ok unauthorized]]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :refer [application-id?]]
            [schema-tools.core :as st]
            [schema.core :as sc])
  (:import [java.time Duration]))

(set! *warn-on-reflection* true)

(def ApplicationsSearchTypes
  (sc/enum "all"       ; The "All" tab in the dashboard UI => Applications with :state "canceled" are filtered out
           "unlimited" ; No filtering by the applications state
           "application"
           "construction"
           "verdict"
           "finalized"
           "inforequest"
           "canceled"
           "readyForArchival"
           "archivingProjects"
           "processing"
           "state-filter"
           ;; foreman document states
           "foreman-all"
           "declared"
           "submitted"
           "approved"
           "acknowledged"
           "rejected"
           "demolition-notice"
           "foremanApplication"
           "foremanNotice"))

(def ApplicationsSearchEventTypes
  (sc/enum "warranty-period-end"
           "license-period-start"
           "license-period-end"
           "license-started-not-ready"
           "license-ended-not-ready"
           "announced-to-ready-state-not-ready"))

(def ApplicationsSearchParams
  (st/merge {:applicationType ApplicationsSearchTypes
             :limit           sc/Int
             :skip            sc/Int
             :sort            {:field sc/Str
                               :asc   sc/Bool}}
            (st/optional-keys
              {:userIsGeneralHandler     sc/Bool
               :searchText               sc/Str
               :areas                    [ssc/NonBlankStr]
               :companyTags              [sc/Str]
               :tags                     [ssc/NonBlankStr]
               :states                   [ssc/NonBlankStr]
               :organizations            [org/OrgId]
               :handlers                 [ssc/NonBlankStr]
               :operations               [ssc/NonBlankStr]
               :event                    {:eventType               [ApplicationsSearchEventTypes]
                                          (sc/optional-key :start) sc/Int
                                          (sc/optional-key :end)   sc/Int}
               :includeConverted         sc/Bool
               :includePaperApplications sc/Bool
               :foremanDocs              sc/Bool
               :foremanRoles             [ssc/NonBlankStr]})))

(defn- global-es-search-enabled?
  "Returns the database flag for provided search type (:application-search /
  :foreman-search). Flag enables elasticsearch search results for all users."
  [search-type]
  (mongo/any? :admin-config {:_id        "dashboard-search-elastic"
                             search-type true}))

(def ^:private es-enabled-cache-ttl (.toMillis (Duration/ofSeconds 10)))

(def global-es-application-search
  (memo/ttl #(global-es-search-enabled? :application-search)
            :ttl/threshold es-enabled-cache-ttl))

(def global-es-foreman-search
  (memo/ttl #(global-es-search-enabled? :foreman-search)
            :ttl/threshold es-enabled-cache-ttl))

(defn es-search-enabled? [search-type]
  (case search-type
    :application-search (global-es-application-search)
    :foreman-search (global-es-foreman-search)))

(defn elasticsearch-allowed [search-type {{:keys [impersonating]} :user}]
  (cond
    (es-search-enabled? search-type)
    nil

    (not (#{"all" "impersonators"} (env/value :applications-search-elastic :user-scope)))
    (fail :error.applications-search-elastic.user-scope-not-defined)

    (not (or (= (env/value :applications-search-elastic :user-scope) "all")
             (and (= (env/value :applications-search-elastic :user-scope) "impersonators")
                  impersonating)))
    (fail :error.applications-search-elastic.user-not-in-scope)))

(defn search-text-shorter-than-max-length [{{:keys [searchText]} :data}]
  (when (> (count searchText) (env/value :search-text-max-length))
    (fail :error.search-text-is-too-long)))

(defquery applications-search-es
  {:description    "Service point for application search component using elasticsearch"
   :request-schema ApplicationsSearchParams
   :user-roles     #{:applicant :authority :financialAuthority}
   :pre-checks     [(partial elasticsearch-allowed :application-search)
                    search-text-shorter-than-max-length]}
  [{:keys [data] :as command}]
  (ok :data (if (:foremanDocs data)
              (search-es/foreman-documents-for-user command data)
              (search-es/applications-for-user command data))))

(defquery foreman-search-es
  {:description    "Service point for foreman document search using elasticsearch"
   :request-schema ApplicationsSearchParams
   :user-roles     #{:applicant :authority :financialAuthority}
   :pre-checks     [(partial elasticsearch-allowed :foreman-search)
                    search-text-shorter-than-max-length]}
  [{:keys [data] :as command}]
  (ok :data (search-es/foreman-documents-for-user command data)))

(defquery applications-search
  {:description "Service point for application search component"
   :parameters  []
   :user-roles  #{:applicant :authority :financialAuthority}
   :pre-checks  [search-text-shorter-than-max-length]}
  [{:keys [user data]}]
  (ok :data (search/applications-for-user user data)))

(defn show-elastic-checkbox [search-type {{:keys [impersonating]} :user}]
  (cond
    (es-search-enabled? search-type)
    (fail :error.applications-search-elastic.is-set-always-on)

    (not (#{"all" "impersonators"} (env/value :applications-search-elastic :user-scope)))
    (fail :error.applications-search-elastic.user-scope-not-defined)

    (and (= "impersonators" (env/value :applications-search-elastic :user-scope))
         (not impersonating))
    (fail :error.applications-search-elastic.elasticsearch-not-allowed)))

(defquery show-application-search-elastic-checkbox
  {:description "Dummy endpoint to check whether elasticsearch checkbox should be shown or not"
   :parameters  []
   :user-roles  #{:applicant :authority :financialAuthority}
   :pre-checks  [(partial show-elastic-checkbox :application-search)]}
  [_]
  (ok))

(defquery show-new-foreman-elastic-checkbox
  {:description "Dummy endpoint to check whether elasticsearch checkbox should be shown or not"
   :parameters  []
   :user-roles  #{:applicant :authority :financialAuthority}
   :pre-checks  [(partial show-elastic-checkbox :foreman-search)]}
  [_]
  (ok))

(defquery applications-search-total-count
  {:description "Separate query for total result count, as it may be slow"
   :parameters  []
   :user-roles  #{:applicant :authority :financialAuthority}
   :pre-checks  [search-text-shorter-than-max-length]}
  [{:keys [user data]}]
  (ok :data (search/query-total-count user data)))

(defquery applications-search-default
  {:description         "The initial applications search. Returns data and used search."
   :parameters          []
   :user-roles          #{:applicant :authority :financialAuthority}
   :allowed-token-types #{:map-3d-token}}
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

(defquery application-map-markers-search-es
  {:description    "Bare minimum info on applications for the map markers from elastic."
   :request-schema ApplicationsSearchParams
   :user-roles     #{:applicant :authority :financialAuthority}
   :pre-checks     [search-text-shorter-than-max-length]}
  [{:keys [data] :as command}]
  (->> data
       ss/trimwalk
       util/strip-blanks
       (search-es/map-markers-for-user command)
       (ok :markers)))

(defquery application-map-markers-search
  {:description "Bare minimum info on applications for the map markers."
   :parameters  []
   :user-roles  #{:applicant :authority :financialAuthority}
   :pre-checks  [search-text-shorter-than-max-length]}
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
  (ok :infos (into [] (search/map-marker-infos user ids))))

(defn- selected-ops-by-permit-type [selected-ops]
  (let [selected-ops-set (set (map keyword selected-ops))]
    (-> operations/operation-names-by-permit-type
        (util/convert-values (partial filterv selected-ops-set)))))

(defquery get-application-operations
  {:user-roles #{:authority :applicant}}
  [{organizations :user-organizations user :user}]
  (if (usr/authority? user)
    (let [is-R?  (some #(= (:permitType %) "R") (mapcat :scope organizations))]
      (->> (if is-R? ["tyonjohtajan-nimeaminen-v2" "aiemmalla-luvalla-hakeminen"
                      "konversio" "konversio-rakennus"] [])
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

(defn localize-application [application]
  (-> application
      (update :secondaryOperations (fn [ops]
                                     (->> ops
                                          (remove (comp nil? :name))
                                          (mapv localize-operation))) )
      (update :primaryOperation localize-operation)
      (assoc
        :stateNameFi (i18n/localize "fi" (:state application))
        :stateNameSv (i18n/localize "sv" (:state application)))))

(defquery applications
  {:description "Query for integrations"
   :parameters  []
   :user-roles  #{:applicant :authority}
   :pre-checks  [search-text-shorter-than-max-length]}
  [{user :user data :data}]
  (let [user-query (domain/basic-application-query-for user)
        query      (search/make-query user-query data user)
        fields     [:id :address :applicant :authority
                    :drawings :infoRequest :location
                    :modified :municipality
                    :primaryOperation :secondaryOperations
                    :permitType
                    :state
                    :submitted]
        skip       (or (util/->long (:skip data)) 0)
        limit      (or (util/->long (:limit data)) Integer/MAX_VALUE)
        xf         (comp
                     (filter (partial auth/application-allowed-for-company-user? user))
                     (map #(-> (mongo/with-id %)
                               (domain/filter-application-content-for user)
                               (select-keys fields) ; filters empty lists from previous step
                               localize-application
                               location->object)))
        rows       (search/search query fields (search/make-sort data) skip limit xf)]
    (ok :applications rows)))

(defquery event-search
  {:description "Query for event search availability"
   :parameters  []
   :user-roles  #{:authority}
   :pre-checks  [(fn [command]
                   (when-not (some #(= (:permitType %) "YA") (mapcat :scope (:user-organizations command)))
                     unauthorized))]}
  [_]
  (ok))

;; Dashboard API

(defquery dashboard-query
  {:description   "Placeholder: Run dashboard a query, for search view or for the widgets."
   ; :request-malli [:map]
   :feature       :dashboard-view
   :user-roles    #{:applicant :authority}}
  [{user :user, data :data}]
  (ok {:total (rand-int 10)}))
