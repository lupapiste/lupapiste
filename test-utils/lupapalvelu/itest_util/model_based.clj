(ns lupapalvelu.itest-util.model-based
  "Utilities for Model Based Testing with state graphs. Extracted from allu-itest so the implementation is somewhat
  sloppy but is provided here in the hope that it might be useful and improved in other contexts."
  (:require [taoensso.timbre :refer [warn]]))

(defn state-graph->transitions
  "Convert a {:state [:successor ...]} graph into a ([:state :successor] ...) graph."
  [states]
  (mapcat (fn [[state succs]] (map #(vector state %) succs)) states))

(defn- transitions-todo
  "Which transitions haven't been visited enough times or been soured?"
  [visited-total soured visit-goal]
  (->> visited-total
       (filter (fn [[transition visit-count]] (and (not (soured transition)) (< visit-count visit-goal))))
       (map key)))

(defn- probe-next-transition
  "Get the next transition that will lead to progress in the testing."
  [states visit-goal visited-total soured current-state]
  ;; This code is not so great but does the job for now.
  (letfn [(useful [current visited]
            (let [[usefuls visited]
                  (reduce (fn [[transitions visited] succ]
                            (let [transition [current succ]
                                  visited* (conj visited transition)]
                              (cond
                                (soured transition) [transitions visited*]
                                (< (get visited-total transition) visit-goal) [(conj transitions transition) visited*]
                                (not (visited transition)) (let [[transition* visited*] (useful succ visited*)]
                                                             (if transition*
                                                               [(conj transitions transition) visited*]
                                                               [transitions visited*]))
                                :else [transitions visited*])))
                          [[] visited] (get states current))]
              [(when (seq usefuls) (rand-nth usefuls))    ; HACK: rand-nth
               visited]))]
    (first (useful current-state #{}))))

(defn traverse-state-transitions
  "Traverse the `states` graph so that each transition is visited at least `visit-goal` times. If a transition fails
  to proceed to another state it will never be tried again regardless of `visit-goal`.

  states: A {:state [:successor ...]} graph (as in e.g. `lupapalvelu.states`)
  initial-state: The state to start from
  init!: A zero-argument initialization function that should return the initial userdata
  transition-adapters: What to do when executing transitions;
                       {[:state :successor] (fn [[state successor] userdata] ...) ...};
                       Each fn should return the next state.
  visit-goal: How many times at least should each transition be visited?

  `userdata` is just some additional data that you might want to return from `init!` and provide to each
  transition-adapter e.g. an application id."
  [& {:keys [states initial-state init! transition-adapters visit-goal]}]
  (let [initial-visited (zipmap (state-graph->transitions states) (repeat 0))]
    (loop [visited-total initial-visited
           visited visited-total
           soured #{}
           current-state initial-state
           user-state (init!)]
      (if-let [[_ next-state :as transition] (probe-next-transition states visit-goal visited-total soured
                                                                    current-state)]
        (if-let [transition-adapter (get transition-adapters transition)]
          (let [next-state* (transition-adapter transition user-state)]
            (if (or (not= next-state* current-state)        ; Led somewhere else?
                    (= next-state* next-state))             ; Wasn't supposed to!
              (recur (update visited-total transition inc) (update visited transition inc) soured
                     next-state* user-state)
              (recur (update visited-total transition inc)
                     (update visited transition inc)
                     (conj soured transition)               ; Let's not try that again.
                     next-state*
                     user-state)))
          (do (warn "No adapter provided for" transition)
              (recur (update visited-total transition inc) (update visited transition inc) soured
                     next-state user-state)))
        (when (seq (transitions-todo visited-total soured visit-goal))
          (recur visited-total initial-visited soured initial-state (init!)))))))
