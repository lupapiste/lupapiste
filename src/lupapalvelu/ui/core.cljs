(ns lupapalvelu.ui.core
  (:require [rum.core :as rum]))

(enable-console-print!)

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Hello world from rum!"}))

(rum/defc hello-world []
          [:h1 (:text @app-state)])

(rum/mount (hello-world)
           (. js/document (getElementById "app")))

(defn on-js-reload []
      ;; optionally touch your app-state to force rerendering depending on
      ;; your application
      ;; (swap! app-state update-in [:__figwheel_counter] inc)
      )

(defn ^:export start [mount-node]
  (rum/mount (hello-world) mount-node))
