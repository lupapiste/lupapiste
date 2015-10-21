(ns lupapalvelu.application-bulletins-api
  (:require [monger.operators :refer :all]
            [monger.query :as query]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.property :as p]
            [lupapalvelu.action :refer [defquery defcommand]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.schemas :as schemas]
            [monger.operators :refer :all]
            [lupapalvelu.states :as states]
            [lupapalvelu.application-search :refer [make-text-query]]))

(def bulletins-fields
  {:versions {$slice -1}
   :versions.state 1 :versions.municipality 1
   :versions.address 1 :versions.location 1
   :versions.primaryOperation 1 :versions.propertyId 1
   :versions.applicant 1 :versions.modified 1
   :modified 1})

(def bulletin-page-size 10)

(defn- make-query [search-text]
  (let [text-query (when-not (ss/blank? search-text)
                     (make-text-query (ss/trim search-text) :prefix "versions"))
        queries    (filter seq [text-query])]
    (when-let [and-query (seq queries)]
      {$and and-query})))

(defn- get-application-bulletins-left [page searchText]
  (let [query (make-query searchText)]
    (- (mongo/count :application-bulletins query)
       (* page bulletin-page-size))))

(defn- get-application-bulletins [page searchText]
  (let [query (or (make-query searchText) {})
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
  [:_applicantIndex :address :applicant :created :documents :location
   :modified :municipality :organization :permitType
   :primaryOperation :propertyId :state :verdicts])

(defcommand publish-bulletin
  {:parameters [id]
   :feature :publish-bulletin
   :user-roles #{:authority}
   :states     (states/all-application-states-but :draft)}
  [{:keys [application created] :as command}]
  (let [app-snapshot (select-keys application app-snapshot-fields)
        attachments  (->> (:attachments application)
                          (filter :latestVersion)
                          (map #(dissoc % :versions)))
        app-snapshot (assoc app-snapshot :attachments attachments)
        changes      {$push {:versions app-snapshot}
                      $set  {:modified created
                             :municipality (:municipality app-snapshot)}}]
    (mongo/update-by-id :application-bulletins id changes :upsert true)
    (ok)))

(def bulletin-fields
  (merge bulletins-fields
    {:_applicantIndex 1 :versions.documents 1}))

(defquery bulletin
  {:parameters [bulletinId]
   :feature :publish-bulletin
   :user-roles #{:anonymous}}
  (let [bulletin (mongo/with-id (mongo/by-id :application-bulletins bulletinId bulletin-fields))
        bulletin-version (assoc (-> bulletin :versions first) :id (:id bulletin))
        append-schema-fn (fn [{schema-info :schema-info :as doc}]
                           (assoc doc :schema (schemas/get-schema schema-info)))
        bulletin (update-in bulletin-version [:documents] (partial map append-schema-fn))]
    (ok :bulletin bulletin)))
