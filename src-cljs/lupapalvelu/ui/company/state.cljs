(ns lupapalvelu.ui.company.state
  (:require [rum.core :as rum]))


(def empty-component-state {:report {:start-date nil
                                     :end-date   nil}})

(defonce component-state (atom empty-component-state))

(def report-start-date (rum/cursor-in component-state [:report :start-date]))
(def report-end-date (rum/cursor-in component-state [:report :end-date]))
