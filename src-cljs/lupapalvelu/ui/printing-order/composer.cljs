(ns lupapalvelu.ui.printing-order.composer
  (:require [rum.core :as rum]
            [lupapalvelu.ui.components :as uc]))

(defonce args (atom {}))

(def log (.-log js/console))

(rum/defc order-composer < rum/reactive
          [_]
          [:div [:p "hello world"]])

(defn mount-component []
  (rum/mount (order-composer)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :dom-id (name domId))
  (mount-component))
