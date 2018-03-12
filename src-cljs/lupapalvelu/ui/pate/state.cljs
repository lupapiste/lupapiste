(ns lupapalvelu.ui.pate.state
  (:refer-clojure :exclude [select-keys])
  (:require [rum.core :as rum]))

(defonce state* (atom {}))

(defn- state-cursor [key]
  (rum/cursor-in state* [key]))

(def current-template  (state-cursor :current-template))
(def current-view      (state-cursor :current-view))
(def current-category  (state-cursor :current-category))
(def template-list     (state-cursor :template-list))
(def categories        (state-cursor :categories))
(def references        (state-cursor :references))
(def settings          (rum/cursor-in references [:settings]))
(def settings-info     (state-cursor :settings-info))
(def reviews           (rum/cursor-in references [:reviews]))
(def plans             (rum/cursor-in references [:plans]))
(def phrases           (state-cursor :phrases))
(def application-id    (state-cursor :application-id))
(def current-verdict   (state-cursor :current-verdict))
(def verdict-list      (state-cursor :verdict-list))

;; ok function of the currently active authModel.
(defonce auth-fn           (atom nil))

(defn select-keys [state ks]
  (reduce (fn [acc k]
            (assoc acc k (rum/cursor-in state [k])))
          {}
          ks))

(defn auth? [action]
  (boolean (when-let [auth @auth-fn]
             (auth (name action)))))
