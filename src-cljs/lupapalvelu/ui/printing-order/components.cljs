(ns lupapalvelu.ui.printing-order.components
  (:require [rum.core :as rum]
            [lupapalvelu.ui.util :as util]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.printing-order.state :as state]))

(rum/defc section-header [ltext]
  [:div.row
   [:div.col-4
    [:span.order-grid-header (loc ltext)]]])

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

(rum/defcs grid-textarea-input < (rum/local "" ::text)
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
     [:textarea.grid-style-input.wide
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

(rum/defc grid-checkbox < rum/reactive
  [state col-class ltext]
  [:div
   {:class (name col-class)}
   [:div
    {:class ["checkbox-wrapper"]}
    [:input {:type    "checkbox"
             :checked (= (rum/react state) value)
             :value   value}]
    [:label.checkbox-label
     {:on-click #(swap! state not)}
     (loc ltext)]]])