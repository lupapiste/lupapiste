(ns lupapalvelu.smoketest.assignment-smoke-tests
  (:require [clojure.set :refer [rename-keys]]
            [schema.core :as sc]
            [taoensso.timbre :refer [errorf]]
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

(defn status-mismatch-str [status target canceled? target-status]
  (str "status was '" status "', but " target " was "
       (if canceled?
         "" "not ")
       target-status))

(defn document-target? [targets]
  (and (= 1 (count targets))
       (or (= (:group (first targets)) "documents")
           (= (:group (first targets)) "parties"))))

(defn attachment-target? [targets]
  (and (= 1 (count targets))
       (= (:group targets) "attachments")))

(defn- status-corresponds-to-application-and-target-state [{:keys [application status targets]}]
  (cond (and (application-canceled? (:id application))       ; if application is canceled, assignments should be too
             (not= status "canceled"))
        (status-mismatch-str status "application" false "canceled")

        (and (not (application-canceled? (:id application))) ; given application is not canceled, assignment should be
             (document-target? targets)                      ; canceled iff document is disabled
             (not= (target-disabled? application (first targets))
                   (= status "canceled")))
        (status-mismatch-str status "document" true "disabled")

        (and (attachment-target? targets)                    ; attachment assignment status should be same as application's
             (not= (application-canceled? (:id application))
                   (= status "canceled")))
        (status-mismatch-str status "application" (application-canceled? (:id application)) "canceled")

        :else nil))

(mongocheck :assignments status-corresponds-to-application-and-target-state :application :status :targets)
