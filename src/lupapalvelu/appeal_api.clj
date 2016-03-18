(ns lupapalvelu.appeal-api
  (:require [sade.core :refer :all]
            [sade.util :as util]
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

(defcommand create-appeal
  {:parameters          [id targetId type appellant made]
   :optional-parameters [text]
   :user-roles          #{:authority}
   :states              states/post-verdict-states
   :input-validators    [appeal/input-validator
                         (partial action/number-parameters [:made])]
   :pre-checks          [verdict-exists]}
  [{app :application :as command}]
  (if-let [updates (appeal/new-appeal-mongo-updates targetId type appellant made text)]
    (action/update-application
      command
      updates)
    (fail :error.invalid-appeal)))

(defcommand create-appeal-verdict
  {:parameters          [id targetId giver made]
   :optional-parameters [text]
   :user-roles          #{:authority}
   :states              states/post-verdict-states
   :input-validators    [appeal-verdict/input-validator
                         (partial action/number-parameters [:made])]
   :pre-checks          [verdict-exists
                         appeal-exists]}
  [command]
  (if-let [updates (appeal-verdict/new-appeal-verdict-mongo-updates targetId giver made text)]
    (action/update-application
      command
      updates)
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
