(ns lupapalvelu.ui.matti.state
  (:require [rum.core :as rum]))

(defonce state* (atom {}))

(defn- state-cursor [key]
  (rum/cursor-in state* [key]))

(def current-template (state-cursor :current-template))
(def current-view     (state-cursor :current-view))
(def current-category (state-cursor :current-category))
(def schemas          (state-cursor :schemas))
(def template-list    (state-cursor :template-list))
(def categories       (state-cursor :categories))
(def references       (state-cursor :references))
(def settings         (rum/cursor-in references [:settings]))
(def reviews          (rum/cursor-in references [:reviews]))
(def plans            (rum/cursor-in references [:plans]))
(def phrases          (state-cursor :phrases))
(def application-id   (state-cursor :application-id))
(def current-verdict  (state-cursor :current-verdict))
(def verdict-list     (state-cursor :verdict-list))

(defn select-keys [state ks]
  (reduce (fn [acc k]
            (assoc acc k (rum/cursor-in state [k])))
          {}
          ks))
