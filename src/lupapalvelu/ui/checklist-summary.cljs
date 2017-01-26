(ns lupapalvelu.ui.checklist-summary
  (:require [rum.core :as rum]))

(enable-console-print!)

(rum/defc root []
  [:h1 "Tarkastusasiakirjan yhteenveto"]
  [:table
   [:tbody
    (doall
      (for [n (take 5 (range))]
        [:tr [:td "Cell"] [:td n]]))]])

(defn ^:export start [domId]
  (js/console.log "checklist-summary" domId)
  (rum/mount (root) (.getElementById js/document (name domId))))
