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

(defn status-mismatch-str [status target canceled?]
  (str "status was '" status "', but " target " was "
       (if canceled?
         "" "not ")
       "canceled"))

(defn- status-corresponds-to-application-state [{:keys [application status targets]}]
  (when-not (= (application-canceled? (:id application))
               (= status "canceled"))
    (status-mismatch-str status "application" (application-canceled? (:id application)))))

(mongocheck :assignments status-corresponds-to-application-state :application :status :targets)

(defn- status-corresponds-to-document-target-state [{:keys [application status targets]}]
  (when (and (= (count targets) 1)
             (= (:group (first targets)) "documents"))
    (let [only-document-target-disabled? (target-disabled? application (first targets))]
      (when-not (= only-document-target-disabled?
                   (= status "canceled"))
        (status-mismatch-str status "document" only-document-target-disabled?)))))

(mongocheck :assignments status-corresponds-to-document-target-state :application :status :targets)
