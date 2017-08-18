(ns lupapalvelu.ui.printing-order.components
  (:require [rum.core :as rum]
            [lupapalvelu.ui.util :as util]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.printing-order.state :as state]))

(rum/defcs grid-text-input < (rum/local "" ::text)
  [local-state path col-class ltext required?]
  (let [text* (::text local-state)
        state (rum/cursor-in state/component-state path)
        commit-fn (fn [v]
                    (reset! text* (-> v .-target .-value))
                    (reset! state @text*))]
    [:div
     {:class (name col-class)}
     [:label
      (when required?
        {:class "required"})
      (loc ltext)]
     [:input.grid-style-input--wide
      {:type "text"
       :value     @text*
       :on-change identity ;; A function is needed
       :on-blur   commit-fn}]]))

(rum/defc grid-radio-button < rum/reactive
  [state value col-class ltext]
  [:div
   {:class (name col-class)}
   [:div
    {:class ["radio-wrapper"]}
    [:input {:type    "radio"
             :checked (= (rum/react state) value)
             :value   value}]
    [:label.radio-label
     {:on-click #(reset! state value)}
     (loc ltext)]]])