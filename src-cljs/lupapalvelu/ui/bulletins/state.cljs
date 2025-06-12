(ns lupapalvelu.ui.bulletins.state
  (:require [rum.core :as rum]))

(defonce state* (atom {}))

(defn- state-cursor [key]
  (rum/cursor-in state* [key]))

(def current-organization (state-cursor :organization))
(def local-bulletins (state-cursor :local-bulletins))
(def local-bulletins-query (state-cursor :local-bulletins-query))
(def local-bulletins-page-settings
  "Texts for the julkipano bulletins page"
  (state-cursor :local-bulletins-page-settings))
(def local-kuulutus-page-settings
  "Texts for the kuulutus bulletins page"
  (state-cursor :local-kuulutus-page-settings))
(def local-bulletins-process
  "Choose whether to display julkipano or kuulutus bulletins"
  (state-cursor :local-bulletins-process))
