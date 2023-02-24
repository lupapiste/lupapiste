(ns lupapalvelu.ui.components.accordion
  (:require [rum.core :as rum]))


(rum/defc caret-accordion-toggle-up []
  [:button [:i.lupicon-chevron-up {:style {:color "#ffffff"}}]])

(rum/defc caret-accordion-toggle-down []
  [:button [:i.lupicon-chevron-down {:style {:color "#ffffff"}}]])

(def caret-toggle {:caret-up caret-accordion-toggle-up
                   :caret-down caret-accordion-toggle-down})


(rum/defc accordion
  [open? {:keys [accordion-toggle-component
                   accordion-content-component
                   header-title-component
                   extra-class]}]
  (let [[open? set-open!] (rum/use-state open?)]
    [:div {:class-name (str extra-class " " (if open? "rollup rollup--open" "rollup rollup--closed"))}
     [:div.roll-up-header
      (when accordion-toggle-component
        [:div.accordion-toggle-container
         {:on-click #(set-open! (not open?))}
         (if open?
           ((:caret-up accordion-toggle-component))
           ((:caret-down accordion-toggle-component)))])
      (when header-title-component
        header-title-component)]

     [:div.rollup-accordion-content.rollup__open accordion-content-component]]))
