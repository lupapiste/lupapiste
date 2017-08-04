(ns lupapalvelu.ui.printing-order.composer
  (:require [rum.core :as rum]
            [lupapalvelu.ui.components :as uc]))

(defonce args (atom {}))

(def log (.-log js/console))

(defn mount-component []
  #_(rum/mount (stamp-editor (:auth-model @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :auth-model (aget componentParams "authModel") :dom-id (name domId))
  (mount-component))
