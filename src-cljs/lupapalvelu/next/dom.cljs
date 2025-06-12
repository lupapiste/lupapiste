(ns lupapalvelu.next.dom
  (:require [re-frame.core :as rf]
            [goog.object :as g]))

(rf/reg-fx :dom/title
  (fn [title]
    (g/set js/document "title" title)))

(rf/reg-event-fx ::set-title
  [rf/trim-v]
  (fn [_ [title]]
    {:dom/title title}))
