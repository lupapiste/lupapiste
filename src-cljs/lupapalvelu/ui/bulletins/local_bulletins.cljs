(ns lupapalvelu.ui.bulletins.local-bulletins
  (:require [rum.core :as rum]))

(defonce args (atom {}))

(rum/defc local-bulletins < rum/reactive
  [_]
  [:div.full.content.orange-bg
   [:div.content-center
    [:h1.slogan "Kunnan julkipanolista"]
    [:p.ingress.intro "Kunnan julkipanolista"]]])

(defn mount-component []
  (rum/mount (local-bulletins)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :dom-id (name domId))
  (mount-component))
