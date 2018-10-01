(ns lupapalvelu.ui.components.accordion
  (:require [rum.core :as rum]))


(rum/defc caret-accordion-toggle-up []
  [:button "foo"])

(rum/defc caret-accordion-toggle-down []
  [:button "foo"])

(def caret-toggle {:caret-up caret-accordion-toggle-up
                   :caret-down caret-accordion-toggle-down})


(rum/defcs accordion < (rum/local true ::is-open?)
  [state {:keys [accordion-toggle-component
                 accordion-content-component
                 extra-class] :as opts}]
  (let [is-open? (::is-open? state)]
    [:div {:class-name (str extra-class " " (if @is-open? "rollup rollup--open" "rollup rollup--closed"))}
     [:div.roll-up-header
      (if accordion-toggle-component [:div.accordion-toggle-container
                                      {:on-click #(reset! is-open? (not @is-open?))}
                                      (if is-open?
                                        ((:caret-up accordion-toggle-component))
                                        ((:caret-down accordion-toggle-component)))])]
     [:div.rollup-accordion-content.rollup__open accordion-content-component]]))
