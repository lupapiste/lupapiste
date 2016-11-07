(ns lupapalvelu.smoketest.assignment-smoke-tests
  (:require [clojure.set :refer [rename-keys]]
            [monger.query :as query]
            [schema.core :as sc]
            [taoensso.timbre :as timbre :refer [errorf]]
            [lupapiste.mongocheck.core :refer [mongocheck]]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer :all]))

(defn- validate-assignment-against-schema [assignment]
  (sc/check assignment/Assignment (rename-keys assignment {:_id :id})))

(apply mongocheck :assignments validate-assignment-against-schema
       (keys assignment/Assignment))

(defn- created-is-first-in-states [assignment]
  (when-not (= "created"
               (-> assignment :states first :type))
    (str "the first state type was not 'created' but '"
         (-> assignment :states first :type) "'")))

(mongocheck :assignments created-is-first-in-states :states)

(defonce canceled-applications (atom nil))

(defn- get-canceled-application-ids []
  (let [query {:state "canceled"}]
    (->> (try
           (mongo/with-collection "applications"
             (query/find query)
             (query/fields [:state]))
           (catch com.mongodb.MongoException e
             (errorf "Application search query=%s failed: %s" query e)
             (fail! :error.unknown)))
         (map :_id))))

(defn- application-canceled? [id]
  (if (nil? @canceled-applications)
    (do (reset! canceled-applications (set (get-canceled-application-ids)))
        (recur id))
    (boolean (@canceled-applications id))))

(defn- status-corresponds-to-application-state [{:keys [application status]}]
  (when-not (= (application-canceled? (:id application))
               (= status "canceled"))
    (str "status was '" status "', but application was "
         (if (application-canceled? (:id application))
           "" "not ")
         "canceled")))

(mongocheck :assignments status-corresponds-to-application-state :application :status)
