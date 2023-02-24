(ns lupapalvelu.ui.auth-admin.ad-login.state
  (:require [lupapalvelu.ui.common :refer [loc query command]]
            [rum.core :as rum]))

(defonce component-state (atom {}))

(def saving-info? (rum/cursor-in component-state [:saving-info?]))
(def org-id (rum/cursor-in component-state [:org-id]))
(def allowed-roles (rum/cursor-in component-state [:allowed-roles]))
(def current-role-mapping (rum/cursor-in component-state [:current-role-keys]))
(def unallowed-roles #{:digitization-project-user})

(defn- save-org-info-to-state [org]
  (let [roles (->> org
                   :allowedRoles
                   (map keyword)
                   (remove unallowed-roles))
        id    (keyword (:id org))
        ad-role-keys (-> org :ad-login :role-mapping)]
    (reset! allowed-roles roles)
    (reset! org-id id)
    (reset! current-role-mapping ad-role-keys)))

(defn init-organization []
  (query "organization-by-user"
         (fn [result]
           (->> result
                :organization
                (save-org-info-to-state)))))
