(ns lupapalvelu.ui.checklist-summary
  (:require [rum.core :as rum]))

(enable-console-print!)

(rum/defc checklist-summary < {:will-unmount (fn [state] (println "Will-unmount" state) state)
                               :will-mount   (fn [state] (println "Will-mount" state) state)}
  []
  [:h1 "Tarkastusasiakirjan yhteenveto"]
  [:table
   [:tbody
    (doall
      (for [n (take 5 (range))]
        [:tr [:td "Cell"] [:td (js/util.randomElementId (str "id-" n "-"))] [:td "Faa"]]))]])

(defn ^:export start [domId]
  (rum/mount (checklist-summary) (.getElementById js/document (name domId))))
