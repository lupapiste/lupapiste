(ns lupapalvelu.ui.printing-order.transitions
  (:require [lupapalvelu.ui.common :refer [loc]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.printing-order.state :as state]
            [lupapalvelu.ui.rum-util :as rum-util]
            [rum.core :as rum]))

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
  (let [submit-pending? (rum/cursor state/component-state :submit-pending?)]
    (components/icon-button {:class    :primary
                             :wait?    submit-pending?
                             :test-id  :forward-button
                             :on-click #(do
                                          (forward-fn)
                                          (when-not long-operation
                                            (js/scrollTo 0 0)))
                             :enabled? forward-cond
                             :text-loc forward-ltext
                             :icon     :lupicon-chevron-right})))

(rum/defc transition-buttons < rum/reactive [phase]
  (let [{:keys [back-fn back-ltext forward-fn] :as opts} (get phase-transitions (dec phase))]
    [:div.dsp--flex.flex--gap2.flex--wrap
     (when back-fn
       (components/icon-button {:class :secondary
                                :test-id :back-button
                                :on-click #(do
                                             (back-fn)
                                             (js/scrollTo 0 0))
                                :icon :lupicon-chevron-left
                                :text-loc back-ltext}))
     (when forward-fn
       (forward-button opts))]))
