(ns lupapalvelu.application-state
  (:require [monger.operators :refer :all]
            [clojure.set :refer [difference]]
            [lupapalvelu.states :as states]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.user :as usr]))

(def timestamp-key
  (merge
    ; Currently used states
    {:draft :created
     :open :opened
     :submitted :submitted
     :sent :sent
     :complementNeeded :complementNeeded
     :verdictGiven nil
     :constructionStarted :started
     :acknowledged :acknowledged
     :foremanVerdictGiven nil
     :agreementPrepared :agreementPrepared
     :agreementSigned   :agreementSigned
     :finished :finished
     :closed :closed
     :canceled :canceled}
    ; New states, timestamps to be determined
    (zipmap
      [:appealed
       :extinct
       :hearing
       :final
       :survey
       :sessionHeld
       :proposal
       :registered
       :proposalApproved
       :sessionProposal
       :inUse
       :onHold
       :ready]
      (repeat nil))))

(assert (= states/all-application-states (set (keys timestamp-key))))

(defn state-history-entries [history]
  (filter :state history)) ; only history elements that regard state change

(defn last-history-item
  [{history :history}]
  (last (sort-by :ts history)))

(defn get-previous-app-state
  "Returns second last history item's state as keyword. Recognizes only items with not nil :state."
  [{history :history}]
  (->> (state-history-entries history)
       (sort-by :ts)
       butlast
       last
       :state
       keyword))

(defn history-entry [to-state timestamp user]
  {:state to-state, :ts timestamp, :user (usr/summary user)})

(defn state-transition-update
  "Returns a MongoDB update map for state transition"
  [to-state timestamp application user]
  {:pre [(sm/valid-state? application to-state)]}
  (let [ts-key (timestamp-key to-state)]
    {$set (merge {:state to-state, :modified timestamp}
                 (when (and ts-key (not (ts-key application))) {ts-key timestamp}))
     $push {:history (history-entry to-state timestamp user)}}))

(defn- push-history-to-$each
  "If $each exists in current, conjoins new history to that $each.
  If $each does not exist, it's created and "
  [current-his new-history]
  (if (get current-his $each)
    (update current-his $each conj new-history)
    (apply dissoc                                           ; move history entries under $each key, remove other keys
           (assoc current-his $each [current-his new-history])
           (difference (set (keys current-his)) #{$each}))))

(defn merge-updates-and-histories
  "Adds next's history to accumulators :history $push.
  Next's $set clause is merged to accumulator's $set."
  [acc next]
  (-> acc
      (update-in [$push :history] push-history-to-$each (get-in next [$push :history]))
      (update $set merge (get next $set))))

(defn state-transition-updates
  "Applies given updates using state-transition-update, but resulting in history entries pushed with $each.
  Last of the given updates will be the next state, but timestamps from each update are preserved in $set clause (merged).
  Retruns mongo update map with $set and $push keys, just like state-transition-update."
  [updates]
  {:pre [(pos? (count updates)) (every? (fn [[p1 p2 p3 p4]] (and p1 p2 p3 p4 (keyword? p1) (integer? p2) (map? p3) (map? p4))) updates)]}
  (->> updates
       (map (partial apply state-transition-update))
       (reduce merge-updates-and-histories)))
