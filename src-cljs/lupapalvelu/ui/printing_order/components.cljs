(ns lupapalvelu.ui.printing-order.components
  (:require [rum.core :as rum]
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
        _ (common/reset-if-needed! text* @state)
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
      {:data-test-id (str "input-" (clojure.string/join "-" (map name path)))
       :type "text"
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
  [state path col-class ltext required?]
  [:div
   {:class (name col-class)}
   [:div
    {:class "checkbox-wrapper"
     :data-test-id (str "checkbox-" (clojure.string/join "-" (map name path)))}
    [:input {:type    "checkbox"
             :checked (true? (rum/react state))
             :value   true}]
    [:label
     {:on-click #(swap! state not)
      :class (if required?
               ["checkbox-label" "required"]
               "checkbox-label")}
     (loc ltext)]]])

(rum/defc contact-form [path]
  [:div
   {:data-test-id (str "form-" (clojure.string/join "-" (map name path)))}
   [:div.row
    (grid-text-input (conj path :firstName) :col-1 :etunimi true)
    (grid-text-input (conj path :lastName) :col-1 :sukunimi true)
    (grid-text-input (conj path :companyName) :col-2 :printing-order.company-name)]
   [:div.row
    (grid-text-input (conj path :streetAddress) :col-2 :printing-order.address true)
    (grid-text-input (conj path :postalCode) :col-1 :printing-order.postal-code true)
    (grid-text-input (conj path :city) :col-1 :printing-order.city true)]
   [:div.row
    (grid-text-input (conj path :email) :col-2 :printing-order.email true)
    (grid-text-input (conj path :phoneNumber) :col-1 :printing-order.phone)]])