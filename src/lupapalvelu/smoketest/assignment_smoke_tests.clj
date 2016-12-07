(ns lupapalvelu.smoketest.assignment-smoke-tests
  (:require [clojure.set :refer [rename-keys]]
            [schema.core :as sc]
            [taoensso.timbre :as timbre :refer [errorf]]
            [lupapiste.mongocheck.core :refer [mongocheck]]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer :all]))

(defn- validate-assignment-against-schema [assignment]
  (sc/check assignment/Assignment (rename-keys assignment {:_id :id})))

(mongocheck :assignments validate-assignment-against-schema)

(defn- created-is-first-in-states [assignment]
  (when-not (= "created"
               (-> assignment :states first :type))
    (str "the first state type was not 'created' but '"
         (-> assignment :states first :type) "'")))

(mongocheck :assignments created-is-first-in-states :states)

(defn- get-canceled-application-ids []
  (let [query {:state "canceled"}]
    (->> (try
           (mongo/select "applications" query {:_id 1})
           (catch com.mongodb.MongoException e
             (errorf "Application search query=%s failed: %s" query e)
             (fail! :error.unknown)))
         (map :id)
         (set))))

(defonce canceled-applications (delay (get-canceled-application-ids)))

(defn- application-canceled? [id]
  (contains? @canceled-applications id))

(defn- status-corresponds-to-application-state [{:keys [application status]}]
  (when-not (and (application-canceled? (:id application))
                 (= status "canceled"))
    (str "status was '" status "', but application was "
         (if (application-canceled? (:id application))
           "" "not ")
         "canceled")))

(mongocheck :assignments status-corresponds-to-application-state :application :status)
