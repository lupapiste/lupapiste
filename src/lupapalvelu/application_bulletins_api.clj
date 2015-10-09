(ns lupapalvelu.application-bulletins-api
  (:require [monger.operators :refer :all]
            [monger.query :as query]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery] :as action]
            [lupapalvelu.mongo :as mongo]))



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
   :parameters []
   :user-roles #{:anonymous}}
  [{data :data}]
  (ok :data (application-bulletins data)))
