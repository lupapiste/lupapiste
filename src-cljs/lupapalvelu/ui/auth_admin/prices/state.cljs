(ns lupapalvelu.ui.auth-admin.prices.state
  (:require [rum.core :as rum]))

(defonce state* (atom {:view :by-rows
                       :mode :show
                       :selected-catalogue-id nil
                       :catalogue-in-edit nil
                       }))

(defn- state-cursor [key]
  (rum/cursor-in state* [key]))

(def org-id                (state-cursor :org-id))
(def selected-catalogue-id (state-cursor :selected-catalogue-id))
(def catalogues            (state-cursor :catalogues))
(def catalogue-in-edit     (state-cursor :catalogue-in-edit))
(def view                  (state-cursor :view))
(def mode                  (state-cursor :mode))

(defn set-selected-catalogue-id [catalogue-id]
  (reset! selected-catalogue-id catalogue-id))

(defn set-catalogue-in-edit [catalogue]
  (reset! catalogue-in-edit catalogue))

(defn get-catalogue [catalogue-id]
  (some (fn [{:keys [id] :as catalogue}]
          (when (= id catalogue-id)
            catalogue))
        @catalogues))

(defn set-view [new-view]
  (reset! view new-view))

(defn set-mode [new-mode]
  (reset! mode new-mode))

(defn update-field-in-catalogue-in-edit! [row-index field new-value]
  (println ">> update-field-in-catalogue-in-edit field " field
           " row-index " row-index " value " new-value)
  (swap! catalogue-in-edit assoc-in [:rows row-index field] new-value))

(defn add-empty-row []
  (println ">> add-empty-row")
  (let [empty-row {:min-total-price nil
                   :unit nil
                   :discount-percent nil
                   :code nil
                   :operations []
                   :max-total-price nil
                   :price-per-unit nil
                   :text nil}
        prepend (fn [coll item]
                  (concat [item] coll))]
    (swap! catalogue-in-edit update :rows prepend empty-row)))
