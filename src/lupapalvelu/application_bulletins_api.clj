(ns lupapalvelu.application-bulletins-api
  (:require [monger.operators :refer :all]
            [monger.query :as query]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]))



(def bulletins-fields
  {:versions {$slice -1}
   :versions.state 1 :versions.municipality 1
   :versions.address 1 :versions.location 1
   :versions.primaryOperation 1 :versions.propertyId 1
   :versions.applicant 1 :versions.modified 1
   :modified 1})

(def bulletin-page-size 10)

(defn- get-application-bulletins-left [page]
  (- (mongo/count :application-bulletins)
     (* page bulletin-page-size)))

(defn- get-application-bulletins [page]
  (let [apps (mongo/with-collection "application-bulletins"
               (query/fields bulletins-fields)
               (query/sort {:modified 1})
               (query/paginate :page page :per-page bulletin-page-size))]
    (map
      #(assoc (first (:versions %)) :id (:_id %))
      apps)))

(defquery application-bulletins
  {:description "Query for Julkipano"
   :feature :publish-bulletin
   :parameters [page]
   :user-roles #{:anonymous}}
  [_]
  (ok :data (get-application-bulletins page)
      :left (get-application-bulletins-left page)))


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
