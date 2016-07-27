(ns lupapalvelu.state-machine
  (:require [lupapalvelu.operations :as operations]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [sade.core :refer [fail unauthorized]]
            [sade.util :as util]))

(defn state-graph
  "Resolves a state graph for an application"
  [application]
  {:pre  [(map? application)]
   :post [(map? %)]}
  (if (:infoRequest application)
    states/default-inforequest-state-graph
    (let [operation (get-in application [:primaryOperation :name])
          state-machine-resolver (operations/get-operation-metadata operation :state-graph-resolver)]
      (if (fn? state-machine-resolver)
        (state-machine-resolver application)
        (-> application permit/permit-type permit/get-state-graph)))))

(defn- state-transitions [{state :state :as application}]
  (let [graph (state-graph application)]
    (graph (keyword state))))

(defn can-proceed?
  "Can application be moved to next state"
  [application next-state]
  {:pre  [(map? application) (keyword? next-state)]}
  (let [transitions (state-transitions application)]
    (util/contains-value? transitions next-state)))

(defn next-state
  "Returns the default next state or nil if application is in terminal state"
  [application]
  {:pre  [(map? application)]}
  (let [transitions (state-transitions application)]
    (first transitions)))

(defn valid-state?
  "Is the given state in application's state graph"
  [application state]
  {:pre  [(map? application)]}
  (let [graph (state-graph application)]
    (util/contains-value? (keys graph) (keyword state))))

(defn validate-state-transition
  "Function for composing action pre-checs.
   E.g. :pre-checks [(partial state-machine/validate-state-transition :canceled)]"
  [next-state {:keys [application]}]
  (if (map? application)
    (when-not (can-proceed? application next-state)
      (fail :error.command-illegal-state :state (:state application) :next-state next-state))
    unauthorized))

(defn verdict-given-state [application]
  (util/find-first (partial valid-state? application) states/verdict-given-states))

(defn state-seq [graph]
  (let [initial-state (states/initial-state graph)]
    (loop [state initial-state, path []]
      (if (or (states/terminal-state? graph state) (util/contains-value? path state) )
        (conj path state)
        (recur (first (graph state)) (conj path state))))))

(defn application-state-seq [application]
  (state-seq (state-graph application)))
