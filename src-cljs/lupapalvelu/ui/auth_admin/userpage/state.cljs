(ns lupapalvelu.ui.auth-admin.userpage.state
  (:require [rum.core :as rum]))

(defonce component-state (atom {:saving? false}))

(def saving-roles? (rum/cursor-in component-state [:saving-roles?]))
(def saving-info? (rum/cursor-in component-state [:saving-info?]))
(def auth-user (rum/cursor-in component-state [:auth-user]))
(def org-id (rum/cursor-in component-state [:org-id]))
(def auth-user-id-observable (rum/cursor-in component-state [:auth-user-id-observable]))
(def allowed-roles (rum/cursor-in component-state [:allowed-roles]))
