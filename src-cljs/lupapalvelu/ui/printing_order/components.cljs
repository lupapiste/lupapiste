(ns lupapalvelu.ui.printing-order.components
  (:require [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.printing-order.state :as state]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(rum/defc section-header [ltext]
  [:div.row
   [:div.col-4
    [:span.order-grid-header (loc ltext)]]])

(rum/defc grid-text-input < rum/reactive
  [path col-class ltext required? & {:keys [lines class]}]
  (let [state* (rum/cursor-in state/component-state path)
        id     (common/unique-id "input")]
    [:div
     {:class (map name (util/split-kw-path col-class))}
     [:label.lux
      {:class (when required? "required")
       :for   id}
      (loc ltext)]
     (components/text-edit (rum/react state*)
                              {:id        id
                               :required? required?
                               :test-id   path
                               :class     (or class :grid-style-input--wide)
                               :lines     lines
                               :callback  #(reset! state* %)})]))

(rum/defc grid-textarea-input
  [path col-class ltext required?]
  (grid-text-input path col-class ltext required? :lines 5 :class :w--100))

(rum/defc grid-radio-button < rum/reactive
  [state value col-class ltext group]
  (let [id (common/unique-id "radio")]
    [:div
     {:class (name col-class)}
     [:div.radio-wrapper
      {}
      [:input
       (common/add-test-id {:type      "radio"
                            :name      (name group)
                            :id        id
                            :checked   (= (rum/react state) value)
                            :value     value
                            :on-change #(reset! state value)}
                           [group value] :input)]
      [:label.radio-label
       (common/add-test-id {:for id} [group value] :label)
       (loc ltext)]]]))

(rum/defc grid-checkbox < rum/reactive
  [state path col-class ltext required?]
  (let [id (common/unique-id "checkbox")]
    [:div
     {:class (name col-class)}
     [:div.checkbox-wrapper
      {}
      [:input
       (common/add-test-id {:id        id
                            :type      "checkbox"
                            :checked   (true? (rum/react state))
                            :value     true
                            :on-change #(swap! state not)}
                           path :input)]
     [:label
      (common/add-test-id {:for   id
                           :class (if required?
                                    ["checkbox-label" "required"]
                                    "checkbox-label")}
                          path :label)
      (loc ltext)]]]))

(rum/defc contact-form [path]
  (let [narrow :boxitem.box--1.pad--v2.pad--r2
        wide   :boxitem.box--2.pad--v2.pad--r2]
    [:div.boxgrid
     {:data-test-id (str "form-" (clojure.string/join "-" (map name path)))}
     (grid-text-input (conj path :firstName) narrow :etunimi true)
     (grid-text-input (conj path :lastName) narrow :sukunimi true)
     (grid-text-input (conj path :companyName) wide :printing-order.company-name)
     (grid-text-input (conj path :streetAddress) wide :printing-order.address true)
     (grid-text-input (conj path :postalCode) narrow :printing-order.postal-code true)
     (grid-text-input (conj path :city) narrow :printing-order.city true)
     (grid-text-input (conj path :email) wide :printing-order.email true)
     (grid-text-input (conj path :phoneNumber) narrow :printing-order.phone)]))
