(ns lupapalvelu.appeal-api
  (:require [sade.core :refer :all]
            [sade.util :as util]
            [monger.operators :refer [$push $elemMatch $set]]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.appeal :as appeal]
            [lupapalvelu.appeal-verdict :as appeal-verdict]
            [lupapalvelu.states :as states]
            [lupapalvelu.mongo :as mongo]))

(defn- verdict-exists
  "Pre-check to validate that for selected verdictId a verdict exists"
  [{{verdictId :targetId} :data} {:keys [verdicts]}]
  (when-not (util/find-first #(= verdictId (:id %)) verdicts)
    (fail :error.verdict-not-found)))

(defn- appeal-exists
  "Pre-check to validate that at least one appeal exists before appeal verdict can be created"
  [_ {:keys [appeals]}]
  (when (zero? (count appeals))
    (fail :error.appeals-not-found)))

(defn- appeal-id-exists
  "Pre-check to validate that given ID exists in application"
  [{{appeal-id :appealId} :data} {:keys [appeals]}]
  (when appeal-id ; optional parameter, could be nil in command
    (when-not (util/find-by-id appeal-id appeals)
      (fail :error.unknown-appeal))))

(defn- appeal-verdict-id-exists
  "Pre-check to validate that id from parameters exist in :appealVerdicts"
  [{{appeal-id :appealId} :data} {:keys [appealVerdicts]}]
  (when appeal-id
    (when-not (util/find-by-id appeal-id appealVerdicts)
      (fail :error.unknown-appeal-verdict))))

(defn appeal-verdicts-after-appeal?
  "Predicate to check if appeal-verdicts have been made AFTER the given appeal in question has been made.
   Returns true if at least one appeal-verdict has been given after the appeal."
  [appeal appeal-verdicts]
  {:pre [(map? appeal) (sequential? appeal-verdicts)]}
  (not-every? (partial > (:made appeal)) (map :made appeal-verdicts)))

(defn- appeal-editable?
  "Pre-check to check that appeal can be edited."
  [{{appeal-id :appealId} :data} {:keys [appeals appealVerdicts]}]
  (when appeal-id
    (if-let [appeal (util/find-by-id appeal-id appeals)]
      (when (appeal-verdicts-after-appeal? appeal appealVerdicts)
        (fail :error.appeal-verdict-already-exists))
      (fail :error.unknown-appeal))))


(defn new-appeal-data-mongo-updates
  "Returns $push mongo update map of data to given 'collection' property.
   Does not validate appeal-data, validation must be taken care elsewhere."
  [collection data]
  {:mongo-updates {$push {(keyword collection) data}}})

(defn update-appeal-data-mongo-updates
  "Generates query and updates for given update-data into given collection.
   Query is $elemMatch to provided matching-id as 'id'.
   Map with mongo query and updates is returned.
   Does not validate appeal-data, validation must be taken care elsewhere."
  [collection matching-id update-data]
  {:mongo-query   {(keyword collection) {$elemMatch {:id matching-id}}}
   :mongo-updates {$set (zipmap
                          (map #(str (name collection) ".$." (name %)) (keys update-data))
                          (vals update-data))}})

(defcommand upsert-appeal
  {:description "Creates new appeal if appealId is not given. Updates appeal with given parameters if appealId is given"
   :parameters          [id targetId type appellant made]
   :optional-parameters [text appealId]
   :user-roles          #{:authority}
   :states              states/post-verdict-states
   :input-validators    [appeal/input-validator
                         (partial action/number-parameters [:made])]
   :pre-checks          [verdict-exists
                         appeal-id-exists
                         appeal-editable?]}
  [command]
  (if-let [updates (if appealId
                     (some->> (appeal/appeal-data-for-upsert targetId type appellant made text appealId)
                              (update-appeal-data-mongo-updates :appeals appealId))
                     (some->> (appeal/appeal-data-for-upsert targetId type appellant made text)
                              (new-appeal-data-mongo-updates :appeals)))]
    (action/update-application
      command
      (:mongo-query updates)
      (:mongo-updates updates))
    (fail :error.invalid-appeal)))

(defcommand upsert-appeal-verdict
  {:parameters          [id targetId giver made]
   :optional-parameters [text appealId]
   :user-roles          #{:authority}
   :states              states/post-verdict-states
   :input-validators    [appeal-verdict/input-validator
                         (partial action/number-parameters [:made])]
   :pre-checks          [verdict-exists
                         appeal-exists
                         appeal-verdict-id-exists]}
  [command]
  (if-let [updates (if appealId
                     (some->> (appeal-verdict/appeal-verdict-data-for-upsert targetId giver made text appealId)
                              (update-appeal-data-mongo-updates :appealVerdicts appealId))
                     (some->> (appeal-verdict/appeal-verdict-data-for-upsert targetId giver made text)
                              (new-appeal-data-mongo-updates :appealVerdicts)))]
    (action/update-application
      command
      (:mongo-query updates)
      (:mongo-updates updates))
    (fail :error.invalid-appeal-verdict)))


(defn- process-appeal
  "Process appeal for frontend"
  [appeal]
  (case (keyword (:type appeal))
    :appealVerdict appeal
    :appeal        appeal
    :rectification appeal))

(defn- add-attachments [{id :id :as appeal}]
  ; get attacments for appeal here
  appeal)


(defquery appeals
  {:description "Query for frontend, that returns all appeals/appeal verdicts of application in pre-processed form."
   :parameters [id]
   :user-roles #{:authority :applicant}
   :states     states/post-verdict-states}
  [{application :application}]
  (let [appeal-verdicts (map #(assoc % :type "appealVerdict") (:appealVerdicts application))
        all-appeals     (concat (:appeals application) appeal-verdicts)
        processed-appeals (->> all-appeals
                               (map (comp add-attachments process-appeal))
                               (sort-by :made))]
    (ok :data (group-by :target-verdict processed-appeals))))
