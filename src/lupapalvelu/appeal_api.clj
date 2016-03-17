(ns lupapalvelu.appeal-api
  (:require [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.appeal :as appeal]
            [lupapalvelu.appeal-verdict :as appeal-verdict]
            [lupapalvelu.states :as states]
            [sade.util :as util]))

(defn- verdict-exists
  "Input validator to validate that for selected verdictId a verdict exists"
  [{{:keys [verdicts]} :application {verdictId :targetId} :data}]
  (when-not (util/find-first #(= verdictId (:id %)) verdicts)
    (fail :error.verdict-not-found)))

(defcommand create-appeal
  {:parameters [targetId type appellant made]
   :user-roles #{:authority}
   :states     states/post-verdict-states
   :input-validators [verdict-exists
                      appeal/input-validator
                      (partial action/number-parameters [:made])]}
  [command]
  (println "hi"))
