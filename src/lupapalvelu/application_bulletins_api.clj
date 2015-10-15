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
   :versions.primaryOperation 1
   :versions.applicant 1 :versions.modified 1
   :modified 1})

(defn application-bulletins [{:keys [limit] :or {limit 10}}]
  (let [query {}
        apps (mongo/with-collection "application-bulletins"
               (query/find query)
               (query/fields bulletins-fields)
               (query/sort {:modified 1})
               (query/limit limit))]
    (map
      #(assoc (first (:versions %)) :id (:_id %))
      apps)))

(defquery application-bulletins
  {:description "Query for Julkipano"
   :feature :publish-bulletin
   :parameters []
   :optional-parameters [page]
   :user-roles #{:anonymous}}
  [{data :data}]
  (let [page  (or page 1)
        limit (* page 10)]
    (ok :data (application-bulletins (assoc data :limit limit)))))


(def app-snapshot-fields
  [:address :created :location :modified :applicant
   :municipality :organization :permitType
   :primaryOperation :propertyId :state :verdicts])

(defcommand publish-bulletin
  {:parameters [id]
   :feature :publish-bulletin
   :user-roles #{:authority}
   :states     states/all-application-states}
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
