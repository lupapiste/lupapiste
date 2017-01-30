(ns lupapalvelu.ui.checklist-summary
  (:require [rum.core :as rum]))

(enable-console-print!)

(def state (atom {:rows []
                  :input ""}))
(def rows  (rum/cursor-in state [:rows]))
(def texti  (rum/cursor-in state [:input]))

(rum/defc summary-row [row-data]
  [:tr [:td "Cell"] [:td (:text row-data)] [:td "Faa"]])

(defn ko-subscription [value]
  (swap! state assoc :input value))

(rum/defc checklist-summary < rum/reactive
                              {:init (fn [init-state props]
                                       (swap! state assoc :input ((-> (aget props ":rum/initial-state")
                                                                      :rum/args
                                                                      first
                                                                      (aget "texti"))))
                                       init-state)}
  [ko-app]
  (.subscribe (aget ko-app "texti") ko-subscription)
  [:div
   [:h1 "Tarkastusasiakirjan yhteenveto"]
   ; [:pre ((aget ko-app "texti")) " -> oneshot, is not updated when ko subscription changes, only when component is re-rendered"]
   [:pre (rum/react texti) "-> rum/react"]
   [:input ]
   [:table
    [:tbody
     (doall
       (for [row (rum/react rows)]
         (summary-row row)))]]
   [:button {:on-click (fn [_] (swap! rows conj {:id (str "row" (rand-int 50)) :text "Faa"}))}
    "Click"]])

(defn ^:export start [domId ko-app]
  (rum/mount (checklist-summary ko-app) (.getElementById js/document (name domId))))
