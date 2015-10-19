(ns lupapalvelu.application-bulletins-api
  (:require [monger.operators :refer :all]
            [monger.query :as query]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.property :as p]
            [lupapalvelu.action :refer [defquery defcommand]]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [lupapalvelu.states :as states]
            [lupapalvelu.application-search :refer [operation-names]]))

(def bulletins-fields
  {:versions {$slice -1}
   :versions.state 1 :versions.municipality 1
   :versions.address 1 :versions.location 1
   :versions.primaryOperation 1 :versions.propertyId 1
   :versions.applicant 1 :versions.modified 1
   :modified 1})

(def bulletin-page-size 10)

(defn- get-application-bulletins-left [page searchText]
  (let [query (make-query searchText)]
    (- (mongo/count :application-bulletins query)
       (* page bulletin-page-size))))

(defn- make-free-text-query [filter-search]
  (let [or-query {$or [{:versions.address {$regex filter-search $options "i"}}
                       {:versions.verdicts.kuntalupatunnus {$regex filter-search $options "i"}}
                       {:versions.applicant {$regex filter-search $options "i"}}]}
        ops (operation-names filter-search)]
    (if (seq ops)
      (update-in or-query [$or] concat [{:versions.primaryOperation.name {$in ops}}])
      or-query)))

(defn- make-text-query [filter-search]
  {:pre [filter-search]}
  (cond
    (re-matches #"^([Ll][Pp])-\d{3}-\d{4}-\d{5}$" filter-search) {:_id (ss/upper-case filter-search)}
    (re-matches p/property-id-pattern filter-search) {:versions.propertyId (p/to-property-id filter-search)}
    :else (make-free-text-query filter-search)))

(defn- make-query [search-text]
  (let [and-query (filter seq
                    [(when-not (ss/blank? search-text) (make-text-query (ss/trim search-text)))])]
    (if (empty? and-query)
      {}
      {$and and-query})))

(defn- get-application-bulletins [page searchText]
  (let [query (make-query searchText)
        apps (mongo/with-collection "application-bulletins"
               (query/find query)
               (query/fields bulletins-fields)
               (query/sort {:modified 1})
               (query/paginate :page page :per-page bulletin-page-size))]
    (map
      #(assoc (first (:versions %)) :id (:_id %))
      apps)))

(defquery application-bulletins
  {:description "Query for Julkipano"
   :feature :publish-bulletin
   :parameters [page searchText]
   :user-roles #{:anonymous}}
  [_]
  (ok :data (get-application-bulletins page searchText)
      :left (get-application-bulletins-left page searchText)))


(def app-snapshot-fields
  [:address :applicant :created :documents :location
   :modified :municipality :organization :permitType
   :primaryOperation :propertyId :state :verdicts])

(defcommand publish-bulletin
  {:parameters [id]
   :feature :publish-bulletin
   :user-roles #{:authority}
   :states     (states/all-application-states-but :draft)}
  [{:keys [application created] :as command}]
  (let [app-snapshot (select-keys application app-snapshot-fields)
        attachments (->> (:attachments application)
                         (filter :latestVersion)
                         (map #(dissoc % :versions)))
        app-snapshot (assoc app-snapshot :attachments attachments)
        changes {$push {:versions app-snapshot}
                 $set  {:modified created}}]
    (mongo/update-by-id :application-bulletins id changes :upsert true)
    (ok)))

(def bulletin-fields
  (merge bulletins-fields
    {:versions.documents 1}))

(defquery bulletin
  {:parameters [bulletinId]
   :feature :publish-bulletin
   :user-roles #{:anonymous}}
  (let [bulletin (mongo/with-id (mongo/by-id :application-bulletins bulletinId bulletin-fields))]
    (ok :bulletin (assoc (-> bulletin :versions first) :id (:id bulletin)))))
