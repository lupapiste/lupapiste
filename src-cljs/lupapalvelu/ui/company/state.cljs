(ns lupapalvelu.ui.company.state
  (:require [rum.core :as rum]
            [cljs-time.coerce :as tc]))


(def empty-component-state {:report {:start-date nil
                                     :end-date   nil}})

(defonce component-state (atom empty-component-state))

(def report-start-date (rum/cursor-in component-state [:report :start-date]))
(def report-end-date (rum/cursor-in component-state [:report :end-date]))
(def report-start-date-ts )

(defn will-unmount [& _]
  (reset! component-state empty-component-state))
