(ns lupapalvelu.next.util)

(defn- update-cofx
  [state [f & args]]
  ;; invoke `f` as if we're running an event handler (`(fn [cofx [event-id & args]])`)
  (let [{db :db fx :fx :as effects} (apply f (:cofx state)
                                           ;; Here `nil` is a placeholder for the event keyword
                                           [(cons nil args)])]
    ;; a reg-event-fx event handler should return a map
    (when-not (or (nil? effects) (map? effects))
      (throw (ex-info "not a map nor nil" {:effects effects})))
    ;; Merging unknown effects is ambiguous -> forbid
    (when-let [unsupported (->> effects keys (remove #{:fx :db}) seq)]
      (throw (ex-info "only :fx and :db supported" {:got unsupported})))
    (cond-> state
      ;; Collect new fxes, drop duplicates
      (some? fx) (update :fx (comp vec distinct into) fx)
      ;; Update the db coeffect
      (some? db) (assoc-in [:cofx :db] db))))

(defn merge-every-fn
  "For each pair of function `f` and `args` in `fns`, applies `f` with `cofx` and `args`
  as if `f` was a re-frame reg-event-fx event handler.
  Each function `f` should return an effect map, similar to `reg-event-fx`.
  Only `:db` and `:fx` keys are supported.
  Resulting effects are merged, `:fx`s are concatenated and deduped.

  Motivation:
  Composing effects by dispatching multiple related events one-by-one might cause
  issues as re-frame may refresh subscriptions between the events. `merge-every-fn`
  allows for composing the individual functions and applying the effects atomically
  within a single re-frame event.

  Example:
  ```clojure
  (defn my-foo [{:keys [db]} [_ counter]]
    {:db (assoc db :counter counter)})

  (defn my-bar [_ [_ token]]
    {:fx [[::fx/foo token]]})

  (rf/reg-event-fx ::foo
    (fn [cofx _]
      (merge-every-fn cofx [[my-foo 5]
                            [my-bar \"a\"]])))
  ```
  "
  [cofx fns]
  (let [{:keys [cofx fx]} (reduce update-cofx
                                  {:cofx cofx}
                                  (keep identity fns))]
    (cond-> {:db (-> cofx :db)}
      (seq fx) (assoc :fx fx))))
