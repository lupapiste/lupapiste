(ns lupapalvelu.ui.checklist-summary
  (:require [rum.core :as rum]))

(enable-console-print!)

(def state (atom {:rows []}))
(def rows  (rum/cursor-in state [:rows]))

(rum/defc summary-row [row-data]
  [:tr [:td "Cell"] [:td (:text row-data)] [:td "Faa"]])

(rum/defc checklist-summary < rum/reactive
  []
  [:div
   [:h1 "Tarkastusasiakirjan yhteenveto"]
   [:table
    [:tbody
     (doall
       (for [row (rum/react rows)]
         (summary-row row)))]]
   [:button {:on-click (fn [_] (swap! rows conj {:id (str "row" (rand-int 50)) :text "Faa"}))}
    "Click"]])

(defn ^:export start [domId]
  (rum/mount (checklist-summary) (.getElementById js/document (name domId))))
