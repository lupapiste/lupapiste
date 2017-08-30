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
    :forward-ltext :printing-order.phase3.button.submit}])

(rum/defc transition-buttons < rum/reactive [phase]
  (let [{:keys [back-fn back-ltext forward-fn forward-ltext
                forward-cond]} (get phase-transitions (dec phase))]
    [:div.operation-button-row
     (when back-fn
       [:button.secondary
        {:on-click back-fn}
        [:i.lupicon-chevron-left]
        [:span (loc back-ltext)]])
     (when forward-fn
       (let [args (merge {:on-click forward-fn}
                         (when forward-cond
                           {:disabled (false? (rum/react forward-cond))}))]
       [:button.positive
        args
        [:span (loc forward-ltext)]
        [:i.lupicon-chevron-right]]))]))
