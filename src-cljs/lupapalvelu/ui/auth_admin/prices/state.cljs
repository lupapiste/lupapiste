(ns lupapalvelu.ui.auth-admin.prices.state
  (:require [rum.core :as rum]))

(defonce state* (atom {}))

(defn- state-cursor [key]
  (rum/cursor-in state* [key]))

(def org-id                (state-cursor :org-id))
(def selected-catalogue-id (state-cursor :selected-catalogue-id))
(def catalogues            (state-cursor :catalogues))
(def view                  (state-cursor :view))

(defn set-selected-catalogue-id [catalogue-id]
  (reset! selected-catalogue-id catalogue-id))

(defn get-catalogue [catalogue-id]
  (some (fn [{:keys [id] :as catalogue}]
          (when (= id catalogue-id)
            catalogue))
        @catalogues))

(defn set-view [new-view]
  (reset! view new-view))
