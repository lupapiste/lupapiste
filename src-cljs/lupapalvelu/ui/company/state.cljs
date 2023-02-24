(ns lupapalvelu.ui.company.state
  (:require [rum.core :as rum]))


(def empty-component-state {:report {:start-date {}
                                     :end-date   {}}})

(defonce component-state (atom empty-component-state))

(def report-start-date (rum/cursor-in component-state [:report :start-date]))
(def report-end-date (rum/cursor-in component-state [:report :end-date]))
(def report-start-ts (rum/cursor-in report-start-date [:ts]))
(def report-end-ts (rum/cursor-in report-end-date [:ts]))
(def report-start-error? (rum/cursor-in report-start-date [:error?]))
(def report-end-error? (rum/cursor-in report-end-date [:error?]))
