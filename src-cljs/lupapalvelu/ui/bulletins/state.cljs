(ns lupapalvelu.ui.bulletins.state
  (:require [rum.core :as rum]))

(defonce state* (atom {}))

(defn- state-cursor [key]
  (rum/cursor-in state* [key]))

(def current-organization (state-cursor :organization))
(def local-bulletins (state-cursor :local-bulletins))
(def current-bulletin (state-cursor :current-bulletin))