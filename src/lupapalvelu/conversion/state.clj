(ns lupapalvelu.conversion.state
  "Converted application state/history resolution and manipulation. From the
  conversion/batchrun point of view, an application state is divined from the following
  sources:

  - KuntaGML: `yht:Tilamuutos` elements are mapped into Lupapiste application states. The
  default mapping can be supplemented with `:state-mapping` configuration property.

  - Verdicts: If a converted application has a verdict, the application state cannot
  remain in a pre-verdict state and is forwarded to a post-verdict state if needed.

  - `:force-terminal-state?` configuration property forwards application state to a
  terminal state, if possible.

  - `:state-overrides` configuration property defines the target states for individual
  applications/backend-ids. Overrides `:force-terminal-state?` for those applications.

  The table below shows how the conflicting resolution candidates are resolved. One lesson
  is that `:force-terminal-state?` and `:state-overrides` properties should not be mixed,
  since it can yield unexpected results.

  +----------+---------+----------+----------+
  | KuntaGML | Verdict | Terminal | Override |
  +----------+---------+----------+----------+
  |    W     |         |          |          |
  +----------+---------+----------+----------+
  |    X     |    W    |          |    X     |
  +----------+---------+----------+----------+
  |    X     |    X    |    W     |          |
  +----------+---------+----------+----------+
  |    X     |         |    X     |    W     |
  +----------+---------+----------+----------+
  |    X     |    W    |    X     |    X     |
  +----------+---------+----------+----------+

  X: The resolution source is applicable to the application.
  W: The winning resolution source for the row."
  (:require [clojure.set :as set]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.schemas :as conv-sc]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(defn- determine-terminal-state [application]
  (->> application
       sm/application-state-seq
       (remove #{:canceled})
       last))

(defn- kw-set-or-nil [kw-or-set]
  (util/pcond->> kw-or-set
    keyword  (conj #{})
    identity not-empty))

(defn- select-valid-state
  "`candidate` is either a keyword or keyword set."
  [application candidate]
  (let [valid-states (set (keys (sm/state-graph application)))]
    (some->> (kw-set-or-nil candidate)
             (some valid-states))))

(defn- terminal-state? [application state]
  (states/terminal-state? (sm/state-graph application) state))

(defn- states->map
  "`data` is either map or vector of pairs. For a map, the entries are processed in the
  default order, so the source data should not make any assumptions."
  ([data start]
   (some->> data
            (keep-indexed (fn [i [from to]]
                            (when-let [states (kw-set-or-nil to)]
                              (when (ss/not-blank? from)
                                [from {:order  (+ start i)
                                       :states states}]))))
            seq
            (into {})))
  ([data]
   (states->map data 0)))

(def ^:private supported-state-map
  "The ordering is meaningful: later states are prioritized when two states have the same timestamp."
  (states->map [["uusi lupa, ei käsittelyssä" :open]
                ["vireillä" :submitted]
                ["lupa käsitelty, siirretty päättäjälle" #{:underReview :sent}]
                ["lupa hyväksytty" #{:verdictGiven :foremanVerdictGiven :ready :acknowledged}]
                ["lupa evätty" #{:verdictGiven :foremanVerdictGiven :ready :acknowledged}]
                ["päätöksestä valitettu, valitusprosessin tulosta ei ole" :appealed]
                ["rakennustyöt aloitettu" :constructionStarted]
                ["rakennustyöt keskeytetty" :onHold]
                ["osittainen loppukatselmus, yksi tai useampia luvan rakennuksista on käyttöönotettu" :inUse]
                ["yksi tai useampia luvan rakennuksista on käyttöönotettu" :inUse]
                ["lopullinen loppukatselmus tehty" #{:closed :ready}]
                ["luvalla ei loppukatselmusehtoa, lupa valmis" #{:closed :ready}]
                ["lupa rauennut" :extinct]]))

(defn- make-state-map
  "Combines the default `supported-state-map` with the (optional) `:state-mapping` from
  `cfg`. The overlapping keys retain their order values but new ones have higher order."
  [cfg]
  (reduce (fn [acc [k {:keys [states] :as v}]]
            (if (get acc k)
              (assoc-in acc [k :states] states)
              (assoc acc k v)))
          supported-state-map
          (states->map (:state-mapping cfg) 100)))

(defn- state-override-or-nil
  "Returns `:state`, `:ts` map or nil. `:state` value is a singular state that is valid for
  `application`. `:ts` value fallbacks to the current time."
  [{:keys [state-overrides]} backend-id application]
  (when-let [{:keys [ts state] :as r} (get state-overrides backend-id)]
    (when-let [state (select-valid-state application (or state r))]
      (let [now (date/now)]
        (some->> (cond
                   (nil? ts)             now
                   (date/before? ts now) ts
                   :else                 (log/error "Bad state-override timestamp:" r))
                 date/timestamp
                 (hash-map :state state :ts))))))

(defn generate-history-array
  ([{:keys [force-terminal-state?] :as cfg}
    {:keys [backend-id]}
    xml
    application]
   (let [user                 (usr/summary conv-util/conversion-user-data)
         now                  (date/now)
         state-map            (make-state-map cfg)
         {over-ts :ts over-state :state
          :as     overridden} (state-override-or-nil cfg backend-id application)
         history              (->> (for [{:keys [pvm tila]} (conv-util/kuntagml-states xml)
                                         :let               [{:keys [order states]} (get state-map tila)
                                                             state (select-valid-state application states)
                                                             ts (or (date/timestamp pvm) 0)]
                                         ;; We assume that future entries are errors
                                         :when              (and state (date/before? ts now))]
                                     {:state state
                                      :ts    ts
                                      :user  user
                                      :order order})
                                   (sort-by (juxt :ts :order))
                                   (map #(dissoc % :order))
                                   distinct)
         last-state           (some->> history last :state)
         terminal?            (some->> last-state (terminal-state? application))]

     (cond
       overridden
       (cond-> history
         (not= over-state last-state) (util/conjv {:state over-state
                                                   :ts    over-ts
                                                   :user  user}))

       (or (empty? history)
           (and force-terminal-state? (not terminal?)))
       (util/conjv history {:state (determine-terminal-state application)
                            :ts    (date/timestamp now)
                            :user  user})

       :else history)))
  ([cfg document application]
   (generate-history-array cfg document (conv-doc/read-xml document) application)))

(defn history-timestamps
  "Application timestamp key map for the given history array. Nil if no supported states."
  [history-array]
  (some->> history-array
           (keep (fn [{:keys [state ts]}]
                   (some-> state keyword app-state/timestamp-key (vector ts))))
           seq
           (into {})))

(defn- forward-verdict-state
  "Returns the verdict state if the application state needs to be forwarded. Nil
  otherwise. Does not update mongo."
  [application]
  (when (and (:verdictDate application)
             (util/includes-as-kw? states/pre-verdict-but-draft (:state application)))
    (sm/verdict-given-state application)))

(defn- set-application-state
  ([application state ts]
   (let [user   (usr/summary conv-util/conversion-user-data)
         ts     (date/timestamp (or ts (date/now)))
         ts-key (app-state/timestamp-key state)]
     (mongo/update-by-id :applications
                         (:id application)
                         {$set  (cond-> {:state state}
                                  ts-key (assoc ts-key ts))
                          $push {:history {:state state
                                           :ts    ts
                                           :user  user}}})
     state))
  ([application state]
   (set-application-state application state nil)))

(defn forward-to-verdict-state-if-needed
  "If the application corresponding to `application-or-id` has a verdict but is in a
  pre-verdict state, updates the state to `sm/verdict-given-state`. Returns the new state
  or nil."
  [application-or-id]
  (let [application (util/pcond->> application-or-id
                      string? (mongo/by-id :applications))]
    (some->> (forward-verdict-state application)
             (set-application-state application))))

(defn- calculate-state
  "Minor optimisation that parses `application` history from `document` xml only if
  needed. Returns a valid `:state` (optional) `:ts` map or nil. The latter can happen only
  if neither `state-overrides` nor history has valid states that are supported by the
  `application` state machine."
  [cfg {:keys [backend-id] :as document} application]
  (let [overridden (state-override-or-nil cfg backend-id application)]
    (when-let [calc-state (or overridden
                              (some-> (generate-history-array cfg document application)
                                      last (select-keys [:state :ts])))]
      (or (some->>  (:state calc-state)
                    (assoc application :state)
                    forward-verdict-state
                    (hash-map :state))
          calc-state))))

(sc/defn fix-state
  "Updates the last/current state of the document application, if it is in conflict with
  `cfg`. However, if the state has been changed by a real user, nothing is done."
  [cfg :- conv-sc/ConversionConfiguration
   {app-id :LP-id reset-state :reset-state :as document} :- conv-sc/ConversionDocument]
  {:pre [(:converted document)]}
  ;; `state` is the current application state
  ;; `calc-state` is the re-calculated 'fixed' state. If the applcation state needs
  ;; fixing, this is the new state.
  ;; `reset-state` is the initial application state after a successful conversion.
  (let [{:keys [history state]
         :as   application} (mongo/by-id :applications app-id)
        _                   (assert (map? application) app-id)
        {calc-state :state
         calc-ts    :ts}    (calculate-state cfg document application)
        user                (->> history reverse
                                 (util/find-by-key :state state)
                                 :user)
        conv-user?          (conv-util/conversion-user? user)
        batch-user?         (usr/batchrun-user? user)]
    (when (and (some->> calc-state (util/not=as-kw state))
               (or conv-user?
                   (and batch-user? (util/=as-kw state reset-state))))
      (log/infof "Fix state from %s to %s." (keyword state) (keyword calc-state))
      (set-application-state application calc-state calc-ts)
      (conv-doc/upsert-document :fix-states document))))

(defn- merge-histories
  "Add _non-state_ entries from `old-history` to `new-history`."
  [old-history new-history]
  (loop [[{old-ts :ts :as ox}
          & oxs :as olds]    (->> old-history
                                  (remove (some-fn :state empty?))
                                  (sort-by :ts))
         [nx & nxs :as news] new-history
         result              []]
    (cond
      (nil? ox)                (into result news)
      (or (nil? nx) (nil? old-ts)
          (< old-ts (:ts nx))) (recur oxs news (conj result ox))
      :else                    (recur olds nxs (conj result nx)))))

(defn- fix-history--updates
  "Mongo update map or nil. `new-history` must have been created via
  `generate-history-array` and `new-state` is its last state."
  [{:keys [history state]} new-history new-state]
  (when-not (and (= (keep (comp keyword :state) history)
                    (keep :state new-history))
                 (util/=as-kw state new-state))
    (let [;; Created and opened timestamps are always retained
          old-ts-keys (disj (set (keys (history-timestamps history)))
                            :created :opened)
          new-ts      (cond-> (history-timestamps new-history)
                        ;; Make sure that non-cancelled application does not include
                        ;; canceled timestamp.
                        (util/not=as-kw new-state :canceled) (dissoc :canceled))
          unset       (some-> (set/difference old-ts-keys (set (keys new-ts)))
                              not-empty
                              (zipmap (repeat true)))]
      (merge (some->> unset (hash-map $unset))
             {$set (merge {:history (merge-histories history new-history)
                           :state   new-state}
                          new-ts)}))))

(sc/defn fix-history
  "Reinitializes state parts of the `application` history according to conversion
  `document`."
  [cfg :- conv-sc/ConversionConfiguration
   document :- conv-sc/ConversionDocument
   {app-id :id :as application}]
  {:pre [(:converted document) application]}
  (let [xml         (conv-doc/read-xml document)
        new-history (generate-history-array cfg document xml application)
        new-state   (:state (last new-history))
        updates     (fix-history--updates application new-history new-state)]
    (some->> updates (mongo/update-by-id :applications app-id))
    (let [verdict-state (forward-to-verdict-state-if-needed app-id)]
      (when (or verdict-state updates)
        (conv-doc/upsert-document :fix-history
                                  (assoc document :reset-state (or verdict-state
                                                                   new-state)))))))
