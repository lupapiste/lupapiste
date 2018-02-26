(ns lupapalvelu.ui.auth-admin.edit-authority.state
  (:require [rum.core :as rum]))

(defonce component-state (atom {}))

(def saving-roles? (rum/cursor-in component-state [:saving-roles?]))
(def saving-info? (rum/cursor-in component-state [:saving-info?]))
(def authority (rum/cursor-in component-state [:authority]))
(def org-id (rum/cursor-in component-state [:org-id]))
(def authority-id (rum/cursor-in component-state [:authority-id]))
(def allowed-roles (rum/cursor-in component-state [:allowed-roles]))
