(ns lupapalvelu.smoketest.assignment-smoke-tests
  (:require [clojure.set :refer [rename-keys]]
            [schema.core :as sc]
            [taoensso.timbre :as timbre :refer [errorf]]
            [lupapiste.mongocheck.core :refer [mongocheck]]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer :all]
            [sade.util :as util]))

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
           (mongo/select "applications" query [:_id])
           (catch com.mongodb.MongoException e
             (errorf "Application search query=%s failed: %s" query e)
             (fail! :error.unknown)))
         (map :id)
         (set))))

(defonce canceled-applications (delay (get-canceled-application-ids)))

(defn- application-canceled? [id]
  (boolean (@canceled-applications id)))

(defn- target-disabled? [{appId :id} {targetId :id}]
  (boolean (:disabled (->> (mongo/select-one "applications" {:_id appId :documents.id targetId} [:documents.id :documents.disabled])
                           :documents
                           (util/find-by-id targetId)))))

(defn- status-corresponds-to-application-state [{:keys [application status target]}]
  (when-not (= (or (application-canceled? (:id application))
                   (target-disabled? application target))
               (= status "canceled"))
    (str "status was '" status "', but application was "
         (if (application-canceled? (:id application))
           "" "not ")
         "canceled")))

(mongocheck :assignments status-corresponds-to-application-state :application :status :target)
