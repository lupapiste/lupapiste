(ns lupapalvelu.bim.bim-util
  (:require [lupapalvelu.bim.schema :as bim-schema]
            [lupapalvelu.states :as states]))

(defn application-state->model-phases
  "Returns ordered seq of valid model phases (those for which new models can be added)"
  [application-state]
  (if (-> application-state keyword states/post-verdict-states)
    (remove #{:submission-time} bim-schema/model-phases)
    (remove #{:construction-time :completion-time} bim-schema/model-phases)))

(defn valid-model-phase?
  "Returns true if the model phase is one that can be affected by the user in the given application state"
  [application-state model-phase]
  (let [valid-phase-set (set (application-state->model-phases application-state))]
    (->> (keyword model-phase)
         valid-phase-set
         boolean)))

