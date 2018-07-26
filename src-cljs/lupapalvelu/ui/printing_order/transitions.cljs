(ns lupapalvelu.ui.printing-order.transitions
  (:require [lupapalvelu.ui.printing-order.state :as state]
            [lupapalvelu.ui.common :refer [loc]]
            [rum.core :as rum]
            [lupapalvelu.ui.rum-util :as rum-util]))

(def phase-transitions
  [{:forward-fn    state/proceed-phase2
    :forward-ltext :printing-order.phase1.button.next
    :forward-cond  (rum-util/derived-atom [state/component-state]
                                          (fn [{order :order}]
                                            (pos-int? (reduce + (vals order)))))}
   {:back-fn       state/back-to-phase1
    :back-ltext    :printing-order.phase2.button.prev
    :forward-fn    state/proceed-phase3
    :forward-ltext :printing-order.phase2.button.next
    :forward-cond  (rum-util/derived-atom [state/component-state] state/valid-order?)}
   {:back-fn       state/back-to-phase2
    :back-ltext    :printing-order.phase3.button.prev
    :forward-fn    state/submit-order
    :forward-ltext :printing-order.phase3.button.submit
    :long-operation true}
   {:forward-fn    state/back-to-application
    :forward-ltext :printing-order.phase4.button.back-to-application}])

(rum/defc forward-button < rum/reactive [{:keys [forward-fn forward-cond forward-ltext long-operation]}]
  (let [submit-pending? (rum/react (rum/cursor state/component-state :submit-pending?))
        args (merge {:class ["positive" (when submit-pending? "waiting")]
                     :data-test-id "forward-button"
                     :on-click #(do
                                  (forward-fn)
                                  (when-not long-operation
                                    (js/scrollTo 0 0)))}
                    (when forward-cond
                      {:disabled (false? (rum/react forward-cond))}))]
    [:button
     args
     [:span (loc forward-ltext)]
     [:i.lupicon-chevron-right]
     [:i.wait.spin.lupicon-refresh]]))

(rum/defc transition-buttons < rum/reactive [phase]
  (let [{:keys [back-fn back-ltext forward-fn] :as opts} (get phase-transitions (dec phase))]
    [:div.operation-button-row
     (when back-fn
       [:button.secondary
        {:data-test-id "back-button"
         :on-click #(do
                     (back-fn)
                     (js/scrollTo 0 0))}
        [:i.lupicon-chevron-left]
        [:span (loc back-ltext)]])
     (when forward-fn
       (forward-button opts))]))
