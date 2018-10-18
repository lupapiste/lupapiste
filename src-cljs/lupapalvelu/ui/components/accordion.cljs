(ns lupapalvelu.ui.components.accordion
  (:require [rum.core :as rum]
            [lupapalvelu.ui.components :refer [initial-value-mixin]]))


(rum/defc caret-accordion-toggle-up []
  [:button [:i.lupicon-chevron-up {:style {:color "#ffffff"}}]])

(rum/defc caret-accordion-toggle-down []
  [:button [:i.lupicon-chevron-down {:style {:color "#ffffff"}}]])

(def caret-toggle {:caret-up caret-accordion-toggle-up
                   :caret-down caret-accordion-toggle-down})


(rum/defcs accordion < (initial-value-mixin ::open?)
  rum/reactive
  [state _ {:keys [accordion-toggle-component
                   accordion-content-component
                   header-title-component
                   extra-class] :as opts}]
  (let [is-open? (::open? state)]
    [:div {:class-name (str extra-class " " (if (rum/react is-open?) "rollup rollup--open" "rollup rollup--closed"))}
     [:div.roll-up-header
      (if accordion-toggle-component [:div.accordion-toggle-container
                                      {:on-click #(reset! is-open? (not @is-open?))}
                                      (if @is-open?
                                        ((:caret-up accordion-toggle-component))
                                        ((:caret-down accordion-toggle-component)))])
      (if header-title-component header-title-component)]

     [:div.rollup-accordion-content.rollup__open accordion-content-component]]))
