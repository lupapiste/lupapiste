(ns lupapalvelu.ui.bulletins.state
  (:require [rum.core :as rum]))

(defonce state* (atom {}))

(defn- state-cursor [key]
  (rum/cursor-in state* [key]))

(def current-organization (state-cursor :organization))
(def local-bulletins (state-cursor :local-bulletins))
(def local-bulletins-query (state-cursor :local-bulletins-query))
(def current-bulletin (state-cursor :current-bulletin))
(def local-bulletins-page-settings (state-cursor :local-bulletins-page-settings))